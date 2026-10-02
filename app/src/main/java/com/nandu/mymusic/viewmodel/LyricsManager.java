package com.nandu.mymusic.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.nandu.mymusic.model.Song;

/**
 * Lyrics fetching + caching. On-song-start the cache is cleared and a fresh fetch
 * is kicked off (online only when the library mode allows it); the user can also
 * force a re-fetch from the lyrics button. A generation counter discards results
 * for a song that has already been superseded.
 */
public final class LyricsManager {

    private final MusicViewModelCore core;

    private final MutableLiveData<String> lyricsLiveData = new MutableLiveData<>(null);
    private String cachedLyrics = null;
    private long cachedLyricsSongId = -1;
    private String previousCachedLyrics = null;
    private long previousCachedLyricsSongId = -1;
    private volatile int lyricsFetchGeneration = 0;

    public LyricsManager(MusicViewModelCore core) {
        this.core = core;
    }

    public LiveData<String> getLyrics() { return lyricsLiveData; }

    /** Called from the player listener when a new song starts: reset cache + fetch. */
    public void onSongStarted(Song song) {
        lyricsLiveData.setValue(null);
        previousCachedLyrics = cachedLyrics;
        previousCachedLyricsSongId = cachedLyricsSongId;
        cachedLyrics = null;
        cachedLyricsSongId = -1;
        lyricsFetchGeneration++;
        fetchLyricsForCurrentSong(song);
    }

    /** User-triggered "load lyrics" (from the lyrics button in Now Playing). */
    public void loadLyrics() {
        Song song = core.currentSongLive().getValue();
        if (song == null) return;
        if (cachedLyrics != null && cachedLyricsSongId == song.getId()) {
            lyricsLiveData.postValue(cachedLyrics);
            return;
        }
        lyricsLiveData.postValue("Loading...");
        final int generation = lyricsFetchGeneration;
        core.executor().execute(() -> {
            String lyrics = com.nandu.mymusic.utils.LyricsUtils.extractLyrics(
                    core.application(), song.getUri(), song.getTitle(), song.getArtist(), true
            );
            if (generation != lyricsFetchGeneration) return;
            cachedLyrics = lyrics;
            cachedLyricsSongId = song.getId();
            if (lyrics != null) lyricsLiveData.postValue(lyrics);
            else lyricsLiveData.postValue("No lyrics found.");
        });
    }

    private void fetchLyricsForCurrentSong(Song song) {
        lyricsLiveData.postValue("Loading...");
        Integer currentMode = core.libraryModeLive().getValue();
        boolean allowOnlineFetch = (currentMode != null && currentMode != 0);
        final int generation = lyricsFetchGeneration;
        core.executor().execute(() -> {
            String lyrics = com.nandu.mymusic.utils.LyricsUtils.extractLyrics(
                    core.application(), song.getUri(), song.getTitle(), song.getArtist(), allowOnlineFetch
            );
            if (generation != lyricsFetchGeneration) return;
            cachedLyrics = lyrics;
            cachedLyricsSongId = song.getId();
            if (lyrics != null) lyricsLiveData.postValue(lyrics);
            else lyricsLiveData.postValue("No lyrics found.");
        });
    }
}
