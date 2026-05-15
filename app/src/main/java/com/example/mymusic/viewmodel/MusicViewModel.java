package com.example.mymusic.viewmodel;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
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
import com.example.mymusic.utils.AppLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.app.PendingIntent;
import android.content.IntentSender;

public class MusicViewModel extends AndroidViewModel {
    
    // ── SharedPreferences keys ────────────────────────────────────────────────
    private static final String PREFS_NAME      = "MyMusicPrefs";
    private static final String KEY_FAV_IDS     = "favorite_song_ids";
    private static final String KEY_LAST_INDEX  = "last_song_index";
    private static final String KEY_LAST_URI    = "last_song_uri";   // FIX #1
    
    private final LocalMusicRepository        repository;
    private final MutableLiveData<List<Song>> songsLiveData            = new MutableLiveData<>();
    private final MutableLiveData<Song>       currentSongLiveData      = new MutableLiveData<>();
    private final MutableLiveData<Boolean>    isPlayingLiveData        = new MutableLiveData<>(false);
    private final MutableLiveData<Long>       progressLiveData         = new MutableLiveData<>(0L);
    private final MutableLiveData<Long>       durationLiveData         = new MutableLiveData<>(0L);
    private final MutableLiveData<Integer>    repeatModeLiveData       = new MutableLiveData<>(Player.REPEAT_MODE_OFF);
    private final MutableLiveData<Boolean>    shuffleModeLiveData      = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean>    endOfQueueLiveData       = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean>    showEndDialogLiveData    = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean>    isNowPlayingOpenLiveData = new MutableLiveData<>(false);
    
    private final Map<Long, Song>            favoritesMap        = new LinkedHashMap<>();
    private final MutableLiveData<Set<Long>> favoriteIdsLiveData = new MutableLiveData<>(new LinkedHashSet<>());
    
    private ExoPlayer  exoPlayer;
    private List<Song> currentPlaylist;
    private int        currentIndex = -1;
    
    private List<Song> shuffleUnplayed = new ArrayList<>();
    private List<Song> shufflePlayed   = new ArrayList<>();
    
    // ── Progress polling at 200 ms ────────────────────────────────────────────
    private final Handler  progressHandler  = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override public void run() {
            if (exoPlayer != null) {
                progressLiveData.postValue(exoPlayer.getCurrentPosition());
                long dur = exoPlayer.getDuration();
                durationLiveData.postValue(dur > 0 ? dur : 0L);
            }
            progressHandler.postDelayed(this, 200);
        }
    };
    
    private final ContentObserver contentObserver;
    private final Handler         handler        = new Handler(Looper.getMainLooper());
    private final Runnable        reloadRunnable = this::loadLocalMusic;
    
    public MusicViewModel(@NonNull Application application) {
        super(application);
        repository = new LocalMusicRepository(application);
        loadFavoritesFromPrefs();
        loadPlaylistsFromPrefs();
        
        exoPlayer = new ExoPlayer.Builder(application).build();
        exoPlayer.setShuffleModeEnabled(false);
        
        exoPlayer.addListener(new Player.Listener() {
            
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                isPlayingLiveData.postValue(isPlaying);
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
					if (isPlaying && !progressHandler.hasCallbacks(progressRunnable)) {
						progressHandler.post(progressRunnable);
					}
				}
			}
            
            @Override
            public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                if (currentPlaylist == null || currentPlaylist.isEmpty()) return;
                // FIX #3 — only sync from player on AUTO transition (natural song end)
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    int idx = exoPlayer.getCurrentMediaItemIndex();
                    if (idx >= 0 && idx < currentPlaylist.size()) {
                        currentIndex = idx;
                        Song song = currentPlaylist.get(idx);
                        currentSongLiveData.postValue(song);
                        if (Boolean.TRUE.equals(shuffleModeLiveData.getValue())) {
                            shufflePlayed.add(song);
                            shuffleUnplayed.remove(song);
                        }
                        persistLastIndex(currentIndex);
                    }
                }
            }
            
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) {
                    isPlayingLiveData.postValue(false);
                    endOfQueueLiveData.postValue(false);
                    showEndDialogLiveData.postValue(false);
                }
            }
        });
        
        progressHandler.post(progressRunnable);
        
        contentObserver = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                handler.removeCallbacks(reloadRunnable);
                handler.postDelayed(reloadRunnable, 500);
            }
        };
        application.getContentResolver().registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, contentObserver);
    }
    
    // ── Helpers ───────────────────────────────────────────────────────────────
    
    private void persistLastIndex(int index) {
        if (currentPlaylist == null || index < 0 || index >= currentPlaylist.size()) return;
        String uriStr = currentPlaylist.get(index).getUri().toString();
        getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_LAST_INDEX, index)
                .putString(KEY_LAST_URI, uriStr)
                .apply();
    }
    
    private int indexInPlaylist(Song song) {
        if (currentPlaylist == null || song == null) return -1;
        for (int i = 0; i < currentPlaylist.size(); i++) {
            if (currentPlaylist.get(i).getId() == song.getId()) return i;
        }
        return -1;
    }
    
    // ── LiveData getters ──────────────────────────────────────────────────────
    public LiveData<List<Song>> getSongs()           { return songsLiveData; }
    public LiveData<Song>       getCurrentSong()     { return currentSongLiveData; }
    public LiveData<Boolean>    getIsPlaying()       { return isPlayingLiveData; }
    public LiveData<Long>       getProgress()        { return progressLiveData; }
    public LiveData<Long>       getDuration()        { return durationLiveData; }
    public LiveData<Integer>    getRepeatMode()      { return repeatModeLiveData; }
    public LiveData<Boolean>    getShuffleMode()     { return shuffleModeLiveData; }
    public LiveData<Boolean>    getEndOfQueue()      { return endOfQueueLiveData; }
    public LiveData<Boolean>    getShowEndDialog()   { return showEndDialogLiveData; }
    public LiveData<Set<Long>>  getFavoriteIds()     { return favoriteIdsLiveData; }
    public LiveData<Boolean>    isNowPlayingOpen()   { return isNowPlayingOpenLiveData; }
    private final MutableLiveData<IntentSender> deleteIntentSenderLiveData = new MutableLiveData<>();
    private Song pendingDeleteSong = null;
    
    public LiveData<IntentSender> getDeleteIntentSender() { return deleteIntentSenderLiveData; }
    public void clearDeleteIntent() { deleteIntentSenderLiveData.postValue(null); }
    public void setNowPlayingOpen(boolean open)      { isNowPlayingOpenLiveData.postValue(open); }
    
    // ── Favorites ─────────────────────────────────────────────────────────────
    
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
        Set<String> stringSet = new LinkedHashSet<>();
        for (Long id : ids) stringSet.add(String.valueOf(id));
        getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_FAV_IDS, stringSet)
                .apply();
    }
    

    // ── Playback controls ─────────────────────────────────────────────────────
    
    public void playSong(Song song, List<Song> playlist) {
        if (exoPlayer == null) return;
        
        currentPlaylist = new ArrayList<>(playlist);
        currentIndex    = currentPlaylist.indexOf(song);
        if (currentIndex < 0) currentIndex = 0;
        
        List<MediaItem> items = new ArrayList<>();
        for (Song s : currentPlaylist) items.add(MediaItem.fromUri(s.getUri()));
        
        exoPlayer.setShuffleModeEnabled(false);
        exoPlayer.setMediaItems(items, currentIndex, 0L); // FIX #4 — atomic
        exoPlayer.prepare();
        exoPlayer.play();
        
        currentSongLiveData.postValue(song);
        progressLiveData.postValue(0L);
        endOfQueueLiveData.postValue(false);
        showEndDialogLiveData.postValue(false);
        persistLastIndex(currentIndex);
        
        if (Boolean.TRUE.equals(shuffleModeLiveData.getValue())) {
            buildShuffleQueues(song);
        }
    }
    
    public void togglePlayPause() {
        if (exoPlayer == null) return;
        if (exoPlayer.isPlaying()) exoPlayer.pause();
        else                       exoPlayer.play();
    }
    
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
    
    // ── Next / Previous ───────────────────────────────────────────────────────
    
    public void playNextFromMiniPlayer() { advanceToNext(); }
    public void playNextFromFullPlayer()  { advanceToNext(); }
    
    private void advanceToNext() {
        if (exoPlayer == null || currentPlaylist == null || currentPlaylist.isEmpty()) return;
        
        if (Boolean.TRUE.equals(shuffleModeLiveData.getValue())) {
            if (shuffleUnplayed.isEmpty()) {
                Song last = shufflePlayed.isEmpty() ? null
                        : shufflePlayed.get(shufflePlayed.size() - 1);
                buildShuffleQueues(last);
                if (shuffleUnplayed.isEmpty()) return;
            }
            Song next = shuffleUnplayed.remove(0);
            int targetIdx = indexInPlaylist(next);
            if (targetIdx < 0) return;
            
            currentIndex = targetIdx;             // FIX #5 — state set BEFORE seek
            currentSongLiveData.postValue(next);
            progressLiveData.postValue(0L);
            shufflePlayed.add(next);
            
            exoPlayer.seekToDefaultPosition(targetIdx);
			
		} else {
            int nextIndex = currentIndex + 1;
            if (nextIndex >= currentPlaylist.size()) {
                if (exoPlayer.getRepeatMode() == Player.REPEAT_MODE_ALL) {
                    nextIndex = 0;
                } else {
                    isPlayingLiveData.postValue(false);
                    endOfQueueLiveData.postValue(true);
                    showEndDialogLiveData.postValue(true);
                    return;
                }
            }
            Song next = currentPlaylist.get(nextIndex);
            
            currentIndex = nextIndex;             // FIX #5 — state set BEFORE seek
            currentSongLiveData.postValue(next);
            progressLiveData.postValue(0L);
            
            exoPlayer.seekToDefaultPosition(nextIndex);
		}
		exoPlayer.play();
		persistLastIndex(currentIndex);
	}
    
    public void playNextSongInQueue() {
        if (exoPlayer == null || currentPlaylist == null || currentPlaylist.isEmpty()) return;
        
        Song next;
        int  targetIdx;
        
        if (Boolean.TRUE.equals(shuffleModeLiveData.getValue())) {
            if (shuffleUnplayed.isEmpty()) {
                Song last = shufflePlayed.isEmpty() ? null
                        : shufflePlayed.get(shufflePlayed.size() - 1);
                buildShuffleQueues(last);
                if (shuffleUnplayed.isEmpty()) return;
            }
            next      = shuffleUnplayed.remove(0);
            targetIdx = indexInPlaylist(next);
        } else {
            int nextIndex = currentIndex + 1;
            if (nextIndex >= currentPlaylist.size()) {
                showEndDialogLiveData.postValue(true);
                return;
            }
            next      = currentPlaylist.get(nextIndex);
            targetIdx = nextIndex;
        }
        
        if (targetIdx < 0) return;
        
        currentIndex = targetIdx;
        currentSongLiveData.postValue(next);
        progressLiveData.postValue(0L);
        
        exoPlayer.seekToDefaultPosition(targetIdx);
        exoPlayer.play();
        persistLastIndex(currentIndex);
    }
    
    // ── Playlist Keys ─────────────────────────────────────────────────────────
    private static final String KEY_PLAYLISTS = "custom_playlists_keys";
    
    // Add this LiveData near your other LiveData variables at the top
    private final MutableLiveData<Map<String, List<Long>>> customPlaylistsLiveData = new MutableLiveData<>(new LinkedHashMap<>());
    
    // Add this inside your constructor `MusicViewModel(...)`:
    // loadFavoritesFromPrefs();
    // loadPlaylistsFromPrefs(); <--- ADD THIS LINE
    
    // ── Custom Playlists ──────────────────────────────────────────────────────
    
    private void loadPlaylistsFromPrefs() {
        SharedPreferences prefs = getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> playlistNames = prefs.getStringSet(KEY_PLAYLISTS, new LinkedHashSet<>());
        Map<String, List<Long>> playlists = new LinkedHashMap<>();
        
        if (playlistNames != null) {
            for (String name : playlistNames) {
                String idsStr = prefs.getString("playlist_" + name, "");
                List<Long> ids = new ArrayList<>();
                if (!idsStr.isEmpty()) {
                    for (String id : idsStr.split(",")) {
                        try { ids.add(Long.parseLong(id)); } catch (NumberFormatException ignored) {}
                    }
                }
                playlists.put(name, ids);
            }
        }
        customPlaylistsLiveData.postValue(playlists);
    }
    
    public LiveData<Map<String, List<Long>>> getCustomPlaylists() {
        return customPlaylistsLiveData;
    }
    
    public void createPlaylist(String name) {
        Map<String, List<Long>> current = customPlaylistsLiveData.getValue();
        if (current == null) current = new LinkedHashMap<>();
        if (!current.containsKey(name)) {
            current.put(name, new ArrayList<>());
            customPlaylistsLiveData.postValue(current);
            savePlaylists(current);
        }
    }
    
    public void addSongToPlaylist(String playlistName, Song song) {
        Map<String, List<Long>> current = customPlaylistsLiveData.getValue();
        if (current != null && current.containsKey(playlistName)) {
            List<Long> ids = current.get(playlistName);
            if (!ids.contains(song.getId())) {
                ids.add(song.getId());
                customPlaylistsLiveData.postValue(current);
                savePlaylists(current);
            }
        }
    }
    
    private void savePlaylists(Map<String, List<Long>> playlists) {
        SharedPreferences.Editor editor = getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putStringSet(KEY_PLAYLISTS, playlists.keySet());
        for (Map.Entry<String, List<Long>> entry : playlists.entrySet()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < entry.getValue().size(); i++) {
                sb.append(entry.getValue().get(i));
                if (i < entry.getValue().size() - 1) sb.append(",");
            }
            editor.putString("playlist_" + entry.getKey(), sb.toString());
        }
        editor.apply();
    }
    
    public void deletePlaylist(String name) {
        Map<String, List<Long>> current = customPlaylistsLiveData.getValue();
        if (current != null && current.containsKey(name)) {
            current.remove(name);
            customPlaylistsLiveData.postValue(current);
            savePlaylists(current);
        }
    }
    
    public void removeSongFromPlaylist(String playlistName, Song song) {
        Map<String, List<Long>> current = customPlaylistsLiveData.getValue();
        if (current != null && current.containsKey(playlistName)) {
            List<Long> ids = current.get(playlistName);
            // Cast to Long to use remove(Object) instead of remove(int index)
            if (ids.remove(Long.valueOf(song.getId()))) {
                customPlaylistsLiveData.postValue(current);
                savePlaylists(current);
            }
        }
    }
    
    // ── Deletion Logic ────────────────────────────────────────────────────────
    
    public void deleteSong(Song song) {
        if (song == null) return;
        
        try {
            // Attempt to physically delete from Device first
            int deletedRows = getApplication().getContentResolver().delete(song.getUri(), null, null);
            if (deletedRows > 0) {
                AppLog.d(AppLog.PERMISSION, "Song permanently deleted from device storage.");
                removeSongFromState(song); // Success without dialog (Android 9 and below)
            }
        } catch (SecurityException e) {
            // Android 11+ (API 30+) Scoped Storage requires user permission dialog
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                List<Uri> uris = new ArrayList<>();
                uris.add(song.getUri());
                PendingIntent pi = MediaStore.createDeleteRequest(getApplication().getContentResolver(), uris);
                pendingDeleteSong = song;
                deleteIntentSenderLiveData.postValue(pi.getIntentSender());
            }
            // Android 10 (API 29) uses RecoverableSecurityException
            else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                if (e instanceof android.app.RecoverableSecurityException) {
                    android.app.RecoverableSecurityException rse = (android.app.RecoverableSecurityException) e;
                    pendingDeleteSong = song;
                    deleteIntentSenderLiveData.postValue(rse.getUserAction().getActionIntent().getIntentSender());
                } else {
                    AppLog.e(AppLog.PERMISSION, "SecurityException: " + e.getMessage());
                }
            } else {
                AppLog.e(AppLog.PERMISSION, "Cannot physically delete file. " + e.getMessage());
            }
        }
    }
    
    public void confirmPendingDeletion() {
        if (pendingDeleteSong != null) {
            removeSongFromState(pendingDeleteSong);
            pendingDeleteSong = null;
        }
    }
    
    private void removeSongFromState(Song song) {
        if (song == null) return;
        
        // 1. Remove from local LiveData (Updates the UI immediately)
        List<Song> currentList = songsLiveData.getValue();
        if (currentList != null) {
            List<Song> updated = new ArrayList<>(currentList);
            updated.remove(song);
            songsLiveData.postValue(updated);
        }
        
        // 2. Remove from favorites if it's there
        if (favoritesMap.containsKey(song.getId())) {
            toggleFavorite(song);
        }
        
        // 3. Remove from Active Queue & ExoPlayer
        if (currentPlaylist != null && currentPlaylist.contains(song)) {
            int indexToRemove = currentPlaylist.indexOf(song);
            currentPlaylist.remove(indexToRemove);
            
            if (exoPlayer != null) {
                exoPlayer.removeMediaItem(indexToRemove); // Removes directly from Player
            }
            
            // Shift our current index tracker
            if (indexToRemove < currentIndex) {
                currentIndex--;
            } else if (indexToRemove == currentIndex) {
                if (currentPlaylist.isEmpty()) {
                    if (exoPlayer != null) exoPlayer.stop();
                    currentSongLiveData.postValue(null);
                    isPlayingLiveData.postValue(false);
                } else {
                    int newIndex = Math.min(currentIndex, currentPlaylist.size() - 1);
                    currentIndex = newIndex;
                    currentSongLiveData.postValue(currentPlaylist.get(newIndex));
                }
            }
        }
        
        // 4. Remove from Shuffle queues
        shuffleUnplayed.remove(song);
        shufflePlayed.remove(song);
    }
    
    // ── Queue Management ──────────────────────────────────────────────────────
    
    public void setPlayNext(Song song) {
        if (exoPlayer == null || currentPlaylist == null || currentPlaylist.isEmpty()) {
            // If nothing is playing, just start playing this song as a new queue
            playSong(song, Collections.singletonList(song));
            return;
        }
        
        int insertIndex = currentIndex + 1;
        currentPlaylist.add(insertIndex, song);
        exoPlayer.addMediaItem(insertIndex, MediaItem.fromUri(song.getUri()));
        
        // Also queue it in the shuffle list if shuffle is active
        if (Boolean.TRUE.equals(shuffleModeLiveData.getValue())) {
            shuffleUnplayed.add(0, song);
        }
    }
    
    public void addToQueue(Song song) {
        if (exoPlayer == null || currentPlaylist == null || currentPlaylist.isEmpty()) {
            playSong(song, Collections.singletonList(song));
            return;
        }
        
        currentPlaylist.add(song);
        exoPlayer.addMediaItem(MediaItem.fromUri(song.getUri()));
        
        // Add to the end of the unplayed shuffle list if shuffle is active
        if (Boolean.TRUE.equals(shuffleModeLiveData.getValue())) {
            shuffleUnplayed.add(song);
        }
    }
    
    public void playPrevious() {
        if (exoPlayer == null) return;
        
        if (exoPlayer.getCurrentPosition() > 3000) {
            exoPlayer.seekTo(0);
            progressLiveData.postValue(0L);
            return;
        }
        
        if (Boolean.TRUE.equals(shuffleModeLiveData.getValue())) {
            // FIX #6 — use shuffle history stack
            if (shufflePlayed.size() > 1) {
                Song current = shufflePlayed.remove(shufflePlayed.size() - 1);
                shuffleUnplayed.add(0, current);
                
                Song prev      = shufflePlayed.get(shufflePlayed.size() - 1);
                int  targetIdx = indexInPlaylist(prev);
                if (targetIdx < 0) return;
                
                currentIndex = targetIdx;
                currentSongLiveData.postValue(prev);
                progressLiveData.postValue(0L);
                
                exoPlayer.seekToDefaultPosition(targetIdx);
                exoPlayer.play();
                persistLastIndex(currentIndex);
            } else {
                exoPlayer.seekTo(0);
                progressLiveData.postValue(0L);
            }
        } else {
            if (exoPlayer.hasPreviousMediaItem()) {
                int prevIndex = currentIndex - 1;
                if (prevIndex < 0) prevIndex = 0;
                Song prev = currentPlaylist.get(prevIndex);
                
                currentIndex = prevIndex;
                currentSongLiveData.postValue(prev);
                progressLiveData.postValue(0L);
                
                exoPlayer.seekToDefaultPosition(prevIndex);
                exoPlayer.play();
                persistLastIndex(currentIndex);
            } else {
                exoPlayer.seekTo(0);
                progressLiveData.postValue(0L);
            }
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
        int next = exoPlayer.getRepeatMode() == Player.REPEAT_MODE_OFF  ? Player.REPEAT_MODE_ALL
                : exoPlayer.getRepeatMode() == Player.REPEAT_MODE_ALL  ? Player.REPEAT_MODE_ONE
                : Player.REPEAT_MODE_OFF;
        exoPlayer.setRepeatMode(next);
        repeatModeLiveData.postValue(next);
    }
    
    public void toggleShuffle() {
        boolean cur  = Boolean.TRUE.equals(shuffleModeLiveData.getValue());
        boolean next = !cur;
        if (next) {
            buildShuffleQueues(currentSongLiveData.getValue());
        } else {
            shuffleUnplayed.clear();
            shufflePlayed.clear();
        }
        exoPlayer.setShuffleModeEnabled(false);
        shuffleModeLiveData.postValue(next);
    }
    
    public void playAllShuffled(List<Song> library) {
        if (library == null || library.isEmpty()) return;
        showEndDialogLiveData.postValue(false);
        endOfQueueLiveData.postValue(false);
        
        List<Song> shuffled = new ArrayList<>(library);
        Collections.shuffle(shuffled);
        
        currentPlaylist = shuffled;
        currentIndex    = 0;
        
        List<MediaItem> items = new ArrayList<>();
        for (Song s : shuffled) items.add(MediaItem.fromUri(s.getUri()));
        
        exoPlayer.setShuffleModeEnabled(false);
        exoPlayer.setMediaItems(items, 0, 0L);
        exoPlayer.prepare();
        exoPlayer.play();
        
        shuffleModeLiveData.postValue(true);
        currentSongLiveData.postValue(shuffled.get(0));
        progressLiveData.postValue(0L);
        buildShuffleQueues(shuffled.get(0));
        persistLastIndex(0);
    }
    
    public void playShuffled(List<Song> playlist) {
        if (playlist == null || playlist.isEmpty()) return;
        
        Song       currentSong = currentSongLiveData.getValue();
        List<Song> shuffled    = new ArrayList<>(playlist);
        int        nowIdx      = -1;
        
        if (currentSong != null) {
            for (int i = 0; i < shuffled.size(); i++) {
                if (shuffled.get(i).getId() == currentSong.getId()) {
                    nowIdx = i; break;
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
        
        currentIndex    = 0;
        currentPlaylist = shuffled;
        
        List<MediaItem> items = new ArrayList<>();
        for (Song s : shuffled) items.add(MediaItem.fromUri(s.getUri()));
        
        exoPlayer.setShuffleModeEnabled(false);
        shuffleModeLiveData.postValue(true);
        currentSongLiveData.postValue(shuffled.get(0));
        buildShuffleQueues(shuffled.get(0));
        
        if (nowIdx >= 0 && exoPlayer.isPlaying()) return;
        
        exoPlayer.setMediaItems(items, 0, 0L);
        exoPlayer.prepare();
        exoPlayer.play();
        
        endOfQueueLiveData.postValue(false);
        showEndDialogLiveData.postValue(false);
        persistLastIndex(0);
    }
    
    // ── Dialog actions ────────────────────────────────────────────────────────
    
    public void dismissEndOfQueue() {
        showEndDialogLiveData.postValue(false);
        endOfQueueLiveData.postValue(false);
    }
    
    public void playFromBeginning() {
        showEndDialogLiveData.postValue(false);
        endOfQueueLiveData.postValue(false);
        if (exoPlayer == null || currentPlaylist == null || currentPlaylist.isEmpty()) return;
        shuffleModeLiveData.postValue(false);
        shuffleUnplayed.clear();
        shufflePlayed.clear();
        exoPlayer.setShuffleModeEnabled(false);
        
        currentIndex = 0;
        currentSongLiveData.postValue(currentPlaylist.get(0));
        progressLiveData.postValue(0L);
        
        exoPlayer.seekToDefaultPosition(0);
        exoPlayer.play();
        persistLastIndex(0);
    }
    
    // ── Local music loader ────────────────────────────────────────────────────
    
    public void loadLocalMusic() {
        new Thread(() -> {
            List<Song> local = repository.fetchLocalSongs();
            songsLiveData.postValue(local);
            syncFavoritesWithSongs(local);
            restoreLastSession(local);
        }).start();
    }
    
    private void restoreLastSession(List<Song> songs) {
        if (songs == null || songs.isEmpty()) return;
        if (currentSongLiveData.getValue() != null) return;
        
        SharedPreferences prefs = getApplication()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int    lastIndex = prefs.getInt(KEY_LAST_INDEX, -1);
        String lastUri   = prefs.getString(KEY_LAST_URI, null);
        
        int resolvedIndex = -1;
        if (lastUri != null) {
            for (int i = 0; i < songs.size(); i++) {
                if (songs.get(i).getUri().toString().equals(lastUri)) {
                    resolvedIndex = i; break;
                }
            }
        }
        if (resolvedIndex < 0 && lastIndex >= 0 && lastIndex < songs.size()) {
            resolvedIndex = lastIndex;
        }
        if (resolvedIndex < 0) return;
        
        currentPlaylist = new ArrayList<>(songs);
        currentIndex    = resolvedIndex;
        
        List<MediaItem> items = new ArrayList<>();
        for (Song s : currentPlaylist) items.add(MediaItem.fromUri(s.getUri()));
        
        final int finalIndex = resolvedIndex;
        new Handler(Looper.getMainLooper()).post(() -> {
            exoPlayer.setMediaItems(items, finalIndex, 0L);
            exoPlayer.prepare();
            // No exoPlayer.play() — restore UI only, do NOT autoplay
            currentSongLiveData.postValue(currentPlaylist.get(finalIndex));
        });
    }
    
    // ─────────────────── Cleanup ──────────────────────────────────────────────
    
    @Override
    protected void onCleared() {
        super.onCleared();
        getApplication().getContentResolver().unregisterContentObserver(contentObserver);
        handler.removeCallbacks(reloadRunnable);
        progressHandler.removeCallbacks(progressRunnable);
        if (exoPlayer != null) { exoPlayer.release(); exoPlayer = null; }
    }
}