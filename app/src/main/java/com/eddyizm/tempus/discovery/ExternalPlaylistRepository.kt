package com.eddyizm.tempus.discovery

import androidx.annotation.Keep
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.App
import com.eddyizm.tempus.repository.PlaylistRepository
import com.eddyizm.tempus.subsonic.base.ApiResponse
import com.eddyizm.tempus.subsonic.api.playlist.PlaylistClient
import com.eddyizm.tempus.subsonic.api.searching.SearchingClient
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.subsonic.models.Playlist
import com.eddyizm.tempus.subsonic.models.SubsonicResponse
import com.eddyizm.tempus.util.Preferences
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.awaitResponse
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Keep data class ImportDownload(val id: String, val title: String, val artists: List<String>, val duration: Int?, val url: String)
@Keep data class ImportBinding(
    val version: Int = 1, val provider: String, val sourceId: String, val chart: Boolean,
    val url: String, val updatedAt: Long, val requestId: String = UUID.randomUUID().toString(),
    val total: Int, val missing: List<ImportDownload>, val complete: Boolean = true,
) {
    fun source() = ExternalPlaylistRef(ChartSource.Provider.valueOf(provider), sourceId, chart)
    fun comment(): String = (PREFIX + Gson().toJson(this)).also {
        require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "待下载清单过大，请先入库部分音乐或选择较小的歌单；此次不会截断保存" }
    }
    companion object {
        const val PREFIX = "Tempus external playlist v1\n"
        // Playlist rows are cached by Room; keep the metadata comfortably within CursorWindow.
        const val MAX_BYTES = 1_000_000
        fun read(comment: String?): ImportBinding? = runCatching {
            if (comment?.startsWith(PREFIX) != true || comment.length > MAX_BYTES || comment.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return null
            Gson().fromJson(comment.removePrefix(PREFIX), ImportBinding::class.java).also {
                require(it.version == 1 && it.source().url == it.url && it.total in 0..ExternalPlaylistParser.MAX_TRACKS)
                require(ExternalPlaylistRef.parse(it.url) == it.source())
                require(it.missing.size <= it.total)
            }
        }.getOrNull()
    }
}

data class ImportedPlaylist(val playlist: Playlist, val binding: ImportBinding)

@OptIn(UnstableApi::class)
class ExternalPlaylistRepository(
    private val playlists: PlaylistClient = App.getSubsonicClientInstance(false).playlistClient,
    private val searching: SearchingClient = App.getSubsonicClientInstance(false).searchingClient,
    private val drafts: android.content.SharedPreferences = App.getContext().getSharedPreferences("external_playlist_drafts", android.content.Context.MODE_PRIVATE),
    private val identity: () -> Pair<String?, String?> = { Preferences.getServerId() to Preferences.getUser() },
    private val notifySaved: () -> Unit = { PlaylistRepository().notifyPlaylistChanged() },
    private val notifyHub: ((String, ImportBinding, ExternalPlaylist, List<ChartTrack>, Map<Int, Child>) -> Unit)? = null,
) {
    private val initialIdentity = identity()
    private val server = initialIdentity.first
    private val owner = initialIdentity.second
    private val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(12, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).callTimeout(35, TimeUnit.SECONDS).build()

    private fun requireServer() = check(initialIdentity == identity()) { "音乐服务器或账号已切换，请重新打开导入页面" }

    private suspend fun execute(call: retrofit2.Call<ApiResponse>): SubsonicResponse {
        requireServer()
        val response = call.awaitResponse()
        requireServer()
        val body = response.body()?.subsonicResponse
        if (!response.isSuccessful || body?.status != "ok") throw IOException("服务器未完成操作，请检查连接和歌单权限后重试")
        return body
    }

    suspend fun imports(): List<ImportedPlaylist> = withContext(Dispatchers.IO) {
        execute(playlists.playlists).playlists?.playlists.orEmpty()
            .filter { it.owner == owner }.mapNotNull { p -> ImportBinding.read(p.comment)?.let { ImportedPlaylist(p, it) } }
    }

    private suspend fun fetch(url: String, referer: String): Pair<String?, String> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val call = http.newCall(Request.Builder().url(url).header("Referer", referer).header("User-Agent", "Mozilla/5.0 Tempus/1.0").build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use {
                            if (it.code in 300..399) it.header("Location") to ""
                            else {
                                if (!it.isSuccessful) throw IOException("平台暂时无法读取歌单（${it.code}）")
                                val bytes = it.body?.byteStream()?.readNBytes(8 * 1024 * 1024 + 1) ?: throw IOException("平台返回空内容")
                                require(bytes.size <= 8 * 1024 * 1024) { "歌单数据过大" }
                                null to bytes.toString(Charsets.UTF_8)
                            }
                        }
                        if (continuation.isActive) continuation.resume(result)
                    } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                }
            })
        }
    }

    suspend fun load(text: String): ExternalPlaylist {
        var uri = ExternalPlaylistRef.extract(text)
        repeat(5) {
            if (uri.host !in ExternalPlaylistRef.shortHosts) return@repeat
            val (redirect, _) = fetch(uri.toString(), "https://${uri.host}/")
            require(redirect != null) { "无法展开分享短链接，请在平台复制完整歌单链接" }
            uri = ExternalPlaylistRef.extract(uri.resolve(redirect).toString())
        }
        val source = ExternalPlaylistRef.parse(uri.toString())
        val endpoint = when {
            source.provider == ChartSource.Provider.NETEASE -> "https://music.163.com/api/playlist/detail?id=${source.id}"
            source.chart -> "https://c.y.qq.com/v8/fcg-bin/fcg_v8_toplist_cp.fcg?tpl=3&page=detail&type=top&topid=${source.id}&song_begin=0&song_num=5000&format=json&inCharset=utf8&outCharset=utf-8"
            else -> "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg?type=1&json=1&utf8=1&onlysong=0&disstid=${source.id}&format=json"
        }
        val (redirect, body) = fetch(endpoint, if (source.provider == ChartSource.Provider.NETEASE) "https://music.163.com/" else "https://y.qq.com/")
        require(redirect == null) { "平台要求登录或跳转，请在客户端确认歌单可以分享" }
        return withContext(Dispatchers.Default) { ExternalPlaylistParser.parse(source, body) }
    }

    suspend fun match(playlist: ExternalPlaylist, progress: (Int, Int) -> Unit): List<ChartTrack> = withContext(Dispatchers.Default) { coroutineScope {
        val counter = AtomicInteger()
        val queries = playlist.tracks.distinctBy { MusicDiscoveryRules.normalize(it.title) }
        val permits = Semaphore(3)
        val candidates = queries.map { row -> async { permits.withPermit {
            val songs = execute(searching.search3(row.title, 40, 0, 0, 0, 0, 0)).searchResult3?.songs.orEmpty()
            progress(counter.incrementAndGet(), queries.size)
            MusicDiscoveryRules.normalize(row.title) to songs
        } } }.awaitAll().toMap()
        playlist.tracks.map { row ->
            val songs = candidates[MusicDiscoveryRules.normalize(row.title)].orEmpty()
            row.matched(MusicDiscoveryRules.matches(row, songs), songs)
        }
    } }

    suspend fun save(source: ExternalPlaylist, rows: List<ChartTrack>, choices: Map<Int, Child>): ImportedPlaylist = withContext(Dispatchers.IO) { saveLock.withLock {
        requireServer()
        require(rows.size == source.tracks.size && rows.map { it.id } == source.tracks.map { it.id } && rows.all { it.checked }) { "请先完成整份歌单的曲库匹配" }
        require(rows.none { it.matchFailed } && rows.indices.none { rows[it].matches.size > 1 && it !in choices }) { "请先确认有多个版本的歌曲" }
        val existing = imports().filter { it.binding.source() == source.source }
        require(existing.size <= 1) { "服务器上有多个同源导入歌单，请先保留一个再更新" }
        val selected = rows.mapIndexed { index, row -> choices[index] ?: row.matches.singleOrNull() }
        require(selected.all { it == null || !it.id.isNullOrBlank() }) { "候选歌曲缺少服务器编号，请重新匹配" }
        val missing = rows.mapIndexedNotNull { i, row -> if (selected[i] != null) null else ImportDownload(row.id, row.title, row.artists, row.duration, row.url) }
        val metadata = ImportBinding(provider = source.source.provider.name, sourceId = source.source.id,
            chart = source.source.chart, url = source.source.url, updatedAt = System.currentTimeMillis(), total = rows.size, missing = missing)
        val pendingComment = metadata.copy(complete = false).comment()
        val finalComment = metadata.comment()
        val key = "$server:$owner:${source.source.provider}:${source.source.chart}:${source.source.id}"
        var playlist = existing.singleOrNull()?.playlist
        val newlyCreated = playlist == null
        if (playlist == null) {
            // The temporary name is persisted *before* creation. Even if the response is lost,
            // retry finds this exact draft instead of blindly creating a second playlist.
            val draftName = drafts.getString(key, null) ?: (source.name.take(160) + " · 导入 " + UUID.randomUUID().toString().take(8)).also {
                check(drafts.edit().putString(key, it).commit()) { "无法保存导入进度" }
            }
            val found = execute(playlists.playlists).playlists?.playlists.orEmpty().filter { it.owner == owner && it.name == draftName }
            require(found.size <= 1) { "发现多个导入草稿，请先在歌单列表中核对" }
            playlist = found.singleOrNull() ?: execute(playlists.saveImportedPlaylist(null, draftName, arrayListOf())).playlist
                ?: throw IOException("服务器未返回新歌单，请刷新导入列表确认结果")
        }
        execute(playlists.setImportComment(playlist.id, pendingComment, if (newlyCreated) false else null, null))
        drafts.edit().remove(key).apply()
        val songIds = ArrayList(selected.mapNotNull { it?.id })
        val saved = execute(playlists.saveImportedPlaylist(playlist.id, source.name, songIds)).playlist
            ?: throw IOException("服务器未返回歌单，请刷新列表后继续更新")
        check(saved.entries.orEmpty().map { it.id } == songIds) { "服务器返回的曲目或顺序与导入结果不一致，请刷新后重试" }
        // Navidrome's createPlaylist replaces tracks but ignores name for an existing ID.
        // Rename through updatePlaylist together with the final source binding.
        execute(playlists.setImportComment(saved.id, finalComment, null, source.name))
        saved.name = source.name
        saved.comment = finalComment
        if (notifyHub != null) notifyHub.invoke(saved.id, metadata, source, rows, choices)
        else com.eddyizm.tempus.hub.HubRepository.registerImport(saved.id, metadata, source, rows, choices, initialIdentity)
        notifySaved()
        ImportedPlaylist(saved, metadata)
    } }
    companion object { private val saveLock = Mutex() }
}
