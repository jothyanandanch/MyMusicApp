package com.example.mymusic.repository;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import com.example.mymusic.model.Song;
import com.example.mymusic.utils.AppLog;

import java.util.ArrayList;
import java.util.List;

public class LocalMusicRepository {
    
    // Album art base URI — every song's art is at this URI + song id
    private static final Uri ALBUM_ART_URI =
            Uri.parse("content://media/external/audio/albumart");
    
    private final Context context;
    
    public LocalMusicRepository(Context context) {
        this.context = context;
    }
    
    public List<Song> fetchLocalSongs() {
        ArrayList<Song> songs = new ArrayList<>();
        
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID  // NEW — needed for album art
        };
        
        AppLog.d(AppLog.REPO, "Starting scan for music...");
        
        Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                MediaStore.Audio.Media.IS_MUSIC + " != 0",
                null,
                MediaStore.Audio.Media.TITLE + " ASC"
        );
        
        if (cursor != null && cursor.moveToFirst()) {
            int idCol       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int titleCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artistCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int albumIdCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
            
            do {
                long id         = cursor.getLong(idCol);
                String title    = cursor.getString(titleCol);
                String artist   = cursor.getString(artistCol);
                long duration   = cursor.getLong(durationCol);
                long albumId    = cursor.getLong(albumIdCol);
                
                Uri songUri     = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                
                // Album art URI — Coil will handle null gracefully in UI
                Uri artUri      = ContentUris.withAppendedId(ALBUM_ART_URI, albumId);
                
                Song song = new Song(id, artist, title, songUri, duration, artUri);
                AppLog.i(AppLog.REPO, "Found: " + title + " by " + artist);
                songs.add(song);
                
            } while (cursor.moveToNext());
            
            cursor.close();
        }
        
        AppLog.d(AppLog.REPO, "Scan done. Total: " + songs.size());
        return songs;
    }
}