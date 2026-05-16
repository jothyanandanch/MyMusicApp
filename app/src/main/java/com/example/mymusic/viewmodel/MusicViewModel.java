package com.example.mymusic.viewmodel;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.example.mymusic.model.Song;
import com.example.mymusic.repository.LocalMusicRepository;
import com.example.mymusic.utils.AppLog;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MusicViewModel extends AndroidViewModel {
    
    // ── SharedPreferences keys ────────────────────────────────────────────────
    private static final String PREFS_NAME      = "MyMusicPrefs";
    private static final String KEY_FAV_IDS     = "favorite_song_ids";
    private static final String KEY_LAST_INDEX  = "last_song_index";
    private static final String KEY_LAST_URI    = "last_song_uri";
    private static final String KEY_PLAYLISTS   = "custom_playlists_keys";
    private static final String KEY_SHUFFLE_MODE = "shuffle_mode_state";
    private static final String KEY_REPEAT_MODE  = "repeat_mode_state";
    private static final String KEY_LAST_POSITION = "last_song_position";
    private static final String KEY_QUEUE_IDS     = "saved_queue_ids";
    
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
    
    private final Map<Long, Song>             favoritesMap             = new LinkedHashMap<>();
    private final MutableLiveData<Set<Long>>  favoriteIdsLiveData      = new MutableLiveData<>(new LinkedHashSet<>());
    private final MutableLiveData<Map<String, List<Long>>> customPlaylistsLiveData = new MutableLiveData<>(new LinkedHashMap<>());
    
    private Player exoPlayer;
    private ListenableFuture<MediaController> controllerFuture;
    private List<Song> currentPlaylist;
    private int        currentIndex = -1;
    
    private MediaItem createMediaItem(Song song) {
        MediaMetadata metadata = new MediaMetadata.Builder()
                .setTitle(song.getTitle())
                .setArtist(song.getArtist())
                .setArtworkUri(song.getAlbumArtUri())
                .build();
        
        return new MediaItem.Builder()
                .setUri(song.getUri())
                .setMediaId(String.valueOf(song.getId()))
                .setMediaMetadata(metadata)
                .build();
    }
    
    private final MutableLiveData<IntentSender> deleteIntentSenderLiveData = new MutableLiveData<>();
    private Song pendingDeleteSong = null;
    private final MutableLiveData<List<Song>> queueLiveData = new MutableLiveData<>(new ArrayList<>());
    
    public LiveData<List<Song>> getQueue() {
        return queueLiveData;
    }
    
    // Helper to push the latest queue to the UI
    private void updateQueueUI() {
        if (currentPlaylist != null) {
            queueLiveData.postValue(new ArrayList<>(currentPlaylist));
            // ✅ Persist the exact queue order
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < currentPlaylist.size(); i++) {
                sb.append(currentPlaylist.get(i).getId());
                if (i < currentPlaylist.size() - 1) sb.append(",");
            }
            getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_QUEUE_IDS, sb.toString())
                    .apply();
        }
    }
    
    // ── Progress polling at 200 ms ────────────────────────────────────────────
    private final Handler  progressHandler  = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override public void run() {
            if (exoPlayer != null && exoPlayer.isPlaying()) {
                progressLiveData.postValue(exoPlayer.getCurrentPosition());
                long dur = exoPlayer.getDuration();
                durationLiveData.postValue(dur > 0 ? dur : 0L);
                progressHandler.postDelayed(this, 200);
            }
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
        
        SessionToken sessionToken = new SessionToken(application,
                new ComponentName(application, com.example.mymusic.service.MusicPlaybackService.class));
        
        controllerFuture = new MediaController.Builder(application, sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                exoPlayer = controllerFuture.get();
                exoPlayer.setShuffleModeEnabled(false);
                
                exoPlayer.addListener(new Player.Listener() {
                    
                    @Override
                    public void onIsPlayingChanged(boolean isPlaying) {
                        isPlayingLiveData.postValue(isPlaying);
                        if (!isPlaying && exoPlayer != null) {
                            persistLastPosition(exoPlayer.getCurrentPosition());
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            if (isPlaying && !progressHandler.hasCallbacks(progressRunnable)) {
                                progressHandler.post(progressRunnable);
                            } else {
                                progressHandler.removeCallbacks(progressRunnable);
                            }
                        }
                    }
                    
                    @Override
                    public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                        if (currentPlaylist == null || currentPlaylist.isEmpty()) return;
                        
                        int idx = exoPlayer.getCurrentMediaItemIndex();
                        
                        if (idx >= 0 && idx < currentPlaylist.size()) {
                            currentIndex = idx;
                            Song song = currentPlaylist.get(idx);
                            
                            currentSongLiveData.setValue(song);
                            persistLastIndex(currentIndex);
                        }
                    }
                    
                    @Override
                    public void onPlaybackStateChanged(int state) {
                        if (state == Player.STATE_ENDED) {
                            isPlayingLiveData.postValue(false);
                            endOfQueueLiveData.postValue(true);
                            showEndDialogLiveData.postValue(true);
                        }
                    }
                });
                
                progressHandler.post(progressRunnable);
                
                // Handle race condition: if local music finished loading before the controller connected
                if (songsLiveData.getValue() != null && !songsLiveData.getValue().isEmpty()) {
                    restoreLastSession(songsLiveData.getValue());
                }
                
            } catch (Exception e) {
                AppLog.e(AppLog.PLAYER, "Failed to connect to MediaSessionService");
            }
        }, ContextCompat.getMainExecutor(application));
        
        contentObserver = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                handler.removeCallbacks(reloadRunnable);
                handler.postDelayed(reloadRunnable, 30000);
            }
        };
        application.getContentResolver().registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, contentObserver);
    }
    
    // ── Helpers ───────────────────────────────────────────────────────────────
    
    private void persistLastPosition(long position) {
        getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_POSITION, position)
                .apply();
    }
    
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
    public LiveData<Map<String, List<Long>>> getCustomPlaylists() { return customPlaylistsLiveData; }
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
    
    @SuppressLint("ApplySharedPref")
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
        
        favoriteIdsLiveData.setValue(ids);
        
        Set<String> stringSet = new LinkedHashSet<>();
        for (Long id : ids) stringSet.add(String.valueOf(id));
        getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY_FAV_IDS, stringSet)
                .commit();
    }
    
    // ── Playback controls ─────────────────────────────────────────────────────
    
    public void playSong(Song song, List<Song> playlist) {
        if (exoPlayer == null) return;
        
        currentPlaylist = new ArrayList<>(playlist);
        currentIndex    = currentPlaylist.indexOf(song);
        if (currentIndex < 0) currentIndex = 0;
        updateQueueUI();
        List<MediaItem> items = new ArrayList<>();
        for (Song s : currentPlaylist) items.add(createMediaItem(s));
        
        exoPlayer.setShuffleModeEnabled(false);
        exoPlayer.setMediaItems(items, currentIndex, 0L);
        exoPlayer.prepare();
        exoPlayer.play();
        
        currentSongLiveData.setValue(song);
        progressLiveData.setValue(0L);
        endOfQueueLiveData.setValue(false);
        showEndDialogLiveData.setValue(false);
        persistLastIndex(currentIndex);
    }
    
    public void togglePlayPause() {
        if (exoPlayer == null) return;
        if (exoPlayer.isPlaying()) exoPlayer.pause();
        else                       exoPlayer.play();
    }
    
    private void shuffleUpcomingQueue() {
        if (currentPlaylist == null || currentPlaylist.size() <= currentIndex + 1) return;
        
        List<Song> upcoming = new ArrayList<>(currentPlaylist.subList(currentIndex + 1, currentPlaylist.size()));
        Collections.shuffle(upcoming);
        
        for (int i = 0; i < upcoming.size(); i++) {
            currentPlaylist.set(currentIndex + 1 + i, upcoming.get(i));
        }
        
        exoPlayer.removeMediaItems(currentIndex + 1, currentPlaylist.size());
        List<MediaItem> newItems = new ArrayList<>();
        for (Song s : upcoming) newItems.add(createMediaItem(s));
        exoPlayer.addMediaItems(currentIndex + 1, newItems);
        
        updateQueueUI();
    }
    
    private void unshuffleUpcomingQueue() {
        if (currentPlaylist == null || currentPlaylist.size() <= currentIndex + 1) return;
        
        List<Song> upcoming = new ArrayList<>(currentPlaylist.subList(currentIndex + 1, currentPlaylist.size()));
        upcoming.sort((s1, s2) -> s1.getTitle().compareToIgnoreCase(s2.getTitle()));
        
        for (int i = 0; i < upcoming.size(); i++) {
            currentPlaylist.set(currentIndex + 1 + i, upcoming.get(i));
        }
        
        exoPlayer.removeMediaItems(currentIndex + 1, currentPlaylist.size());
        List<MediaItem> newItems = new ArrayList<>();
        for (Song s : upcoming) newItems.add(createMediaItem(s));
        exoPlayer.addMediaItems(currentIndex + 1, newItems);
        
        updateQueueUI();
    }
    
    // ── Next / Previous ───────────────────────────────────────────────────────
    
    public void playNextFromMiniPlayer() { advanceToNext(); }
    public void playNextFromFullPlayer()  { advanceToNext(); }
    
    private void advanceToNext() {
        if (exoPlayer == null || currentPlaylist == null || currentPlaylist.isEmpty()) return;
        
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
        
        currentIndex = nextIndex;
        currentSongLiveData.setValue(next);
        progressLiveData.setValue(0L);
        
        exoPlayer.seekToDefaultPosition(nextIndex);
        exoPlayer.play();
        persistLastIndex(currentIndex);
    }
    
    public void playSongFromQueue(Song song) {
        if (currentPlaylist == null || exoPlayer == null) return;
        int index = currentPlaylist.indexOf(song);
        if (index >= 0) {
            currentIndex = index;
            currentSongLiveData.setValue(song);
            progressLiveData.setValue(0L);
            exoPlayer.seekToDefaultPosition(index);
            exoPlayer.play();
            persistLastIndex(currentIndex);
        }
    }
    
    public void removeSongFromUpcoming(Song song) {
        if (currentPlaylist == null || exoPlayer == null) return;
        int index = currentPlaylist.indexOf(song);
        
        if (index > currentIndex) {
            currentPlaylist.remove(index);
            exoPlayer.removeMediaItem(index);
            updateQueueUI();
        }
    }
    
    public void removeSongsFromUpcoming(Set<Song> songsToRemove) {
        if (currentPlaylist == null || exoPlayer == null || songsToRemove == null || songsToRemove.isEmpty()) return;
        
        List<Integer> indicesToRemove = new ArrayList<>();
        for (Song song : songsToRemove) {
            int idx = currentPlaylist.indexOf(song);
            if (idx > currentIndex) {
                indicesToRemove.add(idx);
            }
        }
        
        Collections.sort(indicesToRemove, Collections.reverseOrder());
        
        for (int idx : indicesToRemove) {
            currentPlaylist.remove(idx);
            exoPlayer.removeMediaItem(idx);
        }
        
        updateQueueUI();
    }
    
    public void moveSongInUpcoming(int fromLocal, int toLocal) {
        if (currentPlaylist == null || exoPlayer == null) return;
        
        int absoluteFrom = currentIndex + 1 + fromLocal;
        int absoluteTo   = currentIndex + 1 + toLocal;
        
        if (absoluteFrom >= currentPlaylist.size() || absoluteTo >= currentPlaylist.size()) return;
        
        Song song = currentPlaylist.remove(absoluteFrom);
        currentPlaylist.add(absoluteTo, song);
        
        exoPlayer.moveMediaItem(absoluteFrom, absoluteTo);
        
        updateQueueUI();
    }
    
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
    
    public void createPlaylist(String name) {
        Map<String, List<Long>> current = customPlaylistsLiveData.getValue();
        if (current == null) current = new LinkedHashMap<>();
        if (!current.containsKey(name)) {
            current.put(name, new ArrayList<>());
            customPlaylistsLiveData.setValue(current);
            savePlaylists(current);
        }
    }
    
    public void addSongToPlaylist(String playlistName, Song song) {
        Map<String, List<Long>> current = customPlaylistsLiveData.getValue();
        if (current != null && current.containsKey(playlistName)) {
            List<Long> ids = current.get(playlistName);
            if (!ids.contains(song.getId())) {
                ids.add(song.getId());
                customPlaylistsLiveData.setValue(current);
                savePlaylists(current);
            }
        }
    }
    
    public void addSongsToPlaylist(String playlistName, List<Song> songsToAdd) {
        Map<String, List<Long>> current = customPlaylistsLiveData.getValue();
        if (current != null && current.containsKey(playlistName)) {
            List<Long> ids = current.get(playlistName);
            boolean changed = false;
            
            for (Song song : songsToAdd) {
                if (!ids.contains(song.getId())) {
                    ids.add(song.getId());
                    changed = true;
                }
            }
            
            if (changed) {
                customPlaylistsLiveData.setValue(current);
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
        editor.commit();
    }
    
    public void deletePlaylist(String name) {
        Map<String, List<Long>> current = customPlaylistsLiveData.getValue();
        if (current != null && current.containsKey(name)) {
            current.remove(name);
            customPlaylistsLiveData.setValue(current);
            savePlaylists(current);
        }
    }
    
    public void removeSongFromPlaylist(String playlistName, Song song) {
        Map<String, List<Long>> current = customPlaylistsLiveData.getValue();
        if (current != null && current.containsKey(playlistName)) {
            List<Long> ids = current.get(playlistName);
            if (ids.remove(Long.valueOf(song.getId()))) {
                customPlaylistsLiveData.setValue(current);
                savePlaylists(current);
            }
        }
    }
    
    public void renamePlaylist(String oldName, String newName) {
        Map<String, List<Long>> current = customPlaylistsLiveData.getValue();
        if (current != null && current.containsKey(oldName) && !current.containsKey(newName) && !newName.trim().isEmpty()) {
            List<Long> ids = current.get(oldName);
            current.remove(oldName);
            current.put(newName, ids);
            customPlaylistsLiveData.setValue(current);
            savePlaylists(current);
        }
    }
    
    public void addListToQueue(List<Song> songs) {
        if (songs == null || songs.isEmpty()) return;
        
        if (exoPlayer == null || currentPlaylist == null || currentPlaylist.isEmpty()) {
            playSong(songs.get(0), songs);
            return;
        }
        
        for (Song song : songs) {
            currentPlaylist.add(song);
            exoPlayer.addMediaItem(createMediaItem(song));
            
        }
        updateQueueUI();
    }
    
    // ── Deletion Logic ────────────────────────────────────────────────────────
    
    public void deleteSong(Song song) {
        if (song == null) return;
        
        try {
            int deletedRows = getApplication().getContentResolver().delete(song.getUri(), null, null);
            if (deletedRows > 0) {
                AppLog.d(AppLog.PERMISSION, "Song permanently deleted from device storage.");
                removeSongFromState(song);
            }
        } catch (SecurityException e) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                List<Uri> uris = new ArrayList<>();
                uris.add(song.getUri());
                PendingIntent pi = MediaStore.createDeleteRequest(getApplication().getContentResolver(), uris);
                pendingDeleteSong = song;
                deleteIntentSenderLiveData.postValue(pi.getIntentSender());
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
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
        
        List<Song> currentList = songsLiveData.getValue();
        if (currentList != null) {
            List<Song> updated = new ArrayList<>(currentList);
            updated.remove(song);
            songsLiveData.setValue(updated);
        }
        
        if (favoritesMap.containsKey(song.getId())) {
            toggleFavorite(song);
        }
        
        if (currentPlaylist != null && currentPlaylist.contains(song)) {
            int indexToRemove = currentPlaylist.indexOf(song);
            currentPlaylist.remove(indexToRemove);
            updateQueueUI();
            
            if (exoPlayer != null) {
                exoPlayer.removeMediaItem(indexToRemove);
            }
            
            if (indexToRemove < currentIndex) {
                currentIndex--;
            } else if (indexToRemove == currentIndex) {
                if (currentPlaylist.isEmpty()) {
                    if (exoPlayer != null) exoPlayer.stop();
                    currentSongLiveData.setValue(null);
                    isPlayingLiveData.setValue(false);
                    progressLiveData.setValue(0L);
                    durationLiveData.setValue(0L);
                } else {
                    int newIndex = Math.min(currentIndex, currentPlaylist.size() - 1);
                    currentIndex = newIndex;
                    currentSongLiveData.setValue(currentPlaylist.get(newIndex));
                }
            }
        }
    }
    
    // ── Queue Management ──────────────────────────────────────────────────────
    
    public void setPlayNext(Song song) {
        if (exoPlayer == null || currentPlaylist == null || currentPlaylist.isEmpty()) {
            playSong(song, Collections.singletonList(song));
            return;
        }
        
        int insertIndex = currentIndex + 1;
        currentPlaylist.add(insertIndex, song);
        exoPlayer.addMediaItem(insertIndex, createMediaItem(song));
        updateQueueUI();
    }
    
    public void addToQueue(Song song) {
        if (exoPlayer == null || currentPlaylist == null || currentPlaylist.isEmpty()) {
            playSong(song, Collections.singletonList(song));
            return;
        }
        
        currentPlaylist.add(song);
        exoPlayer.addMediaItem(createMediaItem(song));
        updateQueueUI();
    }
    
    public void playPrevious() {
        if (exoPlayer == null || currentPlaylist == null || currentPlaylist.isEmpty()) return;
        
        if (exoPlayer.getCurrentPosition() > 3000) {
            exoPlayer.seekTo(0);
            progressLiveData.postValue(0L);
            return;
        }
        
        int prevIndex = currentIndex - 1;
        if (prevIndex < 0) prevIndex = 0;
        
        Song prev = currentPlaylist.get(prevIndex);
        
        currentIndex = prevIndex;
        currentSongLiveData.setValue(prev);
        progressLiveData.setValue(0L);
        
        exoPlayer.seekToDefaultPosition(prevIndex);
        exoPlayer.play();
        persistLastIndex(currentIndex);
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
        getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_REPEAT_MODE, next)
                .apply();
    }
    
    public void toggleShuffle() {
        boolean cur  = Boolean.TRUE.equals(shuffleModeLiveData.getValue());
        boolean next = !cur;
        
        if (next) {
            shuffleUpcomingQueue();
        } else {
            unshuffleUpcomingQueue();
        }
        
        exoPlayer.setShuffleModeEnabled(false);
        shuffleModeLiveData.setValue(next);
        getApplication().getSharedPreferences(PREFS_NAME,Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SHUFFLE_MODE,next)
                .apply();
    }
    
    public void playAllShuffled(List<Song> library) {
        if (library == null || library.isEmpty()) return;
        showEndDialogLiveData.setValue(false);
        endOfQueueLiveData.setValue(false);
        
        List<Song> shuffled = new ArrayList<>(library);
        Collections.shuffle(shuffled);
        
        currentPlaylist = shuffled;
        currentIndex    = 0;
        updateQueueUI();
        List<MediaItem> items = new ArrayList<>();
        for (Song s : shuffled) items.add(createMediaItem(s));
        
        exoPlayer.setShuffleModeEnabled(false);
        exoPlayer.setMediaItems(items, 0, 0L);
        exoPlayer.prepare();
        exoPlayer.play();
        
        shuffleModeLiveData.setValue(true);
        getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SHUFFLE_MODE, true)
                .apply();
        currentSongLiveData.setValue(shuffled.get(0));
        progressLiveData.setValue(0L);
        
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
        updateQueueUI();
        
        List<MediaItem> items = new ArrayList<>();
        for (Song s : shuffled) items.add(createMediaItem(s));
        
        exoPlayer.setShuffleModeEnabled(false);
        shuffleModeLiveData.setValue(true);
        currentSongLiveData.setValue(shuffled.get(0));
        
        if (nowIdx >= 0 && exoPlayer.isPlaying()) {
            long currentPos = exoPlayer.getCurrentPosition();
            exoPlayer.setMediaItems(items, 0, currentPos);
            return;
        }
        
        exoPlayer.setMediaItems(items, 0, 0L);
        exoPlayer.prepare();
        exoPlayer.play();
        
        endOfQueueLiveData.setValue(false);
        showEndDialogLiveData.setValue(false);
        persistLastIndex(0);
    }
    
    // ── Dialog actions ────────────────────────────────────────────────────────
    
    public void dismissEndOfQueue() {
        showEndDialogLiveData.postValue(false);
        endOfQueueLiveData.postValue(false);
    }
    
    public void playFromBeginning() {
        showEndDialogLiveData.setValue(false);
        endOfQueueLiveData.setValue(false);
        
        if (exoPlayer == null || currentPlaylist == null || currentPlaylist.isEmpty()) return;
        
        shuffleModeLiveData.setValue(false);
        
        exoPlayer.setShuffleModeEnabled(false);
        
        currentIndex = 0;
        currentSongLiveData.setValue(currentPlaylist.get(0));
        progressLiveData.setValue(0L);
        
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
            updateQueueUI();
        }).start();
    }
    
    // Find restoreLastSession and replace it completely with this:
    private void restoreLastSession(List<Song> songs) {
        if (songs == null || songs.isEmpty()) return;
        if (currentSongLiveData.getValue() != null) return;
        
        SharedPreferences prefs = getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int    lastIndex     = prefs.getInt(KEY_LAST_INDEX, -1);
        String lastUri       = prefs.getString(KEY_LAST_URI, null);
        boolean savedShuffle = prefs.getBoolean(KEY_SHUFFLE_MODE, false);
        int savedRepeat      = prefs.getInt(KEY_REPEAT_MODE, Player.REPEAT_MODE_OFF);
        long savedPosition   = prefs.getLong(KEY_LAST_POSITION, 0L);
        String queueStr      = prefs.getString(KEY_QUEUE_IDS, ""); // ✅ Get saved queue
        
        int resolvedIndex = -1;
        List<Song> restoredQueue = new ArrayList<>();
        
        // ✅ 1. Try to restore the exact queue order
        if (!queueStr.isEmpty()) {
            Map<Long, Song> songMap = new LinkedHashMap<>();
            for (Song s : songs) songMap.put(s.getId(), s);
            
            for (String idStr : queueStr.split(",")) {
                try {
                    long id = Long.parseLong(idStr);
                    if (songMap.containsKey(id)) {
                        restoredQueue.add(songMap.get(id));
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        
        // ✅ 2. Fallback to all songs if the queue was empty or failed to load
        if (restoredQueue.isEmpty()) {
            restoredQueue = new ArrayList<>(songs);
        }
        
        // ✅ 3. Find where we left off in the restored queue
        if (lastUri != null) {
            for (int i = 0; i < restoredQueue.size(); i++) {
                if (restoredQueue.get(i).getUri().toString().equals(lastUri)) {
                    resolvedIndex = i; break;
                }
            }
        }
        if (resolvedIndex < 0 && lastIndex >= 0 && lastIndex < restoredQueue.size()) {
            resolvedIndex = lastIndex;
        }
        
        if (resolvedIndex < 0) return;
        
        currentPlaylist = restoredQueue;
        currentIndex    = resolvedIndex;
        
        List<MediaItem> items = new ArrayList<>();
        for (Song s : currentPlaylist) items.add(createMediaItem(s));
        
        final int finalIndex = resolvedIndex;
        new Handler(Looper.getMainLooper()).post(() -> {
            if (exoPlayer == null) return;
            
            exoPlayer.setMediaItems(items, finalIndex, savedPosition);
            exoPlayer.prepare();
            currentSongLiveData.postValue(currentPlaylist.get(finalIndex));
            shuffleModeLiveData.setValue(savedShuffle);
            repeatModeLiveData.setValue(savedRepeat);
            progressLiveData.setValue(savedPosition);
            
            // ✅ We removed the "if (savedShuffle) shuffleUpcomingQueue()" from here.
            // The saved queue already has the proper shuffled order!
        });
    }
    
    // ─────────────────── Cleanup ──────────────────────────────────────────────
    
    @Override
    protected void onCleared() {
        if (exoPlayer != null) {
            persistLastPosition(exoPlayer.getCurrentPosition());
        }
        super.onCleared();
        getApplication().getContentResolver().unregisterContentObserver(contentObserver);
        handler.removeCallbacks(reloadRunnable);
        progressHandler.removeCallbacks(progressRunnable);
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
        }
    }
}