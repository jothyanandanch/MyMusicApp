package com.nandu.mymusic.viewmodel;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;

import com.nandu.mymusic.model.Song;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The unified playback queue: starting/playing songs, transport (next/prev/seek),
 * repeat/shuffle, and queue curation (reorder, remove, add-to-queue, play-next,
 * clear, autoplay). This was split out of the monolithic ViewModel as
 * "queue, playback, and curation" concerns — they're tightly coupled through the
 * shared queue list + ExoPlayer, so they stay together here.
 *
 * <p>All methods are main-thread (they touch {@code exoPlayer} and the shared
 * queue, both of which are main-thread-only in this app).
 */
public final class QueueManager {

    /** Apple Music–style autoplay pool size at end of queue. */
    private static final int AUTOPLAY_POOL_SIZE = 50;

    /**
     * Resolve the display value of "PLAYING FROM": always prefer the song's album
     * tag (where a movie / soundtrack title lives) when it's non-blank; otherwise
     * fall back to the caller-provided source (or "Library" as a last resort).
     */
    private String resolveDisplaySource(String source, Song song) {
        if (song != null && song.getAlbum() != null && !song.getAlbum().isBlank()) {
            return song.getAlbum();
        }
        return source != null ? source : "Library";
    }

    /**
     * Re-derive "PLAYING FROM" for the currently playing song. A song with an
     * album tracks it; a song without one keeps the last known source (the
     * start value from {@link #playSong}). Called on every media-item
     * transition so the top label keeps tracking as the queue advances —
     * auto, next/prev, or queue tap — instead of freezing on the start value.
     */
    public void refreshNowPlayingSource() {
        Song song = core.currentSongLive().getValue();
        if (song == null) return;
        if (song.getAlbum() == null || song.getAlbum().isBlank()) return;
        String album = song.getAlbum();
        if (album.equals(core.nowPlayingSourceLive().getValue())) return;
        core.nowPlayingSourceLive().setValue(album);
        persistNowPlayingSource();
    }

    private void persistNowPlayingSource() {
        core.executor().execute(() -> {
            String src = core.nowPlayingSourceLive().getValue();
            core.application().getSharedPreferences(PrefsStore.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putString(PrefsStore.KEY_NP_SOURCE, src).apply();
        });
    }

    private final MusicViewModelCore core;

    public QueueManager(MusicViewModelCore core) {
        this.core = core;
    }

    // 3. Apply shuffle behavior gracefully when starting a specific song
    public void playSong(Song song, List<Song> playlist) {
        playSong(song, playlist, null);
    }

    /** Apple Music "Playing From {source}": records where the queue came from. */
    public void playSong(Song song, List<Song> playlist, String source) {
        Player exoPlayer = core.player();
        if (exoPlayer == null) return;
        core.markUserInteracted();
        core.cancelSleepFade(); // starting fresh playback cancels any sleep fade
        core.nowPlayingSourceLive().setValue(resolveDisplaySource(source, song));

        if (song.getUri() != null) {
            String scheme = song.getUri().getScheme();
            boolean isLocalSong = scheme == null || (!"http".equals(scheme) && !"https".equals(scheme));
            if (isLocalSong && Boolean.TRUE.equals(core.onlineSongBlockedLive().getValue())) {
                core.onlineSongBlockedLive().setValue(false);
                core.executor().execute(() ->
                    core.application().getSharedPreferences(PrefsStore.PREFS_NAME, Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean(PrefsStore.KEY_ONLINE_BLOCKED, false)
                            .apply()
                );
            }
        }

        List<Song> queue = new ArrayList<>(playlist);
        int index = queue.indexOf(song);
        if (index < 0) index = 0;
        final int startIdx = index;
        core.withQueue(() -> {
            core.setQueue(queue);
            core.setCurrentIndex(startIdx);
            // If shuffle is active, re-shuffle the upcoming songs in the queue
            if (Boolean.TRUE.equals(core.shuffleModeLive().getValue())) {
                List<Song> upcoming = new ArrayList<>(queue.subList(startIdx + 1, queue.size()));
                Collections.shuffle(upcoming);
                for (int i = 0; i < upcoming.size(); i++) {
                    queue.set(startIdx + 1 + i, upcoming.get(i));
                }
            }
        });

        core.updateQueueUI();
        List<MediaItem> items = new ArrayList<>();
        for (Song s : queue) items.add(core.createMediaItem(s));
        exoPlayer.setShuffleModeEnabled(false);
        exoPlayer.setMediaItems(items, core.currentIndex(), 0L);
        exoPlayer.prepare();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (core.player() != null && core.player().getDuration() > 0) core.durationLive().postValue(core.player().getDuration());
        }, 100);
        exoPlayer.play();
        core.currentSongLive().setValue(song);
        core.progressLive().setValue(0L);
        core.endOfQueueLive().setValue(false);
        core.showEndDialogLive().setValue(false);
        core.persistLastIndex(core.currentIndex());
        // Persist the "Playing From" label so it survives session restore.
        persistNowPlayingSource();
    }

    public void togglePlayPause() {
        core.markUserInteracted();
        // Prevent hardware media buttons/headset clicks from bypassing the block
        if (Boolean.TRUE.equals(core.onlineSongBlockedLive().getValue())) return;

        Player exoPlayer = core.player();
        if (exoPlayer == null) return;
        // User resumed during a sleep-timer fade — cancel it and restore volume.
        core.cancelSleepFade();
        if (exoPlayer.isPlaying()) exoPlayer.pause();
        else {
            // After a playback error the player sits in STATE_IDLE; play() alone is a no-op.
            if (exoPlayer.getPlaybackState() == Player.STATE_IDLE) exoPlayer.prepare();
            exoPlayer.play();
        }
    }

    public void playNextFromMiniPlayer() { if (Boolean.TRUE.equals(core.onlineSongBlockedLive().getValue())) { restoreFromCacheAndPlayNext(); } else { advanceToNext(); } }
    public void playNextFromFullPlayer()  { core.cancelSleepFade(); advanceToNext(); }

    private void advanceToNext() {
        List<Song> snapshot = core.snapshotPlaylist();
        Player exoPlayer = core.player();
        if (exoPlayer == null || snapshot.isEmpty()) return;
        // Always use ExoPlayer's actual index as source of truth for rapid clicks
        int currentIndex = exoPlayer.getCurrentMediaItemIndex();
        int nextIndex = currentIndex + 1;
        if (nextIndex >= snapshot.size()) {
            if (exoPlayer.getRepeatMode() == Player.REPEAT_MODE_ALL) nextIndex = 0;
            else {
                exoPlayer.pause();
                core.isPlayingLive().postValue(false);
                core.endOfQueueLive().postValue(true);
                core.showEndDialogLive().postValue(true);
                return;
            }
        }
        Song next = snapshot.get(nextIndex);
        core.setCurrentIndex(nextIndex);
        core.currentSongLive().setValue(next);
        core.progressLive().setValue(0L);
        exoPlayer.seekToDefaultPosition(nextIndex);
        exoPlayer.play();
        core.persistLastIndex(nextIndex);
    }

    public void playPrevious() {
        Player exoPlayer = core.player();
        if (exoPlayer == null) return;
        core.cancelSleepFade(); // skipping cancels an in-progress fade-out
        if (Boolean.TRUE.equals(core.onlineSongBlockedLive().getValue())) {
            restoreFromCacheAndPlayPrevious();
            return;
        }
        if (core.queueList() == null) return;
        List<Song> snapshot = core.snapshotPlaylist();
        if (snapshot.isEmpty()) return;
        if (exoPlayer.getCurrentPosition() > 3000) {
            exoPlayer.seekTo(0);
            core.progressLive().postValue(0L);
            return;
        }
        int currentIndex = exoPlayer.getCurrentMediaItemIndex();
        int prevIndex = currentIndex - 1;
        if (prevIndex < 0) prevIndex = 0;
        Song prev = snapshot.get(prevIndex);
        core.setCurrentIndex(prevIndex);
        core.currentSongLive().setValue(prev);
        core.progressLive().setValue(0L);
        exoPlayer.seekToDefaultPosition(prevIndex);
        exoPlayer.play();
        core.persistLastIndex(prevIndex);
    }

    private void restoreFromCacheAndPlayPrevious() {
        SharedPreferences prefs = core.prefs().prefs();
        String queueStr = prefs.getString(PrefsStore.KEY_QUEUE_IDS, "");
        if (queueStr.isEmpty()) return;

        List<Song> allSongs = new ArrayList<>();
        List<Song> local = core.songsLive().getValue();
        if (local != null) allSongs.addAll(local);
        List<Song> online = core.allOnlineSongsLive().getValue();
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

        Song current = core.currentSongLive().getValue();
        int curIdx = 0;
        if (current != null) {
            for (int i = 0; i < restored.size(); i++) {
                if (restored.get(i).getId() == current.getId()) { curIdx = i; break; }
            }
        }
        int prevIdx = curIdx - 1;
        if (prevIdx < 0) prevIdx = 0;
        final int finalPrevIdx = prevIdx;
        core.withQueue(() -> {
            core.setQueue(restored);
            core.setCurrentIndex(finalPrevIdx);
        });
        core.onlineSongBlockedLive().setValue(false);

        Player exoPlayer = core.player();
        List<MediaItem> items = new ArrayList<>();
        for (Song s : restored) items.add(core.createMediaItem(s));
        long savedPos = 0L;
        exoPlayer.setMediaItems(items, finalPrevIdx, savedPos);
        exoPlayer.prepare();
        exoPlayer.play();
        core.currentSongLive().setValue(restored.get(finalPrevIdx));
        core.progressLive().setValue(0L);
        core.updateQueueUI();
        core.persistLastIndex(finalPrevIdx);
    }

    private void restoreFromCacheAndPlayNext() {
        SharedPreferences prefs = core.prefs().prefs();
        String queueStr = prefs.getString(PrefsStore.KEY_QUEUE_IDS, "");
        if (queueStr.isEmpty()) return;

        List<Song> allSongs = new ArrayList<>();
        List<Song> local = core.songsLive().getValue();
        if (local != null) allSongs.addAll(local);
        List<Song> online = core.allOnlineSongsLive().getValue();
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

        Song current = core.currentSongLive().getValue();
        int curIdx = 0;
        if (current != null) {
            for (int i = 0; i < restored.size(); i++) {
                if (restored.get(i).getId() == current.getId()) { curIdx = i; break; }
            }
        }
        int nextIdx = curIdx + 1;
        if (nextIdx >= restored.size()) return;

        core.withQueue(() -> {
            core.setQueue(restored);
            core.setCurrentIndex(nextIdx);
        });
        core.onlineSongBlockedLive().setValue(false);

        Player exoPlayer = core.player();
        List<MediaItem> items = new ArrayList<>();
        for (Song s : restored) items.add(core.createMediaItem(s));
        exoPlayer.setMediaItems(items, nextIdx, 0L);
        exoPlayer.prepare();
        exoPlayer.play();
        core.currentSongLive().setValue(restored.get(nextIdx));
        core.progressLive().setValue(0L);
        core.updateQueueUI();
        core.persistLastIndex(nextIdx);
    }

    public void playSongFromQueue(Song song) {
        Player exoPlayer = core.player();
        if (exoPlayer == null) return;
        List<Song> snapshot = core.snapshotPlaylist();
        if (snapshot.isEmpty()) return;
        core.markUserInteracted();
        core.cancelSleepFade(); // skipping cancels an in-progress fade-out
        int index = snapshot.indexOf(song);
        if (index >= 0) {
            core.setCurrentIndex(index);
            core.currentSongLive().setValue(song);
            core.progressLive().setValue(0L);
            exoPlayer.seekToDefaultPosition(index);
            exoPlayer.play();
            core.persistLastIndex(index);
        }
    }

    public void removeSongFromUpcoming(Song song) {
        if (core.queueList() == null || core.player() == null) return;
        core.markUserInteracted();
        core.withQueue(() -> {
            List<Song> currentPlaylist = core.queueList();
            int currentIndex = core.currentIndex();
            int index = currentPlaylist.indexOf(song);
            if (index < 0 || index == currentIndex) return; // not found / protect Now Playing
            currentPlaylist.remove(index);
            if (index < currentIndex) {
                // Removing before the current song re-indexes it: position-preserving re-sync.
                int newCurrent = currentIndex - 1;
                core.setCurrentIndex(newCurrent);
                resyncQueue(currentPlaylist, newCurrent, capturePosition());
                core.persistLastIndex(newCurrent);
            } else {
                // Removing after the current song: its index is unchanged, so the
                // incremental remove keeps playback position intact.
                core.player().removeMediaItem(index);
            }
            core.updateQueueUI();
        });
    }

    public void removeSongsFromUpcoming(Set<Song> songsToRemove) {
        if (core.queueList() == null || core.player() == null || songsToRemove == null || songsToRemove.isEmpty()) return;
        core.markUserInteracted();
        core.withQueue(() -> {
            List<Song> currentPlaylist = core.queueList();
            int currentIndex = core.currentIndex();
            List<Integer> indicesToRemove = new ArrayList<>();
            for (Song s : songsToRemove) {
                int idx = currentPlaylist.indexOf(s);
                if (idx >= 0 && idx != currentIndex) indicesToRemove.add(idx);
            }
            if (indicesToRemove.isEmpty()) return;
            Collections.sort(indicesToRemove, Collections.reverseOrder());
            int removedBeforeCurrent = 0;
            for (int idx : indicesToRemove) {
                currentPlaylist.remove(idx);
                if (idx < currentIndex) removedBeforeCurrent++;
            }
            if (removedBeforeCurrent > 0) {
                // Something before the current song was removed: it re-indexed, so
                // position-preserving re-sync to keep the playing track in place.
                int newCurrent = currentIndex - removedBeforeCurrent;
                core.setCurrentIndex(newCurrent);
                resyncQueue(currentPlaylist, newCurrent, capturePosition());
                core.persistLastIndex(newCurrent);
            } else {
                // All removals were after the current song: its index is unchanged, so
                // incremental removes (descending order) keep the playback position.
                for (int idx : indicesToRemove) {
                    core.player().removeMediaItem(idx);
                }
            }
            core.updateQueueUI();
        });
    }

    /**
     * Rebuild the player timeline from {@code queue} WITHOUT restarting the current song.
     * Keeps the current track at {@code newIndex} and resumes it at {@code positionMs}.
     *
     * <p>Used by every index-shifting edit (reorder, play-next, remove-before-current,
     * insert-before-current, clear-queue). The alternative — {@code moveMediaItem} or
     * {@code removeMediaItem} that re-indexes the current item — is a destructive playlist
     * change in Media3 that resets the current position to 0, which is the "reorder creates
     * a new queue" bug. {@code setMediaItems(items, idx, posMs)} with a captured position
     * is the position-preserving path.
     *
     * <p>Must run on the main thread (it touches the player). Callers already are.
     */
    private void resyncQueue(List<Song> queue, int newIndex, long positionMs) {
        Player p = core.player();
        if (p == null) return;
        if (positionMs < 0 || positionMs == C.TIME_UNSET) {
            positionMs = 0L;
        }
        List<MediaItem> items = new ArrayList<>(queue.size());
        for (Song s : queue) items.add(core.createMediaItem(s));
        p.setMediaItems(items, newIndex, positionMs);
    }

    /** Captured current playback position (0 if the player has none yet). */
    private long capturePosition() {
        Player p = core.player();
        if (p == null) return 0L;
        long pos = p.getCurrentPosition();
        return pos < 0 || pos == C.TIME_UNSET ? 0L : pos;
    }

    /** Takes absolute indices allowing drags across the entire unified queue. */
    public void moveSongInUpcoming(int fromAbsolute, int toAbsolute) {
        if (core.queueList() == null || core.player() == null) return;
        core.markUserInteracted();
        core.withQueue(() -> {
            List<Song> currentPlaylist = core.queueList();
            int currentIndex = core.currentIndex();
            if (fromAbsolute < 0 || fromAbsolute >= currentPlaylist.size() || toAbsolute < 0 || toAbsolute >= currentPlaylist.size()) return;
            if (fromAbsolute == toAbsolute) return;
            if (fromAbsolute == currentIndex || toAbsolute == currentIndex) return; // Protect Now Playing

            Song song = currentPlaylist.remove(fromAbsolute);
            currentPlaylist.add(toAbsolute, song);

            // Retro 3-case current-index bookkeeping. The current item is pinned
            // (guard above), so only a move that *crosses* currentIndex shifts it.
            int newCurrent = currentIndex;
            if (fromAbsolute < currentIndex && toAbsolute > currentIndex) {
                newCurrent = currentIndex - 1;
            } else if (fromAbsolute > currentIndex && toAbsolute < currentIndex) {
                newCurrent = currentIndex + 1;
            }
            core.setCurrentIndex(newCurrent);

            // Position-preserving re-sync: rebuild the timeline but keep the current
            // track at newCurrent and resume where it was — no restart, no rebuffer.
            resyncQueue(currentPlaylist, newCurrent, capturePosition());
            core.persistLastIndex(newCurrent);
            core.updateQueueUI();
        });
    }

    public void setPlayNext(Song song) {
        if (core.player() == null || core.queueList() == null || core.queueList().isEmpty()) {
            playSong(song, Collections.singletonList(song));
            return;
        }
        core.markUserInteracted();
        core.withQueue(() -> {
            List<Song> currentPlaylist = core.queueList();
            int currentIndex = core.currentIndex();
            if (currentIndex < 0 || currentIndex >= currentPlaylist.size()) return;
            if (currentPlaylist.get(currentIndex).getId() == song.getId()) return;
            Player p = core.player();
            // Insert the new "play next" item at currentIndex + 1. Add FIRST (index
            // after the current item) so the current item's player/timeline index
            // stays stable, then remove a pre-existing copy if any. Doing it the
            // other way around shifts the current item's player index and leaves
            // the VM's currentIndex out of sync with ExoPlayer → IndexOutOfBounds
            // crash in the 300ms queue-drift check that follows.
            int insertIndex = currentIndex + 1;
            p.addMediaItems(insertIndex, Collections.singletonList(core.createMediaItem(song)));
            currentPlaylist.add(insertIndex, song);
            int existingIdx = -1;
            for (int i = 0; i < currentPlaylist.size(); i++) {
                if (i == insertIndex) continue;
                if (currentPlaylist.get(i).getId() == song.getId()) { existingIdx = i; break; }
            }
            if (existingIdx == -1) {
                core.setCurrentIndex(currentIndex);
                core.persistLastIndex(currentIndex);
                core.updateQueueUI();
                return;
            }
            // Remove the older duplicate (never the copy we just inserted).
            p.removeMediaItems(existingIdx, 1);
            currentPlaylist.remove(existingIdx);
            if (existingIdx < currentIndex) currentIndex--;
            core.setCurrentIndex(currentIndex);
            core.persistLastIndex(currentIndex);
            core.updateQueueUI();
        });
    }

    public void addToQueue(Song song) {
        if (core.player() == null || core.queueList() == null || core.queueList().isEmpty()) {
            playSong(song, Collections.singletonList(song));
            return;
        }
        core.markUserInteracted();
        core.withQueue(() -> {
            List<Song> currentPlaylist = core.queueList();
            if (currentPlaylist.stream().anyMatch(s -> s.getId() == song.getId())) return;
            currentPlaylist.add(song);
            core.player().addMediaItem(core.createMediaItem(song));
            core.updateQueueUI();
        });
    }

    public void seekTo(long positionMs) {
        Player exoPlayer = core.player();
        if (exoPlayer != null) {
            exoPlayer.seekTo(positionMs);
            core.progressLive().postValue(positionMs);
        }
    }

    public void toggleRepeat() {
        Player exoPlayer = core.player();
        if (exoPlayer == null) return;
        int next = exoPlayer.getRepeatMode() == Player.REPEAT_MODE_OFF  ? Player.REPEAT_MODE_ALL
                : exoPlayer.getRepeatMode() == Player.REPEAT_MODE_ALL  ? Player.REPEAT_MODE_ONE
                : Player.REPEAT_MODE_OFF;
        exoPlayer.setRepeatMode(next);
        core.repeatModeLive().postValue(next);

        core.executor().execute(() -> {
            core.application().getSharedPreferences(PrefsStore.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putInt(PrefsStore.KEY_REPEAT_MODE, next).commit();
        });
    }

    public void toggleShuffle() {
        boolean cur  = Boolean.TRUE.equals(core.shuffleModeLive().getValue());
        boolean next = !cur;
        if (core.player() == null || core.queueList() == null || core.queueList().isEmpty()) return;
        core.withQueue(() -> {
            List<Song> currentPlaylist = core.queueList();
            if (next) {
                core.setOriginalBeforeShuffle(new ArrayList<>(currentPlaylist));
                shuffleUpcomingQueue();
            } else {
                unshuffleUpcomingQueue();
            }
            core.player().setShuffleModeEnabled(false);
        });
        core.shuffleModeLive().setValue(next);

        // Capture the pre-shuffle full queue order for prefs (only set when
        // shuffling ON) so a shuffle OFF from the system notification (service)
        // can restore the same baseline. Read after the monitor block — the
        // list is set/cleared inside withQueue and toggleShuffle is main-thread.
        final String persistOriginal;
        if (next) {
            List<Song> original = core.originalBeforeShuffle();
            StringBuilder sb = new StringBuilder();
            if (original != null) {
                for (int i = 0; i < original.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(original.get(i).getId());
                }
            }
            persistOriginal = sb.toString();
        } else {
            persistOriginal = "";
        }

        core.executor().execute(() -> {
            android.content.SharedPreferences.Editor edit = core.application().getSharedPreferences(PrefsStore.PREFS_NAME, Context.MODE_PRIVATE).edit();
            edit.putBoolean(PrefsStore.KEY_SHUFFLE_MODE, next);
            if (!persistOriginal.isEmpty()) {
                edit.putString(PrefsStore.KEY_ORIGINAL_QUEUE_IDS, persistOriginal);
            } else {
                edit.remove(PrefsStore.KEY_ORIGINAL_QUEUE_IDS);
            }
            edit.commit();
        });
    }

    private void shuffleUpcomingQueue() {
        int currentIndex = core.currentIndex();
        List<Song> currentPlaylist = core.queueList();
        if (currentIndex < 0) return;
        if (currentPlaylist == null || currentPlaylist.size() <= currentIndex + 1) return;
        List<Song> upcoming = new ArrayList<>(currentPlaylist.subList(currentIndex + 1, currentPlaylist.size()));
        Collections.shuffle(upcoming);
        List<MediaItem> newItems = new ArrayList<>();
        for (Song s : upcoming) newItems.add(core.createMediaItem(s));
        core.withQueue(() -> {
            List<Song> lp = core.queueList();
            for (int i = 0; i < upcoming.size(); i++) lp.set(currentIndex + 1 + i, upcoming.get(i));
            core.player().removeMediaItems(currentIndex + 1, lp.size());
            core.player().addMediaItems(currentIndex + 1, newItems);
        });
        core.updateQueueUI();
    }

    private void unshuffleUpcomingQueue() {
        int currentIndex = core.currentIndex();
        List<Song> currentPlaylist = core.queueList();
        if (currentIndex < 0) return;
        if (currentPlaylist == null || currentPlaylist.size() <= currentIndex + 1) return;
        List<Song> original = core.originalBeforeShuffle();
        if (original == null || original.isEmpty()) return;

        List<Song> newQueue = new ArrayList<>(original.subList(0, currentIndex + 1));
        List<Song> upcomingOriginal = original.subList(currentIndex + 1, original.size());
        newQueue.addAll(upcomingOriginal);
        int newSize = newQueue.size();
        core.withQueue(() -> {
            core.setQueue(newQueue);
            core.player().removeMediaItems(currentIndex + 1, newSize);
        });
        List<MediaItem> newItems = new ArrayList<>();
        for (Song s : upcomingOriginal) newItems.add(core.createMediaItem(s));
        core.player().addMediaItems(currentIndex + 1, newItems);
        core.setOriginalBeforeShuffle(null);
        core.updateQueueUI();
    }

    public void playAllShuffled(List<Song> library) {
        playAllShuffled(library, null);
    }

    public void playAllShuffled(List<Song> library, String source) {
        if (library == null || library.isEmpty()) return;
        Player exoPlayer = core.player();
        if (exoPlayer == null) return;
        core.showEndDialogLive().setValue(false);
        core.endOfQueueLive().setValue(false);
        List<Song> shuffled = new ArrayList<>(library);
        Collections.shuffle(shuffled);
        core.nowPlayingSourceLive().setValue(resolveDisplaySource(source, shuffled.get(0)));
        core.markUserInteracted();
        core.withQueue(() -> {
            core.setQueue(shuffled);
            core.setCurrentIndex(0);
        });
        core.updateQueueUI();
        List<MediaItem> items = new ArrayList<>();
        for (Song s : shuffled) items.add(core.createMediaItem(s));
        exoPlayer.setShuffleModeEnabled(false);
        exoPlayer.setMediaItems(items, 0, 0L);
        exoPlayer.prepare();
        exoPlayer.play();
        core.shuffleModeLive().setValue(true);

        core.executor().execute(() -> {
            core.application().getSharedPreferences(PrefsStore.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(PrefsStore.KEY_SHUFFLE_MODE, true).commit();
        });

        core.currentSongLive().setValue(shuffled.get(0));
        core.progressLive().setValue(0L);
        core.persistLastIndex(0);
    }

    public void playShuffled(List<Song> playlist) {
        playShuffled(playlist, null);
    }

    public void playShuffled(List<Song> playlist, String source) {
        if (playlist == null || playlist.isEmpty()) return;
        Player exoPlayer = core.player();
        if (exoPlayer == null) return;
        core.nowPlayingSourceLive().setValue(resolveDisplaySource(source, playlist.get(0)));
        Song       currentSong = core.currentSongLive().getValue();
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

        core.markUserInteracted();
        core.withQueue(() -> {
            core.setCurrentIndex(0);
            core.setQueue(shuffled);
        });
        core.updateQueueUI();
        List<MediaItem> items = new ArrayList<>();
        for (Song s : shuffled) items.add(core.createMediaItem(s));
        exoPlayer.setShuffleModeEnabled(false);
        core.shuffleModeLive().setValue(true);
        core.currentSongLive().setValue(shuffled.get(0));
        if (nowIdx >= 0 && exoPlayer.isPlaying()) {
            long currentPos = exoPlayer.getCurrentPosition();
            exoPlayer.setMediaItems(items, 0, currentPos);
            return;
        }
        exoPlayer.setMediaItems(items, 0, 0L);
        exoPlayer.prepare();
        exoPlayer.play();
        core.endOfQueueLive().setValue(false);
        core.showEndDialogLive().setValue(false);
        core.persistLastIndex(0);
    }

    public void dismissEndOfQueue() {
        core.showEndDialogLive().postValue(false);
        core.endOfQueueLive().postValue(false);
    }

    public void playFromBeginning() {
        core.showEndDialogLive().setValue(false);
        core.endOfQueueLive().setValue(false);
        Player exoPlayer = core.player();
        if (exoPlayer == null || core.queueList() == null || core.queueList().isEmpty()) return;
        core.shuffleModeLive().setValue(false);
        exoPlayer.setShuffleModeEnabled(false);
        core.withQueue(() -> {
            core.setCurrentIndex(0);
            core.currentSongLive().setValue(core.queueList().get(0));
        });
        core.progressLive().setValue(0L);
        exoPlayer.seekToDefaultPosition(0);
        exoPlayer.play();
        core.persistLastIndex(0);
    }

    public void addListToQueue(List<Song> songs) {
        if (songs == null || songs.isEmpty()) return;
        if (core.player() == null || core.queueList() == null || core.queueList().isEmpty()) {
            playSong(songs.get(0), songs);
            return;
        }
        core.markUserInteracted();
        core.withQueue(() -> {
            List<Song> currentPlaylist = core.queueList();
            Set<Long> existingIds = new LinkedHashSet<>();
            for (Song s : currentPlaylist) existingIds.add(s.getId());
            for (Song song : songs) {
                if (!existingIds.contains(song.getId())) {
                    currentPlaylist.add(song);
                    existingIds.add(song.getId());
                    core.player().addMediaItem(core.createMediaItem(song));
                }
            }
            core.updateQueueUI();
        });
    }

    public void clearQueue() {
        if (core.player() == null || core.queueList() == null || core.queueList().isEmpty()) return;
        int currentIndex = core.currentIndex();
        if (currentIndex < 0 || currentIndex >= core.queueList().size()) return;
        core.markUserInteracted();
        core.withQueue(() -> {
            Song current = core.queueList().get(currentIndex);
            // Keep only the current song (Apple Music behavior) via one
            // position-preserving re-sync — the playing track is at index 0 and
            // resumes where it was, instead of a destructive removeMediaItems reset.
            core.setQueue(new ArrayList<>(Collections.singletonList(current)));
            core.setCurrentIndex(0);
            core.setOriginalBeforeShuffle(null);
            resyncQueue(core.queueList(), 0, capturePosition());
        });
        core.updateQueueUI();
        core.persistLastIndex(0);
    }

    public void restoreSongsToQueue(List<Song> songs, int afterIndex) {
        if (songs == null || songs.isEmpty() || core.player() == null || core.queueList() == null) return;
        core.markUserInteracted();
        core.withQueue(() -> {
            int currentIndex = core.currentIndex();
            List<Song> currentPlaylist = core.queueList();
            if (afterIndex < currentIndex) {
                // Inserting before the current song re-indexes it: position-preserving
                // re-sync to the shifted index, keeping the playing track in place.
                int insertAt = afterIndex + 1;
                for (Song song : songs) {
                    currentPlaylist.add(insertAt, song);
                    insertAt++;
                }
                int newCurrent = currentIndex + songs.size();
                core.setCurrentIndex(newCurrent);
                resyncQueue(currentPlaylist, newCurrent, capturePosition());
                core.persistLastIndex(newCurrent);
            } else {
                // Inserting after the current song: its index is unchanged, so
                // incremental adds preserve the playback position.
                int insertAt = afterIndex + 1;
                for (Song song : songs) {
                    currentPlaylist.add(insertAt, song);
                    core.player().addMediaItem(insertAt, core.createMediaItem(song));
                    insertAt++;
                }
            }
            core.updateQueueUI();
        });
    }

    /**
     * Apple Music behavior: when the queue finishes, keep the music going with a
     * fresh shuffled batch from the library (recently played excluded first).
     * Returns true if an autoplay batch was queued.
     */
    public boolean attemptAutoplay() {
        // Opt-in Apple Music autoplay: disabled by default so the queue ends
        // cleanly (end-of-queue dialog) instead of being silently refilled.
        if (!core.autoplayEnabled()) return false;
        Player exoPlayer = core.player();
        if (exoPlayer == null || !isDataSaverEligibleLibraryAvailable()) return false;
        List<Song> library = new ArrayList<>();
        List<Song> local = core.songsLive().getValue();
        if (local != null) library.addAll(local);
        List<Song> online = core.allOnlineSongsLive().getValue();
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
        List<Song> snapshot = core.snapshotPlaylist();
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
        for (Song s : autoplay) items.add(core.createMediaItem(s));
        core.withQueue(() -> core.queueList().addAll(autoplay));
        exoPlayer.addMediaItems(items);
        core.updateQueueUI();
        com.nandu.mymusic.utils.AppLog.d(com.nandu.mymusic.utils.AppLog.PLAYER, "Autoplay added " + count + " songs to the queue");
        return true;
    }

    private boolean isDataSaverEligibleLibraryAvailable() {
        List<Song> local = core.songsLive().getValue();
        List<Song> online = core.allOnlineSongsLive().getValue();
        return (local != null && !local.isEmpty()) || (online != null && !online.isEmpty());
    }

    // ── Playback error resilience (used by the VM's player listener) ───────

    /** The VM listener owns the consecutive-error counter; this skips past a
     *  broken track so the queue keeps moving (Apple Music behavior). */
    public void skipBrokenTrack() {
        Player exoPlayer = core.player();
        if (exoPlayer == null || core.queueList() == null || core.queueList().isEmpty()) return;
        exoPlayer.seekToNextMediaItem();
        exoPlayer.prepare();
        exoPlayer.play();
    }
}
