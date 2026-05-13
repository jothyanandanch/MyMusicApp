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
import java.util.LinkedHashSet;
import java.util.List;
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
    // true  = mini-player clicked next at end → auto-restart signal
    // For full-player: endOfQueueDialogLiveData controls the dialog
    private final MutableLiveData<Boolean>    endOfQueueLiveData    = new MutableLiveData<>(false);
    // Used ONLY by the full Now Playing screen to show its dialog
    private final MutableLiveData<Boolean>    showEndDialogLiveData = new MutableLiveData<>(false);

    private ExoPlayer  exoPlayer;
    private List<Song> currentPlaylist;
    private int        currentIndex = -1;

    // Shuffle no-repeat tracking:
    // Keeps track of song IDs already played in the current shuffle cycle.
    // Cleared when: shuffle is turned off, a fresh playAllShuffled() is called,
    // or we deliberately start a new cycle (user added to queue).
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
                    // Track played songs for shuffle no-repeat
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
    public LiveData<List<Song>> getSongs()           { return songsLiveData; }
    public LiveData<Song>       getCurrentSong()     { return currentSongLiveData; }
    public LiveData<Boolean>    getIsPlaying()       { return isPlayingLiveData; }
    public LiveData<Long>       getProgress()        { return progressLiveData; }
    public LiveData<Long>       getDuration()        { return durationLiveData; }
    public LiveData<Integer>    getRepeatMode()      { return repeatModeLiveData; }
    public LiveData<Boolean>    getShuffleMode()     { return shuffleModeLiveData; }
    public LiveData<Boolean>    getEndOfQueue()      { return endOfQueueLiveData; }
    public LiveData<Boolean>    getShowEndDialog()   { return showEndDialogLiveData; }

    // ── Shuffle no-repeat helpers ─────────────────────────────────────
    /**
     * Returns true when shuffle is ON and all songs in the current playlist
     * have been played at least once in this cycle.
     */
    public boolean isShuffleCycleComplete() {
        if (currentPlaylist == null || currentPlaylist.isEmpty()) return false;
        if (!Boolean.TRUE.equals(shuffleModeLiveData.getValue())) return false;
        for (Song s : currentPlaylist) {
            if (!playedInShuffleCycle.contains(s.getId())) return false;
        }
        return true;
    }

    /** Resets the shuffle cycle so all songs are eligible again. */
    public void resetShuffleCycle() {
        playedInShuffleCycle.clear();
    }

    // ── End-of-queue actions ──────────────────────────────────────────
    /**
     * "Play Randomly" from the full Now Playing end-of-queue dialog.
     * Uses entire music library (songsLiveData) with shuffle ON.
     * No repeats until all songs are played.
     */
    public void playAllShuffled() {
        List<Song> library = songsLiveData.getValue();
        if (library == null || library.isEmpty()) return;
        showEndDialogLiveData.postValue(false);
        endOfQueueLiveData.postValue(false);
        playedInShuffleCycle.clear();

        List<Song> shuffled = new ArrayList<>(library);
        Collections.shuffle(shuffled);
        currentPlaylist = shuffled;
        currentIndex = 0;

        exoPlayer.clearMediaItems();
        for (Song s : shuffled) exoPlayer.addMediaItem(MediaItem.fromUri(s.getUri()));
        exoPlayer.setShuffleModeEnabled(false); // order already shuffled manually for no-repeat
        shuffleModeLiveData.postValue(true);     // UI still shows shuffle as ON
        exoPlayer.seekToDefaultPosition(0);
        exoPlayer.prepare();
        exoPlayer.play();
        currentSongLiveData.postValue(shuffled.get(0));
        playedInShuffleCycle.add(shuffled.get(0).getId());
    }

    /**
     * "Play from Beginning" — restarts the current playlist in order, shuffle OFF.
     */
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

    /** Dismiss end-of-queue dialog (full player) without action. */
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

    /**
     * Called from MINI PLAYER next button.
     * At end of queue → auto-restart from beginning with current shuffle state.
     * No dialog shown.
     */
    public void playNextFromMiniPlayer() {
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem();
        } else {
            // Auto-restart: no dialog, just restart with current shuffle preference
            boolean shuffle = Boolean.TRUE.equals(shuffleModeLiveData.getValue());
            if (currentPlaylist == null || currentPlaylist.isEmpty()) return;
            playedInShuffleCycle.clear();
            exoPlayer.clearMediaItems();
            List<Song> toPlay = new ArrayList<>(currentPlaylist);
            if (shuffle) Collections.shuffle(toPlay);
            for (Song s : toPlay) exoPlayer.addMediaItem(MediaItem.fromUri(s.getUri()));
            exoPlayer.setShuffleModeEnabled(false); // manual order for no-repeat guarantee
            exoPlayer.seekToDefaultPosition(0);
            exoPlayer.prepare();
            exoPlayer.play();
            currentSongLiveData.postValue(toPlay.get(0));
            playedInShuffleCycle.add(toPlay.get(0).getId());
        }
    }

    /**
     * Called from FULL NOW PLAYING screen next button.
     * At end of queue → show dialog asking user what to do.
     */
    public void playNextFromFullPlayer() {
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem();
        } else {
            showEndDialogLiveData.postValue(true);
        }
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
        int next = exoPlayer.getRepeatMode() == Player.REPEAT_MODE_OFF  ? Player.REPEAT_MODE_ALL
                : exoPlayer.getRepeatMode() == Player.REPEAT_MODE_ALL  ? Player.REPEAT_MODE_ONE
                : Player.REPEAT_MODE_OFF;
        exoPlayer.setRepeatMode(next);
        repeatModeLiveData.postValue(next);
    }

    public void toggleShuffle() {
        boolean cur = Boolean.TRUE.equals(shuffleModeLiveData.getValue());
        boolean next = !cur;
        if (next) {
            // Turning shuffle ON: re-order remaining songs in current playlist for no-repeat
            if (currentPlaylist != null && currentIndex >= 0) {
                Song nowPlaying = currentPlaylist.get(currentIndex);
                List<Song> remaining = new ArrayList<>(currentPlaylist.subList(currentIndex + 1, currentPlaylist.size()));
                Collections.shuffle(remaining);
                List<Song> reordered = new ArrayList<>();
                reordered.add(nowPlaying);
                reordered.addAll(remaining);
                // Prepend already-played songs before current
                List<Song> before = new ArrayList<>(currentPlaylist.subList(0, currentIndex));
                List<Song> full = new ArrayList<>(before);
                full.addAll(reordered);
                currentPlaylist = full;
                exoPlayer.clearMediaItems();
                for (Song s : full) exoPlayer.addMediaItem(MediaItem.fromUri(s.getUri()));
                exoPlayer.seekToDefaultPosition(before.size()); // seek to current song
                exoPlayer.prepare();
                // do NOT call play() — maintain current playing state
            }
            playedInShuffleCycle.clear();
            if (currentPlaylist != null && currentIndex >= 0) {
                playedInShuffleCycle.add(currentPlaylist.get(Math.min(currentIndex, currentPlaylist.size()-1)).getId());
            }
        } else {
            playedInShuffleCycle.clear();
        }
        exoPlayer.setShuffleModeEnabled(false); // we handle order manually
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
