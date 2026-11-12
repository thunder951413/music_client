package com.eddyizm.tempus.ui.browse

import com.eddyizm.tempus.App
import com.eddyizm.tempus.subsonic.Subsonic
import com.eddyizm.tempus.subsonic.api.albumsonglist.AlbumSongListClient
import com.eddyizm.tempus.subsonic.api.browsing.BrowsingClient
import com.eddyizm.tempus.subsonic.api.searching.SearchingClient
import com.eddyizm.tempus.subsonic.base.ApiResponse
import com.eddyizm.tempus.subsonic.models.AlbumID3
import com.eddyizm.tempus.subsonic.models.AlbumList2
import com.eddyizm.tempus.subsonic.models.AlbumWithSongsID3
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.subsonic.models.SimilarSongs
import com.eddyizm.tempus.subsonic.models.SimilarSongs2
import com.eddyizm.tempus.subsonic.models.Songs
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.MockedStatic
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import retrofit2.Call
import retrofit2.Response

class BrowseRepositoryRoamTest {
    private lateinit var previousProvider: () -> Subsonic
    private lateinit var subsonic: Subsonic
    private lateinit var browsing: BrowsingClient
    private lateinit var albumSongList: AlbumSongListClient
    private lateinit var searching: SearchingClient

    @Before
    fun setUp() {
        previousProvider = BrowseRepository.clientProvider
        subsonic = mock(Subsonic::class.java)
        browsing = mock(BrowsingClient::class.java)
        albumSongList = mock(AlbumSongListClient::class.java)
        searching = mock(SearchingClient::class.java)
        `when`(subsonic.browsingClient).thenReturn(browsing)
        `when`(subsonic.albumSongListClient).thenReturn(albumSongList)
        `when`(subsonic.searchingClient).thenReturn(searching)
        BrowseRepository.clientProvider = { subsonic }
    }

    @After
    fun tearDown() {
        BrowseRepository.clientProvider = previousProvider
    }

    @Test
    fun similarSongsQueriesSongEndpointFirstAndFallsBackToArtistId() {
        runBlocking {
            val artistMatch = Child("sim-artist-1", title = "Artist Track", artist = "Band", artistId = "art-9")
            val emptySimCall = syncCall(similarSongsResponse())
            val artistSimCall = syncCall(similarSongs2Response(artistMatch))
            `when`(browsing.getSimilarSongs("song-1", 20)).thenReturn(emptySimCall)
            `when`(browsing.getSimilarSongs2("art-9", 20)).thenReturn(artistSimCall)

            val found = BrowseRepository.similarSongs("song-1", 20, "art-9")

            assertEquals(listOf("sim-artist-1"), found.map { it.id })
            verify(browsing).getSimilarSongs("song-1", 20)
            verify(browsing).getSimilarSongs2("art-9", 20)
            verify(browsing, never()).getSimilarSongs2("song-1", 20)
        }
    }

    @Test
    fun randomSongsRetriesSmallerBatchWhen200IsRejectedByServer() {
        runBlocking {
            val fallbackTracks = (1..12).map { Child("r$it", title = "Song $it", artist = "Artist $it") }
            val err200 = errorCall(400)
            val err100 = errorCall(400)
            val ok50 = syncCall(randomSongsResponse(fallbackTracks))
            `when`(albumSongList.getRandomSongs(eq(200), isNull(), isNull())).thenReturn(err200)
            `when`(albumSongList.getRandomSongs(eq(100), isNull(), isNull())).thenReturn(err100)
            `when`(albumSongList.getRandomSongs(eq(50), isNull(), isNull())).thenReturn(ok50)

            val songs = BrowseRepository.randomSongs(200)

            assertEquals(12, songs.size)
            assertEquals("r1", songs.first().id)
        }
    }

    @Test
    fun resilientCandidatePoolHarvestsFromAlbumsWhenRandomSongsCompletelyFails() {
        runBlocking {
            val errRandom = errorCall(500)
            `when`(albumSongList.getRandomSongs(anyInt(), isNull(), isNull())).thenReturn(errRandom)
            val album1 = AlbumID3().apply { id = "alb-1"; name = "Album 1" }
            val album1WithSongs = AlbumWithSongsID3(
                songs = (1..15).map { Child("a$it", title = "Album Song $it", artist = "Artist $it") }
            ).apply { id = "alb-1" }
            val listCall = syncCall(ApiResponse().apply {
                subsonicResponse.albumList2 = AlbumList2().apply { albums = listOf(album1) }
            })
            val albumCall = syncCall(ApiResponse().apply {
                subsonicResponse.album = album1WithSongs
            })
            `when`(albumSongList.getAlbumList2(eq("random"), anyInt(), eq(0), isNull(), isNull()))
                .thenReturn(listCall)
            `when`(browsing.getAlbum("alb-1")).thenReturn(albumCall)

            val pool = BrowseRepository.resilientCandidatePool(minSize = 15)

            assertEquals(15, pool.size)
            assertTrue(pool.all { it.id.startsWith("a") })
        }
    }

    private fun similarSongsResponse(vararg songs: Child) = ApiResponse().apply {
        subsonicResponse.similarSongs = SimilarSongs().apply { this.songs = songs.toList() }
    }

    private fun similarSongs2Response(vararg songs: Child) = ApiResponse().apply {
        subsonicResponse.similarSongs2 = SimilarSongs2().apply { this.songs = songs.toList() }
    }

    private fun randomSongsResponse(songs: List<Child>) = ApiResponse().apply {
        subsonicResponse.randomSongs = Songs().apply { this.songs = songs }
    }

    @Suppress("UNCHECKED_CAST")
    private fun syncCall(body: ApiResponse): Call<ApiResponse> {
        val call = mock(Call::class.java) as Call<ApiResponse>
        `when`(call.execute()).thenReturn(Response.success(body))
        return call
    }

    @Suppress("UNCHECKED_CAST")
    private fun errorCall(code: Int): Call<ApiResponse> {
        val call = mock(Call::class.java) as Call<ApiResponse>
        `when`(call.execute()).thenReturn(
            Response.error(code, "{}".toResponseBody("application/json".toMediaType()))
        )
        return call
    }
}
