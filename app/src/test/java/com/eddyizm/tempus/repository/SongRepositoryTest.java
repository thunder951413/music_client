package com.eddyizm.tempus.repository;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;

import com.eddyizm.tempus.App;
import com.eddyizm.tempus.subsonic.Subsonic;
import com.eddyizm.tempus.subsonic.api.browsing.BrowsingClient;
import com.eddyizm.tempus.subsonic.base.ApiResponse;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.subsonic.models.SimilarSongs;
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
    private BrowsingClient browsing;

    @Before public void setUp() {
        app = mockStatic(App.class);
        Subsonic subsonic = mock(Subsonic.class);
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

    private static Child song(String id) {
        return new Child(id);
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
