package com.example.mymusic.model;

import android.net.Uri;

import org.jetbrains.annotations.NotNull;

public class Song {
    private long id;
    private String title;
    private String artist;
    private Uri uri;
    private long duration;
    private Uri albumArtUri; // NEW
    
    public Song(long id, String artist, String title, Uri uri, long duration, Uri albumArtUri) {
        this.id = id;
        this.artist = artist;
        this.title = title;
        this.uri = uri;
        this.duration = duration;
        this.albumArtUri = albumArtUri; // NEW
    }
    
    public long getId()          { return id; }
    public String getTitle()     { return title; }
    public String getArtist()    { return artist; }
    public Uri getUri()          { return uri; }
    public long getDuration()    { return duration; }
    public Uri getAlbumArtUri()  { return albumArtUri; } // NEW — can be null
}