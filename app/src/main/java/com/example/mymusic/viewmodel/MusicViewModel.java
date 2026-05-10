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

import com.example.mymusic.model.Song;
import com.example.mymusic.repository.LocalMusicRepository;

import java.util.List;

public class MusicViewModel extends AndroidViewModel {
    private final LocalMusicRepository repository;
    private final MutableLiveData<List<Song>> songsLiveData = new MutableLiveData<>();
    
    private final ContentObserver contentObserver;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable reloadRunnable = this::loadLocalMusic;
    
    public MusicViewModel(@NonNull Application application) {
        super(application);
        repository = new LocalMusicRepository(application);
        
        // Initialize the ContentObserver with debouncing logic
        contentObserver = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                // Cancel any pending reload and schedule a new one in 500ms
                // This prevents multiple reloads if many files change at once
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
        
        // Initial load of music when ViewModel is created
        loadLocalMusic();
    }
    
    public LiveData<List<Song>> getSongs() {
        return songsLiveData;
    }
    
    public void loadLocalMusic() {
        // This creates a background thread to fetch songs and updates LiveData
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<Song> localSongs = repository.fetchLocalSongs();
                songsLiveData.postValue(localSongs);
            }
        }).start();
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        // IMPORTANT: Unregister the observer to prevent memory leaks when the ViewModel is destroyed
        getApplication().getContentResolver().unregisterContentObserver(contentObserver);
        handler.removeCallbacks(reloadRunnable);
    }
}