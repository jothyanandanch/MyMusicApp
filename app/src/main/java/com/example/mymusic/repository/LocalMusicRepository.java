package com.example.mymusic.repository;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;
import android.net.Uri;

import com.example.mymusic.model.Song;

import java.util.ArrayList;
import java.util.List;
import com.example.mymusic.utils.AppLog;

public class LocalMusicRepository {

    private final Context context;
    public LocalMusicRepository(Context context){
        this.context=context;
    }
    public List<Song> fetchLocalSongs(){
        ArrayList<Song> songs=new ArrayList<>();
        String[] projection={
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION
        };
        AppLog.d(AppLog.REPO, "Starting scan for music...");
        Cursor cursor=context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,projection, MediaStore.Audio.Media.IS_MUSIC + " !=0",null,MediaStore.Audio.Media.TITLE + " ASC");

        if (cursor!=null && cursor.moveToFirst()) {
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            do{
                long id = cursor.getLong(idColumn);
                String title = cursor.getString(titleColumn);
                String artist = cursor.getString(artistColumn);
                long duration = cursor.getLong(durationColumn);
                //The Below Line creates a new Unique uri for the media file
                Uri songuri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
                Song song = new Song(id, title, artist, songuri, duration);
                AppLog.i(AppLog.REPO, "Found Song: " + title + " by " + artist);
                songs.add(song);
            }while (cursor.moveToNext());

        }
        AppLog.d(AppLog.REPO, "Scan complete. Total songs found: " + songs.size());
        assert cursor != null;
        cursor.close();
        return songs;

    }
}
