package com.eddyizm.tempus.hub

import android.os.Build
import android.util.Base64
import androidx.annotation.Keep
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.App
import com.eddyizm.tempus.discovery.ChartTrack
import com.eddyizm.tempus.discovery.ExternalPlaylist
import com.eddyizm.tempus.discovery.ImportBinding
import com.eddyizm.tempus.model.ServerContext
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.ui.browse.DailyMixRepository
import com.eddyizm.tempus.util.ClientCertManager
import com.eddyizm.tempus.util.Constants
import com.eddyizm.tempus.util.Preferences
import com.eddyizm.tempus.util.ServerHeaders
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** An optional same-server hub. A confirmed hub never falls back to a second local generator. */
@OptIn(UnstableApi::class)
object HubRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val lock = Mutex()
    private val gson = Gson()
    private val outbox by lazy { HubOutbox(App.getContext()) }
    private val http by lazy { OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).callTimeout(35, TimeUnit.SECONDS)
        .apply { ClientCertManager.sslSocketFactory?.let { sslSocketFactory(it, ClientCertManager.trustManager) } }.build() }

    internal data class Session(val account: String, val base: String, val auth: String, val headers: Map<String, String>, val device: String, val identity: Pair<String?, String?>)
    @Keep private data class Daily(val date: String, val songs: List<Child>, val wishes: List<com.eddyizm.tempus.ui.browse.DailyWish>, val playlist_id: String?, val stale: Boolean = false)

    private fun session(): Session? {
        val user = Preferences.getUser()?.takeIf { it.isNotBlank() } ?: return null
        val identity = Preferences.getServerId() to user
        val server = Preferences.getServer()?.trimEnd('/') ?: return null
        val base = (Preferences.getInUseServerAddress() ?: server).trimEnd('/') + "/hub/v1"
        val params = App.getSubsonicClientInstance(false).params
        if (params["u"] != user) return null
        var token = params["t"]; var salt = params["s"]
        if (token == null || salt == null) {
            val password = params["p"] ?: return null
            salt = UUID.randomUUID().toString()
            token = MessageDigest.getInstance("MD5").digest((password + salt).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }
        val account = gson.toJson(listOf(server, user))
        val prefs = App.getInstance().preferences
        val device = synchronized(this) {
            prefs.getString("hub_device", null) ?: UUID.randomUUID().toString().also { check(prefs.edit().putString("hub_device", it).commit()) }
        }
        val auth = Base64.encodeToString(gson.toJson(mapOf("u" to user, "t" to token, "s" to salt)).toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        if (identity != (Preferences.getServerId() to Preferences.getUser())) return null
        return Session(account, base, auth, ServerHeaders.getHeadersForUrl(base, ServerContext(listOf(server, Preferences.getLocalAddress(), Preferences.getInUseServerAddress()), Preferences.getCustomHeaders())), device, identity)
    }

    private fun checkCurrent(s: Session) { check(session()?.let { it.account == s.account && it.identity == s.identity } == true) { "音乐服务器或账号已切换" } }

    @JvmStatic fun currentAccount(): String? {
        val server = Preferences.getServer()?.trimEnd('/') ?: return null
        val user = Preferences.getUser() ?: return null
        return gson.toJson(listOf(server, user))
    }
    @JvmStatic fun isEnabled(): Boolean = session()?.let { known(it) } ?: false

    private fun known(s: Session) = App.getInstance().preferences.getBoolean("hub_known:${s.account}", false)
    private suspend fun available(s: Session): Boolean {
        if (known(s)) return true
        val response = request(s, "health", null, authenticated = false, allowMissing = true) ?: return false
        check(response["service"]?.asString == "tempus-hub" && response["version"]?.asInt == 1) { "不支持的 hub 服务" }
        App.getInstance().preferences.edit().putBoolean("hub_known:${s.account}", true).apply()
        return true
    }

    private suspend fun request(s: Session, path: String, body: String?, authenticated: Boolean = true, allowMissing: Boolean = false): JsonObject? = withContext(Dispatchers.IO) {
        checkCurrent(s)
        val builder = Request.Builder().url("${s.base}/$path")
        s.headers.forEach { (k,v) -> builder.header(k,v) }
        if (authenticated) builder.header("X-Tempus-Auth", s.auth)
        if (body != null) builder.post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
        val call = http.newCall(builder.build())
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) { if (continuation.isActive) continuation.resumeWith(Result.failure(e)) }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    try {
                        val value = response.use {
                            if (allowMissing && it.code == 404) null
                            else {
                                check(it.isSuccessful) { "hub 请求未完成（${it.code}），待同步数据已保留" }
                                val bytes = it.body?.byteStream()?.readNBytes(8 * 1024 * 1024 + 1) ?: throw IOException("hub 返回空内容")
                                check(bytes.size <= 8 * 1024 * 1024) { "hub 响应过大" }
                                JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
                            }
                        }
                        checkCurrent(s)
                        if (continuation.isActive) continuation.resumeWith(Result.success(value))
                    } catch (e: Exception) { if (continuation.isActive) continuation.resumeWith(Result.failure(e)) }
                }
            })
        }
    }

    @JvmStatic fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            while (isActive) {
                try { session()?.let { synchronize(it) } }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* Durable outbox retries at next tick, play or home visit. */ }
                delay(60_000)
            }
        }
    }

    /** Captures identity before dispatch; a later account switch cannot relabel queued events. */
    fun record(item: MediaItem?, completed: Boolean, positionMs: Long) {
        val extras = item?.mediaMetadata?.extras ?: return
        if (extras.getString("type") != Constants.MEDIA_TYPE_MUSIC) return
        val s = session() ?: return
        if (extras.getString("hubAccount") != s.account) return
        val id = extras.getString("id") ?: return
        val event = mapOf("event_id" to UUID.randomUUID().toString(), "song_id" to id, "at" to System.currentTimeMillis(),
            "completed" to completed, "position_ms" to positionMs.coerceIn(0, 7 * 86400000L), "duration_ms" to (extras.getInt("duration").toLong() * 1000).coerceIn(0, 7 * 86400000L))
        val json = gson.toJson(event)
        // Commit before returning from the playback callback, so process death cannot lose it.
        outbox.put(s.account, "play", event["event_id"].toString(), json)
        scope.launch { try { synchronize(s) } catch (e: CancellationException) { throw e } catch (_: Exception) {} }
    }

    private suspend fun synchronize(s: Session): Boolean = lock.withLock {
        checkCurrent(s)
        if (!available(s)) return@withLock false
        request(s, "devices", gson.toJson(mapOf("id" to s.device, "name" to Build.MODEL)))
        repeat(20) {
            val rows = outbox.pending(s.account, "play", 100)
            if (rows.isEmpty()) return@repeat
            val events = JsonArray().apply { rows.forEach { add(JsonParser.parseString(it.second)) } }
            val body = JsonObject().apply { addProperty("device", s.device); add("events", events) }
            val accepted = request(s, "plays", body.toString())?.getAsJsonArray("accepted")?.map { it.asString }.orEmpty()
            check(accepted.toSet() == rows.map { it.first }.toSet()) { "hub 未确认全部记录" }
            outbox.acknowledge(s.account, "play", accepted)
        }
        for ((id, body) in outbox.pending(s.account, "import", 20)) {
            request(s, "imports", body)
            outbox.acknowledge(s.account, "import", listOf(id))
        }
        true
    }

    /** Null means this server has no hub. A hub outage returns its cached result without generating. */
    suspend fun today(): DailyMixRepository.Today? = withContext(Dispatchers.IO) {
        val s = session() ?: return@withContext null
        try {
            if (!synchronize(s)) return@withContext null
            val json = request(s, "daily?device=${s.device}", null) ?: error("hub 返回空内容")
            val value = gson.fromJson(json, Daily::class.java)
            if (value.songs.isNotEmpty() && value.playlist_id != null) outbox.cache(s.account, json.toString())
            val visible = if (value.songs.isEmpty()) outbox.cached(s.account)?.let { gson.fromJson(it, Daily::class.java) } ?: value else value
            checkCurrent(s)
            DailyMixRepository.Today(visible.songs, visible.wishes, pending = value.playlist_id == null, stale = visible.stale)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            checkCurrent(s)
            if (!known(s)) throw e
            val cached = outbox.cached(s.account)?.let { gson.fromJson(it, Daily::class.java) }
            DailyMixRepository.Today(cached?.songs.orEmpty(), cached?.wishes.orEmpty(), pending = cached == null, stale = true)
        }
    }

    /** Persist the complete order, including duplicate occurrences and explicit version choices. */
    fun registerImport(playlistId: String, binding: ImportBinding, source: ExternalPlaylist, rows: List<ChartTrack>, choices: Map<Int, Child>, identity: Pair<String?, String?>) {
        val s = session() ?: return
        check(s.identity == identity) { "音乐服务器或账号已切换，请重新更新歌单" }
        val items = rows.mapIndexed { index, row -> mapOf("provider" to source.source.provider.name, "id" to row.id,
            "title" to row.title, "artists" to row.artists, "duration" to row.duration, "song_id" to (choices[index] ?: row.matches.singleOrNull())?.id) }
        val body = gson.toJson(mapOf("playlist_id" to playlistId, "request_id" to binding.requestId, "tracks" to items))
        outbox.put(s.account, "import", playlistId, body)
        scope.launch { try { synchronize(s) } catch (e: CancellationException) { throw e } catch (_: Exception) {} }
    }
}
