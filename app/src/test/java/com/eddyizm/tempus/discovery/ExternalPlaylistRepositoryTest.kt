package com.eddyizm.tempus.discovery

import android.content.SharedPreferences
import com.eddyizm.tempus.subsonic.api.playlist.PlaylistClient
import com.eddyizm.tempus.subsonic.api.searching.SearchingClient
import com.eddyizm.tempus.subsonic.base.ApiResponse
import com.eddyizm.tempus.subsonic.models.Playlist
import com.eddyizm.tempus.subsonic.models.Child
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ExternalPlaylistRepositoryTest {
    private class Backend {
        val client = mock(PlaylistClient::class.java)
        val preferences = mock(SharedPreferences::class.java)
        private val editor = mock(SharedPreferences.Editor::class.java)
        private val drafts = mutableMapOf<String, String>()
        var identity: Pair<String?, String?> = "server" to "tester"
        var remote: Playlist? = null
        var creations = 0
        var failCommentOnce = false
        var failSongsOnce = false
        var returnedSongIds: List<String>? = null
        val privacyWrites = mutableListOf<Boolean?>()
        val source = ExternalPlaylist(ExternalPlaylistRef(ChartSource.Provider.NETEASE, "123"), "Source playlist", emptyList())

        init {
            `when`(preferences.edit()).thenReturn(editor)
            `when`(preferences.getString(anyString(), nullable(String::class.java))).thenAnswer { drafts[it.getArgument(0)] }
            `when`(editor.putString(anyString(), anyString())).thenAnswer { drafts[it.getArgument(0)] = it.getArgument(1); editor }
            `when`(editor.remove(anyString())).thenAnswer { drafts.remove(it.getArgument<String>(0)); editor }
            `when`(editor.commit()).thenReturn(true)
            `when`(client.playlists).thenAnswer { success(list = listOfNotNull(remote)) }
            `when`(client.saveImportedPlaylist(nullable(String::class.java), anyString(), any())).thenAnswer {
                val id: String? = it.getArgument(0)
                if (id == null) {
                    creations++
                    remote = Playlist("created", it.getArgument(1)).apply { owner = "tester" }
                } else {
                    if (failSongsOnce) { failSongsOnce = false; return@thenAnswer failure() }
                    check(id == remote?.id)
                }
                success(playlist = remote)
            }
            `when`(client.setImportComment(anyString(), anyString(), nullable(Boolean::class.javaObjectType), nullable(String::class.java))).thenAnswer {
                if (failCommentOnce) { failCommentOnce = false; return@thenAnswer failure() }
                check(it.getArgument<String>(0) == remote!!.id)
                remote!!.comment = it.getArgument(1)
                it.getArgument<String?>(3)?.let { name -> remote!!.name = name }
                privacyWrites += it.getArgument<Boolean?>(2)
                success()
            }
        }

        fun repository(searching: SearchingClient = mock(SearchingClient::class.java)) = ExternalPlaylistRepository(client, searching, preferences, { identity }, {}, { _, _, _, _, _ -> })

        private fun success(playlist: Playlist? = null, list: List<Playlist>? = null): Call<ApiResponse> {
            val data = JsonObject().apply {
                addProperty("status", "ok")
                if (playlist != null) add("playlist", Gson().toJsonTree(playlist).asJsonObject.apply {
                    returnedSongIds?.let { ids -> add("entry", JsonArray().apply { ids.forEach { id -> add(JsonObject().apply { addProperty("id", id) }) } }) }
                })
                if (list != null) add("playlists", JsonObject().apply { add("playlist", Gson().toJsonTree(list)) })
            }
            val json = JsonObject().apply { add("subsonic-response", data) }
            val body = Gson().fromJson(json, ApiResponse::class.java)
            val call = mock(Call::class.java) as Call<ApiResponse>
            doAnswer { it.getArgument<Callback<ApiResponse>>(0).onResponse(call, Response.success(body)); null }.`when`(call).enqueue(any())
            return call
        }

        private fun failure(): Call<ApiResponse> {
            val call = mock(Call::class.java) as Call<ApiResponse>
            doAnswer { it.getArgument<Callback<ApiResponse>>(0).onFailure(call, IOException("Connection interrupted")); null }.`when`(call).enqueue(any())
            return call
        }
    }

    @Test fun lostSourceBindingWriteReusesThePersistedDraftOnRetry() = runBlocking {
        val backend = Backend().apply { failCommentOnce = true }
        val repository = backend.repository()
        try { repository.save(backend.source, emptyList(), emptyMap()); fail("Expected interrupted write") } catch (_: IOException) { }
        val result = repository.save(backend.source, emptyList(), emptyMap())
        assertEquals(1, backend.creations)
        assertEquals("created", result.playlist.id)
        assertEquals(backend.source.name, result.playlist.name)
        assertEquals(backend.source.name, backend.remote!!.name)
        assertEquals(listOf(false, null), backend.privacyWrites)
    }

    @Test fun failedSongReplacementKeepsAResumableIncompleteBinding() = runBlocking {
        val backend = Backend().apply { failSongsOnce = true }
        val repository = backend.repository()
        try { repository.save(backend.source, emptyList(), emptyMap()); fail("Expected interrupted write") } catch (_: IOException) { }
        assertFalse(ImportBinding.read(backend.remote!!.comment)!!.complete)
        repository.save(backend.source, emptyList(), emptyMap())
        assertEquals(1, backend.creations)
        assertTrue(ImportBinding.read(backend.remote!!.comment)!!.complete)
    }

    @Test fun anExistingImportedPlaylistIsUpdatedWithoutResettingItsVisibility() = runBlocking {
        val backend = Backend()
        val binding = ImportBinding(provider = "NETEASE", sourceId = "123", chart = false,
            url = backend.source.source.url, updatedAt = 1, total = 0, missing = emptyList())
        backend.remote = Playlist("existing", "Old source name").apply {
            owner = "tester"; isUniversal = true; comment = binding.comment()
        }
        val result = backend.repository().save(backend.source, emptyList(), emptyMap())
        assertEquals("existing", result.playlist.id)
        assertEquals(0, backend.creations)
        assertEquals(listOf(null, null), backend.privacyWrites)
    }

    @Test fun changingAccountsPreventsWritesThroughTheOldPreview(): Unit = runBlocking {
        val backend = Backend()
        val repository = backend.repository()
        backend.identity = "other-server" to "other-user"
        try { repository.save(backend.source, emptyList(), emptyMap()); fail("Expected changed server") } catch (_: IllegalStateException) { }
        assertEquals(0, backend.creations)
        verify(backend.client, never()).saveImportedPlaylist(nullable(String::class.java), anyString(), any())
    }

    @Test fun unexpectedServerSongContentsRemainIncompleteInsteadOfReportingSuccess() = runBlocking {
        val backend = Backend().apply { returnedSongIds = listOf("wrong-song") }
        val song = mock(Child::class.java)
        `when`(song.id).thenReturn("expected-song")
        val row = ChartTrack().apply { id = "1"; title = "Song"; artists = listOf("Artist"); artist = "Artist"; checked = true; matches = listOf(song) }
        try { backend.repository().save(backend.source.copy(tracks = listOf(row)), listOf(row), emptyMap()); fail("Expected mismatched server result") }
        catch (_: IllegalStateException) { }
        assertFalse(ImportBinding.read(backend.remote!!.comment)!!.complete)
    }

    @Test fun suspendedSearchesNeverExceedThreeInFlightRequests() = runBlocking {
        val backend = Backend()
        val searching = mock(SearchingClient::class.java)
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val response = Gson().fromJson("""{"subsonic-response":{"status":"ok"}}""", ApiResponse::class.java)
        `when`(searching.search3(anyString(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer {
            val call = mock(Call::class.java) as Call<ApiResponse>
            doAnswer { invocation ->
                val callback = invocation.getArgument<Callback<ApiResponse>>(0)
                maximum.accumulateAndGet(active.incrementAndGet(), ::maxOf)
                scheduler.schedule({ active.decrementAndGet(); callback.onResponse(call, Response.success(response)) }, 12, TimeUnit.MILLISECONDS)
                null
            }.`when`(call).enqueue(any())
            call
        }
        try {
            val rows = (1..12).map { i -> ChartTrack().apply { id = "$i"; title = "Song $i"; artist = "Artist"; artists = listOf("Artist") } }
            val result = backend.repository(searching).match(backend.source.copy(tracks = rows)) { _, _ -> }
            assertEquals(12, result.size)
            assertTrue("Observed ${maximum.get()} concurrent requests", maximum.get() in 1..3)
        } finally { scheduler.shutdownNow() }
    }
}
