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

import java.util.Collections;
import java.util.List;

public class MusicViewModel extends AndroidViewModel {

    private final LocalMusicRepository        repository;
    private final MutableLiveData<List<Song>> songsLiveData       = new MutableLiveData<>();
    private final MutableLiveData<Song>       currentSongLiveData  = new MutableLiveData<>();
    private final MutableLiveData<Boolean>    isPlayingLiveData    = new MutableLiveData<>(false);
    private final MutableLiveData<Long>       progressLiveData     = new MutableLiveData<>(0L);
    private final MutableLiveData<Long>       durationLiveData     = new MutableLiveData<>(0L);
    private final MutableLiveData<Integer>    repeatModeLiveData   = new MutableLiveData<>(Player.REPEAT_MODE_OFF);
    private final MutableLiveData<Boolean>    shuffleModeLiveData  = new MutableLiveData<>(false);
    // Signals the UI to show the end-of-queue dialog (true = show, false = dismiss)
    private final MutableLiveData<Boolean>    endOfQueueLiveData   = new MutableLiveData<>(false);

    private ExoPlayer  exoPlayer;
    private List<Song> currentPlaylist;
    private int        currentIndex = -1;

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
                if (currentPlaylist != null) {
                    int idx = exoPlayer.getCurrentMediaItemIndex();
                    if (idx < currentPlaylist.size()) {
                        currentIndex = idx;
                        currentSongLiveData.postValue(currentPlaylist.get(currentIndex));
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
    public LiveData<List<Song>> getSongs()        { return songsLiveData; }
    public LiveData<Song>       getCurrentSong()  { return currentSongLiveData; }
    public LiveData<Boolean>    getIsPlaying()    { return isPlayingLiveData; }
    public LiveData<Long>       getProgress()     { return progressLiveData; }
    public LiveData<Long>       getDuration()     { return durationLiveData; }
    public LiveData<Integer>    getRepeatMode()   { return repeatModeLiveData; }
    public LiveData<Boolean>    getShuffleMode()  { return shuffleModeLiveData; }
    public LiveData<Boolean>    getEndOfQueue()   { return endOfQueueLiveData; }

    // ── End-of-queue helpers ──────────────────────────────────────────
    /** Returns true when we are on the last song and shuffle+repeat are both OFF */
    public boolean isAtEndOfQueue() {
        if (exoPlayer == null || currentPlaylist == null) return false;
        boolean repeatOff  = exoPlayer.getRepeatMode() == Player.REPEAT_MODE_OFF;
        boolean shuffleOff = !Boolean.TRUE.equals(shuffleModeLiveData.getValue());
        return repeatOff && shuffleOff && !exoPlayer.hasNextMediaItem();
    }

    /** Called by UI when user chooses "Play Randomly" from the end-of-queue dialog */
    public void playAllShuffled() {
        if (currentPlaylist == null || currentPlaylist.isEmpty()) return;
        endOfQueueLiveData.postValue(false);
        exoPlayer.clearMediaItems();
        for (Song s : currentPlaylist) exoPlayer.addMediaItem(MediaItem.fromUri(s.getUri()));
        exoPlayer.setShuffleModeEnabled(true);
        shuffleModeLiveData.postValue(true);
        exoPlayer.seekToDefaultPosition(0);
        exoPlayer.prepare();
        exoPlayer.play();
        currentSongLiveData.postValue(currentPlaylist.get(0));
    }

    /** Called by UI when user chooses "Play from Beginning" from the end-of-queue dialog */
    public void playFromBeginning() {
        if (currentPlaylist == null || currentPlaylist.isEmpty()) return;
        endOfQueueLiveData.postValue(false);
        exoPlayer.clearMediaItems();
        for (Song s : currentPlaylist) exoPlayer.addMediaItem(MediaItem.fromUri(s.getUri()));
        exoPlayer.setShuffleModeEnabled(false);
        shuffleModeLiveData.postValue(false);
        exoPlayer.seekToDefaultPosition(0);
        exoPlayer.prepare();
        exoPlayer.play();
        currentSongLiveData.postValue(currentPlaylist.get(0));
    }

    /** Dismiss the dialog without doing anything */
    public void dismissEndOfQueue() {
        endOfQueueLiveData.postValue(false);
    }

    // ── Playback ──────────────────────────────────────────────────────
    public void playSong(Song song, List<Song> playlist, int index) {
        this.currentPlaylist = playlist;
        this.currentIndex    = index;

        exoPlayer.clearMediaItems();
        for (Song s : playlist) exoPlayer.addMediaItem(MediaItem.fromUri(s.getUri()));
        exoPlayer.seekToDefaultPosition(index);
        exoPlayer.prepare();
        exoPlayer.play();
        currentSongLiveData.postValue(song);
        endOfQueueLiveData.postValue(false); // always reset when a new song starts
    }

    public void togglePlayPause() {
        if (exoPlayer.isPlaying()) exoPlayer.pause();
        else                       exoPlayer.play();
    }

    public void playNext() {
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem();
        } else {
            // At the end of the queue — signal UI to show dialog
            endOfQueueLiveData.postValue(true);
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
        exoPlayer.setShuffleModeEnabled(!cur);
        shuffleModeLiveData.postValue(!cur);
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
