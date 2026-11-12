package com.eddyizm.tempus.repository;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.util.UnstableApi;

import com.eddyizm.tempus.App;
import com.eddyizm.tempus.database.AppDatabase;
import com.eddyizm.tempus.discovery.MusicDiscoveryRules;
import com.eddyizm.tempus.model.Chronology;
import com.eddyizm.tempus.subsonic.Subsonic;
import com.eddyizm.tempus.subsonic.api.albumsonglist.AlbumSongListClient;
import com.eddyizm.tempus.subsonic.api.browsing.BrowsingClient;
import com.eddyizm.tempus.subsonic.api.searching.SearchingClient;
import com.eddyizm.tempus.subsonic.base.ApiResponse;
import com.eddyizm.tempus.subsonic.models.AlbumID3;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.subsonic.models.SubsonicResponse;
import com.eddyizm.tempus.ui.browse.LibraryMixes;
import com.eddyizm.tempus.util.Constants.SeedType;
import com.eddyizm.tempus.util.Preferences;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SongRepository {

    private static final String TAG = "SongRepository";
    private static final int MIN_SIMILAR_SONGS = 5;
    private static final int PER_ARTIST_CAP = 3;
    private static final int MADE_FOR_YOU_PER_ARTIST_CAP = 2;
    private static final long HISTORY_WINDOW_DAYS = 30L;
    private static final Executor IO_EXECUTOR = Executors.newCachedThreadPool();

    private final Executor backgroundExecutor;

    public SongRepository() {
        this(IO_EXECUTOR);
    }

    SongRepository(Executor backgroundExecutor) {
        this.backgroundExecutor = backgroundExecutor != null ? backgroundExecutor : IO_EXECUTOR;
    }

    public interface MediaCallbackInternal {
        void onSongsAvailable(List<Child> songs);
    }

    public MutableLiveData<List<Child>> getStarredSongs(boolean random, int size) {
        MutableLiveData<List<Child>> starredSongs = new MutableLiveData<>(Collections.emptyList());

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getStarred2()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getStarred2() != null) {
                            List<Child> songs = response.body().getSubsonicResponse().getStarred2().getSongs();

                            if (songs != null) {
                                if (!random) {
                                    starredSongs.setValue(new ArrayList<>(songs));
                                } else {
                                    List<Child> copy = new ArrayList<>(songs);
                                    Collections.shuffle(copy);
                                    starredSongs.setValue(new ArrayList<>(copy.subList(0, Math.min(size, copy.size()))));
                                }
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
                });

        return starredSongs;
    }

    /**
     * Builds the multi-signal "Made for you" (`为您定制`) shelf sample (~12 tracks) by blending:
     * 1) red-heart favorites (`getStarred2`),
     * 2) local recent plays (`chronologyDao`),
     * 3) server frequent & recent album tracks (`getAlbumList2("frequent" / "recent")`),
     * falling back to library random tracks only when all 3 user signals are empty.
     */
    public MutableLiveData<List<Child>> getMadeForYouSample(int size) {
        MutableLiveData<List<Child>> result = new MutableLiveData<>(new ArrayList<>());
        final int target = Math.max(1, size);
        backgroundExecutor.execute(() -> {
            try {
                Subsonic subsonic = App.getSubsonicClientInstance(false);
                AlbumSongListClient albumSongClient = subsonic != null ? subsonic.getAlbumSongListClient() : null;
                BrowsingClient browsingClient = subsonic != null ? subsonic.getBrowsingClient() : null;

                List<Child> starred = fetchStarredSync(albumSongClient);
                List<Child> recentLocal = fetchLocalHistorySync(60);
                List<Child> frequentAlbums = fetchAlbumTracksByTypeSync(albumSongClient, browsingClient, "frequent", 4, target * 2);
                List<Child> recentAlbums = fetchAlbumTracksByTypeSync(albumSongClient, browsingClient, "recent", 4, target * 2);

                List<Child> randomFallback = Collections.emptyList();
                if (starred.isEmpty() && recentLocal.isEmpty() && frequentAlbums.isEmpty() && recentAlbums.isEmpty()) {
                    randomFallback = fetchResilientPoolSync(albumSongClient, browsingClient, null, target * 2);
                }

                List<Child> sample = blendMadeForYouSample(
                        starred,
                        recentLocal,
                        frequentAlbums,
                        recentAlbums,
                        randomFallback,
                        target,
                        new Random(System.nanoTime())
                );
                result.postValue(new ArrayList<>(sample));
            } catch (Exception e) {
                Log.e(TAG, "getMadeForYouSample failed", e);
                result.postValue(new ArrayList<>());
            }
        });
        return result;
    }

    /** Each request completes once, including partial mixes and unsupported recommendations. */
    public MutableLiveData<List<Child>> getInstantMix(String id, SeedType type, int count) {
        MutableLiveData<List<Child>> instantMix = new MutableLiveData<>();
        getInstantMix(id, type, count, instantMix::postValue);
        return instantMix;
    }

    /** A visible discovery card already has the seed; playing it must not require getSong again. */
    public MutableLiveData<List<Child>> getInstantMix(Child seed, int count) {
        MutableLiveData<List<Child>> instantMix = new MutableLiveData<>();
        if (seed == null || seed.getId() == null || seed.getId().isEmpty() || count <= 0) {
            instantMix.setValue(Collections.emptyList());
        } else {
            completeMix(seed.getId(), Collections.singletonList(seed), count, instantMix::postValue);
        }
        return instantMix;
    }

    public void getInstantMix(String id, SeedType type, int count, MediaCallbackInternal callback) {
        if (id == null || id.isEmpty() || type == null || count <= 0) {
            callback.onSongsAvailable(Collections.emptyList());
            return;
        }
        performSmartMix(id, type, count, seeds -> completeMix(id, seeds, count, callback));
    }

    private void completeMix(String id, List<Child> seeds, int count, MediaCallbackInternal callback) {
        List<Child> initial = mergeMix(seeds, Collections.emptyList(), count);
        if (initial.size() >= count) {
            callback.onSongsAvailable(initial);
        } else {
            fetchSimilarOnly(id, count, similar -> {
                List<Child> merged = mergeMix(initial, similar, count);
                if (initial.isEmpty() || similar.size() >= MIN_SIMILAR_SONGS || merged.size() >= count) {
                    callback.onSongsAvailable(merged);
                    return;
                }
                expandInstantMixFallback(initial, similar, count, callback);
            });
        }
    }

    private void expandInstantMixFallback(
            List<Child> seeds,
            List<Child> similar,
            int count,
            MediaCallbackInternal callback
    ) {
        Subsonic subsonic = App.getSubsonicClientInstance(false);
        BrowsingClient browsing = subsonic != null ? subsonic.getBrowsingClient() : null;
        AlbumSongListClient albumSongClient = subsonic != null ? subsonic.getAlbumSongListClient() : null;
        SearchingClient searchingClient = subsonic != null ? subsonic.getSearchingClient() : null;
        boolean hasContext = App.getContext() != null;

        Child primarySeed = seeds.get(0);
        Call<ApiResponse> artistSimCall = null;
        if (browsing != null && primarySeed != null && !isBlank(primarySeed.getArtistId())) {
            artistSimCall = browsing.getSimilarSongs2(primarySeed.getArtistId(), count);
        }

        // Preserve deterministic synchronous completion when no fallback clients or DB context exist.
        if (!hasContext && albumSongClient == null && searchingClient == null && artistSimCall == null) {
            callback.onSongsAvailable(mergeMix(seeds, similar, count));
            return;
        }

        final Call<ApiResponse> preparedArtistSimCall = artistSimCall;
        backgroundExecutor.execute(() -> {
            try {
                List<Child> artistSimilar = Collections.emptyList();
                if (preparedArtistSimCall != null) {
                    SubsonicResponse res = executeSync(preparedArtistSimCall);
                    if (res != null && res.getSimilarSongs2() != null && res.getSimilarSongs2().getSongs() != null) {
                        artistSimilar = filterPlayable(res.getSimilarSongs2().getSongs());
                    }
                }

                List<Child> sameArtist = fetchSameArtistTracksSync(primarySeed, searchingClient, browsing);
                List<Child> sameGenre = fetchSameGenreTracksSync(primarySeed, albumSongClient, count);
                List<Child> taste = new ArrayList<>();
                taste.addAll(fetchLocalHistorySync(60));
                taste.addAll(fetchStarredSync(albumSongClient));

                List<Child> combinedSimilar = new ArrayList<>();
                if (similar != null) combinedSimilar.addAll(similar);
                combinedSimilar.addAll(artistSimilar);

                List<Child> libraryPool = fetchResilientPoolSync(
                        albumSongClient,
                        browsing,
                        searchingClient,
                        Math.max(count * 3, 60)
                );

                List<Child> assembled = assembleInstantMix(
                        seeds,
                        combinedSimilar,
                        sameArtist,
                        sameGenre,
                        taste,
                        libraryPool,
                        count,
                        new Random(System.nanoTime())
                );
                callback.onSongsAvailable(assembled);
            } catch (Exception e) {
                Log.e(TAG, "expandInstantMixFallback failed", e);
                callback.onSongsAvailable(mergeMix(seeds, similar, count));
            }
        });
    }

    static List<Child> blendMadeForYouSample(
            List<Child> starred,
            List<Child> recentLocal,
            List<Child> frequentAlbumTracks,
            List<Child> recentAlbumTracks,
            List<Child> randomFallback,
            int limit,
            Random random
    ) {
        if (limit <= 0) return Collections.emptyList();
        Random rng = random != null ? random : new Random(1L);

        List<List<Child>> tasteBuckets = new ArrayList<>();
        for (List<Child> raw : Arrays.asList(starred, recentLocal, frequentAlbumTracks, recentAlbumTracks)) {
            List<Child> playable = new ArrayList<>(filterPlayable(raw));
            if (!playable.isEmpty()) {
                Collections.shuffle(playable, rng);
                tasteBuckets.add(playable);
            }
        }

        if (tasteBuckets.isEmpty()) {
            List<Child> fallback = new ArrayList<>(filterPlayable(randomFallback));
            Collections.shuffle(fallback, rng);
            return mergeMix(Collections.emptyList(), fallback, limit);
        }

        Collections.shuffle(tasteBuckets, rng);
        Map<String, Child> picked = new LinkedHashMap<>();
        Map<String, Integer> perArtist = new HashMap<>();

        // First pass: round-robin across active taste signals with light artist diversity.
        int[] cursors = new int[tasteBuckets.size()];
        boolean progressed = true;
        while (picked.size() < limit && progressed) {
            progressed = false;
            for (int b = 0; b < tasteBuckets.size(); b++) {
                if (picked.size() >= limit) break;
                List<Child> bucket = tasteBuckets.get(b);
                while (cursors[b] < bucket.size()) {
                    Child candidate = bucket.get(cursors[b]++);
                    if (tryPutWithArtistCap(picked, perArtist, candidate, limit, MADE_FOR_YOU_PER_ARTIST_CAP)) {
                        progressed = true;
                        break;
                    }
                }
            }
        }

        // Second pass: relax artist cap within user's taste tracks if small/single-artist taste pool.
        if (picked.size() < limit) {
            for (List<Child> bucket : tasteBuckets) {
                for (Child candidate : bucket) {
                    if (picked.size() >= limit) break;
                    tryPutWithArtistCap(picked, perArtist, candidate, limit, Integer.MAX_VALUE);
                }
            }
        }

        return new ArrayList<>(picked.values());
    }

    static List<Child> assembleInstantMix(
            List<Child> seeds,
            List<Child> similar,
            List<Child> sameArtist,
            List<Child> sameGenre,
            List<Child> taste,
            List<Child> libraryPool,
            int count,
            Random random
    ) {
        if (count <= 0) return Collections.emptyList();
        Random rng = random != null ? random : new Random(1L);

        Map<String, Child> chosen = new LinkedHashMap<>();
        Map<String, Integer> perArtist = new HashMap<>();

        // 1. Always preserve visible seed(s) at the head of the queue.
        if (seeds != null) {
            for (Child seed : seeds) {
                if (chosen.size() >= count) break;
                if (seed != null && seed.getId() != null && !seed.getId().isEmpty() && !seed.isDir() && !seed.isVideo()) {
                    Set<String> artists = MusicDiscoveryRules.artistNames(seed.getArtist());
                    for (String a : artists) {
                        perArtist.put(a, perArtist.getOrDefault(a, 0) + 1);
                    }
                    chosen.putIfAbsent(seed.getId(), seed);
                }
            }
        }

        // 2. Add direct similarity matches first.
        for (Child song : filterPlayable(similar)) {
            if (chosen.size() >= count) break;
            tryPutWithArtistCap(chosen, perArtist, song, count, PER_ARTIST_CAP + 1);
        }

        // 3. Interleave same-artist, same-genre, user taste, and library discovery with artist cap.
        List<Child> artistShuffled = new ArrayList<>(filterPlayable(sameArtist));
        List<Child> genreShuffled = new ArrayList<>(filterPlayable(sameGenre));
        List<Child> tasteShuffled = new ArrayList<>(filterPlayable(taste));
        List<Child> poolShuffled = new ArrayList<>(filterPlayable(libraryPool));
        Collections.shuffle(artistShuffled, rng);
        Collections.shuffle(genreShuffled, rng);
        Collections.shuffle(tasteShuffled, rng);
        Collections.shuffle(poolShuffled, rng);

        List<ArrayDeque<Child>> queues = new ArrayList<>();
        if (!artistShuffled.isEmpty()) queues.add(new ArrayDeque<>(artistShuffled));
        if (!genreShuffled.isEmpty()) queues.add(new ArrayDeque<>(genreShuffled));
        if (!tasteShuffled.isEmpty()) queues.add(new ArrayDeque<>(tasteShuffled));
        if (!poolShuffled.isEmpty()) queues.add(new ArrayDeque<>(poolShuffled));

        boolean anyRemaining = true;
        while (chosen.size() < count && anyRemaining) {
            anyRemaining = false;
            for (ArrayDeque<Child> q : queues) {
                if (chosen.size() >= count) break;
                while (!q.isEmpty()) {
                    anyRemaining = true;
                    // Allow up to 4 total tracks for the seed artist (seed + 3 same-artist), 3 for others.
                    if (tryPutWithArtistCap(chosen, perArtist, q.pollFirst(), count, PER_ARTIST_CAP + 1)) {
                        break;
                    }
                }
            }
        }

        // 4. Second pass with relaxed artist cap so small or single-artist libraries never return [seed].
        if (chosen.size() < count) {
            for (List<Child> batch : Arrays.asList(similar, artistShuffled, genreShuffled, tasteShuffled, poolShuffled)) {
                for (Child song : filterPlayable(batch)) {
                    if (chosen.size() >= count) break;
                    tryPutWithArtistCap(chosen, perArtist, song, count, Integer.MAX_VALUE);
                }
            }
        }

        return new ArrayList<>(chosen.values());
    }

    private static boolean tryPutWithArtistCap(
            Map<String, Child> target,
            Map<String, Integer> perArtist,
            Child song,
            int limit,
            int artistCap
    ) {
        if (target.size() >= limit || !isPlayable(song) || target.containsKey(song.getId())) {
            return false;
        }
        Set<String> artists = MusicDiscoveryRules.artistNames(song.getArtist());
        // Never apply per-artist cap to blank/unknown artist metadata.
        if (artistCap < Integer.MAX_VALUE && !artists.isEmpty()) {
            for (String artist : artists) {
                if (perArtist.getOrDefault(artist, 0) >= artistCap) {
                    return false;
                }
            }
        }
        for (String artist : artists) {
            perArtist.put(artist, perArtist.getOrDefault(artist, 0) + 1);
        }
        target.put(song.getId(), song);
        return true;
    }

    private static boolean isPlayable(Child song) {
        return song != null
                && !song.isDir()
                && !song.isVideo()
                && song.getId() != null
                && !song.getId().trim().isEmpty();
    }

    private static List<Child> filterPlayable(List<Child> rows) {
        if (rows == null || rows.isEmpty()) return Collections.emptyList();
        List<Child> valid = new ArrayList<>(rows.size());
        for (Child song : rows) {
            if (isPlayable(song)) valid.add(song);
        }
        return valid;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static SubsonicResponse executeSync(Call<ApiResponse> call) {
        if (call == null) return null;
        try {
            if (call.timeout() != null) {
                call.timeout().timeout(4, TimeUnit.SECONDS);
            }
            Response<ApiResponse> response = call.execute();
            if (response != null && response.isSuccessful() && response.body() != null) {
                SubsonicResponse sub = response.body().getSubsonicResponse();
                if (sub != null && sub.getError() == null) {
                    return sub;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static List<Child> fetchStarredSync(AlbumSongListClient albumSongClient) {
        if (albumSongClient == null) return Collections.emptyList();
        SubsonicResponse res = executeSync(albumSongClient.getStarred2());
        if (res != null && res.getStarred2() != null && res.getStarred2().getSongs() != null) {
            return filterPlayable(res.getStarred2().getSongs());
        }
        return Collections.emptyList();
    }

    @OptIn(markerClass = UnstableApi.class)
    private static List<Child> fetchLocalHistorySync(int limit) {
        if (App.getContext() == null) return Collections.emptyList();
        try {
            long since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(HISTORY_WINDOW_DAYS);
            List<Chronology> rows = AppDatabase.getInstance()
                    .chronologyDao()
                    .getPlayedSince(Preferences.getServerId(), since, limit);
            if (rows == null || rows.isEmpty()) return Collections.emptyList();
            List<Child> out = new ArrayList<>(rows.size());
            for (Chronology c : rows) {
                if (c == null) continue;
                if (c.getUserRating() != null && c.getUserRating() == 0) {
                    c.setUserRating(null);
                }
                if (isPlayable(c)) out.add(c);
            }
            return out;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private static List<Child> fetchAlbumTracksByTypeSync(
            AlbumSongListClient albumSongClient,
            BrowsingClient browsingClient,
            String type,
            int albumLimit,
            int trackLimit
    ) {
        if (albumSongClient == null || browsingClient == null) return Collections.emptyList();
        SubsonicResponse listRes = executeSync(albumSongClient.getAlbumList2(type, albumLimit, 0, null, null));
        if (listRes == null || listRes.getAlbumList2() == null || listRes.getAlbumList2().getAlbums() == null) {
            return Collections.emptyList();
        }
        Map<String, Child> collected = new LinkedHashMap<>();
        for (AlbumID3 album : listRes.getAlbumList2().getAlbums()) {
            if (collected.size() >= trackLimit) break;
            if (album == null || isBlank(album.getId())) continue;
            SubsonicResponse albumRes = executeSync(browsingClient.getAlbum(album.getId()));
            if (albumRes != null && albumRes.getAlbum() != null && albumRes.getAlbum().getSongs() != null) {
                for (Child song : filterPlayable(albumRes.getAlbum().getSongs())) {
                    collected.putIfAbsent(song.getId(), song);
                    if (collected.size() >= trackLimit) break;
                }
            }
        }
        return new ArrayList<>(collected.values());
    }

    private static List<Child> fetchSameArtistTracksSync(
            Child seed,
            SearchingClient searchingClient,
            BrowsingClient browsingClient
    ) {
        if (seed == null) return Collections.emptyList();
        Map<String, Child> found = new LinkedHashMap<>();
        Set<String> seedArtists = MusicDiscoveryRules.artistNames(seed.getArtist());
        String lead = null;
        if (!isBlank(seed.getArtist())) {
            String[] parts = seed.getArtist().split("[/•·,&、，(]");
            if (parts.length > 0 && !isBlank(parts[0])) {
                lead = parts[0].trim();
            }
        }
        if (lead != null && searchingClient != null) {
            SubsonicResponse searchRes = executeSync(searchingClient.search3(lead, 50, 0, 0, 0, 0, 0));
            if (searchRes != null && searchRes.getSearchResult3() != null && searchRes.getSearchResult3().getSongs() != null) {
                for (Child candidate : filterPlayable(searchRes.getSearchResult3().getSongs())) {
                    if (candidate.getId().equals(seed.getId())) continue;
                    boolean sameId = !isBlank(seed.getArtistId()) && seed.getArtistId().equals(candidate.getArtistId());
                    boolean sameName = !seedArtists.isEmpty()
                            && !Collections.disjoint(MusicDiscoveryRules.artistNames(candidate.getArtist()), seedArtists);
                    if (sameId || sameName) {
                        found.putIfAbsent(candidate.getId(), candidate);
                    }
                }
            }
        }
        if (found.isEmpty() && !isBlank(seed.getAlbumId()) && browsingClient != null) {
            SubsonicResponse albumRes = executeSync(browsingClient.getAlbum(seed.getAlbumId()));
            if (albumRes != null && albumRes.getAlbum() != null && albumRes.getAlbum().getSongs() != null) {
                for (Child sibling : filterPlayable(albumRes.getAlbum().getSongs())) {
                    if (!sibling.getId().equals(seed.getId())) {
                        found.putIfAbsent(sibling.getId(), sibling);
                    }
                }
            }
        }
        return new ArrayList<>(found.values());
    }

    private static List<Child> fetchSameGenreTracksSync(Child seed, AlbumSongListClient albumSongClient, int count) {
        if (seed == null || albumSongClient == null) return Collections.emptyList();
        List<String> genres = LibraryMixes.INSTANCE.genresOf(seed);
        if (genres.isEmpty()) return Collections.emptyList();
        String genre = genres.get(0);
        SubsonicResponse randomRes = executeSync(albumSongClient.getRandomSongs(Math.max(count, 20), null, null, genre));
        if (randomRes != null && randomRes.getRandomSongs() != null && randomRes.getRandomSongs().getSongs() != null) {
            List<Child> byRandom = filterPlayable(randomRes.getRandomSongs().getSongs());
            if (!byRandom.isEmpty()) return byRandom;
        }
        SubsonicResponse genreRes = executeSync(albumSongClient.getSongsByGenre(genre, Math.max(count * 2, 40), 0));
        if (genreRes != null && genreRes.getSongsByGenre() != null && genreRes.getSongsByGenre().getSongs() != null) {
            return filterPlayable(genreRes.getSongsByGenre().getSongs());
        }
        return Collections.emptyList();
    }

    private static List<Child> fetchResilientPoolSync(
            AlbumSongListClient albumSongClient,
            BrowsingClient browsingClient,
            SearchingClient searchingClient,
            int targetSize
    ) {
        Map<String, Child> pool = new LinkedHashMap<>();
        if (albumSongClient != null) {
            for (int size : new int[]{Math.min(Math.max(targetSize, 50), 100), 50}) {
                SubsonicResponse res = executeSync(albumSongClient.getRandomSongs(size, null, null));
                if (res != null && res.getRandomSongs() != null && res.getRandomSongs().getSongs() != null) {
                    for (Child song : filterPlayable(res.getRandomSongs().getSongs())) {
                        pool.putIfAbsent(song.getId(), song);
                    }
                }
                if (pool.size() >= targetSize) break;
            }
        }
        if (pool.size() < targetSize && albumSongClient != null && browsingClient != null) {
            for (String type : new String[]{"frequent", "recent", "random", "newest"}) {
                if (pool.size() >= targetSize) break;
                for (Child song : fetchAlbumTracksByTypeSync(albumSongClient, browsingClient, type, 4, targetSize)) {
                    pool.putIfAbsent(song.getId(), song);
                }
            }
        }
        if (pool.size() < targetSize && searchingClient != null) {
            SubsonicResponse res = executeSync(searchingClient.search3("", 80, 0, 0, 0, 0, 0));
            if (res != null && res.getSearchResult3() != null && res.getSearchResult3().getSongs() != null) {
                for (Child song : filterPlayable(res.getSearchResult3().getSongs())) {
                    pool.putIfAbsent(song.getId(), song);
                }
            }
        }
        return new ArrayList<>(pool.values());
    }

    static List<Child> mergeMix(List<Child> seeds, List<Child> similar, int count) {
        List<Child> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (List<Child> batch : java.util.Arrays.asList(seeds, similar)) {
            if (batch == null) continue;
            for (Child song : batch) {
                if (result.size() >= count) return result;
                if (song != null && song.getId() != null && !song.getId().isEmpty() && ids.add(song.getId())) {
                    result.add(song);
                }
            }
        }
        return result;
    }

    private void performSmartMix(final String id, final SeedType type, final int count, final MediaCallbackInternal callback) {
        switch (type) {
            case ARTIST:
                fetchSimilarByArtist(id, count, callback);
                break;
            case ALBUM:
                fetchAlbumSongs(id, count, callback);
                break;
            case TRACK:
                fetchSingleTrackThenSimilar(id, count, callback);
                break;
        }
    }

    private void fetchAlbumSongs(String albumId, int count, MediaCallbackInternal callback) {
        App.getSubsonicClientInstance(false).getBrowsingClient().getAlbum(albumId).enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                if (response.isSuccessful() && response.body() != null &&
                    response.body().getSubsonicResponse() != null &&
                    response.body().getSubsonicResponse().getAlbum() != null) {
                    List<Child> albumSongs = response.body().getSubsonicResponse().getAlbum().getSongs();
                    if (albumSongs != null && !albumSongs.isEmpty()) {
                        int fromAlbum = Math.min(count, albumSongs.size());
                        List<Child> limitedAlbumSongs = albumSongs.subList(0, fromAlbum);
                        callback.onSongsAvailable(new ArrayList<>(limitedAlbumSongs));
                        return;
                    }
                }
                callback.onSongsAvailable(Collections.emptyList());
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "fetchAlbumSongsThenSimilar.onFailure()", t);
                callback.onSongsAvailable(Collections.emptyList());
            }
        });
    }

    private void fetchSimilarByArtist(String artistId, final int count, final MediaCallbackInternal callback) {
        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getSimilarSongs2(artistId, count)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        List<Child> similar = extractSongs(response, "similarSongs2");
                        Log.d(TAG, "fetchSimilarByArtist.onResponse() - similar songs: " + similar.size());

                        if (!similar.isEmpty()) {
                            List<Child> limitedSimilar = similar.subList(0, Math.min(count, similar.size()));
                            callback.onSongsAvailable(limitedSimilar);
                        } else {
                            callback.onSongsAvailable(Collections.emptyList());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "fetchSimilarByArtist.onFailure()", t);
                        callback.onSongsAvailable(Collections.emptyList());
                    }
                });
    }

    private void fetchSingleTrackThenSimilar(String trackId, int count, MediaCallbackInternal callback) {
        App.getSubsonicClientInstance(false).getBrowsingClient().getSong(trackId).enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse() != null) {
                    Child song = response.body().getSubsonicResponse().getSong();
                    if (song != null) {
                        callback.onSongsAvailable(Collections.singletonList(song));
                        return;
                    }
                }
                callback.onSongsAvailable(Collections.emptyList());
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "fetchSingleTrackThenSimilar.onFailure()", t);
                callback.onSongsAvailable(Collections.emptyList());
            }
        });
    }

    private void fetchSimilarOnly(String id, int count, MediaCallbackInternal callback) {
        Call<ApiResponse> request = App.getSubsonicClientInstance(false).getBrowsingClient().getSimilarSongs(id, count);
        // Recommendations are optional: don't hold an already available seed behind a long API timeout.
        request.timeout().timeout(4, TimeUnit.SECONDS);
        request.enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                List<Child> songs = extractSongs(response, "similarSongs");
                int limit = Math.min(count, songs.size());
                callback.onSongsAvailable(songs.subList(0, limit));
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "fetchSimilarOnly.onFailure()", t);
                callback.onSongsAvailable(Collections.emptyList());
            }
        });
    }


    public MutableLiveData<List<Child>> getContinuousMix(String trackId, String artistId, int count) {
        MutableLiveData<List<Child>> continuousMix = new MutableLiveData<>();

        if (artistId != null && !artistId.isEmpty()) {
            App.getSubsonicClientInstance(false)
                    .getBrowsingClient()
                    .getSimilarSongs2(artistId, count)
                    .enqueue(new Callback<ApiResponse>() {
                        @Override
                        public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                            List<Child> songs = extractSongs(response, "similarSongs2");
                            if (!songs.isEmpty()) {
                                continuousMix.postValue(songs);
                            } else {
                                fetchContinuousFallback(trackId, count, continuousMix);
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                            fetchContinuousFallback(trackId, count, continuousMix);
                        }
                    });
        } else {
            fetchContinuousFallback(trackId, count, continuousMix);
        }

        return continuousMix;
    }

    private void fetchContinuousFallback(String trackId, int count, MutableLiveData<List<Child>> target) {
        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getSimilarSongs(trackId, count)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        target.postValue(extractSongs(response, "similarSongs"));
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        target.postValue(new ArrayList<>());
                    }
                });
    }

    private List<Child> extractSongs(Response<ApiResponse> response, String type) {
        if (response.isSuccessful() && response.body() != null) {
            SubsonicResponse res = response.body().getSubsonicResponse();
            if (res == null) return Collections.emptyList();
            List<Child> list = null;
            if (type.equals("similarSongs") && res.getSimilarSongs() != null) {
                list = res.getSimilarSongs().getSongs();
            } else if (type.equals("similarSongs2") && res.getSimilarSongs2() != null) {
                list = res.getSimilarSongs2().getSongs();
            }
            return (list != null) ? list : new ArrayList<>();
        }

        return new ArrayList<>();
    }

    public MutableLiveData<List<Child>> getRandomSample(int number, Integer fromYear, Integer toYear) {
        MutableLiveData<List<Child>> randomSongsSample = new MutableLiveData<>();
        App.getSubsonicClientInstance(false).getAlbumSongListClient().getRandomSongs(number, fromYear, toYear).enqueue(new Callback<ApiResponse>() {
            @Override public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                List<Child> songs = new ArrayList<>();
                if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getRandomSongs() != null) {
                    List<Child> returned = response.body().getSubsonicResponse().getRandomSongs().getSongs();
                    if (returned != null) {
                        songs.addAll(returned);
                    }
                }
                randomSongsSample.setValue(songs);
            }
            @Override public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
        });
        return randomSongsSample;
    }

    public MutableLiveData<List<Child>> getRandomSampleWithGenre(int number, Integer fromYear, Integer toYear, String genre) {
        MutableLiveData<List<Child>> randomSongsSample = new MutableLiveData<>();

        App.getSubsonicClientInstance(false).getAlbumSongListClient().getRandomSongs(number, fromYear, toYear, genre).enqueue(new Callback<ApiResponse>() {
            @Override public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                List<Child> songs = new ArrayList<>();
                if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getRandomSongs() != null) {
                    List<Child> returned = response.body().getSubsonicResponse().getRandomSongs().getSongs();
                    if (returned != null) {
                        songs.addAll(returned);
                    }
                }
                randomSongsSample.setValue(songs);
            }
            @Override public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
        });
        return randomSongsSample;
    }

    public void scrobble(String id, boolean submission) {
        App.getSubsonicClientInstance(false).getMediaAnnotationClient().scrobble(id, submission).enqueue(new Callback<ApiResponse>() {
            @Override public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {}
            @Override public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
        });
    }

    public void setRating(String id, int rating) {
        App.getSubsonicClientInstance(false).getMediaAnnotationClient().setRating(id, rating).enqueue(new Callback<ApiResponse>() {
            @Override public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {}
            @Override public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
        });
    }

    public MutableLiveData<List<Child>> getSongsByGenre(String id, int page) {
        MutableLiveData<List<Child>> songsByGenre = new MutableLiveData<>();
        App.getSubsonicClientInstance(false).getAlbumSongListClient().getSongsByGenre(id, 100, 100 * page).enqueue(new Callback<ApiResponse>() {
            @Override public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getSongsByGenre() != null) {
                    songsByGenre.setValue(response.body().getSubsonicResponse().getSongsByGenre().getSongs());
                }
            }
            @Override public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
        });
        return songsByGenre;
    }

    public MutableLiveData<List<Child>> getSongsByGenres(ArrayList<String> genresId) {
        MutableLiveData<List<Child>> songsByGenre = new MutableLiveData<>();
        for (String id : genresId) {
            App.getSubsonicClientInstance(false).getAlbumSongListClient().getSongsByGenre(id, 500, 0).enqueue(new Callback<ApiResponse>() {
                @Override public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                    List<Child> songs = new ArrayList<>();
                    if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getSongsByGenre() != null) {
                        List<Child> returned = response.body().getSubsonicResponse().getSongsByGenre().getSongs();
                        if (returned != null) {
                            songs.addAll(returned);
                        }
                    }
                    songsByGenre.setValue(songs);
                }
                @Override public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
            });
        }
        return songsByGenre;
    }

    public MutableLiveData<Child> getSong(String id) {
        MutableLiveData<Child> song = new MutableLiveData<>();
        App.getSubsonicClientInstance(false).getBrowsingClient().getSong(id).enqueue(new Callback<ApiResponse>() {
            @Override public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    song.setValue(response.body().getSubsonicResponse().getSong());
                }
            }
            @Override public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
        });
        return song;
    }

    public MutableLiveData<String> getSongLyrics(Child song) {
        MutableLiveData<String> lyrics = new MutableLiveData<>(null);
        App.getSubsonicClientInstance(false).getMediaRetrievalClient().getLyrics(song.getArtist(), song.getTitle()).enqueue(new Callback<ApiResponse>() {
            @Override public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getLyrics() != null) {
                    lyrics.setValue(response.body().getSubsonicResponse().getLyrics().getValue());
                }
            }
            @Override public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
        });
        return lyrics;
    }
}
