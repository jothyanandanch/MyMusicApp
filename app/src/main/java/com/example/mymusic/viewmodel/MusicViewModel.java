package com.example.mymusic.viewmodel;

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
import com.example.mymusic.service.MusicPlaybackService;
import com.example.mymusic.utils.AppLog;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MusicViewModel extends AndroidViewModel {
    
    // ── SharedPreferences keys ────────────────────────────────────────────────
    private static final String PREFS_NAME      = "MyMusicPrefs";
    private static final String KEY_FAV_IDS     = "favorite_song_ids";
    private static final String KEY_PLAYLISTS   = "custom_playlists_keys";
    
    // Smart Queue Persistence Keys
    private static final String KEY_BASE_QUEUE    = "base_queue_ids";
    private static final String KEY_HISTORY_QUEUE = "history_queue_ids";
    private static final String KEY_UPCOMING_QUEUE= "upcoming_queue_ids";
    private static final String KEY_LAST_URI      = "last_song_uri";
    
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
    
    // ✅ The Visible Queue for the UI
    private final MutableLiveData<List<Song>> activeQueueLiveData      = new MutableLiveData<>(new ArrayList<>());
    
    private final Map<Long, Song>            favoritesMap        = new LinkedHashMap<>();
    private final MutableLiveData<Set<Long>> favoriteIdsLiveData = new MutableLiveData<>(new LinkedHashSet<>());
    private final MutableLiveData<Map<String, List<Long>>> customPlaylistsLiveData = new MutableLiveData<>(new LinkedHashMap<>());
    
    private final MutableLiveData<IntentSender> deleteIntentSenderLiveData = new MutableLiveData<>();
    private Song pendingDeleteSong = null;
    
    // Background Service MediaController
    private Player exoPlayer;
    private ListenableFuture<MediaController> controllerFuture;
    
    // ✅ THE SMART QUEUE ARCHITECTURE
    private List<Song> baseQueue     = new ArrayList<>(); // Original unmodified order
    private List<Song> playHistory   = new ArrayList<>(); // Songs already played
    private List<Song> upcomingQueue = new ArrayList<>(); // Songs to play next
    
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
        
        SessionToken sessionToken = new SessionToken(application, new ComponentName(application, MusicPlaybackService.class));
        controllerFuture = new MediaController.Builder(application, sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                exoPlayer = controllerFuture.get();
                setupPlayer();
                if (songsLiveData.getValue() != null && !songsLiveData.getValue().isEmpty()) {
                    restoreLastSession(songsLiveData.getValue());
                }
            } catch (Exception e) {
                AppLog.e(AppLog.PLAYER, "Failed to connect to MediaSessionService: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(application));
        
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
    
    private void setupPlayer() {
        exoPlayer.setShuffleModeEnabled(false); // We handle custom shuffling
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
                // Keep our lists in perfect sync with ExoPlayer's internal index
                syncListsWithIndex(exoPlayer.getCurrentMediaItemIndex());
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
    }
    
    // ── SMART QUEUE HELPERS ───────────────────────────────────────────────────
    
    private MediaItem createMediaItem(Song song) {
        MediaMetadata metadata = new MediaMetadata.Builder()
                .setTitle(song.getTitle())
                .setArtist(song.getArtist())
                .setArtworkUri(song.getAlbumArtUri())
                .build();
        return new MediaItem.Builder()
                .setMediaId(String.valueOf(song.getId()))
                .setUri(song.getUri())
                .setMediaMetadata(metadata)
                .build();
    }
    
    private void updateQueueLiveData() {
        List<Song> fullQueue = new ArrayList<>(playHistory);
        if (currentSongLiveData.getValue() != null) fullQueue.add(currentSongLiveData.getValue());
        fullQueue.addAll(upcomingQueue);
        activeQueueLiveData.postValue(fullQueue);
    }
    
    /**
     * Shifts songs dynamically between history, current, and upcoming lists
     * based on ExoPlayer's index. Covers next, previous, and auto-advance.
     */
    private void syncListsWithIndex(int targetIndex) {
        Song current = currentSongLiveData.getValue();
        if (current == null) return;
        
        // If moved forward
        while (playHistory.size() < targetIndex && !upcomingQueue.isEmpty()) {
            playHistory.add(current);
            current = upcomingQueue.remove(0);
        }
        // If moved backward
        while (playHistory.size() > targetIndex && !playHistory.isEmpty()) {
            upcomingQueue.add(0, current);
            current = playHistory.remove(playHistory.size() - 1);
        }
        
        currentSongLiveData.postValue(current);
        updateQueueLiveData();
        persistQueueAndIndex();
    }
    
    private String idsToString(List<Song> songs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < songs.size(); i++) {
            sb.append(songs.get(i).getId());
            if (i < songs.size() - 1) sb.append(",");
        }
        return sb.toString();
    }
    
    private void persistQueueAndIndex() {
        Song current = currentSongLiveData.getValue();
        if (current == null) return;
        SharedPreferences.Editor ed = getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        ed.putString(KEY_BASE_QUEUE, idsToString(baseQueue));
        ed.putString(KEY_HISTORY_QUEUE, idsToString(playHistory));
        ed.putString(KEY_UPCOMING_QUEUE, idsToString(upcomingQueue));
        ed.putString(KEY_LAST_URI, current.getUri().toString());
        ed.apply();
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
    public LiveData<List<Song>> getQueue()           { return activeQueueLiveData; }
    
    public LiveData<IntentSender> getDeleteIntentSender() { return deleteIntentSenderLiveData; }
    public void clearDeleteIntent() { deleteIntentSenderLiveData.postValue(null); }
    public void setNowPlayingOpen(boolean open)      { isNowPlayingOpenLiveData.postValue(open); }
    
    // ── Playback controls ─────────────────────────────────────────────────────
    
    public void playSong(Song song, List<Song> playlist) {
        if (exoPlayer == null) return;
        
        baseQueue = new ArrayList<>(playlist);
        playHistory.clear();
        upcomingQueue.clear();
        
        int startIndex = playlist.indexOf(song);
        if (startIndex < 0) startIndex = 0;
        
        for (int i = 0; i < startIndex; i++) playHistory.add(playlist.get(i));
        currentSongLiveData.postValue(playlist.get(startIndex));
        for (int i = startIndex + 1; i < playlist.size(); i++) upcomingQueue.add(playlist.get(i));
        
        if (Boolean.TRUE.equals(shuffleModeLiveData.getValue())) {
            Collections.shuffle(upcomingQueue);
        }
        
        List<MediaItem> items = new ArrayList<>();
        for (Song s : playHistory) items.add(createMediaItem(s));
        items.add(createMediaItem(playlist.get(startIndex)));
        for (Song s : upcomingQueue) items.add(createMediaItem(s));
        
        exoPlayer.setMediaItems(items, playHistory.size(), 0L);
        exoPlayer.prepare();
        exoPlayer.play();
        
        endOfQueueLiveData.postValue(false);
        showEndDialogLiveData.postValue(false);
        updateQueueLiveData();
        persistQueueAndIndex();
    }
    
    public void togglePlayPause() {
        if (exoPlayer == null) return;
        if (exoPlayer.isPlaying()) exoPlayer.pause();
        else exoPlayer.play();
    }
    
    // ── Next / Previous ───────────────────────────────────────────────────────
    
    public void playNextFromMiniPlayer() { advanceToNext(); }
    public void playNextFromFullPlayer() { advanceToNext(); }
    public void playNextSongInQueue()    { advanceToNext(); }
    
    private void advanceToNext() {
        if (exoPlayer == null) return;
        if (upcomingQueue.isEmpty()) {
            if (exoPlayer.getRepeatMode() == Player.REPEAT_MODE_ALL) {
                exoPlayer.seekToDefaultPosition(0);
                exoPlayer.play();
            } else {
                endOfQueueLiveData.postValue(true);
                showEndDialogLiveData.postValue(true);
            }
            return;
        }
        exoPlayer.seekToDefaultPosition(exoPlayer.getCurrentMediaItemIndex() + 1);
        exoPlayer.play();
    }
    
    public void playPrevious() {
        if (exoPlayer == null) return;
        if (exoPlayer.getCurrentPosition() > 3000 || playHistory.isEmpty()) {
            exoPlayer.seekTo(0);
            progressLiveData.postValue(0L);
            return;
        }
        exoPlayer.seekToDefaultPosition(exoPlayer.getCurrentMediaItemIndex() - 1);
        exoPlayer.play();
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
    
    // ✅ EXACT SHUFFLE TOGGLE LOGIC
    public void toggleShuffle() {
        boolean isShuffle = !Boolean.TRUE.equals(shuffleModeLiveData.getValue());
        
        if (exoPlayer == null || currentSongLiveData.getValue() == null) {
            shuffleModeLiveData.postValue(isShuffle);
            return;
        }
        
        int currentIndex = exoPlayer.getCurrentMediaItemIndex();
        
        // 1. Remove all upcoming items from ExoPlayer
        while (exoPlayer.getMediaItemCount() > currentIndex + 1) {
            exoPlayer.removeMediaItem(exoPlayer.getMediaItemCount() - 1);
        }
        
        if (isShuffle) {
            // SHUFFLE ON: Randomize what's left
            Collections.shuffle(upcomingQueue);
        } else {
            // SHUFFLE OFF: Rebuild original order skipping what we already played
            List<Song> rebuiltQueue = new ArrayList<>();
            Set<Long> playedIds = new HashSet<>();
            for (Song s : playHistory) playedIds.add(s.getId());
            playedIds.add(currentSongLiveData.getValue().getId());
            
            for (Song s : baseQueue) {
                if (!playedIds.contains(s.getId())) rebuiltQueue.add(s);
            }
            upcomingQueue = rebuiltQueue;
        }
        
        // 2. Feed the new perfectly formatted queue to ExoPlayer
        for (Song s : upcomingQueue) exoPlayer.addMediaItem(createMediaItem(s));
        
        shuffleModeLiveData.postValue(isShuffle);
        updateQueueLiveData();
        persistQueueAndIndex();
    }
    
    public void playAllShuffled(List<Song> library) {
        if (library == null || library.isEmpty()) return;
        shuffleModeLiveData.postValue(true);
        playSong(library.get(0), library); // Automatically triggers shuffle logic inside playSong
    }
    
    public void playShuffled(List<Song> playlist) {
        playAllShuffled(playlist);
    }
    
    // ── Editing Queue ─────────────────────────────────────────────────────────
    
    public void setPlayNext(Song song) {
        if (exoPlayer == null || currentSongLiveData.getValue() == null) {
            playSong(song, Collections.singletonList(song));
            return;
        }
        upcomingQueue.add(0, song);
        baseQueue.add(song); // Added to base so it persists when turning off shuffle
        exoPlayer.addMediaItem(exoPlayer.getCurrentMediaItemIndex() + 1, createMediaItem(song));
        updateQueueLiveData();
        persistQueueAndIndex();
    }
    
    public void addToQueue(Song song) {
        if (exoPlayer == null || currentSongLiveData.getValue() == null) {
            playSong(song, Collections.singletonList(song));
            return;
        }
        upcomingQueue.add(song);
        baseQueue.add(song);
        exoPlayer.addMediaItem(createMediaItem(song));
        updateQueueLiveData();
        persistQueueAndIndex();
    }
    
    // ── Local music loader & Restore ──────────────────────────────────────────
    
    public void loadLocalMusic() {
        new Thread(() -> {
            List<Song> local = repository.fetchLocalSongs();
            songsLiveData.postValue(local);
            syncFavoritesWithSongs(local);
            restoreLastSession(local);
        }).start();
    }
    
    private void restoreLastSession(List<Song> songs) {
        if (songs == null || songs.isEmpty() || currentSongLiveData.getValue() != null || exoPlayer == null) return;
        
        SharedPreferences prefs = getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String lastUri = prefs.getString(KEY_LAST_URI, "");
        if (lastUri.isEmpty()) return;
        
        baseQueue     = parseQueue(prefs.getString(KEY_BASE_QUEUE, ""), songs);
        playHistory   = parseQueue(prefs.getString(KEY_HISTORY_QUEUE, ""), songs);
        upcomingQueue = parseQueue(prefs.getString(KEY_UPCOMING_QUEUE, ""), songs);
        
        Song restoredCurrent = null;
        for (Song s : songs) {
            if (s.getUri().toString().equals(lastUri)) {
                restoredCurrent = s; break;
            }
        }
        if (restoredCurrent == null) return;
        currentSongLiveData.postValue(restoredCurrent);
        
        List<MediaItem> items = new ArrayList<>();
        for (Song s : playHistory) items.add(createMediaItem(s));
        items.add(createMediaItem(restoredCurrent));
        for (Song s : upcomingQueue) items.add(createMediaItem(s));
        
        final int finalIndex = playHistory.size();
        new Handler(Looper.getMainLooper()).post(() -> {
            exoPlayer.setMediaItems(items, finalIndex, 0L);
            exoPlayer.prepare(); // Do not autoplay
            updateQueueLiveData();
        });
    }
    
    private List<Song> parseQueue(String idsStr, List<Song> allSongs) {
        List<Song> result = new ArrayList<>();
        if (idsStr.isEmpty()) return result;
        Map<Long, Song> songMap = new LinkedHashMap<>();
        for (Song s : allSongs) songMap.put(s.getId(), s);
        
        for (String idStr : idsStr.split(",")) {
            try {
                Song s = songMap.get(Long.parseLong(idStr));
                if (s != null) result.add(s);
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }
    
    // ── Basic Favorites & Playlist Methods omitted for brevity, keep the exact same methods from before ──
    private void loadFavoritesFromPrefs() { /* Keep existing */ }
    private void syncFavoritesWithSongs(List<Song> songs) { /* Keep existing */ }
    public void toggleFavorite(Song song) { /* Keep existing */ }
    private void loadPlaylistsFromPrefs() { /* Keep existing */ }
    public LiveData<Map<String, List<Long>>> getCustomPlaylists() { return customPlaylistsLiveData; }
    public void createPlaylist(String name) { /* Keep existing */ }
    public void addSongToPlaylist(String playlistName, Song song) { /* Keep existing */ }
    public void addSongsToPlaylist(String playlistName, List<Song> songsToAdd) { /* Keep existing */ }
    public void deletePlaylist(String name) { /* Keep existing */ }
    public void removeSongFromPlaylist(String playlistName, Song song) { /* Keep existing */ }
    public void renamePlaylist(String oldName, String newName) { /* Keep existing */ }
    public void addListToQueue(List<Song> songs) { for(Song s : songs) addToQueue(s); } // Simplified!
    
    // ── Deletion Logic ────────────────────────────────────────────────────────
    public void deleteSong(Song song) { /* Keep existing intent sender logic */ }
    public void confirmPendingDeletion() { /* Keep existing confirmation */ }
    
    // ── Dialog actions ────────────────────────────────────────────────────────
    public void dismissEndOfQueue() {
        showEndDialogLiveData.postValue(false);
        endOfQueueLiveData.postValue(false);
    }
    public void playFromBeginning() {
        dismissEndOfQueue();
        if (exoPlayer == null || baseQueue.isEmpty()) return;
        shuffleModeLiveData.postValue(false);
        playSong(baseQueue.get(0), baseQueue);
    }
    
    @Override
    protected void onCleared() {
        super.onCleared();
        getApplication().getContentResolver().unregisterContentObserver(contentObserver);
        handler.removeCallbacks(reloadRunnable);
        progressHandler.removeCallbacks(progressRunnable);
        if (controllerFuture != null) MediaController.releaseFuture(controllerFuture);
    }
}