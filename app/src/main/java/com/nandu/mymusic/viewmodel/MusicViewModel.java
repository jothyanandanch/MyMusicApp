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
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.nandu.mymusic.model.Song;
import com.nandu.mymusic.repository.LocalMusicRepository;
import com.nandu.mymusic.repository.OnlineMusicRepository;
import com.nandu.mymusic.service.MusicPlaybackService;
import com.nandu.mymusic.utils.AppLog;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

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
    // Apple Music "Autoplay": opt-in toggle for keeping music going at end of queue.
    // Default OFF — the old always-on behavior silently replaced the queue with
    // random library songs, which users experienced as the queue "resetting".
    private static final String KEY_AUTOPLAY_ENABLED = "autoplay_enabled";
    private static final String KEY_NP_SOURCE = "now_playing_source";

    // ✅ Apple Music "Recently Played": persistent play history (newest first)
    private static final String KEY_PLAY_HISTORY = "play_history_ids";
    private static final int PLAY_HISTORY_MAX = 50;
    private final MutableLiveData<List<Song>> playHistoryLiveData = new MutableLiveData<>(new ArrayList<>());
    private final Deque<Long> playHistoryIds = new ArrayDeque<>();

    // ✅ Apple Music "Hot Tracks": persistent per-song play counts
    private static final String KEY_PLAY_COUNTS = "play_counts";
    private static final int HOT_TRACKS_MAX = 20;
    private final MutableLiveData<List<Song>> hotTracksLiveData = new MutableLiveData<>(new ArrayList<>());
    private final Map<Long, Integer> playCounts = new LinkedHashMap<>();

    // ✅ NEW: Persist Sort Type
    private static final String KEY_SORT_TYPE = "last_sort_type_preference";
    private final MutableLiveData<String> sortTypeLiveData;
    
    private final LocalMusicRepository repository;
    private final OnlineMusicRepository onlineRepository = new OnlineMusicRepository();
    
    // --- SLEEP TIMER STATE ---
    private android.os.CountDownTimer sleepTimer;
    /** Fade-out timer used at the sleep timer's end (Apple Music fades instead of hard-pausing). */
    private android.os.CountDownTimer sleepFadeTimer;
    /** Player volume before a sleep fade, restored on pause/cancel. */
    private volatile float volBeforeSleepFade = 1f;
    /** True while a sleep fade-out is running. */
    private volatile boolean sleepFadeActive = false;
    /** Apple Music: ~5s graceful fade before the sleep timer pauses playback. */
    private static final long SLEEP_FADE_MS = 5000L;
    private static final long SLEEP_FADE_TICK_MS = 100L;
    private final MutableLiveData<Boolean> isSleepTimerActive = new MutableLiveData<>(false);
    private final MutableLiveData<Long> sleepTimerRemainingLiveData = new MutableLiveData<>(0L); // ✅ NEW: Live Countdown
    private final MutableLiveData<Integer> sleepTimerTracksRemainingLiveData = new MutableLiveData<>(-1);
    private int sleepTrackCounter = -1; // -1 means inactive
    private boolean stopAtQueueEnd = false;
    // ── Thread Management ────────────────────────────────────────────────
    /** Reference to the SharedPreferences used to register/unregister the favorites listener. */
    private final android.content.SharedPreferences appPrefs;

    /**
     * Watch favorite_song_ids changes pushed by the media notification handler
     * (system UI toggles) so the app's heart state never desyncs.
     */
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener
            favoritesPrefsListener = (prefs, key) -> {
        if (!KEY_FAV_IDS.equals(key)) return;
        new Handler(Looper.getMainLooper()).post(this::refreshFavoritesFromPrefs);
    };

    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    private final MutableLiveData<Boolean> isDataSaverModeLiveData;
    private final MutableLiveData<Boolean> isAutoplayEnabledLiveData;
    private final MutableLiveData<List<Song>> songsLiveData = new MutableLiveData<>();
    private final MutableLiveData<List<Song>> allOnlineSongsLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isRefreshingOnlineLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Song> currentSongLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isPlayingLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Long> progressLiveData = new MutableLiveData<>(0L);
    private final MutableLiveData<Long> durationLiveData = new MutableLiveData<>(0L);
    private final MutableLiveData<Integer> repeatModeLiveData = new MutableLiveData<>(Player.REPEAT_MODE_OFF);
    private final MutableLiveData<Boolean> shuffleModeLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> endOfQueueLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> showEndDialogLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isNowPlayingOpenLiveData = new MutableLiveData<>(false);
    /** Apple Music "Playing From" — the queue's origin (playlist, album, Listen Now…). */
    private final MutableLiveData<String> nowPlayingSourceLiveData = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> isLoadingLiveData = new MutableLiveData<>(true);
    private final MutableLiveData<String> lyricsLiveData = new MutableLiveData<>(null);
    private String cachedLyrics = null;
    private long cachedLyricsSongId = -1;
    private String previousCachedLyrics = null;
    private long previousCachedLyricsSongId = -1;
    private volatile int lyricsFetchGeneration = 0;
    private final MutableLiveData<Boolean> onlineSongBlockedLiveData = new MutableLiveData<>(false);
    private boolean wasPlayingBeforeBlock = false;

    private final Map<Long, Song> favoritesMap = new LinkedHashMap<>();
    private final MutableLiveData<Set<Long>> favoriteIdsLiveData = new MutableLiveData<>(new LinkedHashSet<>());
    private final MutableLiveData<Map<String, List<Long>>> customPlaylistsLiveData = new MutableLiveData<>(new LinkedHashMap<>());
    private final MutableLiveData<Integer> libraryModeLiveData;
    private final Set<Long> deletedSongIds = new LinkedHashSet<>();
    
    private Player exoPlayer;
    private ListenableFuture<MediaController> controllerFuture;
    private List<Song> currentPlaylist;
    private List<Song> originalPlaylistBeforeShuffle;
    private int currentIndex = -1;
    
    private final MutableLiveData<IntentSender> deleteIntentSenderLiveData = new MutableLiveData<>();
    private Song pendingDeleteSong = null;
    private final MutableLiveData<List<Song>> queueLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Song>> onlineSearchResults = new MutableLiveData<>(new ArrayList<>());
    private final AtomicBoolean sessionRestoreInProgress = new AtomicBoolean(false);
    // Set once the user starts playback or curates the queue themselves — any pending
    // session-restore apply must then abort instead of clobbering the user's queue.
    private volatile boolean userInteractedWithPlayback = false;
    
    // ── Progress polling ────────────────────────────────────────────
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private int progressTicks = 0;
    /** Apple Music–style error resilience: give up after N consecutive broken tracks. */
    private static final int MAX_CONSECUTIVE_ERRORS = 3;
    private int consecutivePlaybackErrors = 0;
    /** Apple Music–style autoplay pool size at end of queue. */
    private static final int AUTOPLAY_POOL_SIZE = 50;
    private final Runnable queueDriftCheckRunnable = new Runnable() {
        @Override public void run() {
            // External controllers can edit the queue behind our back; if the
            // player's timeline diverges from our snapshot, resync instead of
            // letting the UI go stale.
            if (exoPlayer == null || sessionRestoreInProgress.get()) return;
            Integer queueSize;
            synchronized (MusicViewModel.this) {
                queueSize = currentPlaylist == null ? null : currentPlaylist.size();
            }
            if (queueSize != null && exoPlayer.getMediaItemCount() != queueSize) {
                AppLog.d(AppLog.PLAYER, "Queue drift detected — resyncing UI from player");
                syncUiFromPlayer();
            }
        }
    };
    private final Runnable progressRunnable = new Runnable() {
        @Override public void run() {
            if (exoPlayer != null) {
                progressLiveData.postValue(exoPlayer.getCurrentPosition());
                long dur = exoPlayer.getDuration();
                durationLiveData.postValue(dur > 0 ? dur : 0L);

                progressHandler.removeCallbacks(progressRunnable);
                if (exoPlayer.isPlaying()) {
                    // 16ms interval for smooth 60fps progress updates
                    progressHandler.postDelayed(this, 16);
                    // Persist position every ~5 seconds (312 ticks × 16ms ≈ 5s)
                    if (++progressTicks >= 312) {
                        progressTicks = 0;
                        persistLastPosition(exoPlayer.getCurrentPosition());
                    }
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
        isAutoplayEnabledLiveData = new MutableLiveData<>(prefs.getBoolean(KEY_AUTOPLAY_ENABLED, false));
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
        appPrefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        appPrefs.registerOnSharedPreferenceChangeListener(favoritesPrefsListener);
        loadFavoritesFromPrefs();
        loadPlaylistsFromPrefs();
        loadPlayHistoryFromPrefs();
        loadPlayCountsFromPrefs();

        // Cache-first: load from cache, then fetch from Firestore if missing/expired
        List<Song> cached = onlineRepository.loadCachedSongs(application);
        if (!cached.isEmpty()) {
            allOnlineSongsLiveData.postValue(cached);
            emitPlayHistory(); // re-resolve history now that online songs are known
            emitHotTracks();
        }
        if (cached.isEmpty() || onlineRepository.isCacheExpired(application)) {
            AppLog.d(AppLog.REPO, "Cache missing or expired — fetching from Firestore");
            fetchOnlineSongsAndCache(application);
        } else {
            AppLog.d(AppLog.REPO, "Cache is fresh — skipping Firestore fetch");
        }
        
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
                        if (!isPlaying && exoPlayer != null) {
                            persistLastPosition(exoPlayer.getCurrentPosition());
                            progressTicks = 0;
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            if (isPlaying) {
                                if (!progressHandler.hasCallbacks(progressRunnable)) {
                                    progressHandler.post(progressRunnable);
                                }
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
                        List<Song> snapshot = snapshotPlaylist();
                        if (snapshot.isEmpty()) return;
                        int idx = exoPlayer.getCurrentMediaItemIndex();
                        if (idx >= 0 && idx < snapshot.size()) {
                            currentIndex = idx;
                            Song song = snapshot.get(idx);
                            currentSongLiveData.setValue(song);
                            // Apple Music behavior: every song that starts playing
                            // enters the persistent "Recently Played" history and
                            // bumps its "Hot Tracks" play count.
                            recordPlayHistory(song);
                            recordPlayCount(song);
                            persistLastIndex(currentIndex);
                            lyricsLiveData.setValue(null);
                            previousCachedLyrics = cachedLyrics;
                            previousCachedLyricsSongId = cachedLyricsSongId;
                            cachedLyrics = null;
                            cachedLyricsSongId = -1;
                            lyricsFetchGeneration++;
                            fetchLyricsForCurrentSong(song);
                            if (exoPlayer.getDuration() > 0) durationLiveData.postValue(exoPlayer.getDuration());
                        }
                    }
                    
                    @Override
                    public void onPlaybackStateChanged(int state) {
                        if (state == Player.STATE_READY) consecutivePlaybackErrors = 0;
                        if (exoPlayer != null && exoPlayer.getDuration() > 0)
                            durationLiveData.postValue(exoPlayer.getDuration());
                        if (state == Player.STATE_ENDED) {
                            // --- SLEEP TIMER: Handle repeat-one mode ---
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
                                return;
                            }

                            if (exoPlayer.getRepeatMode() == Player.REPEAT_MODE_ALL) return;

                            // Apple Music behavior: autoplay keeps the music going with a
                            // fresh shuffled batch from the library instead of stopping.
                            if (attemptAutoplay()) return;

                            isPlayingLiveData.postValue(false);
                            endOfQueueLiveData.postValue(true);
                            showEndDialogLiveData.postValue(true);
                        }
                    }

                    @Override
                    public void onTimelineChanged(Timeline timeline, int reason) {
                        if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return;
                        // Debounced drift check — VM-initiated bursts settle within 300ms;
                        // a persistent mismatch means an external controller edited the queue.
                        progressHandler.removeCallbacks(queueDriftCheckRunnable);
                        progressHandler.postDelayed(queueDriftCheckRunnable, 300);
                    }

                    @Override
                    public void onRepeatModeChanged(int repeatMode) {
                        // Keeps the in-app Repeat pill in sync when repeat is toggled
                        // from the system media notification (Android 13+ layout) or
                        // any other external controller.
                        repeatModeLiveData.postValue(repeatMode);
                    }

                    @Override
                    public void onPlayerError(PlaybackException error) {
                        AppLog.e(AppLog.PLAYER, "Playback error: " + error.getMessage());
                        isPlayingLiveData.postValue(false);
                        // Apple Music behavior: a broken/unavailable track never stalls
                        // playback — it is skipped and the queue keeps moving.
                        consecutivePlaybackErrors++;
                        if (exoPlayer != null && currentPlaylist != null
                                && !currentPlaylist.isEmpty()
                                && consecutivePlaybackErrors < MAX_CONSECUTIVE_ERRORS) {
                            AppLog.d(AppLog.PLAYER, "Skipping broken track ("
                                    + consecutivePlaybackErrors + "/" + MAX_CONSECUTIVE_ERRORS + ")");
                            exoPlayer.seekToNextMediaItem();
                            exoPlayer.prepare();
                            exoPlayer.play();
                        }
                    }
                });
                progressHandler.post(progressRunnable);
                // If the player already has media loaded (e.g. reopening from notification),
                // sync UI from the live player state instead of restoring from stale prefs.
                if (exoPlayer.getCurrentMediaItem() != null) {
                    syncUiFromPlayer();
                } else if (songsLiveData.getValue() != null && !songsLiveData.getValue().isEmpty()) {
                    // restoreLastSession self-publishes on the main thread; no
                    // trailing updateQueueUI() — publishing here raced the apply.
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

    /** Apple Music "Autoplay" — opt-in. When off, the queue simply ends (dialog). */
    public LiveData<Boolean> getIsAutoplayEnabled() { return isAutoplayEnabledLiveData; }
    public void setAutoplayEnabled(boolean isEnabled) {
        isAutoplayEnabledLiveData.setValue(isEnabled);
        executorService.execute(() -> {
            getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_AUTOPLAY_ENABLED, isEnabled).commit();
        });
    }
    
    public LiveData<Integer> getLibraryMode() { return libraryModeLiveData; }
    public void setLibraryMode(int mode) {
        boolean wasBlocked = Boolean.TRUE.equals(onlineSongBlockedLiveData.getValue());
        libraryModeLiveData.setValue(mode);

        if (mode == 0 && currentSongLiveData.getValue() != null) {
            String scheme = currentSongLiveData.getValue().getUri().getScheme();
            boolean isOnlineSong = "http".equals(scheme) || "https".equals(scheme);

            if (isOnlineSong) {
                wasPlayingBeforeBlock = exoPlayer != null && exoPlayer.isPlaying();
                if (exoPlayer != null) {
                    persistLastPosition(exoPlayer.getCurrentPosition());
                    exoPlayer.pause();
                }
                isPlayingLiveData.setValue(false);
                onlineSongBlockedLiveData.setValue(true);
            } else {
                onlineSongBlockedLiveData.setValue(false);
            }

            executorService.execute(() -> {
                boolean isBlocked = Boolean.TRUE.equals(onlineSongBlockedLiveData.getValue());
                getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putInt(KEY_LIBRARY_MODE, mode)
                        .putBoolean("is_online_blocked", isBlocked)
                        .apply();
            });
            new Handler(Looper.getMainLooper()).post(this::loadPlaylistsFromPrefs);
            return;
        }

        onlineSongBlockedLiveData.setValue(false);

        if (wasBlocked && wasPlayingBeforeBlock && mode != 0 && exoPlayer != null) {
            exoPlayer.play();
        }
        wasPlayingBeforeBlock = false;

        executorService.execute(() -> {
            boolean isBlocked = Boolean.TRUE.equals(onlineSongBlockedLiveData.getValue());
            getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_LIBRARY_MODE, mode)
                    .putBoolean("is_online_blocked", isBlocked)
                    .apply();

            new Handler(Looper.getMainLooper()).post(this::loadPlaylistsFromPrefs);
        });
    }
    
    public LiveData<String> getLyrics() { return lyricsLiveData; }
    public LiveData<Boolean> getIsLoading() { return isLoadingLiveData; }
    public LiveData<List<Song>> getAllOnlineSongs() { return allOnlineSongsLiveData; }
    public LiveData<Boolean> getIsRefreshingOnline() { return isRefreshingOnlineLiveData; }
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
    public LiveData<String>     getNowPlayingSource() { return nowPlayingSourceLiveData; }
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
        // Derive the Cloudinary cover URL from the audio URL when no art is stored —
        // the same transform the UI's AlbumArt uses — so the media notification
        // always shows artwork.
        Uri artworkUri = song.getAlbumArtUri();
        if (artworkUri == null && finalUrl.contains("cloudinary.com")) {
            artworkUri = Uri.parse(finalUrl
                    .replace("/video/upload/", "/image/upload/")
                    .replace("/songs/", "/covers/")
                    .replace(".mp3", ".jpg"));
        }
        MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder()
                .setTitle(song.getTitle())
                .setArtist(song.getArtist())
                .setDisplayTitle(song.getTitle());
        if (song.getAlbum() != null) metadataBuilder.setAlbumTitle(song.getAlbum());
        if (artworkUri != null) metadataBuilder.setArtworkUri(artworkUri);
        return new MediaItem.Builder()
                .setUri(Uri.parse(finalUrl))
                .setMediaId(String.valueOf(song.getId()))
                .setMediaMetadata(metadataBuilder.build())
                .build();
    }
    
    private List<MediaItem> buildMediaItems() {
        List<MediaItem> items = new ArrayList<>();
        for (Song s : currentPlaylist) items.add(createMediaItem(s));
        return items;
    }
    
    /** Snapshot of the live queue — iterate the returned list, never currentPlaylist directly. */
    private List<Song> snapshotPlaylist() {
        synchronized (this) {
            return currentPlaylist == null ? new ArrayList<Song>() : new ArrayList<>(currentPlaylist);
        }
    }

    /**
     * Apple Music behavior: when the queue finishes, keep the music going with a
     * fresh shuffled batch from the library (recently played excluded first).
     * Returns true if an autoplay batch was queued.
     */
    private boolean attemptAutoplay() {
        // Opt-in Apple Music autoplay: disabled by default so the queue ends
        // cleanly (end-of-queue dialog) instead of being silently refilled
        // with random songs the user never asked for.
        if (!Boolean.TRUE.equals(isAutoplayEnabledLiveData.getValue())) return false;
        if (exoPlayer == null || !isDataSaverEligibleLibraryAvailable()) return false;
        List<Song> library = new ArrayList<>();
        List<Song> local = songsLiveData.getValue();
        if (local != null) library.addAll(local);
        List<Song> online = allOnlineSongsLiveData.getValue();
        if (online != null) {
            Set<Long> seen = new LinkedHashSet<>();
            for (Song s : library) seen.add(s.getId());
            for (Song s : online) {
                if (seen.add(s.getId())) library.add(s);
            }
        }
        if (library.size() <= 1) return false;

        // Exclude what was just played so autoplay feels fresh.
        Set<Long> played = new LinkedHashSet<>();
        List<Song> snapshot = snapshotPlaylist();
        for (Song s : snapshot) played.add(s.getId());
        List<Song> pool = new ArrayList<>();
        for (Song s : library) {
            if (!played.contains(s.getId())) pool.add(s);
        }
        if (pool.size() < 10) {
            // Small library: allow repeats but keep the shuffle random.
            pool = new ArrayList<>(library);
        }
        if (pool.isEmpty()) return false;
        Collections.shuffle(pool);
        int count = Math.min(AUTOPLAY_POOL_SIZE, pool.size());
        List<Song> autoplay = new ArrayList<>(pool.subList(0, count));

        List<MediaItem> items = new ArrayList<>();
        for (Song s : autoplay) items.add(createMediaItem(s));
        synchronized (this) {
            currentPlaylist.addAll(autoplay);
        }
        exoPlayer.addMediaItems(items);
        updateQueueUI();
        AppLog.d(AppLog.PLAYER, "Autoplay added " + count + " songs to the queue");
        return true;
    }

    private boolean isDataSaverEligibleLibraryAvailable() {
        List<Song> local = songsLiveData.getValue();
        List<Song> online = allOnlineSongsLiveData.getValue();
        return (local != null && !local.isEmpty()) || (online != null && !online.isEmpty());
    }

    private void updateQueueUI() {
        final List<Song> snapshot;
        synchronized (this) {
            if (currentPlaylist == null) return;
            snapshot = new ArrayList<>(currentPlaylist);
        }
        queueLiveData.postValue(snapshot);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < snapshot.size(); i++) {
            sb.append(snapshot.get(i).getId());
            if (i < snapshot.size() - 1) sb.append(",");
        }
        getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_QUEUE_IDS, sb.toString()).apply();
    }
    
    private void persistLastPosition(long position) {
        try {
            executorService.execute(() -> {
                getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putLong(KEY_LAST_POSITION, position).commit();
            });
        } catch (RejectedExecutionException ignored) {
            // ViewModel clearing — executor already shut down, safe to skip the write.
        }
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
        String uriStr;
        synchronized (this) {
            List<Song> snapshot = snapshotPlaylist();
            if (snapshot.isEmpty() || index < 0 || index >= snapshot.size()) return;
            uriStr = snapshot.get(index).getUri().toString();
        }
        final String uri = uriStr;
        executorService.execute(() -> {
            getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putInt(KEY_LAST_INDEX, index).putString(KEY_LAST_URI, uri).commit();
        });
    }

    // ── Play history ("Recently Played") ───────────────────────────────────

    /** Apple Music behavior: every played song moves to the front of the history. */
    private void recordPlayHistory(Song song) {
        if (song == null) return;
        Long id = song.getId();
        playHistoryIds.remove(id); // move-to-front dedupe (replays don't duplicate)
        playHistoryIds.addFirst(id);
        while (playHistoryIds.size() > PLAY_HISTORY_MAX) {
            playHistoryIds.removeLast();
        }
        persistPlayHistory();
        emitPlayHistory();
    }

    private void loadPlayHistoryFromPrefs() {
        SharedPreferences prefs = getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String saved = prefs.getString(KEY_PLAY_HISTORY, "");
        playHistoryIds.clear();
        if (saved != null && !saved.isEmpty()) {
            for (String idStr : saved.split(",")) {
                try { playHistoryIds.addLast(Long.parseLong(idStr)); } catch (NumberFormatException ignored) {}
            }
        }
        emitPlayHistory();
    }

    private void persistPlayHistory() {
        StringBuilder sb = new StringBuilder();
        for (Long id : playHistoryIds) {
            if (sb.length() > 0) sb.append(",");
            sb.append(id);
        }
        final String encoded = sb.toString();
        try {
            executorService.execute(() -> {
                getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putString(KEY_PLAY_HISTORY, encoded).apply();
            });
        } catch (RejectedExecutionException ignored) {
            // ViewModel clearing — executor already shut down.
        }
    }

    /**
     * Re-emit the resolved history — called whenever the underlying queue or
     * library changes so removed/renamed songs disappear from "Recently Played".
     */
    private void rebuildPlayHistoryFromQueue(List<Song> orderedQueue) {
        if (orderedQueue == null) return;
        // Keep entries still present in the given queue, preserving their order.
        Set<Long> present = new LinkedHashSet<>();
        for (Song s : orderedQueue) present.add(s.getId());
        playHistoryIds.removeIf(id -> !present.contains(id));
        persistPlayHistory();
        emitPlayHistory();
    }

    /** Resolve stored ids → Song objects against the combined library. */
    private List<Song> resolvePlayHistory() {
        List<Song> out = new ArrayList<>();
        if (playHistoryIds.isEmpty()) return out;
        Map<Long, Song> byId = new LinkedHashMap<>();
        List<Song> online = allOnlineSongsLiveData.getValue();
        if (online != null) {
            for (Song s : online) byId.put(s.getId(), s);
        }
        List<Song> local = songsLiveData.getValue();
        if (local != null) {
            for (Song s : local) byId.put(s.getId(), s); // local wins on id collision
        }
        for (Long id : playHistoryIds) {
            Song s = byId.get(id);
            if (s != null) out.add(s);
        }
        return out;
    }

    private void emitPlayHistory() {
        playHistoryLiveData.postValue(resolvePlayHistory());
    }

    /** Apple Music "Recently Played" — persistent across app restarts. */
    public LiveData<List<Song>> getPlayHistory() { return playHistoryLiveData; }

    // ── Play counts ("Hot Tracks") ─────────────────────────────────────────

    /** Apple Music behavior: every playback increments the song's play count. */
    private void recordPlayCount(Song song) {
        if (song == null) return;
        Long id = song.getId();
        playCounts.merge(id, 1, Integer::sum);
        persistPlayCounts();
        emitHotTracks();
    }

    private void loadPlayCountsFromPrefs() {
        SharedPreferences prefs = getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> saved = prefs.getStringSet(KEY_PLAY_COUNTS, null);
        playCounts.clear();
        if (saved != null) {
            for (String entry : saved) {
                int sep = entry.indexOf(':');
                if (sep > 0) {
                    try {
                        playCounts.put(Long.parseLong(entry.substring(0, sep)),
                                Integer.parseInt(entry.substring(sep + 1)));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        emitHotTracks();
    }

    private void persistPlayCounts() {
        Set<String> encoded = new LinkedHashSet<>();
        for (Map.Entry<Long, Integer> e : playCounts.entrySet()) {
            encoded.add(e.getKey() + ":" + e.getValue());
        }
        try {
            executorService.execute(() -> {
                getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putStringSet(KEY_PLAY_COUNTS, encoded).apply();
            });
        } catch (RejectedExecutionException ignored) {
            // ViewModel clearing — executor already shut down.
        }
    }

    /** Resolve stored ids → Song objects, ordered by play count descending. */
    private List<Song> resolveHotTracks() {
        if (playCounts.isEmpty()) return new ArrayList<>();
        Map<Long, Song> byId = new LinkedHashMap<>();
        List<Song> online = allOnlineSongsLiveData.getValue();
        if (online != null) {
            for (Song s : online) byId.put(s.getId(), s);
        }
        List<Song> local = songsLiveData.getValue();
        if (local != null) {
            for (Song s : local) byId.put(s.getId(), s); // local wins on id collision
        }
        List<Map.Entry<Long, Integer>> ranked = new ArrayList<>(playCounts.entrySet());
        ranked.sort((a, b) -> b.getValue() - a.getValue());
        List<Song> out = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : ranked) {
            Song s = byId.get(e.getKey());
            if (s != null) {
                out.add(s);
                if (out.size() >= HOT_TRACKS_MAX) break;
            }
        }
        return out;
    }

    private void emitHotTracks() {
        hotTracksLiveData.postValue(resolveHotTracks());
    }

    /** Apple Music "Hot Tracks" — your most-played songs, persistent across restarts. */
    public LiveData<List<Song>> getHotTracks() { return hotTracksLiveData; }

    // ── "Made for You" personalized mixes ──────────────────────────────────

    /** Apple Music behavior: auto-generated mixes, refreshed weekly (Favorites Mix) and per session (Replay). */
    public void generatePersonalizedMixes() {
        List<Song> favs = new ArrayList<>(favoritesMap.values());
        List<Song> hot = resolveHotTracks();
        if (favs.isEmpty() && hot.size() < 5) return;

        Map<String, List<Long>> updated = new LinkedHashMap<>(
                customPlaylistsLiveData.getValue() != null ? customPlaylistsLiveData.getValue() : new LinkedHashMap<>());
        Set<String> autos = new LinkedHashSet<>(autoGeneratedPlaylistsLiveData.getValue() != null
                ? autoGeneratedPlaylistsLiveData.getValue() : new LinkedHashSet<>());
        boolean changed = false;

        // Favorites Mix — favorites first in weekly-rotated order, filled with most-played.
        java.util.Calendar cal = java.util.Calendar.getInstance();
        java.util.Random weekly = new java.util.Random(cal.get(java.util.Calendar.YEAR) * 100 + cal.get(java.util.Calendar.WEEK_OF_YEAR));
        List<Song> favShuffled = new ArrayList<>(favs);
        Collections.shuffle(favShuffled, weekly);
        Set<Long> seen = new LinkedHashSet<>();
        List<Long> mixIds = new ArrayList<>();
        for (Song s : favShuffled) { if (mixIds.size() >= 25) break; if (seen.add(s.getId())) mixIds.add(s.getId()); }
        for (Song s : hot) { if (mixIds.size() >= 25) break; if (seen.add(s.getId())) mixIds.add(s.getId()); }
        if (!mixIds.isEmpty() && !mixIds.equals(updated.get("Favorites Mix"))) {
            updated.put("Favorites Mix", mixIds);
            autos.add("Favorites Mix");
            changed = true;
        }

        // Replay — your top 25 most-played songs.
        List<Long> replayIds = new ArrayList<>();
        for (Song s : hot) { replayIds.add(s.getId()); if (replayIds.size() >= 25) break; }
        if (!replayIds.isEmpty() && !replayIds.equals(updated.get("Replay"))) {
            updated.put("Replay", replayIds);
            autos.add("Replay");
            changed = true;
        }

        if (changed) {
            customPlaylistsLiveData.postValue(updated);
            autoGeneratedPlaylistsLiveData.postValue(autos);
            savePlaylistsToPrefs(updated);
            final Set<String> autosFinal = autos;
            executorService.execute(() -> {
                getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putStringSet(KEY_AUTO_PLAYLISTS, autosFinal).apply();
            });
        }
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

    /**
     * Re-read favorites from prefs (shared storage, same process as the media
     * session). Called when the notification's heart action toggles a song so
     * in-app UI stays authoritative.
     */
    public void refreshFavoritesFromPrefs() {
        loadFavoritesFromPrefs();
        List<Song> songs = songsLiveData.getValue();
        if (songs != null && !songs.isEmpty()) syncFavoritesWithSongs(songs);
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
        persistFavorites(ids);
    }

    /**
     * Add/remove many songs from favorites in one pass. Bulk callers (Library
     * "select all" / "remove all") previously looped over [toggleFavorite],
     * which fired one executor task per song — N blocking prefs commits and N
     * notification layout refreshes. This collapses that to a single write.
     * Already-in-target-state songs are skipped, so no-op when nothing changes.
     */
    @SuppressLint("ApplySharedPref")
    public void setFavoriteBatch(List<Song> songs, boolean favorite) {
        if (songs == null || songs.isEmpty()) return;
        Set<Long> ids = new LinkedHashSet<>(favoriteIdsLiveData.getValue() != null ? favoriteIdsLiveData.getValue() : new LinkedHashSet<>());
        boolean changed = false;
        for (Song song : songs) {
            if (favorite) {
                if (ids.add(song.getId())) {
                    favoritesMap.put(song.getId(), song);
                    changed = true;
                }
            } else {
                if (ids.remove(song.getId())) {
                    favoritesMap.remove(song.getId());
                    changed = true;
                }
            }
        }
        if (!changed) return;
        favoriteIdsLiveData.setValue(ids);
        persistFavorites(ids);
    }

    private void persistFavorites(Set<Long> ids) {
        executorService.execute(() -> {
            Set<String> stringSet = new LinkedHashSet<>();
            for (Long id : ids) stringSet.add(String.valueOf(id));
            getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putStringSet(KEY_FAV_IDS, stringSet).commit();
            // Android 13+ SystemUI heart button reads prefs — refresh its icon.
            com.nandu.mymusic.service.MusicPlaybackService.notifyMediaStateChange();
        });
    }
    
    // ── Playback / Queue / Custom Playlists / Deletion logic ───────────────────
    // 3. Apply shuffle behavior gracefully when starting a specific song
    public void playSong(Song song, List<Song> playlist) {
        playSong(song, playlist, null);
    }

    /** Apple Music "Playing From {source}": records where the queue came from. */
    public void playSong(Song song, List<Song> playlist, String source) {
        if (exoPlayer == null) return;
        userInteractedWithPlayback = true;
        cancelSleepFade(); // starting fresh playback cancels any sleep fade
        nowPlayingSourceLiveData.setValue(source != null ? source
                : (song.getAlbum() != null ? song.getAlbum() : "Library"));

        if (song.getUri() != null) {
            String scheme = song.getUri().getScheme();
            boolean isLocalSong = scheme == null || (!"http".equals(scheme) && !"https".equals(scheme));
            if (isLocalSong && Boolean.TRUE.equals(onlineSongBlockedLiveData.getValue())) {
                onlineSongBlockedLiveData.setValue(false);
                executorService.execute(() ->
                    getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("is_online_blocked", false)
                            .apply()
                );
            }
        }

        List<Song> queue = new ArrayList<>(playlist);
        int index = queue.indexOf(song);
        if (index < 0) index = 0;
        synchronized (this) {
            currentPlaylist = queue;
            currentIndex    = index;
            // ✅ FIXED: If shuffle is active, re-shuffle the upcoming songs in the queue
            if (Boolean.TRUE.equals(shuffleModeLiveData.getValue())) {
                List<Song> upcoming = new ArrayList<>(queue.subList(index + 1, queue.size()));
                Collections.shuffle(upcoming);
                for (int i = 0; i < upcoming.size(); i++) {
                    queue.set(index + 1 + i, upcoming.get(i));
                }
            }
        }

        updateQueueUI();
        List<MediaItem> items = new ArrayList<>();
        for (Song s : queue) items.add(createMediaItem(s));
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
        // Persist the "Playing From" label so it survives session restore.
        executorService.execute(() -> {
            String src = nowPlayingSourceLiveData.getValue();
            getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putString(KEY_NP_SOURCE, src).apply();
        });
    }

    public void togglePlayPause() {
        userInteractedWithPlayback = true;
        // 🐛 FIX: Prevent hardware media buttons/headset clicks from bypassing the block
        if (Boolean.TRUE.equals(onlineSongBlockedLiveData.getValue())) return;

        if (exoPlayer == null) return;
        // User resumed during a sleep-timer fade — cancel it and restore volume.
        cancelSleepFade();
        if (exoPlayer.isPlaying()) exoPlayer.pause();
        else {
            // After a playback error the player sits in STATE_IDLE; play() alone is a no-op.
            if (exoPlayer.getPlaybackState() == Player.STATE_IDLE) exoPlayer.prepare();
            exoPlayer.play();
        }
    }
    
    public void playNextFromMiniPlayer() { if (Boolean.TRUE.equals(onlineSongBlockedLiveData.getValue())) { restoreFromCacheAndPlayNext(); } else { advanceToNext(); } }
    public void playNextFromFullPlayer()  { cancelSleepFade(); advanceToNext(); }
    
    private void advanceToNext() {
        List<Song> snapshot = snapshotPlaylist();
        if (exoPlayer == null || snapshot.isEmpty()) return;
        // Always use ExoPlayer's actual index as source of truth for rapid clicks
        currentIndex = exoPlayer.getCurrentMediaItemIndex();
        int nextIndex = currentIndex + 1;
        if (nextIndex >= snapshot.size()) {
            if (exoPlayer.getRepeatMode() == Player.REPEAT_MODE_ALL) nextIndex = 0;
            else {
                exoPlayer.pause();
                isPlayingLiveData.postValue(false);
                endOfQueueLiveData.postValue(true);
                showEndDialogLiveData.postValue(true);
                return;
            }
        }
        Song next = snapshot.get(nextIndex);
        currentIndex = nextIndex;
        currentSongLiveData.setValue(next);
        progressLiveData.setValue(0L);
        exoPlayer.seekToDefaultPosition(nextIndex);
        exoPlayer.play();
        persistLastIndex(currentIndex);
    }
    
    public void playPrevious() {
        if (exoPlayer == null) return;
        cancelSleepFade(); // skipping cancels an in-progress fade-out
        if (Boolean.TRUE.equals(onlineSongBlockedLiveData.getValue())) {
            restoreFromCacheAndPlayPrevious();
            return;
        }
        if (currentPlaylist == null) return;
        List<Song> snapshot = snapshotPlaylist();
        if (snapshot.isEmpty()) return;
        if (exoPlayer.getCurrentPosition() > 3000) {
            exoPlayer.seekTo(0);
            progressLiveData.postValue(0L);
            return;
        }
        // Always use ExoPlayer's actual index as source of truth for rapid clicks
        currentIndex = exoPlayer.getCurrentMediaItemIndex();
        int prevIndex = currentIndex - 1;
        if (prevIndex < 0) prevIndex = 0;
        Song prev = snapshot.get(prevIndex);
        currentIndex = prevIndex;
        currentSongLiveData.setValue(prev);
        progressLiveData.setValue(0L);
        exoPlayer.seekToDefaultPosition(prevIndex);
        exoPlayer.play();
        persistLastIndex(currentIndex);
    }
    
    private void restoreFromCacheAndPlayPrevious() {
        SharedPreferences prefs = getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String queueStr = prefs.getString(KEY_QUEUE_IDS, "");
        if (queueStr.isEmpty()) return;
        
        List<Song> allSongs = new ArrayList<>();
        List<Song> local = songsLiveData.getValue();
        if (local != null) allSongs.addAll(local);
        List<Song> online = allOnlineSongsLiveData.getValue();
        if (online != null) {
            Set<Long> ids = new LinkedHashSet<>();
            for (Song s : allSongs) ids.add(s.getId());
            for (Song s : online) { if (!ids.contains(s.getId())) allSongs.add(s); }
        }
        if (allSongs.isEmpty()) return;
        
        Map<Long, Song> songMap = new LinkedHashMap<>();
        for (Song s : allSongs) songMap.put(s.getId(), s);
        List<Song> restored = new ArrayList<>();
        for (String idStr : queueStr.split(",")) {
            try {
                long id = Long.parseLong(idStr);
                if (songMap.containsKey(id)) restored.add(songMap.get(id));
            } catch (NumberFormatException ignored) {}
        }
        if (restored.isEmpty()) return;

        Song current = currentSongLiveData.getValue();
        int curIdx = 0;
        if (current != null) {
            for (int i = 0; i < restored.size(); i++) {
                if (restored.get(i).getId() == current.getId()) { curIdx = i; break; }
            }
        }
        int prevIdx = curIdx - 1;
        if (prevIdx < 0) prevIdx = 0;

        synchronized (this) {
            currentPlaylist = restored;
            currentIndex = prevIdx;
        }
        onlineSongBlockedLiveData.setValue(false);

        List<MediaItem> items = new ArrayList<>();
        for (Song s : restored) items.add(createMediaItem(s));
        long savedPos = 0L;
        exoPlayer.setMediaItems(items, prevIdx, savedPos);
        exoPlayer.prepare();
        exoPlayer.play();
        currentSongLiveData.setValue(restored.get(prevIdx));
        progressLiveData.setValue(0L);
        updateQueueUI();
        persistLastIndex(prevIdx);
    }

    private void restoreFromCacheAndPlayNext() {
        SharedPreferences prefs = getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String queueStr = prefs.getString(KEY_QUEUE_IDS, "");
        if (queueStr.isEmpty()) return;

        List<Song> allSongs = new ArrayList<>();
        List<Song> local = songsLiveData.getValue();
        if (local != null) allSongs.addAll(local);
        List<Song> online = allOnlineSongsLiveData.getValue();
        if (online != null) {
            Set<Long> ids = new LinkedHashSet<>();
            for (Song s : allSongs) ids.add(s.getId());
            for (Song s : online) { if (!ids.contains(s.getId())) allSongs.add(s); }
        }
        if (allSongs.isEmpty()) return;

        Map<Long, Song> songMap = new LinkedHashMap<>();
        for (Song s : allSongs) songMap.put(s.getId(), s);
        List<Song> restored = new ArrayList<>();
        for (String idStr : queueStr.split(",")) {
            try {
                long id = Long.parseLong(idStr);
                if (songMap.containsKey(id)) restored.add(songMap.get(id));
            } catch (NumberFormatException ignored) {}
        }
        if (restored.isEmpty()) return;

        Song current = currentSongLiveData.getValue();
        int curIdx = 0;
        if (current != null) {
            for (int i = 0; i < restored.size(); i++) {
                if (restored.get(i).getId() == current.getId()) { curIdx = i; break; }
            }
        }
        int nextIdx = curIdx + 1;
        if (nextIdx >= restored.size()) return;

        synchronized (this) {
            currentPlaylist = restored;
            currentIndex = nextIdx;
        }
        onlineSongBlockedLiveData.setValue(false);
        
        List<MediaItem> items = new ArrayList<>();
        for (Song s : restored) items.add(createMediaItem(s));
        exoPlayer.setMediaItems(items, nextIdx, 0L);
        exoPlayer.prepare();
        exoPlayer.play();
        currentSongLiveData.setValue(restored.get(nextIdx));
        progressLiveData.setValue(0L);
        updateQueueUI();
        persistLastIndex(nextIdx);
    }
    
    public void playSongFromQueue(Song song) {
        if (exoPlayer == null) return;
        List<Song> snapshot = snapshotPlaylist();
        if (snapshot.isEmpty()) return;
        userInteractedWithPlayback = true;
        cancelSleepFade(); // skipping cancels an in-progress fade-out
        int index = snapshot.indexOf(song);
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
        userInteractedWithPlayback = true;
        synchronized (this) {
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
    }
    
    public void removeSongsFromUpcoming(Set<Song> songsToRemove) {
        if (currentPlaylist == null || exoPlayer == null || songsToRemove == null || songsToRemove.isEmpty()) return;
        userInteractedWithPlayback = true;
        synchronized (this) {
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
    }
    
    // ✅ FIXED: Takes absolute indices allowing drags across the entire unified queue
    public void moveSongInUpcoming(int fromAbsolute, int toAbsolute) {
        if (currentPlaylist == null || exoPlayer == null) return;
        userInteractedWithPlayback = true;
        synchronized (this) {
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
    }
    
    public void setPlayNext(Song song) {
        if (exoPlayer == null || currentPlaylist == null || currentPlaylist.isEmpty()) {
            playSong(song, Collections.singletonList(song));
            return;
        }
        userInteractedWithPlayback = true;
        synchronized (this) {
            if (currentIndex < 0 || currentIndex >= currentPlaylist.size()) return;
            if (currentPlaylist.get(currentIndex).getId() == song.getId()) return;
            // If the song is already in the queue, MOVE it to play-next instead of duplicating it.
            // Removing it only from the list (not from ExoPlayer) would desync the two timelines.
            int existingIdx = -1;
            for (int i = 0; i < currentPlaylist.size(); i++) {
                if (currentPlaylist.get(i).getId() == song.getId()) { existingIdx = i; break; }
            }
            if (existingIdx >= 0) {
                currentPlaylist.remove(existingIdx);
                exoPlayer.removeMediaItem(existingIdx);
                if (existingIdx < currentIndex) currentIndex--;
            }
            int insertIndex = currentIndex + 1;
            currentPlaylist.add(insertIndex, song);
            // Use addMediaItem to insert without rebuilding the playlist — avoids rebuffering/pause
            exoPlayer.addMediaItem(insertIndex, createMediaItem(song));
            updateQueueUI();
        }
    }
    
    public void addToQueue(Song song) {
        if (exoPlayer == null || currentPlaylist == null || currentPlaylist.isEmpty()) {
            playSong(song, Collections.singletonList(song));
            return;
        }
        userInteractedWithPlayback = true;
        synchronized (this) {
            if (currentPlaylist.stream().anyMatch(s -> s.getId() == song.getId())) return;
            currentPlaylist.add(song);
            exoPlayer.addMediaItem(createMediaItem(song));
            updateQueueUI();
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
        
        executorService.execute(() -> {
            getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putInt(KEY_REPEAT_MODE, next).commit();
        });
    }
    
    public void toggleShuffle() {
        boolean cur  = Boolean.TRUE.equals(shuffleModeLiveData.getValue());
        boolean next = !cur;
        if (exoPlayer == null || currentPlaylist == null || currentPlaylist.isEmpty()) return;
        if (next) {
            originalPlaylistBeforeShuffle = new ArrayList<>(currentPlaylist);
            shuffleUpcomingQueue();
        } else {
            unshuffleUpcomingQueue();
        }
        exoPlayer.setShuffleModeEnabled(false);
        shuffleModeLiveData.setValue(next);
        
        executorService.execute(() -> {
            getApplication().getSharedPreferences(PREFS_NAME,Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_SHUFFLE_MODE,next).commit();
        });
    }
    
    private void shuffleUpcomingQueue() {
        if (currentIndex < 0) return;
        if (currentPlaylist == null || currentPlaylist.size() <= currentIndex + 1) return;
        List<Song> upcoming = new ArrayList<>(currentPlaylist.subList(currentIndex + 1, currentPlaylist.size()));
        Collections.shuffle(upcoming);
        List<MediaItem> newItems = new ArrayList<>();
        for (Song s : upcoming) newItems.add(createMediaItem(s));
        synchronized (this) {
            for (int i = 0; i < upcoming.size(); i++) currentPlaylist.set(currentIndex + 1 + i, upcoming.get(i));
            exoPlayer.removeMediaItems(currentIndex + 1, currentPlaylist.size());
            exoPlayer.addMediaItems(currentIndex + 1, newItems);
        }
        updateQueueUI();
    }
    
    private void unshuffleUpcomingQueue() {
        if (currentIndex < 0) return;
        if (currentPlaylist == null || currentPlaylist.size() <= currentIndex + 1) return;
        if (originalPlaylistBeforeShuffle == null || originalPlaylistBeforeShuffle.isEmpty()) return;
        
        List<Song> newQueue = new ArrayList<>(originalPlaylistBeforeShuffle.subList(0, currentIndex + 1));
        List<Song> upcomingOriginal = originalPlaylistBeforeShuffle.subList(currentIndex + 1, originalPlaylistBeforeShuffle.size());
        newQueue.addAll(upcomingOriginal);
        int newSize = newQueue.size();
        synchronized (this) {
            currentPlaylist = newQueue;
            exoPlayer.removeMediaItems(currentIndex + 1, newSize);
        }
        List<MediaItem> newItems = new ArrayList<>();
        for (Song s : upcomingOriginal) newItems.add(createMediaItem(s));
        exoPlayer.addMediaItems(currentIndex + 1, newItems);
        originalPlaylistBeforeShuffle = null;
        updateQueueUI();
    }
    
    public void playAllShuffled(List<Song> library) {
        playAllShuffled(library, null);
    }

    public void playAllShuffled(List<Song> library, String source) {
        if (library == null || library.isEmpty()) return;
        if (exoPlayer == null) return;
        showEndDialogLiveData.setValue(false);
        endOfQueueLiveData.setValue(false);
        nowPlayingSourceLiveData.setValue(source != null ? source : "Library");
        List<Song> shuffled = new ArrayList<>(library);
        Collections.shuffle(shuffled);
        userInteractedWithPlayback = true;
        synchronized (this) {
            currentPlaylist = shuffled;
            currentIndex    = 0;
        }
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
        playShuffled(playlist, null);
    }

    public void playShuffled(List<Song> playlist, String source) {
        if (playlist == null || playlist.isEmpty()) return;
        if (exoPlayer == null) return;
        nowPlayingSourceLiveData.setValue(source != null ? source : "Library");
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
        
        userInteractedWithPlayback = true;
        synchronized (this) {
            currentIndex    = 0;
            currentPlaylist = shuffled;
        }
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
        // 1. Check if this is an auto-generated language playlist
        Set<String> autoPlaylists = autoGeneratedPlaylistsLiveData.getValue();
        if (autoPlaylists != null && autoPlaylists.contains(playlistName)) {
            // Extract the expected language (e.g., "English" from "English Songs")
            String expectedLanguage = playlistName.replace(" Songs", "").trim();
            
            // Reject the addition if the song's language doesn't match
            if (song.getLanguage() == null || !song.getLanguage().equalsIgnoreCase(expectedLanguage)) {
                AppLog.e(AppLog.REPO, "Blocked adding " + song.getLanguage() + " song to " + playlistName);
                return;
            }
        }
        
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
        // 1. Check if this is an auto-generated language playlist
        Set<String> autoPlaylists = autoGeneratedPlaylistsLiveData.getValue();
        String expectedLanguage = null;
        boolean isAutoGenerated = autoPlaylists != null && autoPlaylists.contains(playlistName);
        
        if (isAutoGenerated) {
            expectedLanguage = playlistName.replace(" Songs", "").trim();
        }
        
        Map<String, List<Long>> current = customPlaylistsLiveData.getValue();
        if (current != null && current.containsKey(playlistName)) {
            Map<String, List<Long>> newPlaylists = new LinkedHashMap<>(current);
            List<Long> newSongs = new ArrayList<>(newPlaylists.get(playlistName));
            boolean changed = false;
            for (Song song : songsToAdd) {
                // 2. Skip songs that don't match the required language
                if (isAutoGenerated && (song.getLanguage() == null || !song.getLanguage().equalsIgnoreCase(expectedLanguage))) {
                    continue;
                }
                
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
    
    //   NEW: Cross-reference Local and Online songs to generate Local Language Playlists
    private void matchAndGenerateLocalPlaylists(List<Song> localSongs, List<Song> onlineSongs) {
        if (localSongs == null || localSongs.isEmpty() || onlineSongs == null || onlineSongs.isEmpty()) return;
        
        Set<String> enabledLangs = getEnabledLanguages();
        enabledLangs.add("Telugu");
        enabledLangs.add("English");
        
        Map<String, List<Long>> localLanguagePlaylists = new LinkedHashMap<>();
        
        // 1. Find all matching local songs and group them by language
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
        
        // 2. Force save these playlists specifically to the Local Library (Mode 0)
        SharedPreferences prefs = getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String scopedKey = KEY_PLAYLISTS + "_0";
        
        Set<String> existingPlaylists = new java.util.HashSet<>(prefs.getStringSet(scopedKey, new java.util.HashSet<>()));
        SharedPreferences.Editor editor = prefs.edit();
        
        Set<String> autoGeneratedSet = new LinkedHashSet<>(prefs.getStringSet(KEY_AUTO_PLAYLISTS, new LinkedHashSet<>()));
        
        // 3. Smart Sync: Read existing to preserve order, remove bad ones, append new ones
        for (Map.Entry<String, List<Long>> entry : localLanguagePlaylists.entrySet()) {
            String pName = entry.getKey();
            List<Long> correctIds = entry.getValue();
            
            existingPlaylists.add(pName);
            autoGeneratedSet.add(pName);
            
            String existingIdsStr = prefs.getString("playlist_" + pName + scopedKey, "");
            List<Long> syncedIds = new ArrayList<>();
            
            if (!existingIdsStr.isEmpty()) {
                for (String idStr : existingIdsStr.split(",")) {
                    try {
                        long id = Long.parseLong(idStr);
                        // Step A: Only keep the song if it's actually in the correct metadata list
                        if (correctIds.contains(id)) {
                            syncedIds.add(id);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
            
            // Step B: Add new matching local songs that aren't in the playlist yet
            for (Long id : correctIds) {
                if (!syncedIds.contains(id)) {
                    syncedIds.add(id);
                }
            }
            
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < syncedIds.size(); i++) {
                sb.append(syncedIds.get(i));
                if (i < syncedIds.size() - 1) sb.append(",");
            }
            editor.putString("playlist_" + pName + scopedKey, sb.toString());
        }
        
        editor.putStringSet(scopedKey, existingPlaylists);
        editor.putStringSet(KEY_AUTO_PLAYLISTS, autoGeneratedSet);
        editor.apply();
        
        // 4. Refresh LiveData if the user is currently looking at the Local Library
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
        
        // 1. Group the CORRECT song IDs by language based purely on metadata
        Map<String, List<Long>> correctSongsByLanguage = new LinkedHashMap<>();
        for (Song song : onlineSongs) {
            String lang = song.getLanguage();
            if (lang != null && enabledLangs.contains(lang)) {
                String playlistName = lang + " Songs";
                if (!correctSongsByLanguage.containsKey(playlistName)) {
                    correctSongsByLanguage.put(playlistName, new ArrayList<>());
                }
                correctSongsByLanguage.get(playlistName).add(song.getId());
            }
        }
        
        Map<String, List<Long>> currentPlaylists = customPlaylistsLiveData.getValue();
        if (currentPlaylists == null) currentPlaylists = new LinkedHashMap<>();
        Map<String, List<Long>> newPlaylists = new LinkedHashMap<>(currentPlaylists);
        Set<String> autoGeneratedSet = new LinkedHashSet<>(autoGeneratedPlaylistsLiveData.getValue() != null ? autoGeneratedPlaylistsLiveData.getValue() : new LinkedHashSet<>());
        
        // 2. Smart Sync: Add missing, remove incorrect, preserve order
        for (Map.Entry<String, List<Long>> entry : correctSongsByLanguage.entrySet()) {
            String playlistName = entry.getKey();
            List<Long> correctIds = entry.getValue();
            
            // Get the existing list to maintain the order of songs already there
            List<Long> syncedIds = new ArrayList<>(newPlaylists.getOrDefault(playlistName, new ArrayList<>()));
            
            // Step A: Remove songs that shouldn't be here (e.g., manual mistakes)
            syncedIds.removeIf(id -> !correctIds.contains(id));
            
            // Step B: Add new songs that belong here but are missing
            for (Long id : correctIds) {
                if (!syncedIds.contains(id)) {
                    syncedIds.add(id);
                }
            }
            
            newPlaylists.put(playlistName, syncedIds);
            autoGeneratedSet.add(playlistName);
        }
        
        autoGeneratedPlaylistsLiveData.postValue(autoGeneratedSet);
        customPlaylistsLiveData.setValue(newPlaylists);
        savePlaylistsToPrefs(newPlaylists);
        
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
        userInteractedWithPlayback = true;
        synchronized (this) {
            Set<Long> existingIds = new LinkedHashSet<>();
            for (Song s : currentPlaylist) existingIds.add(s.getId());
            for (Song song : songs) {
                if (!existingIds.contains(song.getId())) {
                    currentPlaylist.add(song);
                    existingIds.add(song.getId());
                    exoPlayer.addMediaItem(createMediaItem(song));
                }
            }
            updateQueueUI();
        }
    }
    
    public void clearQueue() {
        if (exoPlayer == null || currentPlaylist == null || currentPlaylist.isEmpty()) return;
        if (currentIndex < 0 || currentIndex >= currentPlaylist.size()) return;
        userInteractedWithPlayback = true;
        Song current = currentPlaylist.get(currentIndex);
        // Remove every item except the one currently playing, WITHOUT rebuilding the
        // playlist — so the current song keeps playing uninterrupted (no rebuffer/pause).
        exoPlayer.removeMediaItems(0, currentIndex);
        exoPlayer.removeMediaItems(1, exoPlayer.getMediaItemCount());
        synchronized (this) {
            currentPlaylist = new ArrayList<>(Collections.singletonList(current));
            currentIndex = 0;
            originalPlaylistBeforeShuffle = null;
        }
        updateQueueUI();
        persistLastIndex(0);
    }
    
    public void restoreSongsToQueue(List<Song> songs, int afterIndex) {
        if (songs == null || songs.isEmpty() || exoPlayer == null || currentPlaylist == null) return;
        userInteractedWithPlayback = true;
        synchronized (this) {
            int insertAt = afterIndex + 1;
            for (Song song : songs) {
                currentPlaylist.add(insertAt, song);
                exoPlayer.addMediaItem(insertAt, createMediaItem(song));
                insertAt++;
            }
            if (afterIndex < currentIndex) {
                currentIndex += songs.size();
                persistLastIndex(currentIndex);
            }
            updateQueueUI();
        }
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
        // Dropped from library → drop from history and play counts too
        playHistoryIds.remove(song.getId());
        persistPlayHistory();
        emitPlayHistory();
        playCounts.remove(song.getId());
        persistPlayCounts();
        emitHotTracks();
        if (favoritesMap.containsKey(song.getId())) toggleFavorite(song);
        if (currentPlaylist != null && currentPlaylist.contains(song)) {
            userInteractedWithPlayback = true;
            synchronized (this) {
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
                boolean matchTitle = song.getTitle() != null && song.getTitle().toLowerCase().contains(queryLower);
                boolean matchArtist = song.getArtist() != null && song.getArtist().toLowerCase().contains(queryLower);
                boolean matchAlbum = song.getAlbum() != null && song.getAlbum().toLowerCase().contains(queryLower);
                if (matchTitle || matchArtist || matchAlbum) matches.add(song);
            }
        }
        onlineSearchResults.postValue(matches);
    }
    
    private void fetchOnlineSongsAndCache(Context context) {
        onlineRepository.fetchAllOnlineSongs(new OnlineMusicRepository.OnMusicFetchedListener() {
            @Override
            public void onSuccess(List<Song> songs) {
                allOnlineSongsLiveData.postValue(songs);
                emitPlayHistory(); // re-resolve history against the fresh catalog
                emitHotTracks();
                generatePersonalizedMixes();
                onlineRepository.saveCache(context, songs);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    generateLanguagePlaylistsFromOnlineMusic();

                    executorService.execute(() -> {
                        List<Song> local = songsLiveData.getValue();
                        if (local != null && !local.isEmpty()) {
                            matchAndGenerateLocalPlaylists(local, songs);
                        }
                    });

                    executorService.execute(() -> {
                        List<Song> local = songsLiveData.getValue();
                        if (local != null && !local.isEmpty()) {
                            // Self-publishing on main thread — no trailing publish here.
                            restoreLastSession(local);
                        }
                    });
                }, 1000);
            }
            @Override
            public void onError(Exception e) {
                AppLog.e(AppLog.REPO, "Failed to fetch online songs: " + e.getMessage());
            }
        });
    }

    public void refreshOnlineMusic() {
        isRefreshingOnlineLiveData.postValue(true);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (Boolean.TRUE.equals(isRefreshingOnlineLiveData.getValue())) {
                AppLog.e(AppLog.REPO, "Refresh timed out — resetting indicator");
                isRefreshingOnlineLiveData.postValue(false);
            }
        }, 10_000);
        try {
            onlineRepository.fetchAllOnlineSongs(new OnlineMusicRepository.OnMusicFetchedListener() {
                @Override
                public void onSuccess(List<Song> songs) {
                    allOnlineSongsLiveData.postValue(songs);
                    emitPlayHistory(); // re-resolve history against the fresh catalog
                    emitHotTracks();
                    onlineRepository.saveCache(getApplication(), songs);
                    generateLanguagePlaylistsFromOnlineMusic();
                    executorService.execute(() -> {
                        List<Song> local = songsLiveData.getValue();
                        if (local != null && !local.isEmpty()) {
                            matchAndGenerateLocalPlaylists(local, songs);
                        }
                    });
                    isRefreshingOnlineLiveData.postValue(false);
                }
                @Override
                public void onError(Exception e) {
                    AppLog.e(AppLog.REPO, "Refresh online music failed: " + e.getMessage());
                    isRefreshingOnlineLiveData.postValue(false);
                }
            });
        } catch (Exception e) {
            AppLog.e(AppLog.REPO, "refreshOnlineMusic exception: " + e.getMessage());
            isRefreshingOnlineLiveData.postValue(false);
        }
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
            emitPlayHistory(); // re-resolve history now that local songs are known
            emitHotTracks();
            syncFavoritesWithSongs(local);
            generatePersonalizedMixes();
            restoreLastSession(local); // self-publishes on main thread
            // If the service survived but syncUiFromPlayer ran before songs loaded,
            // the queue was never synced. Retry now that songsLiveData is populated.
            if (currentSongLiveData.getValue() == null
                    && exoPlayer != null
                    && exoPlayer.getCurrentMediaItem() != null) {
                new Handler(Looper.getMainLooper()).post(this::syncUiFromPlayer);
            }
            isLoadingLiveData.postValue(false);
            // ✅ Trigger the local playlist matching
            List<Song> online = allOnlineSongsLiveData.getValue();
            if (online != null && !online.isEmpty()) {
                matchAndGenerateLocalPlaylists(local, online);
            }
        });
    }
    
    public void loadLyrics() {
        Song song = currentSongLiveData.getValue();
        if (song == null) return;
        if (cachedLyrics != null && cachedLyricsSongId == song.getId()) {
            lyricsLiveData.postValue(cachedLyrics);
            return;
        }
        lyricsLiveData.postValue("Loading...");
        final int generation = lyricsFetchGeneration;
        executorService.execute(() -> {
            String lyrics = com.nandu.mymusic.utils.LyricsUtils.extractLyrics(
                    getApplication(), song.getUri(), song.getTitle(), song.getArtist(), true
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
        Integer currentMode = libraryModeLiveData.getValue();
        boolean allowOnlineFetch = (currentMode != null && currentMode != 0);
        final int generation = lyricsFetchGeneration;
        executorService.execute(() -> {
            String lyrics = com.nandu.mymusic.utils.LyricsUtils.extractLyrics(
                    getApplication(), song.getUri(), song.getTitle(), song.getArtist(), allowOnlineFetch
            );
            if (generation != lyricsFetchGeneration) return;
            cachedLyrics = lyrics;
            cachedLyricsSongId = song.getId();
            if (lyrics != null) lyricsLiveData.postValue(lyrics);
            else lyricsLiveData.postValue("No lyrics found.");
        });
    }

    /**
     * Sync ViewModel UI state from the live ExoPlayer state.
     * Used when reconnecting to an already-playing service (e.g. opening from notification)
     * to avoid overwriting the player with stale SharedPreferences data.
     */
    private void syncUiFromPlayer() {
        if (exoPlayer == null || exoPlayer.getCurrentMediaItem() == null) return;

        List<Song> allSongs = new ArrayList<>();
        List<Song> local = songsLiveData.getValue();
        if (local != null) allSongs.addAll(local);
        List<Song> online = allOnlineSongsLiveData.getValue();
        if (online != null) {
            Set<Long> localIds = new LinkedHashSet<>();
            for (Song s : local != null ? local : new ArrayList<Song>()) localIds.add(s.getId());
            for (Song s : online) {
                if (!localIds.contains(s.getId())) allSongs.add(s);
            }
        }

        // Rebuild currentPlaylist from ExoPlayer's media items.
        // Resolve by URI first — URIs are unique, while numeric IDs share one
        // namespace between local (MediaStore._ID) and online (Firestore id)
        // songs, so an id-only lookup can silently substitute a DIFFERENT
        // song on collision. ID is only a fallback for URI-less items.
        Map<String, Song> songByUri = new LinkedHashMap<>();
        Map<Long, Song> songById = new LinkedHashMap<>();
        for (Song s : allSongs) {
            songByUri.put(s.getUri().toString(), s);
            songById.putIfAbsent(s.getId(), s);
        }
        List<Song> restoredQueue = new ArrayList<>();
        int resolvedIndex = -1;
        for (int i = 0; i < exoPlayer.getMediaItemCount(); i++) {
            MediaItem item = exoPlayer.getMediaItemAt(i);
            if (item == null) continue;
            Song resolved = null;
            if (item.localConfiguration != null && item.localConfiguration.uri != null) {
                resolved = songByUri.get(item.localConfiguration.uri.toString());
            }
            if (resolved == null) {
                try {
                    long id = Long.parseLong(item.mediaId);
                    resolved = songById.get(id);
                } catch (NumberFormatException ignored) {}
            }
            if (resolved != null) {
                restoredQueue.add(resolved);
                if (i == exoPlayer.getCurrentMediaItemIndex()) {
                    resolvedIndex = restoredQueue.size() - 1;
                }
            }
        }

        if (restoredQueue.isEmpty() || resolvedIndex < 0) return;

        synchronized (this) {
            currentPlaylist = restoredQueue;
            currentIndex = resolvedIndex;
        }
        currentSongLiveData.postValue(currentPlaylist.get(currentIndex));
        progressLiveData.postValue(exoPlayer.getCurrentPosition());
        isPlayingLiveData.postValue(exoPlayer.isPlaying());
        shuffleModeLiveData.setValue(exoPlayer.getShuffleModeEnabled());
        repeatModeLiveData.setValue(exoPlayer.getRepeatMode());
        updateQueueUI();
    }

    private void restoreLastSession(List<Song> localSongs) {
        if (localSongs == null || localSongs.isEmpty()) return;
        if (!sessionRestoreInProgress.compareAndSet(false, true)) return;
        // NOTE: the old early guards (currentSong != null / player has media)
        // ran on the EXECUTOR thread and could be stale by the time the result
        // was applied — that race let a stale saved queue clobber a live one.
        // All guards now run fresh on the main thread in the apply phase below.

        List<Song> allSongs = new ArrayList<>(localSongs);
        List<Song> online = allOnlineSongsLiveData.getValue();
        if (online != null && !online.isEmpty()) {
            Set<Long> localIds = new LinkedHashSet<>();
            for (Song s : localSongs) localIds.add(s.getId());
            for (Song s : online) {
                if (!localIds.contains(s.getId())) {
                    allSongs.add(s);
                }
            }
        }

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
            for (Song s : allSongs) songMap.put(s.getId(), s);
            for (String idStr : queueStr.split(",")) {
                try {
                    long id = Long.parseLong(idStr);
                    if (songMap.containsKey(id)) restoredQueue.add(songMap.get(id));
                } catch (NumberFormatException ignored) {}
            }
        }
        
        if (restoredQueue.isEmpty()) {
            // Don't fall back to the entire local library — just restore the single song
            // that was playing. Find it by lastUri from the combined song list.
            if (lastUri != null) {
                for (Song s : allSongs) {
                    if (s.getUri().toString().equals(lastUri)) {
                        restoredQueue.add(s);
                        break;
                    }
                }
            }
            // If still empty (song not found), bail out
            if (restoredQueue.isEmpty()) {
                sessionRestoreInProgress.set(false);
                return;
            }
        }
        
        if (lastUri != null) {
            for (int i = 0; i < restoredQueue.size(); i++) {
                if (restoredQueue.get(i).getUri().toString().equals(lastUri)) {
                    resolvedIndex = i; break;
                }
            }
        }
        if (resolvedIndex < 0 && lastIndex >= 0 && lastIndex < restoredQueue.size()) resolvedIndex = lastIndex;
        if (resolvedIndex < 0) { sessionRestoreInProgress.set(false); return; }

        // ── PHASE 2: main thread — fresh guards, THEN mutate + publish. The old
        // code assigned currentPlaylist and called updateQueueUI() on the executor
        // BEFORE the abort checks, so a stale restored queue could clobber a queue
        // the user had just started (the "queue resets to other songs" bug).
        final List<Song> finalQueue = new ArrayList<>(restoredQueue);
        final int finalIndex = resolvedIndex;
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                if (exoPlayer == null || userInteractedWithPlayback) return;
                // Fresh abort checks — the user may have started playback (or the
                // player already had media) while the executor was resolving.
                if (currentSongLiveData.getValue() != null) return;
                if (exoPlayer.getCurrentMediaItem() != null) return;
                // Never clobber a queue that exists since we resolved ours.
                synchronized (this) {
                    if (currentPlaylist != null && !currentPlaylist.isEmpty()) return;
                }

                List<MediaItem> items = new ArrayList<>();
                for (Song s : finalQueue) items.add(createMediaItem(s));

                synchronized (this) {
                    currentPlaylist = finalQueue;
                    currentIndex    = finalIndex;
                }
                updateQueueUI();
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
                // Apple Music "Playing From" — restore the label with the session.
                String savedSource = prefs.getString(KEY_NP_SOURCE, null);
                if (savedSource != null) nowPlayingSourceLiveData.setValue(savedSource);
            } finally {
                // Every path (including aborts) must release the guard.
                sessionRestoreInProgress.set(false);
            }
            // Start progress polling so the bar updates when user plays
            progressHandler.removeCallbacks(progressRunnable);
            progressHandler.post(progressRunnable);
        });
    }
    
    // ✅ SLEEP TIMER LOGIC
    public LiveData<Boolean> getIsSleepTimerActive() { return isSleepTimerActive; }
    public LiveData<Boolean> getOnlineSongBlocked() { return onlineSongBlockedLiveData; }
    
    public void switchToOnlineAndPlay() {
        Song song = currentSongLiveData.getValue();
        if (song == null) return;
        onlineSongBlockedLiveData.setValue(false);
        setLibraryMode(1);
        List<Song> songs = allOnlineSongsLiveData.getValue();
        if (songs != null && !songs.isEmpty()) {
            int idx = -1;
            for (int i = 0; i < songs.size(); i++) {
                if (songs.get(i).getId() == song.getId()) { idx = i; break; }
            }
            if (idx < 0) {
                playSong(song, songs);
            } else {
                synchronized (this) {
                    currentPlaylist = new ArrayList<>(songs);
                    currentIndex = idx;
                }
                List<MediaItem> items = new ArrayList<>();
                for (Song s : currentPlaylist) items.add(createMediaItem(s));
                SharedPreferences prefs = getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                long savedPos = prefs.getLong(KEY_LAST_POSITION, 0L);
                if (exoPlayer != null) {
                    exoPlayer.setMediaItems(items, idx, savedPos);
                    exoPlayer.prepare();
                    exoPlayer.play();
                }
                currentSongLiveData.setValue(song);
                progressLiveData.setValue(savedPos);
                updateQueueUI();
                persistLastIndex(idx);
            }
        }
    }
    
    public void playLocalShuffled() {
        onlineSongBlockedLiveData.setValue(false);
        executorService.execute(() ->
            getApplication().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("is_online_blocked", false)
                    .apply()
        );
        List<Song> local = songsLiveData.getValue();
        if (local != null && !local.isEmpty()) {
            playAllShuffled(local);
        }
    }
    
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
        cancelSleepFade(); // restores volume if a fade is mid-flight
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
        // Don't cancel the timer yet: the fade must finish cleanly either way.
        if (exoPlayer == null) { cancelSleepTimer(); return; }
        sleepFadeActive = true;
        volBeforeSleepFade = Math.min(1f, exoPlayer.getVolume());
        sleepFadeTimer = new android.os.CountDownTimer(SLEEP_FADE_MS, SLEEP_FADE_TICK_MS) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (!sleepFadeActive || exoPlayer == null) return;
                float ratio = millisUntilFinished / (float) SLEEP_FADE_MS;
                exoPlayer.setVolume(volBeforeSleepFade * Math.max(0f, ratio));
            }
            @Override
            public void onFinish() {
                if (sleepFadeActive) {
                    if (exoPlayer != null) {
                        exoPlayer.pause();
                        exoPlayer.setVolume(volBeforeSleepFade);
                    }
                    sleepFadeActive = false;
                }
                sleepFadeTimer = null;
                cancelSleepTimer();
            }
        }.start();
    }

    /**
     * Aborts an in-flight sleep fade and restores the pre-fade volume. Called
     * from every user-initiated play so a fade never hijacks a resume.
     */
    private void cancelSleepFade() {
        if (sleepFadeTimer != null) {
            sleepFadeTimer.cancel();
            sleepFadeTimer = null;
        }
        if (sleepFadeActive) {
            sleepFadeActive = false;
            if (exoPlayer != null) exoPlayer.setVolume(volBeforeSleepFade);
        }
    }
    
    @Override
    protected void onCleared() {
        // Persist position BEFORE shutting the executor down — persistLastPosition submits
        // to it, and submitting after shutdown() throws RejectedExecutionException.
        if (exoPlayer != null) persistLastPosition(exoPlayer.getCurrentPosition());
        appPrefs.unregisterOnSharedPreferenceChangeListener(favoritesPrefsListener);

        getApplication().getContentResolver().unregisterContentObserver(contentObserver);
        handler.removeCallbacks(reloadRunnable);
        progressHandler.removeCallbacks(progressRunnable);

        if (executorService != null) {
            executorService.shutdown();
        }
        super.onCleared();
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
        }
    }
}