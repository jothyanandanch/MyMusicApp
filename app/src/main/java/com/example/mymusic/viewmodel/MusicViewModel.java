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

import java.util.Set;

public class MusicViewModel extends AndroidViewModel {
    
    // ── SharedPreferences keys ────────────────────────────────────
    private static final String PREFS_NAME    = "MyMusicPrefs";
    private static final String KEY_FAV_IDS   = "favorite_song_ids";
    private static final String KEY_LAST_INDEX = "last_song_index";
    
    private final LocalMusicRepository        repository;
    private final MutableLiveData<List<Song>> songsLiveData         = new MutableLiveData<>();
    private final MutableLiveData<Song>       currentSongLiveData   = new MutableLiveData<>();
    private final MutableLiveData<Boolean>    isPlayingLiveData     = new MutableLiveData<>(false);
    private final MutableLiveData<Long>       progressLiveData      = new MutableLiveData<>(0L);
    private final MutableLiveData<Long>       durationLiveData      = new MutableLiveData<>(0L);
    private final MutableLiveData<Integer>    repeatModeLiveData    = new MutableLiveData<>(Player.REPEAT_MODE_OFF);
    private final MutableLiveData<Boolean>    shuffleModeLiveData   = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean>    endOfQueueLiveData    = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean>    showEndDialogLiveData  = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean>    isNowPlayingOpenLiveData = new MutableLiveData<>(false);
    
    // Favorites — insertion-ordered
    private final Map<Long, Song>            favoritesMap        = new LinkedHashMap<>();
    private final MutableLiveData<Set<Long>> favoriteIdsLiveData = new MutableLiveData<>(new LinkedHashSet<>());
    
    private ExoPlayer  exoPlayer;
    private List<Song> currentPlaylist;
    private int        currentIndex = -1;
    
    // ── Shuffle: manual no-repeat queue ──────────────────────────────
    // When shuffle is ON we maintain our own unplayed list so ExoPlayer's
    // built-in shuffle (which can repeat) is never used.
    private List<Song> shuffleUnplayed = new ArrayList<>();  // songs not yet played this cycle
    private List<Song> shufflePlayed   = new ArrayList<>();  // songs already played this cycle
    
    // ── Progress polling at 200 ms for smooth seek bar ─────────────────
    private final Handler  progressHandler  = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override public void run() {
            if (exoPlayer != null && exoPlayer.isPlaying()) {
                progressLiveData.postValue(exoPlayer.getCurrentPosition());
                long dur = exoPlayer.getDuration();
                durationLiveData.postValue(dur > 0 ? dur : 0L);
            }
            progressHandler.postDelayed(this, 200);  // 200 ms — smoother bar
        }
    };
    
    private final ContentObserver contentObserver;
    private final Handler         handler        = new Handler(Looper.getMainLooper());
    private final Runnable        reloadRunnable = this::loadLocalMusic;
    
    public MusicViewModel(@NonNull Application application) {
        super(application);
        repository = new LocalMusicRepository(application);
        
        loadFavoritesFromPrefs();
        
        exoPlayer = new ExoPlayer.Builder(application).build();
        // Disable ExoPlayer's own shuffle — we handle ordering manually
        exoPlayer.setShuffleModeEnabled(false);
        
        exoPlayer.addListener(new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean isPlaying) {
                isPlayingLiveData.postValue(isPlaying);
                if (isPlaying) progressHandler.post(progressRunnable);
                else           progressHandler.removeCallbacks(progressRunnable);
            }
            
            @Override public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                if (currentPlaylist == null) return;
                int idx = exoPlayer.getCurrentMediaItemIndex();
                if (idx >= 0 && idx < currentPlaylist.size()) {
                    currentIndex = idx;
                    Song song = currentPlaylist.get(currentIndex);
                    currentSongLiveData.postValue(song);
                    
                    // Track played songs for the no-repeat shuffle cycle
                    if (Boolean.TRUE.equals(shuffleModeLiveData.getValue())) {
                        shufflePlayed.add(song);
                        shuffleUnplayed.remove(song);
                    }
                    
                    // Persist last song index so mini player survives process death
                    getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putInt(KEY_LAST_INDEX, currentIndex)
                        .apply();
                }
            }
            
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) {
                    isPlayingLiveData.postValue(false);
                    endOfQueueLiveData.postValue(false);
                    showEndDialogLiveData.postValue(false);
                }
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
    
    // ── LiveData getters ──────────────────────────────────────────────────────
    public LiveData<List<Song>> getSongs()         { return songsLiveData; }
    public LiveData<Song>       getCurrentSong()   { return currentSongLiveData; }
    public LiveData<Boolean>    getIsPlaying()     { return isPlayingLiveData; }
    public LiveData<Long>       getProgress()      { return progressLiveData; }
    public LiveData<Long>       getDuration()      { return durationLiveData; }
    public LiveData<Integer>    getRepeatMode()    { return repeatModeLiveData; }
    public LiveData<Boolean>    getShuffleMode()   { return shuffleModeLiveData; }
    public LiveData<Boolean>    getEndOfQueue()    { return endOfQueueLiveData; }
    public LiveData<Boolean>    getShowEndDialog() { return showEndDialogLiveData; }
    public LiveData<Set<Long>>  getFavoriteIds()       { return favoriteIdsLiveData; }
    public LiveData<Boolean>    isNowPlayingOpen()  { return isNowPlayingOpenLiveData; }
    public void setNowPlayingOpen(boolean open)        { isNowPlayingOpenLiveData.postValue(open); }
    
    // ── Favorites ─────────────────────────────────────────────────────────────────
    
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
        favoriteIdsLiveData.postValue(restoredIds);
    }
    
    private void syncFavoritesWithSongs(List<Song> songs) {
        Set<Long> persistedIds = favoriteIdsLiveData.getValue();
        if (persistedIds == null || persistedIds.isEmpty()) return;
        Map<Long, Song> songById = new LinkedHashMap<>();
        for (Song s : songs) songById.put(s.getId(), s);
        favoritesMap.clear();
        for (Long id : persistedIds) {
            Song s = songById.get(id);
            if (s != null) favoritesMap.put(id, s);
        }
    }
    
    public void toggleFavorite(Song song) {
        Set<Long> ids = new LinkedHashSet<>(favoriteIdsLiveData.getValue() != null
                ? favoriteIdsLiveData.getValue() : new LinkedHashSet<>());
        if (ids.contains(song.getId())) {
            ids.remove(song.getId());
            favoritesMap.remove(song.getId());
        } else {
            ids.add(song.getId());
            favoritesMap.put(song.getId(), song);
        }
        favoriteIdsLiveData.postValue(ids);
        // Persist
        Set<String> stringSet = new LinkedHashSet<>();
        for (Long id : ids) stringSet.add(String.valueOf(id));
        getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_FAV_IDS, stringSet)
                .apply();
    }
    
    public List<Song> getFavoritesList() {
        return new ArrayList<>(favoritesMap.values());
    }
    
    // ── Playback controls ────────────────────────────────────────────────────
    
    public void playSong(Song song, List<Song> playlist) {
        if (exoPlayer == null) return;
        
        currentPlaylist = new ArrayList<>(playlist);
        currentIndex    = playlist.indexOf(song);
        if (currentIndex < 0) currentIndex = 0;
        
        exoPlayer.clearMediaItems();
        for (Song s : playlist) exoPlayer.addMediaItem(MediaItem.fromUri(s.getUri()));
        exoPlayer.setShuffleModeEnabled(false);
        exoPlayer.prepare();
        exoPlayer.seekToDefaultPosition(currentIndex);
        exoPlayer.play();
        
        currentSongLiveData.postValue(song);
        progressLiveData.postValue(0L);
        endOfQueueLiveData.postValue(false);
        showEndDialogLiveData.postValue(false);
        
        // Init shuffle queues
        if (Boolean.TRUE.equals(shuffleModeLiveData.getValue())) {
            buildShuffleQueues(song);
        }
    }
    
    public void togglePlayPause() {
        if (exoPlayer == null) return;
        if (exoPlayer.isPlaying()) exoPlayer.pause();
        else                       exoPlayer.play();
    }
    
    /**
     * Build the no-repeat shuffle queues.
     * [nowPlaying] is already being played — it goes straight into shufflePlayed.
     * Everything else is shuffled into shuffleUnplayed.
     */
    private void buildShuffleQueues(Song nowPlaying) {
        shuffleUnplayed.clear();
        shufflePlayed.clear();
        if (currentPlaylist == null) return;
        
        List<Song> pool = new ArrayList<>(currentPlaylist);
        if (nowPlaying != null) pool.remove(nowPlaying);
        Collections.shuffle(pool);
        shuffleUnplayed.addAll(pool);
        if (nowPlaying != null) shufflePlayed.add(nowPlaying);
    }
    
    /**
     * Advance to the next track.
     *
     * • Shuffle ON  → pick from shuffleUnplayed (no-repeat); when exhausted
     *                  cycle resets automatically.
     * • Shuffle OFF → move to the next index in currentPlaylist; at the end
     *                  either loop (REPEAT_ALL) or stop and show the end dialog.
     *
     * Called from the mini-player “Next” button and from NowPlaying.
     */
    public void playNextFromMiniPlayer() { advanceToNext(false); }
    public void playNextFromFullPlayer()  { advanceToNext(false); }
    
    private void advanceToNext(boolean fromAuto) {
        if (exoPlayer == null || currentPlaylist == null || currentPlaylist.isEmpty()) return;
        
        if (Boolean.TRUE.equals(shuffleModeLiveData.getValue())) {
            // ── Shuffle mode ──────────────────────────────────────────────────
            if (shuffleUnplayed.isEmpty()) {
                // All songs played this cycle — reset
                Song lastPlayed = shufflePlayed.isEmpty() ? null
                        : shufflePlayed.get(shufflePlayed.size() - 1);
                buildShuffleQueues(lastPlayed);
                if (shuffleUnplayed.isEmpty()) return;  // only 1 song in playlist
            }
            Song next = shuffleUnplayed.remove(0);
            // Find its index in currentPlaylist for ExoPlayer
            int targetIndex = -1;
            for (int i = 0; i < currentPlaylist.size(); i++) {
                if (currentPlaylist.get(i).getId() == next.getId()) {
                    targetIndex = i; break;
                }
            }
            if (targetIndex < 0) return;
            currentIndex = targetIndex;
            shufflePlayed.add(next);
            exoPlayer.seekToDefaultPosition(targetIndex);
            exoPlayer.play();
            currentSongLiveData.postValue(next);
            progressLiveData.postValue(0L);
        } else {
            // ── Normal sequential mode ─────────────────────────────────────────
            int nextIndex = currentIndex + 1;
            if (nextIndex >= currentPlaylist.size()) {
                // End of playlist
                if (exoPlayer.getRepeatMode() == Player.REPEAT_MODE_ALL) {
                    nextIndex = 0;          // wrap around
                } else {
                    // Show end-of-queue dialog
                    isPlayingLiveData.postValue(false);
                    endOfQueueLiveData.postValue(true);
                    showEndDialogLiveData.postValue(true);
                    return;
                }
            }
            currentIndex = nextIndex;
            exoPlayer.seekToDefaultPosition(nextIndex);
            exoPlayer.play();
            currentSongLiveData.postValue(currentPlaylist.get(nextIndex));
            progressLiveData.postValue(0L);
        }
    }
    
    public void playNextSongInQueue() {
        if (exoPlayer == null || currentPlaylist == null || currentPlaylist.isEmpty()) return;
        
        Song next;
        if (Boolean.TRUE.equals(shuffleModeLiveData.getValue())) {
            if (shuffleUnplayed.isEmpty()) {
                Song lastPlayed = shufflePlayed.isEmpty() ? null
                        : shufflePlayed.get(shufflePlayed.size() - 1);
                buildShuffleQueues(lastPlayed);
                if (shuffleUnplayed.isEmpty()) return;
            }
            next = shuffleUnplayed.remove(0);
        } else {
            int nextIndex = currentIndex + 1;
            if (nextIndex >= currentPlaylist.size()) {
                showEndDialogLiveData.postValue(true);
                return;
            }
            next = currentPlaylist.get(nextIndex);
        }
        
        if (next == null) return;
        
        // Find its index in currentPlaylist for ExoPlayer
        int targetIndex = -1;
        for (int i = 0; i < currentPlaylist.size(); i++) {
            if (currentPlaylist.get(i).getId() == next.getId()) {
                targetIndex = i;
                break;
            }
        }
        if (targetIndex < 0) return;
        
        currentIndex = targetIndex;
        exoPlayer.seekToDefaultPosition(targetIndex);
        exoPlayer.play();
        currentSongLiveData.postValue(next);
        progressLiveData.postValue(0L);
    }
    
    public void playPrevious() {
        if (exoPlayer == null) return;
        if (exoPlayer.getCurrentPosition() > 3000) {
            exoPlayer.seekTo(0);
            progressLiveData.postValue(0L);
        } else if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPreviousMediaItem();
            progressLiveData.postValue(0L);
        }
    }
    
    public void seekTo(long positionMs) {
        if (exoPlayer != null) {
            exoPlayer.seekTo(positionMs);
            progressLiveData.postValue(positionMs);
        }
    }
    
    public void toggleRepeat() {
        if (exoPlayer == null) return;
        int next = exoPlayer.getRepeatMode() == Player.REPEAT_MODE_OFF ? Player.REPEAT_MODE_ALL
                : exoPlayer.getRepeatMode() == Player.REPEAT_MODE_ALL  ? Player.REPEAT_MODE_ONE
                : Player.REPEAT_MODE_OFF;
        exoPlayer.setRepeatMode(next);
        repeatModeLiveData.postValue(next);
    }
    
    /**
     * Toggle shuffle ON/OFF.
     * ON  — Builds the no-repeat unplayed queue from all songs in
     *        currentPlaylist EXCEPT the currently playing one.
     *        The current song keeps playing without interruption.
     
     * OFF — Clears the shuffle queues. ExoPlayer's natural ordered
     *        playback resumes from the current song's position.
     */
    public void toggleShuffle() {
        boolean cur  = Boolean.TRUE.equals(shuffleModeLiveData.getValue());
        boolean next = !cur;
        
        if (next) {
            // Build the no-repeat queue; current song is already "played"
            Song nowPlaying = currentSongLiveData.getValue();
            buildShuffleQueues(nowPlaying);
            // Do NOT seekTo, clearMediaItems, or call prepare() — the song
            // currently playing must NOT restart.
        } else {
            // Turning shuffle off — clear queues
            shuffleUnplayed.clear();
            shufflePlayed.clear();
        }
        
        // Keep ExoPlayer's own shuffle off — we manage order ourselves
        exoPlayer.setShuffleModeEnabled(false);
        shuffleModeLiveData.postValue(next);
    }
    
    /**
     * Shuffle the whole playlist and start playing from the first song.
     * Used by "Shuffle Play" buttons outside NowPlaying.
     */
    public void playAllShuffled(List<Song> library) {
        if (library == null || library.isEmpty()) return;
        showEndDialogLiveData.postValue(false);
        endOfQueueLiveData.postValue(false);
        
        List<Song> shuffled = new ArrayList<>(library);
        Collections.shuffle(shuffled);
        
        currentPlaylist = shuffled;
        currentIndex    = 0;
        
        exoPlayer.clearMediaItems();
        for (Song s : shuffled) exoPlayer.addMediaItem(MediaItem.fromUri(s.getUri()));
        exoPlayer.setShuffleModeEnabled(false);
        exoPlayer.prepare();
        exoPlayer.seekToDefaultPosition(0);
        exoPlayer.play();
        
        shuffleModeLiveData.postValue(true);
        currentSongLiveData.postValue(shuffled.get(0));
        
        buildShuffleQueues(shuffled.get(0));
    }
    
    /**
     * Shuffle the given playlist but keep the currently playing song
     * at the front so it is not interrupted.
     */
    public void playShuffled(List<Song> playlist) {
        if (playlist == null || playlist.isEmpty()) return;
        
        Song       currentSong = currentSongLiveData.getValue();
        List<Song> shuffled    = new ArrayList<>(playlist);
        int        nowIdx      = -1;
        
        if (currentSong != null) {
            for (int i = 0; i < shuffled.size(); i++) {
                if (shuffled.get(i).getId() == currentSong.getId()) {
                    nowIdx = i;
                    break;
                }
            }
        }
        
        if (nowIdx >= 0) {
            Song nowPlaying = shuffled.remove(nowIdx);
            Collections.shuffle(shuffled);
            shuffled.add(0, nowPlaying);
		} else {
            Collections.shuffle(shuffled);
		}
		currentIndex = 0;
		
		currentPlaylist = shuffled;
        
        exoPlayer.clearMediaItems();
        for (Song s : shuffled) exoPlayer.addMediaItem(MediaItem.fromUri(s.getUri()));
        exoPlayer.setShuffleModeEnabled(false);
        shuffleModeLiveData.postValue(true);
        currentSongLiveData.postValue(shuffled.get(0));
        
        buildShuffleQueues(shuffled.get(0));
        
        // If same song is already playing, do not restart it
        if (nowIdx >= 0 && exoPlayer.isPlaying()) return;
        
        exoPlayer.prepare();
        exoPlayer.seekToDefaultPosition(0);
        exoPlayer.play();
        
        endOfQueueLiveData.postValue(false);
        showEndDialogLiveData.postValue(false);
    }
    
    // ─── Dialog actions ─────────────────────────────────────────────────────────────
    
    /** Called when the user taps Dismiss in the end-of-queue AlertDialog. */
    public void dismissEndOfQueue() {
        showEndDialogLiveData.postValue(false);
        endOfQueueLiveData.postValue(false);
    }
    
    /**
     * Called when user taps "Play Again" in the end-of-queue AlertDialog.
     * Restarts the currentPlaylist from the first song in original order.
     */
    public void playFromBeginning() {
        showEndDialogLiveData.postValue(false);
        endOfQueueLiveData.postValue(false);
        if (exoPlayer == null || currentPlaylist == null || currentPlaylist.isEmpty()) return;
        shuffleModeLiveData.postValue(false);
        shuffleUnplayed.clear();
        shufflePlayed.clear();
        exoPlayer.setShuffleModeEnabled(false);
        exoPlayer.seekToDefaultPosition(0);
        exoPlayer.play();
        currentSongLiveData.postValue(currentPlaylist.get(0));
        progressLiveData.postValue(0L);
    }
    // ── Local music loader ────────────────────────────────────────────────
    
    public void loadLocalMusic() {
        new Thread(() -> {
            List<Song> local = repository.fetchLocalSongs();
            songsLiveData.postValue(local);
            syncFavoritesWithSongs(local);
            restoreLastSession(local);
        }).start();
    }
    
    /**
     * After songs load, restore the last-played song so the mini player
     * stays visible after the app is removed from recents and reopened.
     * Restores song metadata only — playback does NOT auto-start.
     */
    private void restoreLastSession(List<Song> songs) {
        if (songs == null || songs.isEmpty()) return;
        // Only restore if no song is already set (fresh launch / process death)
        if (currentSongLiveData.getValue() != null) return;
        SharedPreferences prefs = getApplication()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int lastIndex = prefs.getInt(KEY_LAST_INDEX, -1);
        if (lastIndex >= 0 && lastIndex < songs.size()) {
            currentIndex = lastIndex;
            currentSongLiveData.postValue(songs.get(lastIndex));
        }
    }
    
    // ── Cleanup ─────────────────────────────────────────────────────────────
    
    @Override
    protected void onCleared() {
        super.onCleared();
        getApplication().getContentResolver().unregisterContentObserver(contentObserver);
        handler.removeCallbacks(reloadRunnable);
        progressHandler.removeCallbacks(progressRunnable);
        if (exoPlayer != null) { exoPlayer.release(); exoPlayer = null; }
    }
}
