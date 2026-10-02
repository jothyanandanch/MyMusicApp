package com.nandu.mymusic.viewmodel;

import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.nandu.mymusic.model.Song;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;

/**
 * Apple Music "Recently Played" (persistent play history) and "Hot Tracks"
 * (persistent per-song play counts). Owns its own state; the ViewModel composes
 * it and calls {@link #onSongStarted(Song)} whenever a track starts playing.
 *
 * <p>Threading: state is only touched on the main thread (called from the player
 * listener and VM public methods); prefs writes are off-loaded to the shared
 * executor.
 */
public final class ListeningStats {

    private final MusicViewModelCore core;

    private final MutableLiveData<List<Song>> playHistoryLiveData = new MutableLiveData<>(new ArrayList<>());
    private final Deque<Long> playHistoryIds = new ArrayDeque<>();

    private final MutableLiveData<List<Song>> hotTracksLiveData = new MutableLiveData<>(new ArrayList<>());
    private final Map<Long, Integer> playCounts = new LinkedHashMap<>();

    public ListeningStats(MusicViewModelCore core) {
        this.core = core;
    }

    /** Initialize both from persisted prefs (called from the VM constructor). */
    public void loadFromPrefs() {
        loadPlayHistoryFromPrefs();
        loadPlayCountsFromPrefs();
    }

    /** Every track that starts playing moves to the front of history + bumps its count. */
    public void onSongStarted(Song song) {
        recordPlayHistory(song);
        recordPlayCount(song);
    }

    // ── Play history ("Recently Played") ───────────────────────────────────

    private void recordPlayHistory(Song song) {
        if (song == null) return;
        Long id = song.getId();
        playHistoryIds.remove(id); // move-to-front dedupe (replays don't duplicate)
        playHistoryIds.addFirst(id);
        while (playHistoryIds.size() > PrefsStore.PLAY_HISTORY_MAX) {
            playHistoryIds.removeLast();
        }
        persistPlayHistory();
        emitPlayHistory();
    }

    private void loadPlayHistoryFromPrefs() {
        SharedPreferences prefs = core.prefs().prefs();
        String saved = prefs.getString(PrefsStore.KEY_PLAY_HISTORY, "");
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
            core.executor().execute(() -> {
                core.application().getSharedPreferences(PrefsStore.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                        .edit().putString(PrefsStore.KEY_PLAY_HISTORY, encoded).apply();
            });
        } catch (RejectedExecutionException ignored) {
            // ViewModel clearing — executor already shut down.
        }
    }

    /** Re-emit the resolved history when the underlying queue/library changes. */
    private void rebuildPlayHistoryFromQueue(List<Song> orderedQueue) {
        if (orderedQueue == null) return;
        Set<Long> present = new LinkedHashSet<>();
        for (Song s : orderedQueue) present.add(s.getId());
        playHistoryIds.removeIf(id -> !present.contains(id));
        persistPlayHistory();
        emitPlayHistory();
    }

    private List<Song> resolvePlayHistory() {
        List<Song> out = new ArrayList<>();
        if (playHistoryIds.isEmpty()) return out;
        Map<Long, Song> byId = new LinkedHashMap<>();
        List<Song> online = core.allOnlineSongsLive().getValue();
        if (online != null) {
            for (Song s : online) byId.put(s.getId(), s);
        }
        List<Song> local = core.songsLive().getValue();
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

    private void recordPlayCount(Song song) {
        if (song == null) return;
        Long id = song.getId();
        playCounts.merge(id, 1, Integer::sum);
        persistPlayCounts();
        emitHotTracks();
    }

    private void loadPlayCountsFromPrefs() {
        SharedPreferences prefs = core.prefs().prefs();
        Set<String> saved = prefs.getStringSet(PrefsStore.KEY_PLAY_COUNTS, null);
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
            core.executor().execute(() -> {
                core.application().getSharedPreferences(PrefsStore.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                        .edit().putStringSet(PrefsStore.KEY_PLAY_COUNTS, encoded).apply();
            });
        } catch (RejectedExecutionException ignored) {
            // ViewModel clearing — executor already shut down.
        }
    }

    private List<Song> resolveHotTracks() {
        if (playCounts.isEmpty()) return new ArrayList<>();
        Map<Long, Song> byId = new LinkedHashMap<>();
        List<Song> online = core.allOnlineSongsLive().getValue();
        if (online != null) {
            for (Song s : online) byId.put(s.getId(), s);
        }
        List<Song> local = core.songsLive().getValue();
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
                if (out.size() >= PrefsStore.HOT_TRACKS_MAX) break;
            }
        }
        return out;
    }

    private void emitHotTracks() {
        hotTracksLiveData.postValue(resolveHotTracks());
    }

    /** Apple Music "Hot Tracks" — your most-played songs, persistent across restarts. */
    public LiveData<List<Song>> getHotTracks() { return hotTracksLiveData; }

    /** Re-resolve + re-emit both history and hot tracks (call after the library loads). */
    public void refresh() {
        emitPlayHistory();
        emitHotTracks();
    }

    /** Resolve the current hot-tracks list (used by the personalized-mixes generator). */
    public List<Song> hotTracks() { return resolveHotTracks(); }

    /** Drop a song from both the history and the play counts (e.g. it was deleted). */
    public void forgetSong(Song song) {
        if (song == null) return;
        long id = song.getId();
        playHistoryIds.remove(id);
        persistPlayHistory();
        emitPlayHistory();
        playCounts.remove(id);
        persistPlayCounts();
        emitHotTracks();
    }
}
