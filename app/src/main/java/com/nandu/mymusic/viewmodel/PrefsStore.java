package com.nandu.mymusic.viewmodel;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Single source of truth for every SharedPreferences key the app persists, plus
 * the shared {@link ExecutorService} used to off-load prefs writes.
 *
 * <p>The key <b>values</b> are also hard-coded in {@code MusicPlaybackService}
 * (its companion object) because that service must read them without a dependency
 * on this class. Keep the values byte-identical on both sides; if one changes,
 * the system media notification's favorite/repeat buttons silently desync.
 */
public final class PrefsStore {

    // ── SharedPreferences keys ────────────────────────────────────────────────
    public static final String PREFS_NAME      = "MyMusicPrefs";
    public static final String KEY_FAV_IDS     = "favorite_song_ids";
    public static final String KEY_LAST_INDEX  = "last_song_index";
    public static final String KEY_LAST_URI    = "last_song_uri";
    public static final String KEY_PLAYLISTS   = "custom_playlists_keys";
    public static final String KEY_SHUFFLE_MODE = "shuffle_mode_state";
    public static final String KEY_REPEAT_MODE  = "repeat_mode_state";
    public static final String KEY_LAST_POSITION = "last_song_position";
    public static final String KEY_QUEUE_IDS     = "saved_queue_ids";
    public static final String KEY_DATA_SAVER = "data_saver_mode";
    public static final String KEY_LIBRARY_MODE = "library_mode_state";
    public static final String KEY_DELETED_IDS = "deleted_song_ids";
    // Apple Music "Autoplay": opt-in toggle for keeping music going at end of queue.
    public static final String KEY_AUTOPLAY_ENABLED = "autoplay_enabled";
    public static final String KEY_NP_SOURCE = "now_playing_source";
    /** "Online song is blocked by Local mode" flag. Also read by the service's
     *  {@code ForwardingPlayer.play()} (hard-coded string there). */
    public static final String KEY_ONLINE_BLOCKED = "is_online_blocked";

    // Apple Music "Recently Played": persistent play history (newest first)
    public static final String KEY_PLAY_HISTORY = "play_history_ids";
    public static final int PLAY_HISTORY_MAX = 50;

    // Apple Music "Hot Tracks": persistent per-song play counts
    public static final String KEY_PLAY_COUNTS = "play_counts";
    public static final int HOT_TRACKS_MAX = 20;

    // Persist Sort Type
    public static final String KEY_SORT_TYPE = "last_sort_type_preference";

    // Auto-generated language playlists
    public static final String KEY_AUTO_PLAYLISTS = "auto_generated_playlists";
    public static final String KEY_ENABLED_LANGS = "enabled_language_playlists";

    public final ExecutorService executor;
    private final SharedPreferences appPrefs;

    public PrefsStore(Context context) {
        this.executor = Executors.newFixedThreadPool(4);
        this.appPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** The app's shared prefs instance (same instance the service reads). */
    public SharedPreferences prefs() {
        return appPrefs;
    }
}
