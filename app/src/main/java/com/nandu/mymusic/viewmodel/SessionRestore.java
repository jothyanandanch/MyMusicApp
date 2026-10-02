package com.nandu.mymusic.viewmodel;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;

import com.nandu.mymusic.model.Song;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Rebuilding the queue from persisted state when the app reconnects to the
 * already-playing Media3 service (opening from the notification) or on a cold
 * start. {@link #restoreLastSession(List)} is race-guarded so a stale saved
 * queue can never clobber a queue the user started in the meantime.
 */
public final class SessionRestore {

    private final MusicViewModelCore core;
    private final AtomicBoolean sessionRestoreInProgress = new AtomicBoolean(false);

    public SessionRestore(MusicViewModelCore core) {
        this.core = core;
    }

    /** True while a session restore is mid-apply (used to suppress queue-drift resync). */
    public boolean isRestoring() {
        return sessionRestoreInProgress.get();
    }

    /**
     * Sync the ViewModel's UI state from the live ExoPlayer state. Used when
     * reconnecting to an already-playing service to avoid overwriting the
     * player with stale SharedPreferences data.
     */
    public void syncUiFromPlayer() {
        Player exoPlayer = core.player();
        if (exoPlayer == null || exoPlayer.getCurrentMediaItem() == null) return;

        List<Song> allSongs = new ArrayList<>();
        List<Song> local = core.songsLive().getValue();
        if (local != null) allSongs.addAll(local);
        List<Song> online = core.allOnlineSongsLive().getValue();
        if (online != null) {
            Set<Long> localIds = new LinkedHashSet<>();
            for (Song s : local != null ? local : new ArrayList<Song>()) localIds.add(s.getId());
            for (Song s : online) {
                if (!localIds.contains(s.getId())) allSongs.add(s);
            }
        }

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

        final int finalResolvedIndex = resolvedIndex;
        core.withQueue(() -> {
            core.setQueue(restoredQueue);
            core.setCurrentIndex(finalResolvedIndex);
        });
        core.currentSongLive().postValue(core.queueList().get(core.currentIndex()));
        core.progressLive().postValue(exoPlayer.getCurrentPosition());
        core.isPlayingLive().postValue(exoPlayer.isPlaying());
        core.shuffleModeLive().setValue(exoPlayer.getShuffleModeEnabled());
        core.repeatModeLive().setValue(exoPlayer.getRepeatMode());
        core.updateQueueUI();
    }

    /**
     * Restore the last session from prefs into the (possibly empty) player.
     * Runs fresh guards on the main thread before mutating, so a stale saved
     * queue can never clobber a live one.
     */
    public void restoreLastSession(List<Song> localSongs) {
        if (localSongs == null || localSongs.isEmpty()) return;
        if (!sessionRestoreInProgress.compareAndSet(false, true)) return;
        // NOTE: the old early guards (currentSong != null / player has media)
        // ran on the EXECUTOR thread and could be stale by the time the result
        // was applied — that race let a stale saved queue clobber a live one.
        // All guards now run fresh on the main thread in the apply phase below.

        List<Song> allSongs = new ArrayList<>(localSongs);
        List<Song> online = core.allOnlineSongsLive().getValue();
        if (online != null && !online.isEmpty()) {
            Set<Long> localIds = new LinkedHashSet<>();
            for (Song s : localSongs) localIds.add(s.getId());
            for (Song s : online) {
                if (!localIds.contains(s.getId())) {
                    allSongs.add(s);
                }
            }
        }

        SharedPreferences prefs = core.prefs().prefs();
        int    lastIndex     = prefs.getInt(PrefsStore.KEY_LAST_INDEX, -1);
        String lastUri       = prefs.getString(PrefsStore.KEY_LAST_URI, null);
        boolean savedShuffle = prefs.getBoolean(PrefsStore.KEY_SHUFFLE_MODE, false);
        int savedRepeat      = prefs.getInt(PrefsStore.KEY_REPEAT_MODE, Player.REPEAT_MODE_OFF);
        long savedPosition   = prefs.getLong(PrefsStore.KEY_LAST_POSITION, 0L);
        String queueStr      = prefs.getString(PrefsStore.KEY_QUEUE_IDS, "");

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
            // Don't fall back to the entire local library — just restore the single
            // song that was playing. Find it by lastUri from the combined list.
            if (lastUri != null) {
                for (Song s : allSongs) {
                    if (s.getUri().toString().equals(lastUri)) {
                        restoredQueue.add(s);
                        break;
                    }
                }
            }
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

        // PHASE 2: main thread — fresh guards, THEN mutate + publish.
        final List<Song> finalQueue = new ArrayList<>(restoredQueue);
        final int finalIndex = resolvedIndex;
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Player exoPlayer = core.player();
                if (exoPlayer == null || core.userInteractedWithPlayback()) return;
                if (core.currentSongLive().getValue() != null) return;
                if (exoPlayer.getCurrentMediaItem() != null) return;
                // Never clobber a queue that exists since we resolved ours.
                List<Song> liveQueue = core.queueList();
                if (liveQueue != null && !liveQueue.isEmpty()) return;

                List<MediaItem> items = new ArrayList<>();
                for (Song s : finalQueue) items.add(core.createMediaItem(s));

                core.withQueue(() -> {
                    core.setQueue(finalQueue);
                    core.setCurrentIndex(finalIndex);
                });
                core.updateQueueUI();
                exoPlayer.setMediaItems(items, finalIndex, savedPosition);
                exoPlayer.prepare();
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Player p = core.player();
                    if (p != null) {
                        long songDuration = p.getDuration();
                        if (songDuration > 0) core.durationLive().postValue(songDuration);
                    }
                }, 100);
                core.currentSongLive().postValue(core.queueList().get(finalIndex));
                core.shuffleModeLive().setValue(savedShuffle);
                core.repeatModeLive().setValue(savedRepeat);
                exoPlayer.setRepeatMode(savedRepeat);
                core.progressLive().setValue(savedPosition);
                // Apple Music "Playing From" — restore the label with the session.
                String savedSource = prefs.getString(PrefsStore.KEY_NP_SOURCE, null);
                if (savedSource != null) core.nowPlayingSourceLive().setValue(savedSource);
            } finally {
                // Every path (including aborts) must release the guard.
                sessionRestoreInProgress.set(false);
            }
            // Start progress polling so the bar updates when the user plays.
            core.restartProgressPolling();
        });
    }
}
