package com.nandu.mymusic.repository;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import com.nandu.mymusic.model.Song;
import com.nandu.mymusic.utils.AppLog;

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
    
    public List<Song> fetchLocalSongs() {
        ArrayList<Song> songs = new ArrayList<>();
        
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID,  // needed for album art
                MediaStore.Audio.Media.RELATIVE_PATH // ✅ NEW: Get file path for filtering
        };
        
        String selection=MediaStore.Audio.Media.IS_MUSIC+" !=0 AND "+
                MediaStore.Audio.Media.RELATIVE_PATH+" NOT LIKE ? AND "+
                MediaStore.Audio.Media.RELATIVE_PATH+" NOT LIKE ? AND "+
                MediaStore.Audio.Media.DISPLAY_NAME + " NOT LIKE ? AND " +
                MediaStore.Audio.Media.DISPLAY_NAME + " NOT LIKE ?";
        
        String[] selectionArgs = new String[]{
                "%recording%",
                "%ringtone%",
                "%recording%",
                "%ringtone%"
        };
        
        AppLog.d(AppLog.REPO, "Starting SQL scan for music...");
        
        Cursor cursor = context.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                MediaStore.Audio.Media.TITLE + " ASC"
        );
        
        if (cursor != null) {
            try {
                
                //Even if the cursor doesn't have any data it will return not null because the cursor searched successfully
                //So if there's nothing to movetofirst and if we used any fetching it will throw an error
                
                if (cursor.moveToFirst()) {
                    int idCol       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                    int titleCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                    int artistCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                    int albumCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
                    int durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                    int albumIdCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
                    int dataCol     = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH);  // ✅ NEW
                    
                    do {
                        long id         = cursor.getLong(idCol);
                        String title    = cursor.getString(titleCol);
                        String artist   = cursor.getString(artistCol);
                        String album    = cursor.getString(albumCol);
                        long duration   = cursor.getLong(durationCol);
                        long albumId    = cursor.getLong(albumIdCol);
                        
                        Uri songUri = ContentUris.withAppendedId(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                        
                        // Album art URI — Coil will handle null gracefully in UI
                        //Coil is a third party android library used to load images if no image found it will pass the default image
                        Uri artUri = ContentUris.withAppendedId(ALBUM_ART_URI, albumId);
                        
                        Song song = new Song(id, artist, title,album, songUri, duration, artUri,"Unknown");
                        AppLog.i(AppLog.REPO, "Found: " + title + " by " + artist);
                        songs.add(song);
                        
                    } while (cursor.moveToNext());
                }
            } finally {
                // This ensures the cursor is ALWAYS closed, even if an error happens inside the loop
                cursor.close();
            }
        }
        
        AppLog.d(AppLog.REPO, "Scan done. Total: " + songs.size());
        return songs;
    }
}