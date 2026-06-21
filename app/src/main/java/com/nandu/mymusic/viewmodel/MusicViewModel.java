package com.nandu.mymusic.viewmodel;

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
import androidx.annotation.OptIn;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.nandu.mymusic.model.Song;
import com.nandu.mymusic.repository.LocalMusicRepository;
import com.nandu.mymusic.repository.OnlineMusicRepository;
import com.nandu.mymusic.utils.AppLog;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private static final String KEY_DATA_SAVER = "data_saver_mode";
    private static final String KEY_LIBRARY_MODE = "library_mode_state";
    private static final String KEY_DELETED_IDS = "deleted_song_ids";
    
    // ✅ NEW: Persist Sort Type
    private static final String KEY_SORT_TYPE = "last_sort_type_preference";
    private final MutableLiveData<String> sortTypeLiveData;
    
    private final LocalMusicRepository repository;
    private final OnlineMusicRepository onlineRepository = new OnlineMusicRepository();
    
    // --- SLEEP TIMER STATE ---
    private android.os.CountDownTimer sleepTimer;
    private final MutableLiveData<Boolean> isSleepTimerActive = new MutableLiveData<>(false);
    private final MutableLiveData<Long> sleepTimerRemainingLiveData = new MutableLiveData<>(0L); // ✅ NEW: Live Countdown
    private final MutableLiveData<Integer> sleepTimerTracksRemainingLiveData = new MutableLiveData<>(-1);
    private int sleepTrackCounter = -1; // -1 means inactive
    private boolean stopAtQueueEnd = false;
    // ── Thread Management ────────────────────────────────────────────────
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    
    private final MutableLiveData<Boolean> isDataSaverModeLiveData;
    private final MutableLiveData<List<Song>> songsLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<Song>> allOnlineSongsLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Song> currentSongLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isPlayingLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Long> progressLiveData = new MutableLiveData<>(0L);
    private final MutableLiveData<Long> durationLiveData = new MutableLiveData<>(0L);
    private final MutableLiveData<Integer> repeatModeLiveData = new MutableLiveData<>(Player.REPEAT_MODE_OFF);
    private final MutableLiveData<Boolean> shuffleModeLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> endOfQueueLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> showEndDialogLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isNowPlayingOpenLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isLoadingLiveData = new MutableLiveData<>(true);
    private final MutableLiveData<String> lyricsLiveData = new MutableLiveData<>(null);
    
    private final Map<Long, Song> favoritesMap = new LinkedHashMap<>();
    private final MutableLiveData<Set<Long>> favoriteIdsLiveData = new MutableLiveData<>(new LinkedHashSet<>());
    private final MutableLiveData<Map<String, List<Long>>> customPlaylistsLiveData = new MutableLiveData<>(new LinkedHashMap<>());
    private final MutableLiveData<Integer> libraryModeLiveData;
    private final Set<Long> deletedSongIds = new LinkedHashSet<>();
    
    private Player exoPlayer;
    private ListenableFuture<MediaController> controllerFuture;
    private List<Song> currentPlaylist;
    private int currentIndex = -1;
    
    private final MutableLiveData<IntentSender> deleteIntentSenderLiveData = new MutableLiveData<>();
    private Song pendingDeleteSong = null;
    private final MutableLiveData<List<Song>> queueLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Song>> onlineSearchResults = new MutableLiveData<>(new ArrayList<>());
    
    // ── Progress polling ────────────────────────────────────────────
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressRunnable = new Runnable() {
        @Override public void run() {
            if (exoPlayer != null) {
                progressLiveData.postValue(exoPlayer.getCurrentPosition());
                long dur = exoPlayer.getDuration();
                durationLiveData.postValue(dur > 0 ? dur : 0L);
                
                progressHandler.removeCallbacks(progressRunnable);
                if (exoPlayer.isPlaying()) {
                    progressHandler.postDelayed(this, 200);
                }
            }
        }
    };
    
    private final ContentObserver contentObserver;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable reloadRunnable = this::loadLocalMusic;
    
    @OptIn(markerClass = UnstableApi.class)
    public MusicViewModel(@NonNull Application application) {
        super(application);
        SharedPreferences prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        isDataSaverModeLiveData = new MutableLiveData<>(prefs.getBoolean(KEY_DATA_SAVER, true));
        libraryModeLiveData = new MutableLiveData<>(prefs.getInt(KEY_LIBRARY_MODE, 0));
        
        // Load deleted song IDs
        Set<String> deletedStrs = prefs.getStringSet(KEY_DELETED_IDS, new LinkedHashSet<>());
        if (deletedStrs != null) {
            for (String idStr : deletedStrs) {
                try { deletedSongIds.add(Long.parseLong(idStr)); } catch (NumberFormatException ignored) {}
            }
        }
        
        // ✅ Initialize Sort Type
        String savedSortType = prefs.getString(KEY_SORT_TYPE, "DEFAULT");
        sortTypeLiveData = new MutableLiveData<>(savedSortType);
        
        repository = new LocalMusicRepository(application);
        loadFavoritesFromPrefs();
        loadPlaylistsFromPrefs();
        loadOnlineMusic();
        
        SessionToken sessionToken = new SessionToken(application,
                new ComponentName(application, com.nandu.mymusic.service.MusicPlaybackService.class));
        
        controllerFuture = new MediaController.Builder(application, sessionToken).buildAsync();
        controllerFuture.addListener(() -> {
            try {
                exoPlayer = controllerFuture.get();
                exoPlayer.setShuffleModeEnabled(false);
                
                exoPlayer.addListener(new Player.Listener() {
                    @Override
                    public void onIsPlayingChanged(boolean isPlaying) {
                        isPlayingLiveData.postValue(isPlaying);
                        if (!isPlaying && exoPlayer != null) persistLastPosition(exoPlayer.getCurrentPosition());
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
                        // --- SLEEP TIMER TRACK COUNTER CHECK ---
                        if (sleepTrackCounter > 0 && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                            sleepTrackCounter--;
                            sleepTimerTracksRemainingLiveData.postValue(sleepTrackCounter);
                            if (sleepTrackCounter == 0) {
                                triggerSleepTimerAction();
                            }
                        }
                        if (currentPlaylist == null || currentPlaylist.isEmpty()) return;
                        int idx = exoPlayer.getCurrentMediaItemIndex();
                        if (idx >= 0 && idx < currentPlaylist.size()) {
                            currentIndex = idx;
                            Song song = currentPlaylist.get(idx);
                            currentSongLiveData.setValue(song);
                            persistLastIndex(currentIndex);
                            fetchLyricsForCurrentSong(song);
                            if (exoPlayer.getDuration() > 0) durationLiveData.postValue(exoPlayer.getDuration());
                        }
                    }
                    
                    @Override
                    public void onPlaybackStateChanged(int state) {
                        if (exoPlayer != null && exoPlayer.getDuration() > 0)
                            durationLiveData.postValue(exoPlayer.getDuration());
                        if (state == Player.STATE_ENDED) {
                            // --- SLEEP TIMER: Handle repeat-one mode ---
                            // With REPEAT_MODE_ONE, onMediaItemTransition doesn't fire
                            // when the same song replays, so we decrement here instead.
                            if (sleepTrackCounter > 0 && exoPlayer.getRepeatMode() == Player.REPEAT_MODE_ONE) {
                                sleepTrackCounter--;
                                sleepTimerTracksRemainingLiveData.postValue(sleepTrackCounter);
                                if (sleepTrackCounter == 0) {
                                    triggerSleepTimerAction();
                                    return;
                                }
                            }
                            
                            // --- SLEEP TIMER QUEUE END CHECK ---
                            if (stopAtQueueEnd) {
                                triggerSleepTimerAction();
                                return; // Don't show the end dialog, just sleep
                            }
                            
                            isPlayingLiveData.postValue(false);
                            endOfQueueLiveData.postValue(true);
                            showEndDialogLiveData.postValue(true);
                        }
                    }
                });
                progressHandler.post(progressRunnable);
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
                handler.postDelayed(reloadRunnable, 1500);
            }
        };
        application.getContentResolver().registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, contentObserver);
    }
    
    // ── LiveData getters & Setters ──────────────────────────────────────────────────────
    public LiveData<String> getSortType() { return sortTypeLiveData; }
    public void setSortType(String sortTypeStr) {
        sortTypeLiveData.setValue(sortTypeStr);
        executorService.execute(() -> {
            getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putString(KEY_SORT_TYPE, sortTypeStr).commit();
        });
    }
    
    public LiveData<Boolean> getDataSaverMode() { return isDataSaverModeLiveData; }
    public void setDataSaverMode(boolean isEnabled) {
        isDataSaverModeLiveData.setValue(isEnabled);
        executorService.execute(() -> {
            getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_DATA_SAVER, isEnabled).commit();
        });
    }
    
    public LiveData<Integer> getLibraryMode() { return libraryModeLiveData; }
    public void setLibraryMode(int mode) {
        libraryModeLiveData.setValue(mode);
        executorService.execute(() -> {
            getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putInt(KEY_LIBRARY_MODE, mode).commit();
            
            // NEW: Reload the playlists for this specific mode
            new Handler(Looper.getMainLooper()).post(this::loadPlaylistsFromPrefs);
        });
    }
    
    public LiveData<String> getLyrics() { return lyricsLiveData; }
    public LiveData<Boolean> getIsLoading() { return isLoadingLiveData; }
    public LiveData<List<Song>> getAllOnlineSongs() { return allOnlineSongsLiveData; }
    public LiveData<List<Song>> getOnlineSearchResults() { return onlineSearchResults; }
    public LiveData<List<Song>> getQueue() { return queueLiveData; }
    
    public LiveData<List<Song>> getSongs()           { return songsLiveData; }
    public LiveData<Song>       getCurrentSong()     { return currentSongLiveData; }
    public LiveData<Boolean>    getIsPlaying()       { return isPlayingLiveData; }
    public LiveData<Long>       getProgress()        { return progressLiveData; }
    public LiveData<Long>       getDuration()        { return durationLiveData; }
    public LiveData<Integer>    getRepeatMode()      { return repeatModeLiveData; }
    public LiveData<Boolean>    getShuffleMode()     { return shuffleModeLiveData; }
    public LiveData<Boolean>    getShowEndDialog()   { return showEndDialogLiveData; }
    public LiveData<Set<Long>>  getFavoriteIds()     { return favoriteIdsLiveData; }
    public LiveData<Boolean>    isNowPlayingOpen()   { return isNowPlayingOpenLiveData; }
    public LiveData<Map<String, List<Long>>> getCustomPlaylists() { return customPlaylistsLiveData; }
    public LiveData<IntentSender> getDeleteIntentSender() { return deleteIntentSenderLiveData; }
    
    public void clearDeleteIntent() { deleteIntentSenderLiveData.postValue(null); }
    public void setNowPlayingOpen(boolean open)      { isNowPlayingOpenLiveData.postValue(open); }
    // Add this new LiveData variable near your other LiveData declarations
    private static final String KEY_AUTO_PLAYLISTS = "auto_generated_playlists";
    private static final String KEY_ENABLED_LANGS = "enabled_language_playlists";
    private final MutableLiveData<Set<String>> autoGeneratedPlaylistsLiveData = new MutableLiveData<>(new LinkedHashSet<>());
    
    // Add this helper method
    private String getPlaylistPrefsKey() {
        Integer mode = libraryModeLiveData.getValue();
        if (mode == null) mode = 0;
        return KEY_PLAYLISTS + "_" + mode; // e.g., "custom_playlists_keys_0" for Local
    }
    
    // ✅ NEW: Toggle optional language playlists (Malayalam, Tamil, Hindi)
    public void toggleLanguagePlaylist(String lang, boolean enable) {
        Set<String> current = new java.util.HashSet<>(getEnabledLanguages());
        if (enable) current.add(lang);
        else current.remove(lang);
        
        current.add("Telugu");
        current.add("English");
        
        getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_ENABLED_LANGS, current).apply();
        
        if (!enable) {
            // Delete from the current viewing mode
            deletePlaylist(lang + " Songs");
            
            // ✅ Explicitly wipe the playlist from Local Mode (0) storage
            SharedPreferences prefs = getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String scopedKey = KEY_PLAYLISTS + "_0";
            Set<String> localPlaylists = new java.util.HashSet<>(prefs.getStringSet(scopedKey, new java.util.HashSet<>()));
            if (localPlaylists.remove(lang + " Songs")) {
                prefs.edit().putStringSet(scopedKey, localPlaylists).remove("playlist_" + lang + " Songs" + scopedKey).apply();
            }
            
            Set<String> autoGeneratedSet = new LinkedHashSet<>(autoGeneratedPlaylistsLiveData.getValue() != null ? autoGeneratedPlaylistsLiveData.getValue() : new LinkedHashSet<>());
            autoGeneratedSet.remove(lang + " Songs");
            autoGeneratedPlaylistsLiveData.postValue(autoGeneratedSet);
            prefs.edit().putStringSet(KEY_AUTO_PLAYLISTS, autoGeneratedSet).apply();
            
            // Refresh UI if the user is currently on the Local Library
            Integer currentMode = libraryModeLiveData.getValue();
            if (currentMode != null && currentMode == 0) {
                new Handler(Looper.getMainLooper()).post(this::loadPlaylistsFromPrefs);
            }
        } else {
            generateLanguagePlaylistsFromOnlineMusic();
            // ✅ Trigger the match immediately if a language is turned back on
            executorService.execute(() -> {
                matchAndGenerateLocalPlaylists(songsLiveData.getValue(), allOnlineSongsLiveData.getValue());
            });
        }
    }
    
    
    // ✅ NEW: Get Enabled Languages (Telugu and English are always enabled)
    public Set<String> getEnabledLanguages() {
        SharedPreferences prefs = getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> defaultLangs = new java.util.HashSet<>(java.util.Arrays.asList("Telugu", "English"));
        return prefs.getStringSet(KEY_ENABLED_LANGS, defaultLangs);
    }
    
    // Add a getter for the UI
    public LiveData<Set<String>> getAutoGeneratedPlaylists() {
        return autoGeneratedPlaylistsLiveData;
    }
    // ── Helper Methods ──────────────────────────────────────────────────────
    private MediaItem createMediaItem(Song song) {
        String finalUrl = song.getUri().toString();
        boolean dataSaver = isDataSaverModeLiveData.getValue() != null ? isDataSaverModeLiveData.getValue() : true;
        if (!dataSaver && finalUrl.contains("/upload/q_auto,f_auto/")) {
            finalUrl = finalUrl.replace("/upload/q_auto,f_auto/", "/upload/");
        }
        return new MediaItem.Builder().setUri(Uri.parse(finalUrl)).setMediaId(String.valueOf(song.getId())).build();
    }
    
    private void updateQueueUI() {
        if (currentPlaylist != null) {
            queueLiveData.postValue(new ArrayList<>(currentPlaylist));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < currentPlaylist.size(); i++) {
                sb.append(currentPlaylist.get(i).getId());
                if (i < currentPlaylist.size() - 1) sb.append(",");
            }
            executorService.execute(() -> {
                getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putString(KEY_QUEUE_IDS, sb.toString()).commit();
            });
        }
    }
    
    private void persistLastPosition(long position) {
        executorService.execute(() -> {
            getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putLong(KEY_LAST_POSITION, position).commit();
        });
    }
    
    private void persistDeletedId(long songId) {
        deletedSongIds.add(songId);
        executorService.execute(() -> {
            Set<String> stringSet = new LinkedHashSet<>();
            for (Long id : deletedSongIds) stringSet.add(String.valueOf(id));
            getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putStringSet(KEY_DELETED_IDS, stringSet).commit();
        });
    }
    
    private void persistLastIndex(int index) {
        if (currentPlaylist == null || index < 0 || index >= currentPlaylist.size()) return;
        String uriStr = currentPlaylist.get(index).getUri().toString();
        executorService.execute(() -> {
            getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putInt(KEY_LAST_INDEX, index).putString(KEY_LAST_URI, uriStr).commit();
        });
    }
    
    private void loadFavoritesFromPrefs() {
        SharedPreferences prefs = getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> saved = prefs.getStringSet(KEY_FAV_IDS, new LinkedHashSet<>());
        Set<Long> restoredIds = new LinkedHashSet<>();
        if (saved != null) {
            for (String idStr : saved) {
                try { restoredIds.add(Long.parseLong(idStr)); } catch (NumberFormatException ignored) {}
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
        Set<Long> ids = new LinkedHashSet<>(favoriteIdsLiveData.getValue() != null ? favoriteIdsLiveData.getValue() : new LinkedHashSet<>());
        if (ids.contains(song.getId())) {
            ids.remove(song.getId());
            favoritesMap.remove(song.getId());
        } else {
            ids.add(song.getId());
            favoritesMap.put(song.getId(), song);
        }
        favoriteIdsLiveData.setValue(ids);
        
        executorService.execute(() -> {
            Set<String> stringSet = new LinkedHashSet<>();
            for (Long id : ids) stringSet.add(String.valueOf(id));
            getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putStringSet(KEY_FAV_IDS, stringSet).commit();
        });
    }
    
    // ── Playback / Queue / Custom Playlists / Deletion logic ───────────────────
    // 3. Apply shuffle behavior gracefully when starting a specific song
    public void playSong(Song song, List<Song> playlist) {
        if (exoPlayer == null) return;
        currentPlaylist = new ArrayList<>(playlist);
        currentIndex    = currentPlaylist.indexOf(song);
        if (currentIndex < 0) currentIndex = 0;
        
        // ✅ FIXED: If shuffle is active, re-shuffle the upcoming songs in the queue
        if (Boolean.TRUE.equals(shuffleModeLiveData.getValue())) {
            List<Song> upcoming = new ArrayList<>(currentPlaylist.subList(currentIndex + 1, currentPlaylist.size()));
            Collections.shuffle(upcoming);
            for (int i = 0; i < upcoming.size(); i++) {
                currentPlaylist.set(currentIndex + 1 + i, upcoming.get(i));
            }
        }
        
        updateQueueUI();
        List<MediaItem> items = new ArrayList<>();
        for (Song s : currentPlaylist) items.add(createMediaItem(s));
        exoPlayer.setShuffleModeEnabled(false);
        exoPlayer.setMediaItems(items, currentIndex, 0L);
        exoPlayer.prepare();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (exoPlayer != null && exoPlayer.getDuration() > 0) durationLiveData.postValue(exoPlayer.getDuration());
        }, 100);
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
        else exoPlayer.play();
    }
    
    public void playNextFromMiniPlayer() { advanceToNext(); }
    public void playNextFromFullPlayer()  { advanceToNext(); }
    
    private void advanceToNext() {
        if (exoPlayer == null || currentPlaylist == null || currentPlaylist.isEmpty()) return;
        int nextIndex = currentIndex + 1;
        if (nextIndex >= currentPlaylist.size()) {
            if (exoPlayer.getRepeatMode() == Player.REPEAT_MODE_ALL) nextIndex = 0;
            else {
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
        // ✅ FIXED: Allow removal from history (index < currentIndex)
        if (index >= 0 && index != currentIndex) {
            currentPlaylist.remove(index);
            exoPlayer.removeMediaItem(index);
            // ✅ Shift the current index back if we removed a song before it
            if (index < currentIndex) {
                currentIndex--;
                persistLastIndex(currentIndex);
            }
            updateQueueUI();
        }
    }
    
    public void removeSongsFromUpcoming(Set<Song> songsToRemove) {
        if (currentPlaylist == null || exoPlayer == null || songsToRemove == null || songsToRemove.isEmpty()) return;
        List<Integer> indicesToRemove = new ArrayList<>();
        for (Song song : songsToRemove) {
            int idx = currentPlaylist.indexOf(song);
            // ✅ FIXED: Allow targeting history songs
            if (idx >= 0 && idx != currentIndex) indicesToRemove.add(idx);
        }
        
        Collections.sort(indicesToRemove, Collections.reverseOrder());
        for (int idx : indicesToRemove) {
            currentPlaylist.remove(idx);
            exoPlayer.removeMediaItem(idx);
            if (idx < currentIndex) {
                currentIndex--;
            }
        }
        persistLastIndex(currentIndex);
        updateQueueUI();
    }
    
    // ✅ FIXED: Takes absolute indices allowing drags across the entire unified queue
    public void moveSongInUpcoming(int fromAbsolute, int toAbsolute) {
        if (currentPlaylist == null || exoPlayer == null) return;
        if (fromAbsolute < 0 || fromAbsolute >= currentPlaylist.size() || toAbsolute < 0 || toAbsolute >= currentPlaylist.size()) return;
        if (fromAbsolute == currentIndex || toAbsolute == currentIndex) return; // Protect Now Playing
        
        Song song = currentPlaylist.remove(fromAbsolute);
        currentPlaylist.add(toAbsolute, song);
        exoPlayer.moveMediaItem(fromAbsolute, toAbsolute);
        
        // Safely adjust currentIndex if we dragged across it
        if (fromAbsolute < currentIndex && toAbsolute > currentIndex) {
            currentIndex--;
        } else if (fromAbsolute > currentIndex && toAbsolute < currentIndex) {
            currentIndex++;
        }
        persistLastIndex(currentIndex);
        updateQueueUI();
    }
    
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
        
        executorService.execute(() -> {
            getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putInt(KEY_REPEAT_MODE, next).commit();
        });
    }
    
    public void toggleShuffle() {
        boolean cur  = Boolean.TRUE.equals(shuffleModeLiveData.getValue());
        boolean next = !cur;
        if (next) shuffleUpcomingQueue();
        else unshuffleUpcomingQueue();
        exoPlayer.setShuffleModeEnabled(false);
        shuffleModeLiveData.setValue(next);
        
        executorService.execute(() -> {
            getApplication().getSharedPreferences(PREFS_NAME,Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_SHUFFLE_MODE,next).commit();
        });
    }
    
    private void shuffleUpcomingQueue() {
        if (currentPlaylist == null || currentPlaylist.size() <= currentIndex + 1) return;
        List<Song> upcoming = new ArrayList<>(currentPlaylist.subList(currentIndex + 1, currentPlaylist.size()));
        Collections.shuffle(upcoming);
        for (int i = 0; i < upcoming.size(); i++) currentPlaylist.set(currentIndex + 1 + i, upcoming.get(i));
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
        for (int i = 0; i < upcoming.size(); i++) currentPlaylist.set(currentIndex + 1 + i, upcoming.get(i));
        exoPlayer.removeMediaItems(currentIndex + 1, currentPlaylist.size());
        List<MediaItem> newItems = new ArrayList<>();
        for (Song s : upcoming) newItems.add(createMediaItem(s));
        exoPlayer.addMediaItems(currentIndex + 1, newItems);
        updateQueueUI();
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
        
        executorService.execute(() -> {
            getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_SHUFFLE_MODE, true).commit();
        });
        
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
                if (shuffled.get(i).getId() == currentSong.getId()) { nowIdx = i; break; }
            }
        }
        if (nowIdx >= 0) {
            Song nowPlaying = shuffled.remove(nowIdx);
            Collections.shuffle(shuffled);
            shuffled.add(0, nowPlaying);
        } else Collections.shuffle(shuffled);
        
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
    
    // -- Playlists --
    private void loadPlaylistsFromPrefs() {
        SharedPreferences prefs = getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // NEW: Load the auto-generated playlist trackers
        Set<String> autoNames = prefs.getStringSet(KEY_AUTO_PLAYLISTS, new LinkedHashSet<>());
        autoGeneratedPlaylistsLiveData.postValue(new LinkedHashSet<>(autoNames));
        
        // NEW: Use the scoped key based on the current mode
        String scopedKey = getPlaylistPrefsKey();
        Set<String> playlistNames = prefs.getStringSet(scopedKey, new LinkedHashSet<>());
        Map<String, List<Long>> playlists = new LinkedHashMap<>();
        
        if (playlistNames != null) {
            for (String name : playlistNames) {
                String idsStr = prefs.getString("playlist_" + name + scopedKey, ""); // Scope the items too
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
        if (name.isBlank()) return;
        Map<String, List<Long>> current = customPlaylistsLiveData.getValue();
        Map<String, List<Long>> newPlaylists = new LinkedHashMap<>(current != null ? current : new LinkedHashMap<>());
        if (!newPlaylists.containsKey(name)) {
            newPlaylists.put(name, new ArrayList<>());
            customPlaylistsLiveData.setValue(newPlaylists);
            savePlaylistsToPrefs(newPlaylists);
        }
    }
    
    public void deletePlaylist(String name) {
        Map<String, List<Long>> current = customPlaylistsLiveData.getValue();
        if (current != null && current.containsKey(name)) {
            Map<String, List<Long>> newPlaylists = new LinkedHashMap<>(current);
            newPlaylists.remove(name);
            customPlaylistsLiveData.setValue(newPlaylists);
            savePlaylistsToPrefs(newPlaylists);
        }
    }
    
    public void addSongToPlaylist(String playlistName, Song song) {
        Map<String, List<Long>> current = customPlaylistsLiveData.getValue();
        if (current != null && current.containsKey(playlistName)) {
            Map<String, List<Long>> newPlaylists = new LinkedHashMap<>(current);
            List<Long> newSongs = new ArrayList<>(newPlaylists.get(playlistName));
            if (!newSongs.contains(song.getId())) {
                newSongs.add(song.getId());
                newPlaylists.put(playlistName, newSongs);
                customPlaylistsLiveData.setValue(newPlaylists);
                savePlaylistsToPrefs(newPlaylists);
            }
        }
    }
    
    public void addSongsToPlaylist(String playlistName, List<Song> songsToAdd) {
        Map<String, List<Long>> current = customPlaylistsLiveData.getValue();
        if (current != null && current.containsKey(playlistName)) {
            Map<String, List<Long>> newPlaylists = new LinkedHashMap<>(current);
            List<Long> newSongs = new ArrayList<>(newPlaylists.get(playlistName));
            boolean changed = false;
            for (Song song : songsToAdd) {
                if (!newSongs.contains(song.getId())) {
                    newSongs.add(song.getId());
                    changed = true;
                }
            }
            if (changed) {
                newPlaylists.put(playlistName, newSongs);
                customPlaylistsLiveData.setValue(newPlaylists);
                savePlaylistsToPrefs(newPlaylists);
            }
        }
    }
    
    @SuppressLint("ApplySharedPref")
    private void savePlaylistsToPrefs(Map<String, List<Long>> playlists) {
        executorService.execute(() -> {
            SharedPreferences prefs = getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            
            String scopedKey = getPlaylistPrefsKey();
            Set<String> oldNames = prefs.getStringSet(scopedKey, new java.util.HashSet<>());
            
            if (oldNames != null) {
                for (String oldName : oldNames) { editor.remove("playlist_" + oldName + scopedKey); }
            }
            
            editor.putStringSet(scopedKey, new java.util.HashSet<>(playlists.keySet()));
            for (Map.Entry<String, List<Long>> entry : playlists.entrySet()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < entry.getValue().size(); i++) {
                    sb.append(entry.getValue().get(i));
                    if (i < entry.getValue().size() - 1) sb.append(",");
                }
                editor.putString("playlist_" + entry.getKey() + scopedKey, sb.toString());
            }
            editor.commit();
        });
    }
    
    // ✅ NEW: Cross-reference Local and Online songs to generate Local Language Playlists
    private void matchAndGenerateLocalPlaylists(List<Song> localSongs, List<Song> onlineSongs) {
        if (localSongs == null || localSongs.isEmpty() || onlineSongs == null || onlineSongs.isEmpty()) return;
        
        Set<String> enabledLangs = getEnabledLanguages();
        enabledLangs.add("Telugu");
        enabledLangs.add("English");
        
        Map<String, List<Long>> localLanguagePlaylists = new LinkedHashMap<>();
        
        for (Song localSong : localSongs) {
            // Clean up titles (remove brackets/parentheses for better matching)
            String cleanLocal = localSong.getTitle().replaceAll("(?i)\\[.*?\\]|\\(.*?\\)", "").trim().toLowerCase();
            
            for (Song onlineSong : onlineSongs) {
                String lang = onlineSong.getLanguage();
                if (lang != null && enabledLangs.contains(lang)) {
                    String cleanOnline = onlineSong.getTitle().replaceAll("(?i)\\[.*?\\]|\\(.*?\\)", "").trim().toLowerCase();
                    
                    // Check for a match
                    boolean isMatch = false;
                    if (cleanLocal.equalsIgnoreCase(cleanOnline)) {
                        isMatch = true;
                    } else if (cleanOnline.length() > 3 && cleanLocal.contains(cleanOnline)) {
                        isMatch = true;
                    } else if (cleanLocal.length() > 3 && cleanOnline.contains(cleanLocal)) {
                        isMatch = true;
                    }
                    
                    if (isMatch) {
                        String playlistName = lang + " Songs";
                        if (!localLanguagePlaylists.containsKey(playlistName)) {
                            localLanguagePlaylists.put(playlistName, new ArrayList<>());
                        }
                        if (!localLanguagePlaylists.get(playlistName).contains(localSong.getId())) {
                            localLanguagePlaylists.get(playlistName).add(localSong.getId());
                        }
                        break; // Stop checking once we've found a language for this local song
                    }
                }
            }
        }
        
        if (localLanguagePlaylists.isEmpty()) return;
        
        // Force save these playlists specifically to the Local Library (Mode 0)
        SharedPreferences prefs = getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String scopedKey = KEY_PLAYLISTS + "_0";
        
        Set<String> existingPlaylists = new java.util.HashSet<>(prefs.getStringSet(scopedKey, new java.util.HashSet<>()));
        SharedPreferences.Editor editor = prefs.edit();
        
        Set<String> autoGeneratedSet = new LinkedHashSet<>(prefs.getStringSet(KEY_AUTO_PLAYLISTS, new LinkedHashSet<>()));
        
        for (Map.Entry<String, List<Long>> entry : localLanguagePlaylists.entrySet()) {
            String pName = entry.getKey();
            existingPlaylists.add(pName);
            autoGeneratedSet.add(pName);
            
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < entry.getValue().size(); i++) {
                sb.append(entry.getValue().get(i));
                if (i < entry.getValue().size() - 1) sb.append(",");
            }
            editor.putString("playlist_" + pName + scopedKey, sb.toString());
        }
        
        editor.putStringSet(scopedKey, existingPlaylists);
        editor.putStringSet(KEY_AUTO_PLAYLISTS, autoGeneratedSet);
        editor.apply();
        
        // Refresh LiveData if the user is currently looking at the Local Library
        Integer currentMode = libraryModeLiveData.getValue();
        if (currentMode != null && currentMode == 0) {
            new Handler(Looper.getMainLooper()).post(this::loadPlaylistsFromPrefs);
        }
    }
    // ✅ UPDATED: Generate playlists only for enabled languages
    public void generateLanguagePlaylistsFromOnlineMusic() {
        Integer currentMode = libraryModeLiveData.getValue();
        if (currentMode != null && currentMode == 0) return; // Prevent generating online playlists in Local Mode
        
        List<Song> onlineSongs = allOnlineSongsLiveData.getValue();
        if (onlineSongs == null || onlineSongs.isEmpty()) return;
        
        Set<String> enabledLangs = getEnabledLanguages();
        // Mandatory languages
        enabledLangs.add("Telugu");
        enabledLangs.add("English");
        
        Map<String, List<Song>> songsByLanguage = new LinkedHashMap<>();
        for (Song song : onlineSongs) {
            String lang = song.getLanguage();
            // Only group languages that the user has enabled (or the mandatory ones)
            if (lang != null && enabledLangs.contains(lang)) {
                if (!songsByLanguage.containsKey(lang)) {
                    songsByLanguage.put(lang, new ArrayList<>());
                }
                songsByLanguage.get(lang).add(song);
            }
        }
        
        Set<String> autoGeneratedSet = new LinkedHashSet<>(autoGeneratedPlaylistsLiveData.getValue() != null ? autoGeneratedPlaylistsLiveData.getValue() : new LinkedHashSet<>());
        
        for (Map.Entry<String, List<Song>> entry : songsByLanguage.entrySet()) {
            String playlistName = entry.getKey() + " Songs";
            createPlaylist(playlistName);
            addSongsToPlaylist(playlistName, entry.getValue());
            autoGeneratedSet.add(playlistName);
        }
        
        autoGeneratedPlaylistsLiveData.postValue(autoGeneratedSet);
        executorService.execute(() -> {
            getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putStringSet(KEY_AUTO_PLAYLISTS, autoGeneratedSet).commit();
        });
    }
    public void removeSongFromPlaylist(String playlistName, Song song) {
        Map<String, List<Long>> current = customPlaylistsLiveData.getValue();
        if (current != null && current.containsKey(playlistName)) {
            Map<String, List<Long>> newPlaylists = new LinkedHashMap<>(current);
            List<Long> newSongs = new ArrayList<>(newPlaylists.get(playlistName));
            if (newSongs.remove(Long.valueOf(song.getId()))) {
                newPlaylists.put(playlistName, newSongs);
                customPlaylistsLiveData.setValue(newPlaylists);
                savePlaylistsToPrefs(newPlaylists);
            }
        }
    }
    
    public void renamePlaylist(String oldName, String newName) {
        Map<String, List<Long>> current = customPlaylistsLiveData.getValue();
        if (current != null && current.containsKey(oldName) && !current.containsKey(newName) && !newName.trim().isEmpty()) {
            Map<String, List<Long>> newPlaylists = new LinkedHashMap<>(current);
            List<Long> ids = newPlaylists.get(oldName);
            newPlaylists.remove(oldName);
            newPlaylists.put(newName, ids);
            customPlaylistsLiveData.setValue(newPlaylists);
            savePlaylistsToPrefs(newPlaylists);
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
    
    public void deleteSong(Song song) {
        if (song == null) return;
        try {
            int deletedRows = getApplication().getContentResolver().delete(song.getUri(), null, null);
            if (deletedRows > 0) {
                AppLog.d(AppLog.PERMISSION, "Song permanently deleted from device storage.");
                persistDeletedId(song.getId());
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
            persistDeletedId(pendingDeleteSong.getId());
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
        if (favoritesMap.containsKey(song.getId())) toggleFavorite(song);
        if (currentPlaylist != null && currentPlaylist.contains(song)) {
            int indexToRemove = currentPlaylist.indexOf(song);
            currentPlaylist.remove(indexToRemove);
            updateQueueUI();
            if (exoPlayer != null) exoPlayer.removeMediaItem(indexToRemove);
            
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
    
    public void searchOnlineMusic(String query) {
        if (query == null || query.trim().isEmpty()) {
            onlineSearchResults.postValue(new ArrayList<>());
            return;
        }
        String queryLower = query.toLowerCase().trim();
        List<Song> allOnline = allOnlineSongsLiveData.getValue();
        List<Song> matches = new ArrayList<>();
        if (allOnline != null) {
            for (Song song : allOnline) {
                boolean matchTitle = song.getTitle().toLowerCase().contains(queryLower);
                boolean matchArtist = song.getArtist().toLowerCase().contains(queryLower);
                boolean matchAlbum = song.getAlbum() != null && song.getAlbum().toLowerCase().contains(queryLower);
                if (matchTitle || matchArtist || matchAlbum) matches.add(song);
            }
        }
        onlineSearchResults.postValue(matches);
    }
    
    private void loadOnlineMusic() {
        onlineRepository.fetchAllOnlineSongs(new OnlineMusicRepository.OnMusicFetchedListener() {
            @Override
            public void onSuccess(List<Song> songs) {
                allOnlineSongsLiveData.postValue(songs);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    generateLanguagePlaylistsFromOnlineMusic();
                    
                    // ✅ Trigger the local playlist matching
                    executorService.execute(() -> {
                        List<Song> local = songsLiveData.getValue();
                        if (local != null && !local.isEmpty()) {
                            matchAndGenerateLocalPlaylists(local, songs);
                        }
                    });
                }, 1000);
            }
            @Override
            public void onError(Exception e) {
                AppLog.e(AppLog.REPO, "Failed to load online music: " + e.getMessage());
            }
        });
    }
    
    public void loadLocalMusic() {
        isLoadingLiveData.postValue(true);
        // Changed to use executorService instead of raw Thread
        executorService.execute(() -> {
            List<Song> local = repository.fetchLocalSongs();
            // Filter out songs that were previously deleted but not yet removed from MediaStore
            if (!deletedSongIds.isEmpty()) {
                List<Song> filtered = new ArrayList<>();
                for (Song s : local) {
                    if (!deletedSongIds.contains(s.getId())) {
                        filtered.add(s);
                    }
                }
                local = filtered;
            }
            songsLiveData.postValue(local);
            syncFavoritesWithSongs(local);
            restoreLastSession(local);
            updateQueueUI();
            isLoadingLiveData.postValue(false);
            // ✅ Trigger the local playlist matching
            List<Song> online = allOnlineSongsLiveData.getValue();
            if (online != null && !online.isEmpty()) {
                matchAndGenerateLocalPlaylists(local, online);
            }
        });
    }
    
    private void fetchLyricsForCurrentSong(Song song) {
        lyricsLiveData.postValue("Loading...");
        
        // 1. Define 'allowOnlineFetch' based on the current library mode.
        // If mode is 0 (Local), it's false. If 1 (Cloud) or 2 (Hybrid), it's true.
        Integer currentMode = libraryModeLiveData.getValue();
        boolean allowOnlineFetch = (currentMode != null && currentMode != 0);
        
        executorService.execute(() -> {
            String lyrics = com.nandu.mymusic.utils.LyricsUtils.extractLyrics(
                    getApplication(),
                    song.getUri(),
                    song.getTitle(),
                    song.getArtist(),
                    allowOnlineFetch
            );
            if (lyrics != null) lyricsLiveData.postValue(lyrics);
            else lyricsLiveData.postValue("No lyrics found in audio or online.");
        });
    }
    
    private void restoreLastSession(List<Song> songs) {
        if (songs == null || songs.isEmpty()) return;
        if (currentSongLiveData.getValue() != null) return;
        SharedPreferences prefs = getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int    lastIndex     = prefs.getInt(KEY_LAST_INDEX, -1);
        String lastUri       = prefs.getString(KEY_LAST_URI, null);
        boolean savedShuffle = prefs.getBoolean(KEY_SHUFFLE_MODE, false);
        int savedRepeat      = prefs.getInt(KEY_REPEAT_MODE, Player.REPEAT_MODE_OFF);
        long savedPosition   = prefs.getLong(KEY_LAST_POSITION, 0L);
        String queueStr      = prefs.getString(KEY_QUEUE_IDS, "");
        
        int resolvedIndex = -1;
        List<Song> restoredQueue = new ArrayList<>();
        
        if (!queueStr.isEmpty()) {
            Map<Long, Song> songMap = new LinkedHashMap<>();
            for (Song s : songs) songMap.put(s.getId(), s);
            for (String idStr : queueStr.split(",")) {
                try {
                    long id = Long.parseLong(idStr);
                    if (songMap.containsKey(id)) restoredQueue.add(songMap.get(id));
                } catch (NumberFormatException ignored) {}
            }
        }
        
        if (restoredQueue.isEmpty()) restoredQueue = new ArrayList<>(songs);
        
        if (lastUri != null) {
            for (int i = 0; i < restoredQueue.size(); i++) {
                if (restoredQueue.get(i).getUri().toString().equals(lastUri)) {
                    resolvedIndex = i; break;
                }
            }
        }
        if (resolvedIndex < 0 && lastIndex >= 0 && lastIndex < restoredQueue.size()) resolvedIndex = lastIndex;
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
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (exoPlayer != null) {
                    long songDuration = exoPlayer.getDuration();
                    if (songDuration > 0) durationLiveData.postValue(songDuration);
                }
            }, 100);
            currentSongLiveData.postValue(currentPlaylist.get(finalIndex));
            shuffleModeLiveData.setValue(savedShuffle);
            repeatModeLiveData.setValue(savedRepeat);
            exoPlayer.setRepeatMode(savedRepeat);
            progressLiveData.setValue(savedPosition);
        });
    }
    
    // ✅ SLEEP TIMER LOGIC
    public LiveData<Boolean> getIsSleepTimerActive() { return isSleepTimerActive; }
    public LiveData<Long> getSleepTimerRemaining() { return sleepTimerRemainingLiveData; } // ✅ Live counter
    public LiveData<Integer> getSleepTimerTracksRemaining() { return sleepTimerTracksRemainingLiveData; }
    
    // 1. Time-Based Timer
    public void startTimeSleepTimer(int minutes) {
        cancelSleepTimer();
        isSleepTimerActive.postValue(true);
        
        long timeMs = minutes * 60 * 1000L;
        sleepTimer = new android.os.CountDownTimer(timeMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                sleepTimerRemainingLiveData.postValue(millisUntilFinished); // Post live time to UI
            }
            
            @Override
            public void onFinish() {
                sleepTimerRemainingLiveData.postValue(0L);
                triggerSleepTimerAction();
            }
        }.start();
    }
    
    // 2. Track-Based Timer
    public void startTrackSleepTimer(int trackCount) {
        cancelSleepTimer();
        isSleepTimerActive.postValue(true);
        sleepTrackCounter = trackCount;
        sleepTimerTracksRemainingLiveData.postValue(trackCount);
    }
    
    // 3. End of Queue Timer
    public void startQueueEndSleepTimer() {
        cancelSleepTimer();
        isSleepTimerActive.postValue(true);
        stopAtQueueEnd = true;
    }
    
    public void cancelSleepTimer() {
        if (sleepTimer != null) {
            sleepTimer.cancel();
            sleepTimer = null;
        }
        sleepTrackCounter = -1;
        stopAtQueueEnd = false;
        isSleepTimerActive.postValue(false);
        sleepTimerRemainingLiveData.postValue(0L); // Reset time
        sleepTimerTracksRemainingLiveData.postValue(-1); // Reset tracks
    }
    
    private void triggerSleepTimerAction() {
        if (exoPlayer != null) {
            exoPlayer.pause();
        }
        cancelSleepTimer();
    }
    
    @Override
    protected void onCleared() {
        if (executorService != null) {
            executorService.shutdown();
        }
        
        if (exoPlayer != null) persistLastPosition(exoPlayer.getCurrentPosition());
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