package com.eddyizm.tempus.discovery

import org.junit.Assert.*
import org.junit.Test

class ExternalPlaylistTest {
    @Test fun neteaseShareTextAndFragmentResolveToTheSameSource() {
        val expected = ExternalPlaylistRef(ChartSource.Provider.NETEASE, "123")
        assertEquals(expected, ExternalPlaylistRef.parse("分享歌单：https://music.163.com/#/playlist?id=123&userid=456"))
        assertEquals(expected, ExternalPlaylistRef.parse("https://music.163.com/playlist?id=123"))
    }
    @Test fun qqNewAndLegacyPlaylistLinksAreAccepted() {
        val expected = ExternalPlaylistRef(ChartSource.Provider.QQ, "123")
        listOf("https://y.qq.com/n/ryqq_v2/playlist/123", "https://y.qq.com/n/yqq/playsquare/123.html", "https://i.y.qq.com/n2/m/share/details/taoge.html?disstid=123", "https://i.y.qq.com/n2/m/share/details/taoge.html?id=123").forEach {
            assertEquals(expected, ExternalPlaylistRef.parse(it))
        }
        assertTrue(ExternalPlaylistRef.parse("https://y.qq.com/n/ryqq_v2/toplist/26").chart)
    }
    @Test fun arbitraryHostsCredentialsAndSongLinksAreRejected() {
        listOf("https://y.qq.com.evil.example/playlist/123", "https://evil.example/?next=https://y.qq.com/playlist/123", "https://user:password@y.qq.com/playlist/123", "https://127.0.0.1/playlist/123", "https://music.163.com/song?id=123").forEach {
            assertThrows(IllegalArgumentException::class.java) { ExternalPlaylistRef.parse(it) }
        }
    }
    private fun song(id: Int) = """{"id":$id,"name":"Song $id","artists":[{"name":"Artist"}],"duration":180000}"""
    @Test fun importsHaveNoHundredTrackPreviewLimitAndKeepOrderAndDuplicates() {
        val rows = ((1..125).toList() + 1).joinToString(",") { song(it) }
        val result = ExternalPlaylistParser.parse(ExternalPlaylistRef(ChartSource.Provider.NETEASE, "123"), """{"code":200,"result":{"name":"Test","trackCount":126,"tracks":[$rows]}}""")
        assertEquals(126, result.tracks.size)
        assertEquals("1", result.tracks.last().id)
        assertEquals(180, result.tracks.first().duration)
    }
    @Test fun aPartialPlatformResponseCannotReplaceTheServerPlaylist() {
        assertThrows(IllegalArgumentException::class.java) {
            ExternalPlaylistParser.parse(ExternalPlaylistRef(ChartSource.Provider.NETEASE, "123"), """{"code":200,"result":{"trackCount":200,"tracks":[${song(1)}]}}""")
        }
    }
    @Test fun privateOrUnavailablePlaylistsAreErrorsRatherThanEmptyImports() {
        assertThrows(IllegalArgumentException::class.java) {
            ExternalPlaylistParser.parse(ExternalPlaylistRef(ChartSource.Provider.NETEASE, "123"), """{"code":401}""")
        }
    }
    @Test fun qqPlaylistMetadataAndDurationAreRead() {
        val result = ExternalPlaylistParser.parse(ExternalPlaylistRef(ChartSource.Provider.QQ, "123"), """{"code":0,"cdlist":[{"dissname":"Test","songnum":1,"songlist":[{"songmid":"Ab12","songname":"Song","interval":181,"singer":[{"name":"Artist"}]}]}]}""")
        assertEquals("Test", result.name)
        assertEquals("Ab12", result.tracks.single().id)
        assertEquals(181, result.tracks.single().duration)
    }
    @Test fun serverBindingRoundTripsAndRejectsForeignOrMalformedComments() {
        val binding = ImportBinding(provider = "QQ", sourceId = "123", chart = false,
            url = "https://y.qq.com/n/ryqq_v2/playlist/123", updatedAt = 1, total = 1,
            missing = listOf(ImportDownload("Ab12", "Song", listOf("Artist"), 180, "https://y.qq.com/n/ryqq_v2/songDetail/Ab12")))
        assertEquals(binding, ImportBinding.read(binding.comment()))
        assertNull(ImportBinding.read("Personal playlist comment"))
        assertNull(ImportBinding.read(ImportBinding.PREFIX + "{}"))
        assertNull(ImportBinding.read(binding.copy(url = "https://evil.example/").comment()))
    }
    @Test fun oversizedDownloadMetadataIsRejectedBeforeItCanBreakThePlaylistCache() {
        val binding = ImportBinding(provider = "QQ", sourceId = "123", chart = false,
            url = "https://y.qq.com/n/ryqq_v2/playlist/123", updatedAt = 1, total = 1,
            missing = listOf(ImportDownload("Ab12", "歌".repeat(400000), listOf("Artist"), 180, "https://y.qq.com/n/ryqq_v2/songDetail/Ab12")))
        assertThrows(IllegalArgumentException::class.java) { binding.comment() }
    }

    @Test fun neteaseTrackIdsReconstructExactOrderDuplicatesAndOriginalTitle() {
        val detail = """
            {"code":200,"playlist":{
              "name":"完整歌单","trackCount":5,
              "tracks":[
                {"id":10,"name":"Song A (中文译名)","mainTitle":"Song A","ar":[{"name":"Artist A"}],"dt":180000},
                {"id":20,"name":"Song B","ar":[{"name":"Artist B"}],"dt":190000}
              ],
              "trackIds":[{"id":10},{"id":30},{"id":20},{"id":40},{"id":10}]
            }}
        """.trimIndent()
        val missing = ExternalPlaylistParser.missingNeteaseIds(detail)
        assertEquals(listOf("30", "40"), missing)
        val extra = """
            {"code":200,"songs":[
              {"id":40,"name":"Song D","ar":[{"name":"Artist D"}],"dt":210000},
              {"id":30,"name":"Song C","ar":[{"name":"Artist C"}],"dt":200000}
            ]}
        """.trimIndent()
        val parsed = ExternalPlaylistParser.parse(ExternalPlaylistRef(ChartSource.Provider.NETEASE, "999"), detail, listOf(extra))
        assertEquals("完整歌单", parsed.name)
        assertEquals(listOf("10", "30", "20", "40", "10"), parsed.tracks.map { it.id })
        assertEquals("Song A", parsed.tracks.first().originalTitle)
        assertNull(parsed.tracks[2].originalTitle)
    }

    @Test fun qqPrivateSubcodeAndOriginalTitleAreHandledExplicitly() {
        val err = assertThrows(IllegalArgumentException::class.java) {
            ExternalPlaylistParser.parse(ExternalPlaylistRef(ChartSource.Provider.QQ, "123"), """{"code":0,"subcode":4000}""")
        }
        assertTrue(err.message!!.contains("私密"))
        assertThrows(IllegalArgumentException::class.java) {
            ExternalPlaylistParser.parse(ExternalPlaylistRef(ChartSource.Provider.QQ, "123"), """{"code":0,"cdlist":[{"dissname":"Empty","songnum":0,"songlist":[]}]}""")
        }
        val ok = ExternalPlaylistParser.parse(
            ExternalPlaylistRef(ChartSource.Provider.QQ, "123"),
            """{"code":0,"cdlist":[{"dissname":"Test","songnum":1,"songlist":[{"songmid":"Ab12","songname":"Night (夜)","songorig":"Night","interval":181,"singer":[{"name":"Artist"}]}]}]}""",
        )
        assertEquals("Night", ok.tracks.single().originalTitle)
    }
}
