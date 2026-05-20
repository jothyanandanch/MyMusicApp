package com.nandu.mymusic.model;

import android.net.Uri;


public class Song {
    private long id;
    private String title;
    private String artist;
    private String album;
    private Uri uri;
    private long duration;
    private Uri albumArtUri; // NEW
    
    public Song(long id, String artist, String title,String album, Uri uri, long duration, Uri albumArtUri) {
        this.id = id;
        this.artist = artist;
        this.album=album;
        this.title = title;
        this.uri = uri;
        this.duration = duration;
        this.albumArtUri = albumArtUri; // NEW
    }
    
    public long getId()          { return id; }
    public String getTitle()     { return title; }
    public String getArtist()    { return artist; }
    public String getAlbum()    {return album;}
    public Uri getUri()          { return uri; }
    public long getDuration()    { return duration; }
    public Uri getAlbumArtUri()  { return albumArtUri; } // NEW — can be null
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Song song = (Song) o;
        return id == song.id;
    }
    
    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }
}