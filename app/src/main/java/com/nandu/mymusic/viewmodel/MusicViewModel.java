package com.nandu.mymusic.viewmodel;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.Uri;
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
import com.nandu.mymusic.utils.AppLog;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;

/**
 * Thin coordinator / composition root for the music app. Every concern lives in
 * its own {@code com.nandu.mymusic.viewmodel} helper class and is reached through
 * the {@link MusicViewModelCore} facade implemented by this class:
 *
 * <pre>
 *   PrefsStore            – shared prefs keys + executor + content observer target
 *   SettingsManager       – data-saver / autoplay / sort-type toggles
 *   LibraryModeController – Local/Cloud/Hybrid mode + "online song blocked" state
 *   ListeningStats        – "Recently Played" history + "Hot Tracks" play counts
 *   FavoritesManager      – Liked Songs
 *   PlaylistsManager      – custom + language + "Made for You" playlists
 *   QueueManager          – queue, transport, curation, shuffle, autoplay
 *   SleepTimerManager     – time/track/queue-end sleep timer + fade
 *   LyricsManager         – lyrics fetch + cache
 *   SongDeletion          – permanent local deletion + pending intent
 *   SessionRestore        – queue rebuild from prefs / from the live player
 *   LibraryData           – local + online repositories, search, fetch/refresh
 * </pre>
 *
 * <p>This class owns only the things that are genuinely cross-cutting: the
 * shared queue list + {@code synchronized (this)} monitor, the Media3
 * {@code exoPlayer} (main-thread-only), the MediaController wiring and its
 * player listener, the 16ms progress-poll loop, the content observer, and the
 * cleanup order in {@link #onCleared()}. All LiveData the UI observes is
 * re-exposed from the helpers so the public surface is unchanged.
 */
public class MusicViewModel extends AndroidViewModel implements MusicViewModelCore {

    // ── Shared cross-cutting state (owned by the VM, the composition root) ─
    private final PrefsStore prefsStore;
    private Player exoPlayer;
    private ListenableFuture<MediaController> controllerFuture;
    private List<Song> currentPlaylist;
    private List<Song> originalPlaylistBeforeShuffle;
    private int currentIndex = -1;
    private volatile boolean userInteractedWithPlayback = false;
    private final Set<Long> deletedSongIds = new LinkedHashSet<>();
    /** Apple Music–style error resilience: give up after N consecutive broken tracks. */
    private static final int MAX_CONSECUTIVE_ERRORS = 3;
    private int consecutivePlaybackErrors = 0;

    // ── LiveData owned by the VM (not by a helper) ───────────────────────
    private final MutableLiveData<List<Song>> songsLiveData = new MutableLiveData<>();
    private final MutableLiveData<Song> currentSongLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isPlayingLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Long> progressLiveData = new MutableLiveData<>(0L);
    private final MutableLiveData<Long> durationLiveData = new MutableLiveData<>(0L);
    private final MutableLiveData<Integer> repeatModeLiveData = new MutableLiveData<>(Player.REPEAT_MODE_OFF);
    private final MutableLiveData<Boolean> shuffleModeLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> endOfQueueLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> showEndDialogLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> isNowPlayingOpenLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<String> nowPlayingSourceLiveData = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> isLoadingLiveData = new MutableLiveData<>(true);

    // ── Concern helpers (composition) ────────────────────────────────────
    private final LocalMusicRepository repository;
    private SettingsManager settings;
    private LibraryModeController libraryMode;
    private ListeningStats listeningStats;
    private FavoritesManager favoritesManager;
    private PlaylistsManager playlistsManager;
    private QueueManager queueManager;
    private SleepTimerManager sleepTimer;
    private LyricsManager lyricsManager;
    private SongDeletion songDeletion;
    private SessionRestore sessionRestore;
    private LibraryData libraryData;

    /** Reference used to register/unregister the favorites listener on clear. */
    private final SharedPreferences appPrefs;

    /**
     * Watch favorite_song_ids changes pushed by the media notification handler
     * (system UI toggles) so the app's heart state never desyncs.
     */
    private final SharedPreferences.OnSharedPreferenceChangeListener
            favoritesPrefsListener = (prefs, key) -> {
        if (!PrefsStore.KEY_FAV_IDS.equals(key)) return;
        new Handler(Looper.getMainLooper()).post(this::refreshFavoritesFromPrefs);
    };

    // ── Progress polling ────────────────────────────────────────────────
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private int progressTicks = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ContentObserver contentObserver;
    private final Runnable reloadRunnable = this::loadLocalMusic;

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

    private final Runnable queueDriftCheckRunnable = new Runnable() {
        @Override public void run() {
            // External controllers can edit the queue behind our back; if the
            // player's timeline diverges from our snapshot, resync instead of
            // letting the UI go stale.
            if (exoPlayer == null || sessionRestore.isRestoring()) return;
            Integer queueSize;
            synchronized (MusicViewModel.this) {
                queueSize = currentPlaylist == null ? null : currentPlaylist.size();
            }
            if (queueSize != null && exoPlayer.getMediaItemCount() != queueSize) {
                AppLog.d(AppLog.PLAYER, "Queue drift detected — resyncing UI from player");
                sessionRestore.syncUiFromPlayer();
            }
        }
    };

    @OptIn(markerClass = UnstableApi.class)
    public MusicViewModel(@NonNull Application application) {
        super(application);
        Context ctx = application;
        SharedPreferences prefs = ctx.getSharedPreferences(PrefsStore.PREFS_NAME, Context.MODE_PRIVATE);

        prefsStore = new PrefsStore(ctx);
        repository = new LocalMusicRepository(ctx);
        appPrefs = application.getSharedPreferences(PrefsStore.PREFS_NAME, Context.MODE_PRIVATE);
        appPrefs.registerOnSharedPreferenceChangeListener(favoritesPrefsListener);

        // Seed the simple settings from prefs before constructing the helpers that read them.
        boolean dataSaver = prefs.getBoolean(PrefsStore.KEY_DATA_SAVER, true);
        boolean autoplay = prefs.getBoolean(PrefsStore.KEY_AUTOPLAY_ENABLED, false);
        String sortType = prefs.getString(PrefsStore.KEY_SORT_TYPE, "DEFAULT");
        int libraryModeInit = prefs.getInt(PrefsStore.KEY_LIBRARY_MODE, 0);

        // Helpers take `this` (the facade) — all core methods are on `this`, and
        // `player()` is read on demand, so ordering inside the ctor is fine.
        settings = new SettingsManager(this, dataSaver, autoplay, sortType);
        libraryMode = new LibraryModeController(this, libraryModeInit);
        listeningStats = new ListeningStats(this);
        favoritesManager = new FavoritesManager(this);
        playlistsManager = new PlaylistsManager(this);
        queueManager = new QueueManager(this);
        sleepTimer = new SleepTimerManager(this);
        lyricsManager = new LyricsManager(this);
        songDeletion = new SongDeletion(this);
        sessionRestore = new SessionRestore(this);
        libraryData = new LibraryData(this, new LibraryData.Dispatcher() {
            @Override public MutableLiveData<Boolean> isLoading() { return isLoadingLiveData; }
            @Override public Set<Long> deletedIds() { return deletedSongIds; }
            @Override public void localLoaded(List<Song> local) { onLocalLibraryLoaded(local); }
            @Override public void onlineCached(List<Song> online) { listeningStats.refresh(); }
            @Override public void onlineFetched(List<Song> online, boolean isRefresh) { onOnlineLibraryLoaded(online, isRefresh); }
        }, repository);

        // Load deleted song IDs.
        Set<String> deletedStrs = prefs.getStringSet(PrefsStore.KEY_DELETED_IDS, new LinkedHashSet<>());
        if (deletedStrs != null) {
            for (String idStr : deletedStrs) {
                try { deletedSongIds.add(Long.parseLong(idStr)); } catch (NumberFormatException ignored) {}
            }
        }

        // Persisted state bootstrap.
        listeningStats.loadFromPrefs();
        favoritesManager.loadFromPrefs();
        playlistsManager.loadFromPrefs();

        // Cache-first online library.
        libraryData.seedFromCache(application);

        // Media3 controller.
        SessionToken sessionToken = new SessionToken(application,
                new ComponentName(application, com.nandu.mymusic.service.MusicPlaybackService.class));
        controllerFuture = new MediaController.Builder(application, sessionToken).buildAsync();
        controllerFuture.addListener(this::onControllerReady, ContextCompat.getMainExecutor(application));

        contentObserver = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                handler.removeCallbacks(reloadRunnable);
                handler.postDelayed(reloadRunnable, 1500);
            }
        };
        application.getContentResolver().registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, contentObserver);
    }

    private void onControllerReady() {
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
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
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
                    // SLEEP TIMER TRACK COUNTER CHECK
                    sleepTimer.onAutoTrackTransition(reason);
                    List<Song> snapshot = snapshotPlaylist();
                    if (snapshot.isEmpty()) return;
                    int idx = exoPlayer.getCurrentMediaItemIndex();
                    if (idx >= 0 && idx < snapshot.size()) {
                        currentIndex = idx;
                        Song song = snapshot.get(idx);
                        currentSongLiveData.setValue(song);
                        // Every song that starts playing enters "Recently Played",
                        // bumps its "Hot Tracks" count, and resets the lyrics cache.
                        onSongStarted(song);
                        persistLastIndex(currentIndex);
                        if (exoPlayer.getDuration() > 0) durationLiveData.postValue(exoPlayer.getDuration());
                    }
                }

                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY) consecutivePlaybackErrors = 0;
                    if (exoPlayer != null && exoPlayer.getDuration() > 0)
                        durationLiveData.postValue(exoPlayer.getDuration());
                    if (state == Player.STATE_ENDED) {
                        if (sleepTimer.onEnded(exoPlayer.getRepeatMode())) return;
                        if (exoPlayer.getRepeatMode() == Player.REPEAT_MODE_ALL) return;
                        if (queueManager.attemptAutoplay()) return;
                        isPlayingLiveData.postValue(false);
                        endOfQueueLiveData.postValue(true);
                        showEndDialogLiveData.postValue(true);
                    }
                }

                @Override
                public void onTimelineChanged(Timeline timeline, int reason) {
                    if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return;
                    // Debounced drift check — VM-initiated bursts settle within 300ms.
                    progressHandler.removeCallbacks(queueDriftCheckRunnable);
                    progressHandler.postDelayed(queueDriftCheckRunnable, 300);
                }

                @Override
                public void onRepeatModeChanged(int repeatMode) {
                    // Keeps the in-app Repeat pill in sync when repeat is toggled
                    // from the system media notification or any external controller.
                    repeatModeLiveData.postValue(repeatMode);
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    AppLog.e(AppLog.PLAYER, "Playback error: " + error.getMessage());
                    isPlayingLiveData.postValue(false);
                    // A broken/unavailable track never stalls playback — it is skipped
                    // and the queue keeps moving, up to N in a row.
                    consecutivePlaybackErrors++;
                    if (exoPlayer != null && currentPlaylist != null
                            && !currentPlaylist.isEmpty()
                            && consecutivePlaybackErrors < MAX_CONSECUTIVE_ERRORS) {
                        AppLog.d(AppLog.PLAYER, "Skipping broken track ("
                                + consecutivePlaybackErrors + "/" + MAX_CONSECUTIVE_ERRORS + ")");
                        queueManager.skipBrokenTrack();
                    }
                }
            });
            progressHandler.post(progressRunnable);
            // If the player already has media loaded (e.g. reopening from notification),
            // sync UI from the live player state instead of restoring from stale prefs.
            if (exoPlayer.getCurrentMediaItem() != null) {
                sessionRestore.syncUiFromPlayer();
            } else if (songsLiveData.getValue() != null && !songsLiveData.getValue().isEmpty()) {
                // restoreLastSession self-publishes on the main thread; no trailing
                // updateQueueUI() — publishing here raced the apply.
                sessionRestore.restoreLastSession(songsLiveData.getValue());
            }
        } catch (Exception e) {
            AppLog.e(AppLog.PLAYER, "Failed to connect to MediaSessionService");
        }
    }

    // ── LiveData getters (re-exposed from the helpers — unchanged public surface) ─
    public LiveData<String> getSortType() { return settings.getSortType(); }
    public void setSortType(String s) { settings.setSortType(s); }

    public LiveData<Boolean> getDataSaverMode() { return settings.getDataSaverMode(); }
    public void setDataSaverMode(boolean b) { settings.setDataSaverMode(b); }

    public LiveData<Boolean> getIsAutoplayEnabled() { return settings.getIsAutoplayEnabled(); }
    public void setAutoplayEnabled(boolean b) { settings.setAutoplayEnabled(b); }

    public LiveData<Integer> getLibraryMode() { return libraryMode.getLibraryMode(); }
    public void setLibraryMode(int mode) { libraryMode.setLibraryMode(mode); }

    public LiveData<String> getLyrics() { return lyricsManager.getLyrics(); }
    public void loadLyrics() { lyricsManager.loadLyrics(); }

    public LiveData<Boolean> getIsLoading() { return isLoadingLiveData; }
    public LiveData<List<Song>> getAllOnlineSongs() { return libraryData.getAllOnlineSongs(); }
    public LiveData<Boolean> getIsRefreshingOnline() { return libraryData.getIsRefreshingOnline(); }
    public LiveData<List<Song>> getOnlineSearchResults() { return libraryData.getOnlineSearchResults(); }
    public void searchOnlineMusic(String q) { libraryData.searchOnlineMusic(q); }
    public void refreshOnlineMusic() { libraryData.refreshOnlineMusic(); }
    public void loadLocalMusic() { libraryData.loadLocalMusic(); }

    public LiveData<List<Song>> getQueue() { return queueLiveDataInternal; }
    private final MutableLiveData<List<Song>> queueLiveDataInternal = new MutableLiveData<>(new ArrayList<>());

    public LiveData<List<Song>> getSongs()           { return songsLiveData; }
    public LiveData<Song>       getCurrentSong()     { return currentSongLiveData; }
    public LiveData<Boolean>    getIsPlaying()       { return isPlayingLiveData; }
    public LiveData<Long>       getProgress()        { return progressLiveData; }
    public LiveData<Long>       getDuration()        { return durationLiveData; }
    public LiveData<Integer>    getRepeatMode()      { return repeatModeLiveData; }
    public LiveData<Boolean>    getShuffleMode()     { return shuffleModeLiveData; }
    public LiveData<Boolean>    getShowEndDialog()   { return showEndDialogLiveData; }
    public LiveData<Set<Long>>  getFavoriteIds()     { return favoritesManager.getFavoriteIds(); }
    public LiveData<Boolean>    isNowPlayingOpen()   { return isNowPlayingOpenLiveData; }
    public LiveData<String>     getNowPlayingSource() { return nowPlayingSourceLiveData; }
    public LiveData<Map<String, List<Long>>> getCustomPlaylists() { return playlistsManager.getCustomPlaylists(); }
    public LiveData<Set<String>> getAutoGeneratedPlaylists() { return playlistsManager.getAutoGeneratedPlaylists(); }
    public LiveData<IntentSender> getDeleteIntentSender() { return songDeletion.getDeleteIntentSender(); }
    public LiveData<List<Song>> getPlayHistory() { return listeningStats.getPlayHistory(); }
    public LiveData<List<Song>> getHotTracks() { return listeningStats.getHotTracks(); }

    public void clearDeleteIntent() { songDeletion.clearDeleteIntent(); }
    public void setNowPlayingOpen(boolean open)      { isNowPlayingOpenLiveData.postValue(open); }

    public void toggleFavorite(Song s) { favoritesManager.toggleFavorite(s); }
    public void setFavoriteBatch(List<Song> songs, boolean favorite) { favoritesManager.setFavoriteBatch(songs, favorite); }
    public void refreshFavoritesFromPrefs() { favoritesManager.refreshFromPrefs(); }

    public void createPlaylist(String name) { playlistsManager.createPlaylist(name); }
    public void deletePlaylist(String name) { playlistsManager.deletePlaylist(name); }
    public void addSongToPlaylist(String n, Song s) { playlistsManager.addSongToPlaylist(n, s); }
    public void addSongsToPlaylist(String n, List<Song> songs) { playlistsManager.addSongsToPlaylist(n, songs); }
    public void removeSongFromPlaylist(String n, Song s) { playlistsManager.removeSongFromPlaylist(n, s); }
    public void removeSongsFromPlaylist(String n, List<Song> songs) { playlistsManager.removeSongsFromPlaylist(n, songs); }
    public void renamePlaylist(String oldName, String newName) { playlistsManager.renamePlaylist(oldName, newName); }
    public void toggleLanguagePlaylist(String lang, boolean enable) { playlistsManager.toggleLanguagePlaylist(lang, enable); }
    public Set<String> getEnabledLanguages() { return playlistsManager.getEnabledLanguages(); }

    // ── Playback / queue (delegated to QueueManager) ────────────────────
    public void playSong(Song song, List<Song> playlist) { queueManager.playSong(song, playlist); }
    public void playSong(Song song, List<Song> playlist, String source) { queueManager.playSong(song, playlist, source); }
    public void togglePlayPause() { queueManager.togglePlayPause(); }
    public void playNextFromMiniPlayer() { queueManager.playNextFromMiniPlayer(); }
    public void playNextFromFullPlayer() { queueManager.playNextFromFullPlayer(); }
    public void playPrevious() { queueManager.playPrevious(); }
    public void playSongFromQueue(Song song) { queueManager.playSongFromQueue(song); }
    public void removeSongFromUpcoming(Song song) { queueManager.removeSongFromUpcoming(song); }
    public void removeSongsFromUpcoming(Set<Song> songs) { queueManager.removeSongsFromUpcoming(songs); }
    public void moveSongInUpcoming(int from, int to) { queueManager.moveSongInUpcoming(from, to); }
    public void setPlayNext(Song song) { queueManager.setPlayNext(song); }
    public void addToQueue(Song song) { queueManager.addToQueue(song); }
    public void seekTo(long ms) { queueManager.seekTo(ms); }
    public void toggleRepeat() { queueManager.toggleRepeat(); }
    public void toggleShuffle() { queueManager.toggleShuffle(); }
    public void playAllShuffled(List<Song> library) { queueManager.playAllShuffled(library); }
    public void playAllShuffled(List<Song> library, String source) { queueManager.playAllShuffled(library, source); }
    public void playShuffled(List<Song> playlist) { queueManager.playShuffled(playlist); }
    public void playShuffled(List<Song> playlist, String source) { queueManager.playShuffled(playlist, source); }
    public void dismissEndOfQueue() { queueManager.dismissEndOfQueue(); }
    public void playFromBeginning() { queueManager.playFromBeginning(); }
    public void addListToQueue(List<Song> songs) { queueManager.addListToQueue(songs); }
    public void clearQueue() { queueManager.clearQueue(); }
    public void restoreSongsToQueue(List<Song> songs, int afterIndex) { queueManager.restoreSongsToQueue(songs, afterIndex); }

    // ── Library-mode convenience (orchestrate mode + queue) ────────────
    public LiveData<Boolean> getOnlineSongBlocked() { return libraryMode.getOnlineSongBlocked(); }

    public void switchToOnlineAndPlay() {
        Song song = currentSongLiveData.getValue();
        if (song == null) return;
        libraryMode.onlineSongBlocked().setValue(false);
        setLibraryMode(1);
        List<Song> songs = libraryData.getAllOnlineSongs().getValue();
        if (songs != null && !songs.isEmpty()) {
            int idx = -1;
            for (int i = 0; i < songs.size(); i++) {
                if (songs.get(i).getId() == song.getId()) { idx = i; break; }
            }
            if (idx < 0) {
                playSong(song, songs);
            } else {
                final int restoreIdx = idx;
                withQueue(() -> {
                    setQueue(new ArrayList<>(songs));
                    setCurrentIndex(restoreIdx);
                });
                List<MediaItem> items = new ArrayList<>();
                for (Song s : currentPlaylist) items.add(createMediaItem(s));
                SharedPreferences prefs = getApplication().getSharedPreferences(PrefsStore.PREFS_NAME, Context.MODE_PRIVATE);
                long savedPos = prefs.getLong(PrefsStore.KEY_LAST_POSITION, 0L);
                if (exoPlayer != null) {
                    exoPlayer.setMediaItems(items, restoreIdx, savedPos);
                    exoPlayer.prepare();
                    exoPlayer.play();
                }
                currentSongLiveData.setValue(song);
                progressLiveData.setValue(savedPos);
                updateQueueUI();
                persistLastIndex(restoreIdx);
            }
        }
    }

    public void playLocalShuffled() {
        libraryMode.onlineSongBlocked().setValue(false);
        prefsStore.executor.execute(() ->
            getApplication().getSharedPreferences(PrefsStore.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(PrefsStore.KEY_ONLINE_BLOCKED, false)
                    .apply()
        );
        List<Song> local = songsLiveData.getValue();
        if (local != null && !local.isEmpty()) {
            playAllShuffled(local);
        }
    }

    // ── Deletion (delegated to SongDeletion) ────────────────────────────
    public void deleteSong(Song song) { songDeletion.deleteSong(song); }
    public void confirmPendingDeletion() { songDeletion.confirmPendingDeletion(); }

    // ── Sleep timer (delegated to SleepTimerManager) ───────────────────
    public LiveData<Boolean> getIsSleepTimerActive() { return sleepTimer.getIsSleepTimerActive(); }
    public LiveData<Long> getSleepTimerRemaining() { return sleepTimer.getSleepTimerRemaining(); }
    public LiveData<Integer> getSleepTimerTracksRemaining() { return sleepTimer.getSleepTimerTracksRemaining(); }
    public void startTimeSleepTimer(int minutes) { sleepTimer.startTimeSleepTimer(minutes); }
    public void startTrackSleepTimer(int tracks) { sleepTimer.startTrackSleepTimer(tracks); }
    public void startQueueEndSleepTimer() { sleepTimer.startQueueEndSleepTimer(); }
    public void cancelSleepTimer() { sleepTimer.cancelSleepTimer(); }

    // ── "Made for You" (delegated, supplied by stats + favorites) ──────
    public void generatePersonalizedMixes() {
        playlistsManager.generatePersonalizedMixes(favoritesManager.favorites(), listeningStats.hotTracks());
    }

    // ═══════════════════════════════════════════════════════════════════
    //  MusicViewModelCore implementation (the facade the helpers use)
    // ═══════════════════════════════════════════════════════════════════

    @Override public List<Song> queueList() { return currentPlaylist; }
    @Override public List<Song> snapshotPlaylist() {
        synchronized (this) {
            return currentPlaylist == null ? new ArrayList<Song>() : new ArrayList<>(currentPlaylist);
        }
    }
    @Override public int currentIndex() { return currentIndex; }
    @Override public void setCurrentIndex(int index) { currentIndex = index; }
    @Override public void setQueue(List<Song> queue) { currentPlaylist = queue; }
    @Override public List<Song> originalBeforeShuffle() { return originalPlaylistBeforeShuffle; }
    @Override public void setOriginalBeforeShuffle(List<Song> list) { originalPlaylistBeforeShuffle = list; }
    @Override public void withQueue(Runnable action) { synchronized (this) { action.run(); } }
    @Override public boolean userInteractedWithPlayback() { return userInteractedWithPlayback; }
    @Override public void markUserInteracted() { userInteractedWithPlayback = true; }

    @Override
    public void updateQueueUI() {
        final List<Song> snapshot;
        synchronized (this) {
            if (currentPlaylist == null) return;
            snapshot = new ArrayList<>(currentPlaylist);
        }
        queueLiveDataInternal.postValue(snapshot);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < snapshot.size(); i++) {
            sb.append(snapshot.get(i).getId());
            if (i < snapshot.size() - 1) sb.append(",");
        }
        getApplication().getSharedPreferences(PrefsStore.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(PrefsStore.KEY_QUEUE_IDS, sb.toString()).apply();
    }

    @Override
    public void persistLastIndex(int index) {
        String uriStr;
        synchronized (this) {
            List<Song> snapshot = snapshotPlaylist();
            if (snapshot.isEmpty() || index < 0 || index >= snapshot.size()) return;
            uriStr = snapshot.get(index).getUri().toString();
        }
        final String uri = uriStr;
        prefsStore.executor.execute(() -> {
            getApplication().getSharedPreferences(PrefsStore.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putInt(PrefsStore.KEY_LAST_INDEX, index).putString(PrefsStore.KEY_LAST_URI, uri).commit();
        });
    }

    @Override
    public void persistLastPosition(long position) {
        try {
            prefsStore.executor.execute(() -> {
                getApplication().getSharedPreferences(PrefsStore.PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putLong(PrefsStore.KEY_LAST_POSITION, position).commit();
            });
        } catch (RejectedExecutionException ignored) {
            // ViewModel clearing — executor already shut down, safe to skip the write.
        }
    }

    @Override
    public MediaItem createMediaItem(Song song) {
        String finalUrl = song.getUri().toString();
        boolean dataSaver = settings.getDataSaverMode().getValue() != null
                ? settings.getDataSaverMode().getValue() : true;
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

    @Override public Player player() { return exoPlayer; }

    @Override public MutableLiveData<List<Song>> songsLive() { return songsLiveData; }
    @Override public MutableLiveData<List<Song>> allOnlineSongsLive() { return libraryData.allOnlineSongs(); }
    @Override public MutableLiveData<Song> currentSongLive() { return currentSongLiveData; }
    @Override public MutableLiveData<Boolean> isPlayingLive() { return isPlayingLiveData; }
    @Override public MutableLiveData<Long> progressLive() { return progressLiveData; }
    @Override public MutableLiveData<Long> durationLive() { return durationLiveData; }
    @Override public MutableLiveData<Integer> repeatModeLive() { return repeatModeLiveData; }
    @Override public MutableLiveData<Boolean> shuffleModeLive() { return shuffleModeLiveData; }
    @Override public MutableLiveData<Boolean> onlineSongBlockedLive() { return libraryMode.onlineSongBlocked(); }
    @Override public MutableLiveData<Boolean> endOfQueueLive() { return endOfQueueLiveData; }
    @Override public MutableLiveData<Boolean> showEndDialogLive() { return showEndDialogLiveData; }
    @Override public MutableLiveData<String> nowPlayingSourceLive() { return nowPlayingSourceLiveData; }
    @Override public MutableLiveData<Boolean> isDataSaverModeLive() { return settings.dataSaverMode(); }
    @Override public MutableLiveData<Integer> libraryModeLive() { return libraryMode.libraryMode(); }
    @Override public boolean autoplayEnabled() { return settings.isAutoplayOn(); }

    @Override public PrefsStore prefs() { return prefsStore; }
    @Override public java.util.concurrent.ExecutorService executor() { return prefsStore.executor; }
    @Override public Application application() { return getApplication(); }
    @Override public Context context() { return getApplication(); }

    @Override
    public void onSongStarted(Song song) {
        // Every song that starts playing enters "Recently Played", bumps its
        // "Hot Tracks" count, and resets + refetches the lyrics.
        listeningStats.onSongStarted(song);
        lyricsManager.onSongStarted(song);
    }

    @Override
    public void cancelSleepFade() {
        sleepTimer.cancelSleepFade();
    }

    @Override
    public void removeFromState(Song song) {
        if (song == null) return;
        List<Song> currentList = songsLiveData.getValue();
        if (currentList != null) {
            List<Song> updated = new ArrayList<>(currentList);
            updated.remove(song);
            songsLiveData.setValue(updated);
        }
        // Dropped from library → drop from history and play counts too.
        listeningStats.forgetSong(song);
        if (favoritesManager.containsFavorite(song.getId())) {
            favoritesManager.toggleFavorite(song);
        }
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

    @Override
    public void persistDeletedId(long songId) {
        deletedSongIds.add(songId);
        try {
            prefsStore.executor.execute(() -> {
                Set<String> stringSet = new LinkedHashSet<>();
                for (Long id : deletedSongIds) stringSet.add(String.valueOf(id));
                getApplication().getSharedPreferences(PrefsStore.PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putStringSet(PrefsStore.KEY_DELETED_IDS, stringSet).commit();
            });
        } catch (RejectedExecutionException ignored) {
            // ViewModel clearing — executor already shut down.
        }
    }

    @Override public Set<Long> deletedIds() { return deletedSongIds; }

    @Override
    public void restartProgressPolling() {
        progressHandler.removeCallbacks(progressRunnable);
        progressHandler.post(progressRunnable);
    }

    @Override
    public void onLibraryModeChanged(int mode) {
        new Handler(Looper.getMainLooper()).post(this::loadPlaylistsForCurrentMode);
    }

    private void loadPlaylistsForCurrentMode() {
        playlistsManager.loadFromPrefs();
    }

    // ── Library-event fan-out (composed by the VM) ──────────────────────

    private void onLocalLibraryLoaded(List<Song> local) {
        listeningStats.refresh();
        favoritesManager.syncWithSongs(local);
        generatePersonalizedMixes();
        sessionRestore.restoreLastSession(local); // self-publishes on the main thread
        // If the service survived but syncUiFromPlayer ran before songs loaded,
        // the queue was never synced. Retry now that songsLiveData is populated.
        if (currentSongLiveData.getValue() == null
                && exoPlayer != null
                && exoPlayer.getCurrentMediaItem() != null) {
            new Handler(Looper.getMainLooper()).post(() -> sessionRestore.syncUiFromPlayer());
        }
        // Trigger the local playlist matching.
        List<Song> online = libraryData.getAllOnlineSongs().getValue();
        if (online != null && !online.isEmpty()) {
            playlistsManager.matchAndGenerateLocalPlaylists(local, online);
        }
    }

    private void onOnlineLibraryLoaded(List<Song> online, boolean isRefresh) {
        listeningStats.refresh();
        if (isRefresh) {
            playlistsManager.generateLanguagePlaylistsFromOnlineMusic();
            prefsStore.executor.execute(() -> {
                List<Song> local = songsLiveData.getValue();
                if (local != null && !local.isEmpty()) {
                    playlistsManager.matchAndGenerateLocalPlaylists(local, online);
                }
            });
        } else {
            generatePersonalizedMixes();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                playlistsManager.generateLanguagePlaylistsFromOnlineMusic();
                prefsStore.executor.execute(() -> {
                    List<Song> local = songsLiveData.getValue();
                    if (local != null && !local.isEmpty()) {
                        playlistsManager.matchAndGenerateLocalPlaylists(local, online);
                    }
                });
                prefsStore.executor.execute(() -> {
                    List<Song> local = songsLiveData.getValue();
                    if (local != null && !local.isEmpty()) {
                        // Self-publishing on the main thread — no trailing publish here.
                        sessionRestore.restoreLastSession(local);
                    }
                });
            }, 1000);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Cleanup
    // ═══════════════════════════════════════════════════════════════════

    @Override
    protected void onCleared() {
        // Persist position BEFORE shutting the executor down — persistLastPosition
        // submits to it, and submitting after shutdown() throws
        // RejectedExecutionException.
        if (exoPlayer != null) persistLastPosition(exoPlayer.getCurrentPosition());
        appPrefs.unregisterOnSharedPreferenceChangeListener(favoritesPrefsListener);

        getApplication().getContentResolver().unregisterContentObserver(contentObserver);
        handler.removeCallbacks(reloadRunnable);
        progressHandler.removeCallbacks(progressRunnable);

        prefsStore.executor.shutdown();
        super.onCleared();
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
        }
    }
}
