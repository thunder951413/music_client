package com.eddyizm.tempus.repository;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;

import com.eddyizm.tempus.App;
import com.eddyizm.tempus.subsonic.Subsonic;
import com.eddyizm.tempus.subsonic.api.albumsonglist.AlbumSongListClient;
import com.eddyizm.tempus.subsonic.api.browsing.BrowsingClient;
import com.eddyizm.tempus.subsonic.api.searching.SearchingClient;
import com.eddyizm.tempus.subsonic.base.ApiResponse;
import com.eddyizm.tempus.subsonic.models.AlbumID3;
import com.eddyizm.tempus.subsonic.models.AlbumList2;
import com.eddyizm.tempus.subsonic.models.AlbumWithSongsID3;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.subsonic.models.SearchResult3;
import com.eddyizm.tempus.subsonic.models.SimilarSongs;
import com.eddyizm.tempus.subsonic.models.Songs;
import com.eddyizm.tempus.subsonic.models.Starred2;
import com.eddyizm.tempus.util.Constants.SeedType;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okio.Timeout;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SongRepositoryTest {
    @Rule public final InstantTaskExecutorRule executor = new InstantTaskExecutorRule();
    private MockedStatic<App> app;
    private Subsonic subsonic;
    private BrowsingClient browsing;

    @Before public void setUp() {
        app = mockStatic(App.class);
        subsonic = mock(Subsonic.class);
        browsing = mock(BrowsingClient.class);
        app.when(() -> App.getSubsonicClientInstance(false)).thenReturn(subsonic);
        when(subsonic.getBrowsingClient()).thenReturn(browsing);
    }

    @After public void tearDown() { app.close(); }

    @Test public void emptyRecommendationsStillPublishTheVisibleSeed() {
        Child seed = song("seed");
        doReturn(response(similar())).when(browsing).getSimilarSongs("seed", 20);
        assertEquals(Collections.singletonList(seed), new SongRepository().getInstantMix(seed, 20).getValue());
        verify(browsing, never()).getSong(anyString());
    }

    @Test public void unsupportedEndpointStillPublishesTheSeed() {
        Child seed = song("seed");
        doReturn(response(httpError())).when(browsing).getSimilarSongs("seed", 20);
        assertEquals(Collections.singletonList(seed), new SongRepository().getInstantMix(seed, 20).getValue());
    }

    @Test public void timedOutRecommendationsStillPublishTheSeed() {
        Child seed = song("seed");
        Call<ApiResponse> request = call((source, callback) -> callback.onFailure(source, new IOException("timeout")));
        when(browsing.getSimilarSongs("seed", 20)).thenReturn(request);
        assertEquals(Collections.singletonList(seed), new SongRepository().getInstantMix(seed, 20).getValue());
        assertTrue(request.timeout().timeoutNanos() > 0);
        assertTrue(request.timeout().timeoutNanos() <= java.util.concurrent.TimeUnit.SECONDS.toNanos(5));
    }

    @Test public void callbackCompletesWithFewerSongsThanRequested() {
        Child seed = song("seed");
        Child related = song("related");
        ApiResponse seedBody = new ApiResponse();
        seedBody.getSubsonicResponse().setSong(seed);
        doReturn(response(Response.success(seedBody))).when(browsing).getSong("seed");
        doReturn(response(similar(related))).when(browsing).getSimilarSongs("seed", 20);
        List<List<Child>> results = new ArrayList<>();
        new SongRepository().getInstantMix("seed", SeedType.TRACK, 20, results::add);
        assertEquals(1, results.size());
        assertEquals(Arrays.asList(seed, related), results.get(0));
    }

    @Test public void failedSeedAndRecommendationsCompleteWithAnEmptyResult() {
        doReturn(response(httpError())).when(browsing).getSong("missing");
        doReturn(response(httpError())).when(browsing).getSimilarSongs("missing", 20);
        List<List<Child>> results = new ArrayList<>();
        new SongRepository().getInstantMix("missing", SeedType.TRACK, 20, results::add);
        assertEquals(Collections.singletonList(Collections.emptyList()), results);
    }

    @Test public void separateRequestsKeepTheirResultsWhenResponsesArriveOutOfOrder() {
        List<Callback<ApiResponse>> callbacks = new ArrayList<>();
        Call<ApiResponse> first = call((source, callback) -> callbacks.add(callback));
        Call<ApiResponse> second = call((source, callback) -> callbacks.add(callback));
        when(browsing.getSimilarSongs("first", 20)).thenReturn(first);
        when(browsing.getSimilarSongs("second", 20)).thenReturn(second);
        SongRepository repository = new SongRepository();
        Child firstSeed = song("first");
        Child secondSeed = song("second");
        LiveData<List<Child>> firstResult = repository.getInstantMix(firstSeed, 20);
        LiveData<List<Child>> secondResult = repository.getInstantMix(secondSeed, 20);
        assertNull(firstResult.getValue());
        callbacks.get(1).onResponse(second, similar());
        callbacks.get(0).onResponse(first, similar());
        assertEquals(Collections.singletonList(firstSeed), firstResult.getValue());
        assertEquals(Collections.singletonList(secondSeed), secondResult.getValue());
    }

    @Test public void mixKeepsSeedFirstAndDeduplicatesWithinTheLimit() {
        Child seed = song("seed");
        Child next = song("next");
        assertEquals(Arrays.asList(seed, next), SongRepository.mergeMix(
                Collections.singletonList(seed), Arrays.asList(song("seed"), null, song(""), next, song("extra")), 2));
    }

    @Test public void instantMixExpandsToFullQueueWhenLastFmSimilarIsEmpty() {
        AlbumSongListClient albumSongList = mock(AlbumSongListClient.class);
        SearchingClient searching = mock(SearchingClient.class);
        when(subsonic.getAlbumSongListClient()).thenReturn(albumSongList);
        when(subsonic.getSearchingClient()).thenReturn(searching);

        Child seed = songWithMeta("seed", "Mayday", "art-1", "Rock");
        doReturn(response(similar())).when(browsing).getSimilarSongs("seed", 25);

        List<Child> artistHits = Arrays.asList(
                songWithMeta("m1", "Mayday", "art-1", "Rock"),
                songWithMeta("m2", "Mayday", "art-1", "Rock")
        );
        ApiResponse searchBody = new ApiResponse();
        SearchResult3 sr3 = new SearchResult3();
        sr3.setSongs(artistHits);
        searchBody.getSubsonicResponse().setSearchResult3(sr3);
        Call<ApiResponse> searchCall = syncResponse(Response.success(searchBody));
        doReturn(searchCall).when(searching).search3(eq("Mayday"), anyInt(), eq(0), eq(0), eq(0), eq(0), eq(0));

        List<Child> genreHits = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            genreHits.add(songWithMeta("g" + i, "RockBand " + i, "rg-" + i, "Rock"));
        }
        ApiResponse genreBody = new ApiResponse();
        Songs genreSongs = new Songs();
        genreSongs.setSongs(genreHits);
        genreBody.getSubsonicResponse().setRandomSongs(genreSongs);
        Call<ApiResponse> genreCall = syncResponse(Response.success(genreBody));
        doReturn(genreCall).when(albumSongList).getRandomSongs(anyInt(), isNull(), isNull(), eq("Rock"));

        ApiResponse starredBody = new ApiResponse();
        Starred2 st2 = new Starred2();
        st2.setSongs(Arrays.asList(songWithMeta("st1", "Liked 1", "l-1", "Pop"), songWithMeta("st2", "Liked 2", "l-2", "Pop")));
        starredBody.getSubsonicResponse().setStarred2(st2);
        Call<ApiResponse> starredCall = syncResponse(Response.success(starredBody));
        doReturn(starredCall).when(albumSongList).getStarred2();

        List<Child> poolHits = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            poolHits.add(songWithMeta("p" + i, "Library " + i, "lib-" + i, "Indie"));
        }
        ApiResponse poolBody = new ApiResponse();
        Songs poolSongs = new Songs();
        poolSongs.setSongs(poolHits);
        poolBody.getSubsonicResponse().setRandomSongs(poolSongs);
        Call<ApiResponse> poolCall = syncResponse(Response.success(poolBody));
        doReturn(poolCall).when(albumSongList).getRandomSongs(anyInt(), isNull(), isNull());

        List<Child> mix = new SongRepository(Runnable::run).getInstantMix(seed, 25).getValue();
        assertNotNull(mix);
        assertEquals(25, mix.size());
        assertEquals("seed", mix.get(0).getId());
        assertTrue(mix.stream().anyMatch(s -> s.getId().startsWith("m")));
        assertTrue(mix.stream().anyMatch(s -> s.getId().startsWith("g")));
        assertTrue(mix.stream().anyMatch(s -> s.getId().startsWith("st")));
    }

    @Test public void instantMixFallsBackToAlbumsWhenRandomSongsFails() {
        AlbumSongListClient albumSongList = mock(AlbumSongListClient.class);
        when(subsonic.getAlbumSongListClient()).thenReturn(albumSongList);

        Child seed = songWithMeta("seed", "Artist A", "art-a", null);
        doReturn(response(similar())).when(browsing).getSimilarSongs("seed", 20);
        Call<ApiResponse> randomErrCall = syncResponse(httpError());
        doReturn(randomErrCall).when(albumSongList).getRandomSongs(anyInt(), isNull(), isNull());

        AlbumID3 album = new AlbumID3();
        album.setId("alb-99");
        ApiResponse listRes = new ApiResponse();
        AlbumList2 albumList2 = new AlbumList2();
        albumList2.setAlbums(Collections.singletonList(album));
        listRes.getSubsonicResponse().setAlbumList2(albumList2);
        Call<ApiResponse> listCall = syncResponse(Response.success(listRes));
        doReturn(listCall).when(albumSongList).getAlbumList2(eq("frequent"), anyInt(), eq(0), isNull(), isNull());

        List<Child> albumTracks = new ArrayList<>();
        for (int i = 1; i <= 14; i++) {
            albumTracks.add(songWithMeta("alb-s-" + i, "Artist " + i, "art-" + i, "Pop"));
        }
        AlbumWithSongsID3 fullAlbum = new AlbumWithSongsID3(albumTracks);
        fullAlbum.setId("alb-99");
        ApiResponse albumRes = new ApiResponse();
        albumRes.getSubsonicResponse().setAlbum(fullAlbum);
        Call<ApiResponse> albumCall = syncResponse(Response.success(albumRes));
        doReturn(albumCall).when(browsing).getAlbum("alb-99");

        List<Child> mix = new SongRepository(Runnable::run).getInstantMix(seed, 20).getValue();
        assertNotNull(mix);
        assertEquals(15, mix.size());
        assertEquals("seed", mix.get(0).getId());
    }

    @Test public void assembleInstantMixDoesNotCapBlankArtistsAndRelaxesSingleArtistPool() {
        Child seed = songWithMeta("b0", "", null, null);
        List<Child> blanks = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            blanks.add(songWithMeta("b" + i, "", null, null));
        }
        List<Child> blankMix = SongRepository.assembleInstantMix(
                Collections.singletonList(seed), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), blanks, 25, new Random(1L));
        assertEquals(25, blankMix.size());

        Child soloSeed = songWithMeta("solo0", "OneBand", "ob-1", null);
        List<Child> soloPool = new ArrayList<>();
        for (int i = 1; i <= 11; i++) {
            soloPool.add(songWithMeta("solo" + i, "OneBand", "ob-1", null));
        }
        List<Child> soloMix = SongRepository.assembleInstantMix(
                Collections.singletonList(soloSeed), Collections.emptyList(), soloPool,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), 25, new Random(2L));
        assertEquals(12, soloMix.size());
    }

    @Test public void blendMadeForYouSampleMixesAllUserSignalsAndUsesRandomOnlyWhenEmpty() {
        List<Child> starred = Collections.singletonList(songWithMeta("fav1", "Artist Fav", "af", "Pop"));
        List<Child> localRecent = Arrays.asList(
                songWithMeta("loc1", "Artist L1", "al1", "Pop"),
                songWithMeta("loc2", "Artist L2", "al2", "Pop"),
                songWithMeta("loc3", "Artist L3", "al3", "Pop")
        );
        List<Child> frequentAlbums = Arrays.asList(
                songWithMeta("frq1", "Artist F1", "af1", "Rock"),
                songWithMeta("frq2", "Artist F2", "af2", "Rock")
        );
        List<Child> recentAlbums = Arrays.asList(
                songWithMeta("rec1", "Artist R1", "ar1", "Jazz"),
                songWithMeta("rec2", "Artist R2", "ar2", "Jazz")
        );
        List<Child> randomFallback = Arrays.asList(
                songWithMeta("rnd1", "Stranger 1", "sr1", "Classical"),
                songWithMeta("rnd2", "Stranger 2", "sr2", "Classical")
        );

        List<Child> blended = SongRepository.blendMadeForYouSample(
                starred, localRecent, frequentAlbums, recentAlbums, randomFallback, 12, new Random(5L));
        assertEquals(8, blended.size());
        assertTrue(blended.stream().anyMatch(s -> "fav1".equals(s.getId())));
        assertTrue(blended.stream().anyMatch(s -> s.getId().startsWith("loc")));
        assertTrue(blended.stream().anyMatch(s -> s.getId().startsWith("frq")));
        assertTrue(blended.stream().anyMatch(s -> s.getId().startsWith("rec")));
        assertTrue(blended.stream().noneMatch(s -> s.getId().startsWith("rnd")));

        List<Child> coldStart = SongRepository.blendMadeForYouSample(
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), randomFallback, 12, new Random(5L));
        assertEquals(2, coldStart.size());
        assertTrue(coldStart.stream().allMatch(s -> s.getId().startsWith("rnd")));
    }

    private static Child song(String id) {
        return new Child(id);
    }

    private static Child songWithMeta(String id, String artist, String artistId, String genre) {
        Child c = new Child(id);
        c.setTitle("Song " + id);
        c.setArtist(artist);
        c.setArtistId(artistId);
        c.setGenre(genre);
        return c;
    }

    @SuppressWarnings("unchecked")
    private static Call<ApiResponse> syncResponse(Response<ApiResponse> response) {
        Call<ApiResponse> request = mock(Call.class);
        when(request.timeout()).thenReturn(new Timeout());
        try {
            when(request.execute()).thenReturn(response);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return request;
    }

    private static Response<ApiResponse> similar(Child... songs) {
        ApiResponse body = new ApiResponse();
        SimilarSongs similar = new SimilarSongs();
        similar.setSongs(Arrays.asList(songs));
        body.getSubsonicResponse().setSimilarSongs(similar);
        return Response.success(body);
    }

    private static Response<ApiResponse> httpError() {
        return Response.error(503, ResponseBody.create("{}", MediaType.get("application/json")));
    }

    private static Call<ApiResponse> response(Response<ApiResponse> response) {
        return call((source, callback) -> callback.onResponse(source, response));
    }

    @SuppressWarnings("unchecked")
    private static Call<ApiResponse> call(BiConsumer<Call<ApiResponse>, Callback<ApiResponse>> action) {
        Call<ApiResponse> request = mock(Call.class);
        when(request.timeout()).thenReturn(new Timeout());
        doAnswer(invocation -> {
            action.accept(request, invocation.getArgument(0));
            return null;
        }).when(request).enqueue(any());
        return request;
    }
}
