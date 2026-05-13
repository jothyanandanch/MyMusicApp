package com.example.mymusic.viewmodel;

import android.app.Application;
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
import java.util.Set;

public class MusicViewModel extends AndroidViewModel {

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

    // ── Phase 4: Favorites ────────────────────────────────────────────
    // Map<songId, Song> — insertion-ordered so the Favorites tab keeps add order.
    private final Map<Long, Song>           favoritesMap     = new LinkedHashMap<>();
    private final MutableLiveData<Set<Long>> favoriteIdsLiveData =
            new MutableLiveData<>(new LinkedHashSet<>());

    private ExoPlayer  exoPlayer;
    private List<Song> currentPlaylist;
    private int        currentIndex = -1;

    private final Set<Long> playedInShuffleCycle = new LinkedHashSet<>();

    private final Handler  progressHandler  = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (exoPlayer != null && exoPlayer.isPlaying()) {
                progressLiveData.postValue(exoPlayer.getCurrentPosition());
                long dur = exoPlayer.getDuration();
                durationLiveData.postValue(dur > 0 ? dur : 0L);
            }
            progressHandler.postDelayed(this, 500);
        }
    };

    private final ContentObserver contentObserver;
    private final Handler         handler        = new Handler(Looper.getMainLooper());
    private final Runnable        reloadRunnable = this::loadLocalMusic;

    public MusicViewModel(@NonNull Application application) {
        super(application);
        repository = new LocalMusicRepository(application);

        exoPlayer = new ExoPlayer.Builder(application).build();
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                isPlayingLiveData.postValue(isPlaying);
                if (isPlaying) progressHandler.post(progressRunnable);
                else           progressHandler.removeCallbacks(progressRunnable);
            }

            @Override
            public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                if (currentPlaylist == null) return;
                int idx = exoPlayer.getCurrentMediaItemIndex();
                if (idx < currentPlaylist.size()) {
                    currentIndex = idx;
                    Song song = currentPlaylist.get(currentIndex);
                    currentSongLiveData.postValue(song);
                    if (Boolean.TRUE.equals(shuffleModeLiveData.getValue())) {
                        playedInShuffleCycle.add(song.getId());
                    }
                }
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED)
                    isPlayingLiveData.postValue(false);
            }
        });

        contentObserver = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                handler.removeCallbacks(reloadRunnable);
                handler.postDelayed(reloadRunnable, 500);
            }
        };
        application.getContentResolver().registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, contentObserver);
    }

    // ── LiveData getters ──────────────────────────────────────────────
    public LiveData<List<Song>> getSongs()             { return songsLiveData; }
    public LiveData<Song>       getCurrentSong()       { return currentSongLiveData; }
    public LiveData<Boolean>    getIsPlaying()         { return isPlayingLiveData; }
    public LiveData<Long>       getProgress()          { return progressLiveData; }
    public LiveData<Long>       getDuration()          { return durationLiveData; }
    public LiveData<Integer>    getRepeatMode()        { return repeatModeLiveData; }
    public LiveData<Boolean>    getShuffleMode()       { return shuffleModeLiveData; }
    public LiveData<Boolean>    getEndOfQueue()        { return endOfQueueLiveData; }
    public LiveData<Boolean>    getShowEndDialog()     { return showEndDialogLiveData; }
    public LiveData<Set<Long>>  getFavoriteIds()       { return favoriteIdsLiveData; }

    // ── Phase 4: Favorites API ────────────────────────────────────────

    /**
     * Toggles the favourite state of [song].
     * Returns true if the song is now a favourite, false if removed.
     */
    public boolean toggleFavorite(Song song) {
        Set<Long> current = favoriteIdsLiveData.getValue();
        if (current == null) current = new LinkedHashSet<>();

        boolean nowFavorite;
        if (current.contains(song.getId())) {
            favoritesMap.remove(song.getId());
            current.remove(song.getId());
            nowFavorite = false;
        } else {
            favoritesMap.put(song.getId(), song);
            current.add(song.getId());
            nowFavorite = true;
        }
        // Post a new Set copy so LiveData observers fire
        favoriteIdsLiveData.postValue(new LinkedHashSet<>(current));
        return nowFavorite;
    }

    /** Returns true if [songId] is currently a favourite. */
    public boolean isFavorite(long songId) {
        Set<Long> ids = favoriteIdsLiveData.getValue();
        return ids != null && ids.contains(songId);
    }

    /** Snapshot list of favourite songs in add-order (for Favorites tab). */
    public List<Song> getFavoritesList() {
        return new ArrayList<>(favoritesMap.values());
    }

    // ── Shuffle no-repeat helpers ─────────────────────────────────────
    public boolean isShuffleCycleComplete() {
        if (currentPlaylist == null || currentPlaylist.isEmpty()) return false;
        if (!Boolean.TRUE.equals(shuffleModeLiveData.getValue())) return false;
        for (Song s : currentPlaylist) {
            if (!playedInShuffleCycle.contains(s.getId())) return false;
        }
        return true;
    }

    public void resetShuffleCycle() { playedInShuffleCycle.clear(); }

    // ── End-of-queue actions ──────────────────────────────────────────
    public void playAllShuffled() {
        List<Song> library = songsLiveData.getValue();
        if (library == null || library.isEmpty()) return;
        showEndDialogLiveData.postValue(false);
        endOfQueueLiveData.postValue(false);
        playedInShuffleCycle.clear();

        List<Song> shuffled = new ArrayList<>(library);
        Collections.shuffle(shuffled);
        currentPlaylist = shuffled;
        currentIndex    = 0;

        exoPlayer.clearMediaItems();
        for (Song s : shuffled) exoPlayer.addMediaItem(MediaItem.fromUri(s.getUri()));
        exoPlayer.setShuffleModeEnabled(false);
        shuffleModeLiveData.postValue(true);
        exoPlayer.seekToDefaultPosition(0);
        exoPlayer.prepare();
        exoPlayer.play();
        currentSongLiveData.postValue(shuffled.get(0));
        playedInShuffleCycle.add(shuffled.get(0).getId());
    }

    public void playFromBeginning() {
        if (currentPlaylist == null || currentPlaylist.isEmpty()) return;
        showEndDialogLiveData.postValue(false);
        endOfQueueLiveData.postValue(false);
        playedInShuffleCycle.clear();

        exoPlayer.clearMediaItems();
        for (Song s : currentPlaylist) exoPlayer.addMediaItem(MediaItem.fromUri(s.getUri()));
        exoPlayer.setShuffleModeEnabled(false);
        shuffleModeLiveData.postValue(false);
        exoPlayer.seekToDefaultPosition(0);
        exoPlayer.prepare();
        exoPlayer.play();
        currentSongLiveData.postValue(currentPlaylist.get(0));
    }

    public void dismissEndOfQueue() {
        showEndDialogLiveData.postValue(false);
        endOfQueueLiveData.postValue(false);
    }

    // ── Playback ──────────────────────────────────────────────────────
    public void playSong(Song song, List<Song> playlist, int index) {
        this.currentPlaylist = playlist;
        this.currentIndex    = index;
        playedInShuffleCycle.clear();
        if (Boolean.TRUE.equals(shuffleModeLiveData.getValue())) {
            playedInShuffleCycle.add(song.getId());
        }
        exoPlayer.clearMediaItems();
        for (Song s : playlist) exoPlayer.addMediaItem(MediaItem.fromUri(s.getUri()));
        exoPlayer.seekToDefaultPosition(index);
        exoPlayer.prepare();
        exoPlayer.play();
        currentSongLiveData.postValue(song);
        endOfQueueLiveData.postValue(false);
        showEndDialogLiveData.postValue(false);
    }

    public void togglePlayPause() {
        if (exoPlayer.isPlaying()) exoPlayer.pause();
        else                       exoPlayer.play();
    }

    public void playNextFromMiniPlayer() {
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem();
        } else {
            boolean shuffle = Boolean.TRUE.equals(shuffleModeLiveData.getValue());
            if (currentPlaylist == null || currentPlaylist.isEmpty()) return;
            playedInShuffleCycle.clear();
            exoPlayer.clearMediaItems();
            List<Song> toPlay = new ArrayList<>(currentPlaylist);
            if (shuffle) Collections.shuffle(toPlay);
            for (Song s : toPlay) exoPlayer.addMediaItem(MediaItem.fromUri(s.getUri()));
            exoPlayer.setShuffleModeEnabled(false);
            exoPlayer.seekToDefaultPosition(0);
            exoPlayer.prepare();
            exoPlayer.play();
            currentSongLiveData.postValue(toPlay.get(0));
            playedInShuffleCycle.add(toPlay.get(0).getId());
        }
    }

    public void playNextFromFullPlayer() {
        if (exoPlayer.hasNextMediaItem()) exoPlayer.seekToNextMediaItem();
        else showEndDialogLiveData.postValue(true);
    }

    public void playPrevious() {
        if (exoPlayer.getCurrentPosition() > 3000) exoPlayer.seekTo(0);
        else if (exoPlayer.hasPreviousMediaItem())  exoPlayer.seekToPreviousMediaItem();
    }

    public void seekTo(long positionMs) {
        exoPlayer.seekTo(positionMs);
        progressLiveData.postValue(positionMs);
    }

    public void toggleRepeat() {
        int next = exoPlayer.getRepeatMode() == Player.REPEAT_MODE_OFF ? Player.REPEAT_MODE_ALL
                : exoPlayer.getRepeatMode() == Player.REPEAT_MODE_ALL  ? Player.REPEAT_MODE_ONE
                : Player.REPEAT_MODE_OFF;
        exoPlayer.setRepeatMode(next);
        repeatModeLiveData.postValue(next);
    }

    public void toggleShuffle() {
        boolean cur  = Boolean.TRUE.equals(shuffleModeLiveData.getValue());
        boolean next = !cur;
        if (next) {
            if (currentPlaylist != null && currentIndex >= 0) {
                Song nowPlaying  = currentPlaylist.get(currentIndex);
                List<Song> after = new ArrayList<>(currentPlaylist.subList(currentIndex + 1, currentPlaylist.size()));
                Collections.shuffle(after);
                List<Song> before   = new ArrayList<>(currentPlaylist.subList(0, currentIndex));
                List<Song> reordered = new ArrayList<>(before);
                reordered.add(nowPlaying);
                reordered.addAll(after);
                currentPlaylist = reordered;
                exoPlayer.clearMediaItems();
                for (Song s : reordered) exoPlayer.addMediaItem(MediaItem.fromUri(s.getUri()));
                exoPlayer.seekToDefaultPosition(before.size());
                exoPlayer.prepare();
            }
            playedInShuffleCycle.clear();
            if (currentPlaylist != null && currentIndex >= 0)
                playedInShuffleCycle.add(currentPlaylist.get(Math.min(currentIndex, currentPlaylist.size()-1)).getId());
        } else {
            playedInShuffleCycle.clear();
        }
        exoPlayer.setShuffleModeEnabled(false);
        shuffleModeLiveData.postValue(next);
    }

    public void loadLocalMusic() {
        new Thread(() -> {
            List<Song> local = repository.fetchLocalSongs();
            songsLiveData.postValue(local);
        }).start();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        getApplication().getContentResolver().unregisterContentObserver(contentObserver);
        handler.removeCallbacks(reloadRunnable);
        progressHandler.removeCallbacks(progressRunnable);
        if (exoPlayer != null) { exoPlayer.release(); exoPlayer = null; }
    }
}
