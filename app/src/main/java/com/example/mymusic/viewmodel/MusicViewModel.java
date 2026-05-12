package com.example.mymusic.viewmodel;

import android.app.Application;
import android.database.ContentObserver;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mymusic.model.Song;
import com.example.mymusic.repository.LocalMusicRepository;

import java.util.List;

public class MusicViewModel extends AndroidViewModel {
    private final LocalMusicRepository repository;
    private final MutableLiveData<List<Song>> songsLiveData = new MutableLiveData<>();
    
    private final ContentObserver contentObserver;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable reloadRunnable = this::loadLocalMusic;
    
    // 🎵 MediaPlayer + Playback State
    private MediaPlayer mediaPlayer;
    private final MutableLiveData<Song> currentSongLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isPlayingLiveData = new MutableLiveData<>(false);
    
    public MusicViewModel(@NonNull Application application) {
        super(application);
        repository = new LocalMusicRepository(application);
        
        // Initialize the ContentObserver with debouncing logic
        contentObserver = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                handler.removeCallbacks(reloadRunnable);
                handler.postDelayed(reloadRunnable, 500);
            }
        };
        
        // Register the observer to listen for changes in the media database
        application.getContentResolver().registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                contentObserver
        );
        
        
    }
    
    // 🎵 LiveData getters
    public LiveData<List<Song>> getSongs() { return songsLiveData; }
    public LiveData<Song> getCurrentSong() { return currentSongLiveData; }
    public LiveData<Boolean> getIsPlaying() { return isPlayingLiveData; }
    
    // 🎵 Playback controls
    public void playSong(Song song) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        mediaPlayer = MediaPlayer.create(getApplication(), song.getUri());
        mediaPlayer.start();
        currentSongLiveData.postValue(song);
        isPlayingLiveData.postValue(true);
    }
    
    public void togglePlayPause() {
        if (mediaPlayer == null) return;
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPlayingLiveData.postValue(false);
        } else {
            mediaPlayer.start();
            isPlayingLiveData.postValue(true);
        }
    }
    
    public void loadLocalMusic() {
        new Thread(() -> {
            List<Song> localSongs = repository.fetchLocalSongs();
            songsLiveData.postValue(localSongs);
        }).start();
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        getApplication().getContentResolver().unregisterContentObserver(contentObserver);
        handler.removeCallbacks(reloadRunnable);
        
        // 🎵 Release MediaPlayer to prevent leaks
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
