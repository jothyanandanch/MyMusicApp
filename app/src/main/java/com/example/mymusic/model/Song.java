package com.example.mymusic.model;
import android.net.Uri;
public class Song {
    private long id;
    private String title;
    private String artist;
    private Uri uri;
    private long duration;

    public Song(long id, String artist, String title, Uri uri, long duration) {
        this.id = id;
        this.artist = artist;
        this.title = title;
        this.uri = uri;
        this.duration = duration;
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public Uri getUri() {
        return uri;
    }

    public long getDuration() {
        return duration;
    }


}

