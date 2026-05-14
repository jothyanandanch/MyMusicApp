package com.example.mymusic.viewmodel;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import com.example.mymusic.model.Song;
import com.example.mymusic.repository.LocalMusicRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MusicViewModel extends AndroidViewModel {
    
    // ── SharedPreferences key for persisted favorites ────────────────
    private static final String PREFS_NAME  = "MyMusicPrefs";
    private static final String KEY_FAV_IDS = "favorite_song_ids";
    
    private final LocalMusicRepository        repository;
    private final MutableLiveData<List<Song>> songsLiveData         = new MutableLiveData<>();
    private final MutableLiveData<Song>       currentSongLiveData   = new MutableLiveData<>();
    private final MutableLiveData<Boolean>    isPlayingLiveData     = new MutableLiveData<>(false);
    private final MutableLiveData<Long>       progressLiveData      = new MutableLiveData<>(0L);
    private final MutableLiveData<Long>       durationLiveData      = new MutableLiveData<>(0L);
    private final MutableLiveData<Integer>    repeatModeLiveData    = new MutableLiveData<>(Player.REPEAT_MODE_OFF);
    private final MutableLiveData<Boolean>    shuffleModeLiveData   = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean>    endOfQueueLiveData    = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean>    showEndDialogLiveData = new MutableLiveData<>(false);
    
    // Favorites — insertion-ordered so Liked Songs tab keeps add-order
    private final Map<Long, Song> favoritesMap = new LinkedHashMap<>();
    private final MutableLiveData<Set<Long>> favoriteIdsLiveData = new MutableLiveData<>(new LinkedHashSet<>());
    
    private ExoPlayer  exoPlayer;
    private List<Song> currentPlaylist;
    private int        currentIndex = -1;
    
    private final Set<Long> playedInShuffleCycle = new LinkedHashSet<>();
    
    private final Handler  progressHandler  = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override public void run() {
            if (exoPlayer != null && exoPlayer.isPlaying()) {
                progressLiveData.postValue(exoPlayer.getCurrentPosition());
                long dur = exoPlayer.getDuration();
                durationLiveData.postValue(dur > 0 ? dur : 0L);
            }
            progressHandler.postDelayed(this, 500);
        }
    };
    
    private final ContentObserver contentObserver;
    private final Handler         handler        = new Handler(Looper.getMainLooper());
    private final Runnable        reloadRunnable = this::loadLocalMusic;
    
    public MusicViewModel(@NonNull Application application) {
        super(application);
        repository = new LocalMusicRepository(application);
        
        // ── Restore favorites from SharedPreferences ─────────────────
        loadFavoritesFromPrefs();
        
        exoPlayer = new ExoPlayer.Builder(application).build();
        exoPlayer.addListener(new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean isPlaying) {
                isPlayingLiveData.postValue(isPlaying);
                if (isPlaying) progressHandler.post(progressRunnable);
                else           progressHandler.removeCallbacks(progressRunnable);
            }
            
            @Override public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                if (currentPlaylist == null) return;
                int idx = exoPlayer.getCurrentMediaItemIndex();
                if (idx < currentPlaylist.size()) {
                    currentIndex = idx;
                    Song song = currentPlaylist.get(currentIndex);
                    currentSongLiveData.postValue(song);
                    if (Boolean.TRUE.equals(shuffleModeLiveData.getValue()))
                        playedInShuffleCycle.add(song.getId());
                }
            }
            
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED)
                    isPlayingLiveData.postValue(false);
            }
        });
        
        contentObserver = new ContentObserver(handler) {
            @Override public void onChange(boolean selfChange) {
                handler.removeCallbacks(reloadRunnable);
                handler.postDelayed(reloadRunnable, 500);
            }
        };
        application.getContentResolver().registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, contentObserver);
    }
    
    // ── LiveData getters ──────────────────────────────────────────────
    public LiveData<List<Song>> getSongs()         { return songsLiveData; }
    public LiveData<Song>       getCurrentSong()   { return currentSongLiveData; }
    public LiveData<Boolean>    getIsPlaying()     { return isPlayingLiveData; }
    public LiveData<Long>       getProgress()      { return progressLiveData; }
    public LiveData<Long>       getDuration()      { return durationLiveData; }
    public LiveData<Integer>    getRepeatMode()    { return repeatModeLiveData; }
    public LiveData<Boolean>    getShuffleMode()   { return shuffleModeLiveData; }
    public LiveData<Boolean>    getEndOfQueue()    { return endOfQueueLiveData; }
    public LiveData<Boolean>    getShowEndDialog() { return showEndDialogLiveData; }
    public LiveData<Set<Long>>  getFavoriteIds()   { return favoriteIdsLiveData; }
    
    // ── Favorites — persist on every toggle ──────────────────────────
    
    /**
     * Loads saved favorite song IDs from SharedPreferences.
     * Songs whose MediaStore URIs are still valid get restored into favoritesMap;
     * stale IDs (e.g. deleted files) are silently dropped on next sync via
     * syncFavoritesWithSongs() which is called after loadLocalMusic().
     */
    private void loadFavoritesFromPrefs() {
        SharedPreferences prefs = getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> saved = prefs.getStringSet(KEY_FAV_IDS, new LinkedHashSet<>());
        Set<Long> restoredIds = new LinkedHashSet<>();
        if (saved != null) {
            for (String idStr : saved) {
                try { restoredIds.add(Long.parseLong(idStr)); }
                catch (NumberFormatException ignored) {}
            }
        }
        // Post the IDs immediately so the heart icon shows correctly even before
        // loadLocalMusic() completes and populates the Song objects.
        favoriteIdsLiveData.postValue(restoredIds);
    }
    
    /**
     * Called after songs are loaded from MediaStore.
     * Rebuilds favoritesMap from the persisted IDs so Song objects are available.
     */
    private void syncFavoritesWithSongs(List<Song> songs) {
        Set<Long> persistedIds = favoriteIdsLiveData.getValue();
        if (persistedIds == null || persistedIds.isEmpty()) return;
        
        // Build a quick lookup map
        Map<Long, Song> songById = new LinkedHashMap<>();
        for (Song s : songs) songById.put(s.getId(), s);
        
        favoritesMap.clear();
        Set<Long> validIds = new LinkedHashSet<>();
        for (Long id : persistedIds) {
            Song s = songById.get(id);
            if (s != null) {
                favoritesMap.put(id, s);
                validIds.add(id);
            }
        }
        // Remove any IDs whose files were deleted
        favoriteIdsLiveData.postValue(validIds);
        // Persist cleaned-up set
        saveFavoritesToPrefs(validIds);
    }
    
    /** Persists current favorite IDs to SharedPreferences. */
    private void saveFavoritesToPrefs(Set<Long> ids) {
        Set<String> stringSet = new LinkedHashSet<>();
        for (Long id : ids) stringSet.add(String.valueOf(id));
        getApplication()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_FAV_IDS, stringSet)
                .apply();
    }
    
    /** Toggle song favorite status with immediate persistence. */
    public boolean toggleFavorite(Song song) {
        // Get current favorites
        Set<Long> current = favoriteIdsLiveData.getValue();
        if (current == null) {
            current = new LinkedHashSet<>();
        }
        
        // Create brand-new set
        Set<Long> updated = new LinkedHashSet<>(current);
        
        boolean isFavNow = updated.contains(song.getId());
        
        if (isFavNow) {
            // Remove
            updated.remove(song.getId());
            favoritesMap.remove(song.getId());
        } else {
            // Add
            updated.add(song.getId());
            favoritesMap.put(song.getId(), song);
        }
        
        // Post to LiveData (creates new reference - forces recomposition)
        favoriteIdsLiveData.setValue(updated);  // ✅ Use setValue instead of postValue for immediate update
        
        // Save to disk
        saveFavoritesToPrefs(updated);
        
        return !isFavNow;
    }
    
    public boolean isFavorite(long songId) {
        Set<Long> ids = favoriteIdsLiveData.getValue();
        return ids != null && ids.contains(songId);
    }
// ✅ FIXED: getFavoritesList() method for MusicViewModel.java
// Replace lines 228-229 with this:
    
    public List<Song> getFavoritesList() {
        // Rebuild from current songs and favorite IDs to ensure accuracy
        Set<Long> favoriteIds = favoriteIdsLiveData.getValue();
        if (favoriteIds == null || favoriteIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Song> allSongs = songsLiveData.getValue();
        if (allSongs == null) return new ArrayList<>();
        
        List<Song> result = new ArrayList<>();
        for (Song song : allSongs) {
            if (favoriteIds.contains(song.getId())) {
                result.add(song);
            }
        }
        return result;
    }
    
    // ── Shuffle helpers ───────────────────────────────────────────────
    public boolean isShuffleCycleComplete() {
        if (currentPlaylist == null || currentPlaylist.isEmpty()) return false;
        if (!Boolean.TRUE.equals(shuffleModeLiveData.getValue())) return false;
        for (Song s : currentPlaylist)
            if (!playedInShuffleCycle.contains(s.getId())) return false;
        return true;
    }
    
    public void resetShuffleCycle() { playedInShuffleCycle.clear(); }
    
    // ── End-of-queue actions ──────────────────────────────────────────
    public void playAllShuffled() {
        List<Song> library = songsLiveData.getValue();
        if (library == null || library.isEmpty()) return;
        showEndDialogLiveData.postValue(false);
        endOfQueueLiveData.postValue(false);
        playedInShuffleCycle.clear();
        List<Song> shuffled = new ArrayList<>(library);
        Collections.shuffle(shuffled);
        currentPlaylist = shuffled;
        currentIndex = 0;
        exoPlayer.clearMediaItems();
        for (Song s : shuffled) exoPlayer.addMediaItem(MediaItem.fromUri(s.getUri()));
        exoPlayer.setShuffleModeEnabled(false);
        shuffleModeLiveData.postValue(true);
        exoPlayer.seekToDefaultPosition(0);
        exoPlayer.prepare();
        exoPlayer.play();
        currentSongLiveData.postValue(shuffled.get(0));
        playedInShuffleCycle.add(shuffled.get(0).getId());
    }
    
    public void playFromBeginning() {
        if (currentPlaylist == null || currentPlaylist.isEmpty()) return;
        showEndDialogLiveData.postValue(false);
        endOfQueueLiveData.postValue(false);
        playedInShuffleCycle.clear();
        exoPlayer.clearMediaItems();
        for (Song s : currentPlaylist) exoPlayer.addMediaItem(MediaItem.fromUri(s.getUri()));
        exoPlayer.setShuffleModeEnabled(false);
        shuffleModeLiveData.postValue(false);
        exoPlayer.seekToDefaultPosition(0);
        exoPlayer.prepare();
        exoPlayer.play();
        currentSongLiveData.postValue(currentPlaylist.get(0));
    }
    
    public void dismissEndOfQueue() {
        showEndDialogLiveData.postValue(false);
        endOfQueueLiveData.postValue(false);
    }
    
    // ── Playback ──────────────────────────────────────────────────────
    /**
     * ✅ FIXED: If clicking the same song, just toggle play/pause.
     * Only reset playlist if it's a different song or playlist.
     */
    public void playSong(Song song, List<Song> playlist, int index) {
        Song currentSong = currentSongLiveData.getValue();
        
        // If clicking the same song in the same playlist, just toggle play/pause
        if (currentSong != null
                && song.getId() == currentSong.getId()
                && currentPlaylist != null
                && currentPlaylist.size() == playlist.size()
                && currentIndex == index) {
            togglePlayPause();
            return;
        }
        
        this.currentPlaylist = playlist;
        this.currentIndex    = index;
        playedInShuffleCycle.clear();
        if (Boolean.TRUE.equals(shuffleModeLiveData.getValue()))
            playedInShuffleCycle.add(song.getId());
        
        exoPlayer.clearMediaItems();
        for (Song s : playlist) exoPlayer.addMediaItem(MediaItem.fromUri(s.getUri()));
        exoPlayer.seekToDefaultPosition(index);
        exoPlayer.prepare();
        exoPlayer.play();
        currentSongLiveData.postValue(song);
        endOfQueueLiveData.postValue(false);
        showEndDialogLiveData.postValue(false);
    }
    
    public void togglePlayPause() {
        if (exoPlayer.isPlaying()) exoPlayer.pause();
        else                       exoPlayer.play();
    }
    
    public void playNextFromMiniPlayer() {
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem();
        } else {
            boolean shuffle = Boolean.TRUE.equals(shuffleModeLiveData.getValue());
            if (currentPlaylist == null || currentPlaylist.isEmpty()) return;
            playedInShuffleCycle.clear();
            exoPlayer.clearMediaItems();
            List<Song> toPlay = new ArrayList<>(currentPlaylist);
            if (shuffle) Collections.shuffle(toPlay);
            for (Song s : toPlay) exoPlayer.addMediaItem(MediaItem.fromUri(s.getUri()));
            exoPlayer.setShuffleModeEnabled(false);
            exoPlayer.seekToDefaultPosition(0);
            exoPlayer.prepare();
            exoPlayer.play();
            currentSongLiveData.postValue(toPlay.get(0));
            playedInShuffleCycle.add(toPlay.get(0).getId());
        }
    }
    
    public void playNextFromFullPlayer() {
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem();
            // ✅ ADD THIS: Reset progress when switching songs
            progressLiveData.postValue(0L);  // Show at start immediately
        }
        else showEndDialogLiveData.postValue(true);
    }
    
    public void playPrevious() {
        if (exoPlayer.getCurrentPosition() > 3000) {
            exoPlayer.seekTo(0);
            progressLiveData.postValue(0L);  // ✅ Reset to start
        }
        else if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPreviousMediaItem();
            progressLiveData.postValue(0L);  // ✅ Reset when going previous
        }
    }
    
    public void seekTo(long positionMs) {
        exoPlayer.seekTo(positionMs);
        progressLiveData.postValue(positionMs);
    }
    
    public void toggleRepeat() {
        int next = exoPlayer.getRepeatMode() == Player.REPEAT_MODE_OFF  ? Player.REPEAT_MODE_ALL
                : exoPlayer.getRepeatMode() == Player.REPEAT_MODE_ALL   ? Player.REPEAT_MODE_ONE
                : Player.REPEAT_MODE_OFF;
        exoPlayer.setRepeatMode(next);
        repeatModeLiveData.postValue(next);
    }
    
    /**
     * ✅ IMPROVED: Better shuffle logic that works with repeat modes.
     * Keep currently playing song in place and shuffle only the rest.
     */
    public void toggleShuffle() {
        boolean cur  = Boolean.TRUE.equals(shuffleModeLiveData.getValue());
        boolean next = !cur;
        
        if (next) {
            if (currentPlaylist != null && currentIndex >= 0) {
                Song nowPlaying  = currentPlaylist.get(currentIndex);
                
                // Preserve songs before current position
                List<Song> before = currentIndex > 0
                        ? new ArrayList<>(currentPlaylist.subList(0, currentIndex))
                        : new ArrayList<>();
                
                // Shuffle only songs after current position
                List<Song> after = new ArrayList<>(
                        currentPlaylist.subList(currentIndex + 1, currentPlaylist.size())
                );
                Collections.shuffle(after);
                
                // Rebuild: before + current + shuffled after
                List<Song> reordered = new ArrayList<>(before);
                reordered.add(nowPlaying);
                reordered.addAll(after);
                currentPlaylist = reordered;
                
                exoPlayer.clearMediaItems();
                for (Song s : reordered) exoPlayer.addMediaItem(MediaItem.fromUri(s.getUri()));
                exoPlayer.seekToDefaultPosition(before.size());  // Position at current song
                exoPlayer.prepare();
            }
            playedInShuffleCycle.clear();
            if (currentPlaylist != null && currentIndex >= 0) {
                playedInShuffleCycle.add(
                        currentPlaylist.get(Math.min(currentIndex, currentPlaylist.size() - 1)).getId()
                );
            }
        } else {
            playedInShuffleCycle.clear();
        }
        exoPlayer.setShuffleModeEnabled(false);
        shuffleModeLiveData.postValue(next);
    }
    
    /**
     * ✅ NEW: Shuffle current playlist and play from start
     */
    public void playShuffled(List<Song> playlist) {
        if (playlist == null || playlist.isEmpty()) return;
        
        List<Song> shuffled = new ArrayList<>(playlist);
        Song currentSong = currentSongLiveData.getValue();
        Song nowPlaying = null;
        int nowPlayingIndex = -1;
        
        // Find currently playing song in the NEW playlist
        if (currentSong != null) {
            for (int i = 0; i < shuffled.size(); i++) {
                if (shuffled.get(i).getId() == currentSong.getId()) {
                    nowPlaying = shuffled.get(i);
                    nowPlayingIndex = i;
                    break;
                }
            }
        }
        
        // If current song is in this playlist, keep it and shuffle rest
        if (nowPlayingIndex >= 0) {
            shuffled.remove(nowPlayingIndex);
            Collections.shuffle(shuffled);
            shuffled.add(0, nowPlaying);
            currentIndex = 0;
        } else {
            Collections.shuffle(shuffled);
            currentIndex = 0;
        }
        
        playedInShuffleCycle.clear();
        playedInShuffleCycle.add(shuffled.get(0).getId());
        
        exoPlayer.clearMediaItems();
        for (Song s : shuffled) exoPlayer.addMediaItem(MediaItem.fromUri(s.getUri()));
        exoPlayer.setShuffleModeEnabled(false);
        shuffleModeLiveData.postValue(true);
        
        currentPlaylist = shuffled;
        currentSongLiveData.postValue(shuffled.get(0));
        
        // ✅ KEY FIX: If currently playing the same song, DON'T restart it!
        if (nowPlayingIndex >= 0 && exoPlayer.isPlaying()) {
            // Current song is in the new playlist and is already playing
            // The exoPlayer already has it queued, just update the queue
            // Don't seek or restart - let it continue!
            return;
        }
        
        // Otherwise, start from the beginning
        exoPlayer.seekToDefaultPosition(0);
        exoPlayer.prepare();
        exoPlayer.play();
        
        endOfQueueLiveData.postValue(false);
        showEndDialogLiveData.postValue(false);
    }
    
    
    public void loadLocalMusic() {
        new Thread(() -> {
            List<Song> local = repository.fetchLocalSongs();
            songsLiveData.postValue(local);
            // Rebuild favoritesMap with real Song objects after media scan
            syncFavoritesWithSongs(local);
        }).start();
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        getApplication().getContentResolver().unregisterContentObserver(contentObserver);
        handler.removeCallbacks(reloadRunnable);
        progressHandler.removeCallbacks(progressRunnable);
        if (exoPlayer != null) { exoPlayer.release(); exoPlayer = null; }
    }
}