package com.eddyizm.tempus.discovery

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.URLDecoder

/** Only provider IDs cross the boundary into requests; a pasted URL is never an arbitrary fetch. */
data class ExternalPlaylistRef(val provider: ChartSource.Provider, val id: String, val chart: Boolean = false) {
    val url: String get() = when (provider) {
        ChartSource.Provider.NETEASE -> "https://music.163.com/playlist?id=$id"
        ChartSource.Provider.QQ -> "https://y.qq.com/n/ryqq_v2/${if (chart) "toplist" else "playlist"}/$id"
    }

    companion object {
        private val allowed = setOf("music.163.com", "y.music.163.com", "m.music.163.com", "y.qq.com", "i.y.qq.com")
        val shortHosts = setOf("163cn.tv", "c6.y.qq.com")
        fun extract(text: String): URI {
            val link = Regex("https?://[^\\s<>\"'，。；）)]+", RegexOption.IGNORE_CASE).find(text.trim())?.value
                ?: throw IllegalArgumentException("请粘贴网易云或 QQ 音乐的歌单分享链接")
            val uri = URI(link)
            require(uri.userInfo == null && (uri.port == -1 || uri.port == 443)) { "歌单链接格式不正确" }
            require(uri.host?.lowercase() in allowed + shortHosts) { "仅支持网易云和 QQ 音乐的官方歌单链接" }
            return URI("https", null, uri.host.lowercase(), -1, uri.path, uri.query, uri.fragment)
        }

        fun parse(text: String): ExternalPlaylistRef {
            val uri = extract(text)
            require(uri.host !in shortHosts) { "分享短链接需要联网展开" }
            val fragment = uri.fragment.orEmpty().removePrefix("/")
            val path = if (fragment.contains("playlist") || fragment.contains("toplist")) "/" + fragment.substringBefore('?') else uri.path.orEmpty()
            val query = if (fragment.contains('?')) fragment.substringAfter('?') else uri.rawQuery.orEmpty()
            val params = query.split('&').mapNotNull {
                val parts = it.split('=', limit = 2)
                if (parts.size == 2) parts[0] to URLDecoder.decode(parts[1], "UTF-8") else null
            }.toMap()
            val netease = uri.host.endsWith(".163.com")
            val chart = !netease && path.contains("/toplist")
            val id = if (netease) {
                require(path.contains("playlist") || path.contains("toplist")) { "这个网易云链接不是歌单或榜单" }
                params["id"]
            } else {
                Regex("/(?:playlist|playsquare|toplist)/(\\d+)(?:\\.html)?(?:/|$)").find(path)?.groupValues?.get(1)
                    ?: params["topid"]?.takeIf { chart }
                    ?: params["disstid"] ?: params["id"]?.takeIf { path.contains("playlist") || path.contains("playsquare") || path.contains("taoge") }
            }
            require(id != null && id.matches(Regex("[1-9][0-9]{0,19}"))) { "未找到有效的歌单编号" }
            return ExternalPlaylistRef(if (netease) ChartSource.Provider.NETEASE else ChartSource.Provider.QQ, id, chart)
        }
    }
}

data class ExternalPlaylist(val source: ExternalPlaylistRef, val name: String, val tracks: List<ChartTrack>)

/** Unlike chart previews, imports must contain every advertised entry, including duplicates. */
object ExternalPlaylistParser {
    const val MAX_TRACKS = 5000
    const val NETEASE_BATCH = 500

    fun missingNeteaseIds(json: String): List<String> {
        val root = JsonParser.parseString(json).asJsonObject
        require(root.number("code") == 200) { "平台未返回歌单，请确认歌单可分享且无需额外登录" }
        val list = root.getAsJsonObject("playlist") ?: root.getAsJsonObject("result") ?: error("平台未返回歌单")
        val trackIds = list.getAsJsonArray("trackIds")?.mapNotNull { it.asJsonObject.text("id") } ?: return emptyList()
        val count = list.number("trackCount") ?: trackIds.size
        require(count > 0 && trackIds.isNotEmpty()) { "歌单为空或曲目不可见" }
        require(count <= MAX_TRACKS && trackIds.size <= MAX_TRACKS) { "当前一次最多导入 $MAX_TRACKS 首歌曲" }
        trackIds.forEach { id -> require(id.matches(Regex("[0-9]{1,20}"))) { "平台返回的歌曲编号无效" } }
        val present = list.getAsJsonArray("tracks")?.mapNotNull { it.asJsonObject.text("id") }?.toSet().orEmpty()
        return trackIds.distinct().filter { it !in present }
    }

    fun parse(source: ExternalPlaylistRef, json: String, extraPages: List<String> = emptyList()): ExternalPlaylist {
        val root = JsonParser.parseString(json).asJsonObject
        val netease = source.provider == ChartSource.Provider.NETEASE
        if (!netease && (root.number("subcode") == 4000 || root.number("code") == 4000)) {
            throw IllegalArgumentException("该 QQ 歌单可能已设为私密或暂不可分享，请在客户端设为公开后重试")
        }
        require(root.number("code") == if (netease) 200 else 0) { "平台未返回歌单，请确认歌单可分享且无需额外登录" }
        val list: JsonObject
        val rows: JsonArray
        val count: Int
        val name: String
        if (netease) {
            list = root.getAsJsonObject("playlist") ?: root.getAsJsonObject("result") ?: error("平台未返回歌单")
            val initial = list.getAsJsonArray("tracks") ?: JsonArray()
            val trackIds = list.getAsJsonArray("trackIds")?.mapNotNull { it.asJsonObject.text("id") }
            if (!trackIds.isNullOrEmpty()) {
                val byId = HashMap<String, JsonObject>(trackIds.size)
                initial.forEach { el -> el.asJsonObject.let { obj -> obj.text("id")?.let { byId[it] = obj } } }
                extraPages.forEach { pageJson ->
                    val page = JsonParser.parseString(pageJson).asJsonObject
                    require(page.number("code") == 200) { "平台未返回完整曲目详情，请稍后重试" }
                    (page.getAsJsonArray("songs") ?: JsonArray()).forEach { el ->
                        el.asJsonObject.let { obj -> obj.text("id")?.let { byId[it] = obj } }
                    }
                }
                rows = JsonArray()
                trackIds.forEach { id -> byId[id]?.let { rows.add(it) } }
                count = list.number("trackCount") ?: trackIds.size
            } else {
                rows = initial
                count = list.number("trackCount") ?: rows.size()
            }
            name = list.text("name") ?: "网易云歌单 ${source.id}"
        } else if (source.chart) {
            list = root
            rows = root.getAsJsonArray("songlist") ?: JsonArray()
            count = root.number("total_song_num") ?: root.number("cur_song_num") ?: rows.size()
            name = root.getAsJsonObject("topinfo")?.text("ListName") ?: "QQ 榜单 ${source.id}"
        } else {
            list = root.getAsJsonArray("cdlist")?.firstOrNull()?.asJsonObject ?: error("平台未返回歌单")
            rows = list.getAsJsonArray("songlist") ?: JsonArray()
            count = list.number("total_song_num") ?: list.number("songnum") ?: rows.size()
            name = list.text("dissname") ?: "QQ 歌单 ${source.id}"
        }
        require(count > 0 && rows.size() > 0) { "歌单为空或曲目不可见" }
        require(count <= MAX_TRACKS && rows.size() <= MAX_TRACKS) { "当前一次最多导入 $MAX_TRACKS 首歌曲" }
        require(count == rows.size()) { "平台只返回 ${rows.size()} / $count 首，请在客户端确认歌单公开可分享后重试；此次不会覆盖服务器歌单" }
        val tracks = rows.mapIndexed { index, element ->
            val row = element.asJsonObject.let { if (source.chart && !netease) it.getAsJsonObject("data") ?: it else it }
            ChartTrack().apply {
                id = row.text(if (netease) "id" else "songmid") ?: row.text("mid")
                title = row.text(if (netease) "name" else "songname") ?: row.text("name")
                originalTitle = (if (netease) row.text("mainTitle") else row.text("songorig"))
                    ?.trim()?.takeIf { it.isNotEmpty() && it != title }
                val people = row.getAsJsonArray(if (netease) "artists" else "singer") ?: row.getAsJsonArray("ar") ?: JsonArray()
                artists = people.mapNotNull { it.asJsonObject.text("name") }
                artist = artists.joinToString(" / ")
                duration = if (netease) ((row.number("duration") ?: row.number("dt") ?: 0) / 1000) else row.number("interval")
                rank = index + 1
                url = if (netease) "https://music.163.com/song?id=$id" else "https://y.qq.com/n/ryqq_v2/songDetail/$id"
                require(!id.isNullOrBlank() && !title.isNullOrBlank() && artists.isNotEmpty()) { "第 ${index + 1} 首歌曲信息不完整，此次不会覆盖服务器歌单" }
                require(id.matches(if (netease) Regex("[0-9]{1,20}") else Regex("[A-Za-z0-9]{1,64}"))) { "平台返回的歌曲编号无效" }
            }
        }
        return ExternalPlaylist(source, name, tracks)
    }

    private fun JsonObject.text(key: String): String? = get(key)?.takeUnless { it.isJsonNull }?.asString
    private fun JsonObject.number(key: String): Int? = get(key)?.takeUnless { it.isJsonNull }?.asInt
}
