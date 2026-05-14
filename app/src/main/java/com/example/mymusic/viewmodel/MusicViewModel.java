package com.example.mymusic.viewmodel;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import com.example.mymusic.model.Song;
import com.example.mymusic.repository.LocalMusicRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MusicViewModel extends AndroidViewModel {

    // ── SharedPreferences keys ────────────────────────────────────────
    private static final String PREFS_NAME  = "MyMusicPrefs";
    private static final String KEY_FAV_IDS = "favorite_song_ids";

    private final LocalMusicRepository        repository;
    private final MutableLiveData<List<Song>> songsLiveData         = new MutableLiveData<>();
    private final MutableLiveData<Song>       currentSongLiveData   = new MutableLiveData<>();
    private final MutableLiveData<Boolean>    isPlayingLiveData     = new MutableLiveData<>(false);
    private final MutableLiveData<Long>       progressLiveData      = new MutableLiveData<>(0L);
    private final MutableLiveData<Long>       durationLiveData      = new MutableLiveData<>(0L);
    private final MutableLiveData<Integer>    repeatModeLiveData    = new MutableLiveData<>(Player.REPEAT_MODE_OFF);
    private final MutableLiveData<Boolean>    shuffleModeLiveData   = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean>    endOfQueueLiveData    = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean>    showEndDialogLiveData = new MutableLiveData<>(false);

    // Favorites — insertion-ordered
    private final Map<Long, Song>            favoritesMap        = new LinkedHashMap<>();
    private final MutableLiveData<Set<Long>> favoriteIdsLiveData = new MutableLiveData<>(new LinkedHashSet<>());

    private ExoPlayer  exoPlayer;
    private List<Song> currentPlaylist;
    private int        currentIndex = -1;

    // ── Shuffle: manual no-repeat queue ──────────────────────────────
    // When shuffle is ON we maintain our own unplayed list so ExoPlayer's
    // built-in shuffle (which can repeat) is never used.
    private List<Song> shuffleUnplayed = new ArrayList<>();  // songs not yet played this cycle
    private List<Song> shufflePlayed   = new ArrayList<>();  // songs already played this cycle

    // ── Progress polling at 200 ms for smooth seek bar ────────────────
    private final Handler  progressHandler  = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override public void run() {
            if (exoPlayer != null && exoPlayer.isPlaying()) {
                progressLiveData.postValue(exoPlayer.getCurrentPosition());
                long dur = exoPlayer.getDuration();
                durationLiveData.postValue(dur > 0 ? dur : 0L);
            }
            progressHandler.postDelayed(this, 200);  // 200 ms — smoother bar
        }
    };

    private final ContentObserver contentObserver;
    private final Handler         handler        = new Handler(Looper.getMainLooper());
    private final Runnable        reloadRunnable = this::loadLocalMusic;

    public MusicViewModel(@NonNull Application application) {
        super(application);
        repository = new LocalMusicRepository(application);

        loadFavoritesFromPrefs();

        exoPlayer = new ExoPlayer.Builder(application).build();
        // Disable ExoPlayer's own shuffle — we handle ordering manually
        exoPlayer.setShuffleModeEnabled(false);

        exoPlayer.addListener(new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean isPlaying) {
                isPlayingLiveData.postValue(isPlaying);
                if (isPlaying) progressHandler.post(progressRunnable);
                else           progressHandler.removeCallbacks(progressRunnable);
            }

            @Override public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                if (currentPlaylist == null) return;
                int idx = exoPlayer.getCurrentMediaItemIndex();
                if (idx >= 0 && idx < currentPlaylist.size()) {
                    currentIndex = idx;
                    Song song = currentPlaylist.get(currentIndex);
                    currentSongLiveData.postValue(song);

                    // Track played songs for the no-repeat shuffle cycle
                    if (Boolean.TRUE.equals(shuffleModeLiveData.getValue())) {
                        shufflePlayed.add(song);
                        shuffleUnplayed.remove(song);
                    }
                }
            }

            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) {
                    isPlayingLiveData.postValue(false);
                    endOfQueueLiveData.postValue(false);
                    showEndDialogLiveData.postValue(false);
                }
            }
        });

        contentObserver = new ContentObserver(handler) {
            @Override public void onChange(boolean selfChange) {
                handler.removeCallbacks(reloadRunnable);
                handler.postDelayed(reloadRunnable, 500);
            }
        };
        application.getContentResolver().registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, contentObserver);
    }

    // ── LiveData getters ──────────────────────────────────────────────
    public LiveData<List<Song>> getSongs()         { return songsLiveData; }
    public LiveData<Song>       getCurrentSong()   { return currentSongLiveData; }
    public LiveData<Boolean>    getIsPlaying()     { return isPlayingLiveData; }
    public LiveData<Long>       getProgress()      { return progressLiveData; }
    public LiveData<Long>       getDuration()      { return durationLiveData; }
    public LiveData<Integer>    getRepeatMode()    { return repeatModeLiveData; }
    public LiveData<Boolean>    getShuffleMode()   { return shuffleModeLiveData; }
    public LiveData<Boolean>    getEndOfQueue()    { return endOfQueueLiveData; }
    public LiveData<Boolean>    getShowEndDialog() { return showEndDialogLiveData; }
    public LiveData<Set<Long>>  getFavoriteIds()   { return favoriteIdsLiveData; }

    // ── Favorites ─────────────────────────────────────────────────────

    private void loadFavoritesFromPrefs() {
        SharedPreferences prefs = getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> saved = prefs.getStringSet(KEY_FAV_IDS, new LinkedHashSet<>());
        Set<Long> restoredIds = new LinkedHashSet<>();
        if (saved != null) {
            for (String idStr : saved) {
                try { restoredIds.add(Long.parseLong(idStr)); }
                catch (NumberFormatException ignored) {}
            }
        }
        favoriteIdsLiveData.postValue(restoredIds);
    }

    private void syncFavoritesWithSongs(List<Song> songs) {
        Set<Long> persistedIds = favoriteIdsLiveData.getValue();
        if (persistedIds == null || persistedIds.isEmpty()) return;
        Map<Long, Song> songById = new LinkedHashMap<>();
        for (Song s : songs) songById.put(s.getId(), s);
        favoritesMap.clear();
        Set<Long> validIds = new LinkedHashSet<>();
        for (Long id : persistedIds) {
            Song s = songById.get(id);
            if (s != null) { favoritesMap.put(id, s); validIds.add(id); }
        }
        favoriteIdsLiveData.postValue(validIds);
        saveFavoritesToPrefs(validIds);
    }

    private void saveFavoritesToPrefs(Set<Long> ids) {
        Set<String> stringSet = new LinkedHashSet<>();
        for (Long id : ids) stringSet.add(String.valueOf(id));
        getApplication()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_FAV_IDS, stringSet)
                .apply();
    }

    public boolean toggleFavorite(Song song) {
        Set<Long> current = favoriteIdsLiveData.getValue();
        if (current == null) current = new LinkedHashSet<>();
        Set<Long> updated  = new LinkedHashSet<>(current);
        boolean   isFavNow = updated.contains(song.getId());
        if (isFavNow) { updated.remove(song.getId()); favoritesMap.remove(song.getId()); }
        else          { updated.add(song.getId());    favoritesMap.put(song.getId(), song); }
        favoriteIdsLiveData.setValue(updated);
        saveFavoritesToPrefs(updated);
        return !isFavNow;
    }

    public boolean isFavorite(long songId) {
        Set<Long> ids = favoriteIdsLiveData.getValue();
        return ids != null && ids.contains(songId);
    }

    public List<Song> getFavoritesList() {
        return new ArrayList<>(favoritesMap.values());
    }

    // ── Shuffle helpers ───────────────────────────────────────────────

    /**
     * Builds the no-repeat shuffle unplayed queue from currentPlaylist,
     * excluding the song that is currently playing so it is not repeated
     * immediately. The current song is placed in shufflePlayed.
     */
    private void buildShuffleQueues(Song currentSong) {
        shufflePlayed.clear();
        shuffleUnplayed.clear();
        if (currentPlaylist == null) return;
        for (Song s : currentPlaylist) {
            if (currentSong != null && s.getId() == currentSong.getId()) {
                shufflePlayed.add(s);   // already playing — do not put in unplayed
            } else {
                shuffleUnplayed.add(s);
            }
        }
        Collections.shuffle(shuffleUnplayed);
    }

    /**
     * Returns the next song for shuffle mode ensuring no repeats.
     * When the unplayed list is exhausted, starts a fresh cycle
     * (re-shuffles everything except the last played song).
     */
    private Song pickNextShuffleSong() {
        if (shuffleUnplayed.isEmpty()) {
            // All songs played — start a new cycle
            Song lastPlayed = shufflePlayed.isEmpty()
                    ? null
                    : shufflePlayed.get(shufflePlayed.size() - 1);
            shufflePlayed.clear();
            shuffleUnplayed.clear();
            if (currentPlaylist != null) {
                for (Song s : currentPlaylist) {
                    // Keep the last-played song out of the immediate next pick
                    if (lastPlayed == null || s.getId() != lastPlayed.getId()) {
                        shuffleUnplayed.add(s);
                    }
                }
            }
            Collections.shuffle(shuffleUnplayed);
            if (lastPlayed != null) shufflePlayed.add(lastPlayed);
        }
        if (shuffleUnplayed.isEmpty()) return null;
        return shuffleUnplayed.remove(0);
    }

    public boolean isShuffleCycleComplete() {
        if (!Boolean.TRUE.equals(shuffleModeLiveData.getValue())) return false;
        return shuffleUnplayed.isEmpty();
    }

    public void resetShuffleCycle() {
        if (currentPlaylist != null) {
            buildShuffleQueues(currentSongLiveData.getValue());
        }
    }

    // ── Play actions ──────────────────────────────────────────────────

    public void playSong(Song song, List<Song> playlist, int index) {
        if (exoPlayer == null) return;
        currentPlaylist = new ArrayList<>(playlist);
        currentIndex    = index;

        exoPlayer.clearMediaItems();
        for (Song s : currentPlaylist) exoPlayer.addMediaItem(MediaItem.fromUri(s.getUri()));
        exoPlayer.setShuffleModeEnabled(false);
        exoPlayer.prepare();
        exoPlayer.seekToDefaultPosition(index);
        exoPlayer.play();

        currentSongLiveData.postValue(song);
        endOfQueueLiveData.postValue(false);
        showEndDialogLiveData.postValue(false);

        // Rebuild shuffle queues so the newly started song is in "played"
        if (Boolean.TRUE.equals(shuffleModeLiveData.getValue())) {
            buildShuffleQueues(song);
        }
    }

    public void togglePlayPause() {
        if (exoPlayer == null) return;
        if (exoPlayer.isPlaying()) exoPlayer.pause();
        else                       exoPlayer.play();
    }

    public void playNextFromMiniPlayer() {
        if (exoPlayer == null) return;
        if (Boolean.TRUE.equals(shuffleModeLiveData.getValue())) {
            playNextShuffle();
        } else if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem();
            progressLiveData.postValue(0L);
        } else {
            // End of queue in normal mode — wrap around
            exoPlayer.seekToDefaultPosition(0);
            exoPlayer.prepare();
            exoPlayer.play();
            if (currentPlaylist != null && !currentPlaylist.isEmpty())
                currentSongLiveData.postValue(currentPlaylist.get(0));
        }
    }

    public void playNextFromFullPlayer() {
        if (exoPlayer == null) return;
        if (Boolean.TRUE.equals(shuffleModeLiveData.getValue())) {
            playNextShuffle();
        } else if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem();
            progressLiveData.postValue(0L);
        } else {
            showEndDialogLiveData.postValue(true);
        }
    }

    /**
     * Core no-repeat shuffle next: picks the next song from shuffleUnplayed,
     * seeks ExoPlayer to that song's position in currentPlaylist, and plays it.
     * Never restarts the current song.
     */
    private void playNextShuffle() {
        Song next = pickNextShuffleSong();
        if (next == null) return;

        // Find its index in currentPlaylist for ExoPlayer
        int targetIndex = -1;
        for (int i = 0; i < currentPlaylist.size(); i++) {
            if (currentPlaylist.get(i).getId() == next.getId()) {
                targetIndex = i;
                break;
            }
        }
        if (targetIndex < 0) return;

        currentIndex = targetIndex;
        exoPlayer.seekToDefaultPosition(targetIndex);
        exoPlayer.play();
        currentSongLiveData.postValue(next);
        progressLiveData.postValue(0L);
    }

    public void playPrevious() {
        if (exoPlayer == null) return;
        if (exoPlayer.getCurrentPosition() > 3000) {
            exoPlayer.seekTo(0);
            progressLiveData.postValue(0L);
        } else if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPreviousMediaItem();
            progressLiveData.postValue(0L);
        }
    }

    public void seekTo(long positionMs) {
        if (exoPlayer != null) {
            exoPlayer.seekTo(positionMs);
            progressLiveData.postValue(positionMs);
        }
    }

    public void toggleRepeat() {
        if (exoPlayer == null) return;
        int next = exoPlayer.getRepeatMode() == Player.REPEAT_MODE_OFF ? Player.REPEAT_MODE_ALL
                : exoPlayer.getRepeatMode() == Player.REPEAT_MODE_ALL  ? Player.REPEAT_MODE_ONE
                : Player.REPEAT_MODE_OFF;
        exoPlayer.setRepeatMode(next);
        repeatModeLiveData.postValue(next);
    }

    /**
     * Toggle shuffle ON/OFF.
     *
     * ON  — Builds the no-repeat unplayed queue from all songs in
     *        currentPlaylist EXCEPT the currently playing one.
     *        The current song keeps playing without interruption.
     *
     * OFF — Clears the shuffle queues. ExoPlayer's natural ordered
     *        playback resumes from the current song's position.
     */
    public void toggleShuffle() {
        boolean cur  = Boolean.TRUE.equals(shuffleModeLiveData.getValue());
        boolean next = !cur;

        if (next) {
            // Build the no-repeat queue; current song is already "played"
            Song nowPlaying = currentSongLiveData.getValue();
            buildShuffleQueues(nowPlaying);
            // Do NOT seekTo, clearMediaItems, or call prepare() — the song
            // currently playing must NOT restart.
        } else {
            // Turning shuffle off — clear queues
            shuffleUnplayed.clear();
            shufflePlayed.clear();
        }

        // Keep ExoPlayer's own shuffle off — we manage order ourselves
        exoPlayer.setShuffleModeEnabled(false);
        shuffleModeLiveData.postValue(next);
    }

    /**
     * Shuffle the whole playlist and start playing from the first song.
     * Used by "Shuffle Play" buttons outside of NowPlaying.
     */
    public void playAllShuffled(List<Song> library) {
        if (library == null || library.isEmpty()) return;
        showEndDialogLiveData.postValue(false);
        endOfQueueLiveData.postValue(false);

        List<Song> shuffled = new ArrayList<>(library);
        Collections.shuffle(shuffled);

        currentPlaylist = shuffled;
        currentIndex    = 0;

        exoPlayer.clearMediaItems();
        for (Song s : shuffled) exoPlayer.addMediaItem(MediaItem.fromUri(s.getUri()));
        exoPlayer.setShuffleModeEnabled(false);
        exoPlayer.prepare();
        exoPlayer.seekToDefaultPosition(0);
        exoPlayer.play();

        shuffleModeLiveData.postValue(true);
        currentSongLiveData.postValue(shuffled.get(0));

        buildShuffleQueues(shuffled.get(0));
    }

    /**
     * Shuffle the given playlist but keep the currently playing song
     * at the front so it is not interrupted.
     */
    public void playShuffled(List<Song> playlist) {
        if (playlist == null || playlist.isEmpty()) return;

        Song       currentSong = currentSongLiveData.getValue();
        List<Song> shuffled    = new ArrayList<>(playlist);
        int        nowIdx      = -1;

        if (currentSong != null) {
            for (int i = 0; i < shuffled.size(); i++) {
                if (shuffled.get(i).getId() == currentSong.getId()) {
                    nowIdx = i;
                    break;
                }
            }
        }

        if (nowIdx >= 0) {
            Song nowPlaying = shuffled.remove(nowIdx);
            Collections.shuffle(shuffled);
            shuffled.add(0, nowPlaying);
            currentIndex = 0;
        } else {
            Collections.shuffle(shuffled);
            currentIndex = 0;
        }

        currentPlaylist = shuffled;

        exoPlayer.clearMediaItems();
        for (Song s : shuffled) exoPlayer.addMediaItem(MediaItem.fromUri(s.getUri()));
        exoPlayer.setShuffleModeEnabled(false);
        shuffleModeLiveData.postValue(true);
        currentSongLiveData.postValue(shuffled.get(0));

        buildShuffleQueues(shuffled.get(0));

        // If same song is already playing, do not restart it
        if (nowIdx >= 0 && exoPlayer.isPlaying()) return;

        exoPlayer.prepare();
        exoPlayer.seekToDefaultPosition(0);
        exoPlayer.play();

        endOfQueueLiveData.postValue(false);
        showEndDialogLiveData.postValue(false);
    }

    // ── Local music loader ────────────────────────────────────────────

    public void loadLocalMusic() {
        new Thread(() -> {
            List<Song> local = repository.fetchLocalSongs();
            songsLiveData.postValue(local);
            syncFavoritesWithSongs(local);
        }).start();
    }

    // ── Cleanup ───────────────────────────────────────────────────────

    @Override
    protected void onCleared() {
        super.onCleared();
        getApplication().getContentResolver().unregisterContentObserver(contentObserver);
        handler.removeCallbacks(reloadRunnable);
        progressHandler.removeCallbacks(progressRunnable);
        if (exoPlayer != null) { exoPlayer.release(); exoPlayer = null; }
    }
}
