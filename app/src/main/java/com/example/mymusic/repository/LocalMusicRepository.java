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
    
    // Paths to exclude
    private static final String[] EXCLUDED_PATHS = {
            "/allfiles/music/recordings",
            "/recordings/"
    };
    
    private final Context context;
    
    public LocalMusicRepository(Context context) {
        this.context = context;
    }
    
    /**
     * Check if file path should be excluded based on known recording directories
     */
    private boolean shouldExcludePath(String filePath) {
        if (filePath == null) return false;
        
        // Convert to lowercase for case-insensitive comparison
        String lowerPath = filePath.toLowerCase();
        
        // Check against known recording directories
        for (String excludedPath : EXCLUDED_PATHS) {
            if (lowerPath.contains(excludedPath.toLowerCase())) {
                AppLog.d(AppLog.REPO, "Excluding: " + filePath + " (excluded path)");
                return true;
            }
        }
        
        // Exclude any file/folder with "recording" in the name
        if (lowerPath.contains("recording")) {
            AppLog.d(AppLog.REPO, "Excluding: " + filePath + " (contains 'recording')");
            return true;
        }
        
        return false;
    }
    
    public List<Song> fetchLocalSongs() {
        ArrayList<Song> songs = new ArrayList<>();
        
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID,  // needed for album art
                MediaStore.Audio.Media.DATA        // ✅ NEW: Get file path for filtering
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
            int dataCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);  // ✅ NEW
            
            do {
                long id         = cursor.getLong(idCol);
                String title    = cursor.getString(titleCol);
                String artist   = cursor.getString(artistCol);
                long duration   = cursor.getLong(durationCol);
                long albumId    = cursor.getLong(albumIdCol);
                String filePath = cursor.getString(dataCol);  // ✅ NEW: Get file path
                
                // ✅ FILTER: Skip recording files and folders
                if (shouldExcludePath(filePath)) {
                    AppLog.d(AppLog.REPO, "Skipping recording file: " + title);
                    continue;  // Skip this file
                }
                
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