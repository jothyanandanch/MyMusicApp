package com.example.mymusic.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mymusic.model.Song;
import com.example.mymusic.repository.LocalMusicRepository;

import java.util.List;


public class MusicViewModel extends AndroidViewModel {
    private final LocalMusicRepository repository;
    private final MutableLiveData<List<Song>> songsLiveData=new MutableLiveData<>();
    public MusicViewModel(@NonNull Application application) {
        super(application);
        repository=new LocalMusicRepository(application);

    }

    public LiveData<List<Song>> getSongs(){
        return songsLiveData;
    }

    public void loadLocalMusic(){
        //This creates the New Background thread which checks if there is any update to the repo and constantly updates to the main Thread's Live Function
        Thread thread=new Thread(new Runnable() {
            @Override
            public void run() {
                List<Song> localSongs =repository.fetchLocalSongs();
                songsLiveData.postValue(localSongs);
            }
        });
        thread.start();
    }
}

