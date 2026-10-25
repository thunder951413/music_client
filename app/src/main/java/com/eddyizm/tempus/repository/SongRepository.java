package com.eddyizm.tempus.repository;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.eddyizm.tempus.App;
import com.eddyizm.tempus.subsonic.base.ApiResponse;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.subsonic.models.SubsonicResponse;
import com.eddyizm.tempus.util.Constants.SeedType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SongRepository {

    private static final String TAG = "SongRepository";

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
                                    starredSongs.setValue(songs);
                                } else {
                                    Collections.shuffle(songs);
                                    starredSongs.setValue(songs.subList(0, Math.min(size, songs.size())));
                                }
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
                });

        return starredSongs;
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
            fetchSimilarOnly(id, count, similar ->
                    callback.onSongsAvailable(mergeMix(initial, similar, count)));
        }
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
