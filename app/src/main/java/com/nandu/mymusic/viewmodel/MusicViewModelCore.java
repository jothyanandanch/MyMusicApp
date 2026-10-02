package com.nandu.mymusic.viewmodel;

import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;

import com.nandu.mymusic.model.Song;

import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * The surface the extracted concern helpers may use to reach the shared,
 * cross-cutting state owned by {@link MusicViewModel}.
 *
 * <p><b>Threading rules</b> (inherited from the original VM, must be preserved):
 * <ul>
 *   <li>{@link #player()} and the queue accessors may only be touched on the main
 *       thread (ExoPlayer is main-thread-only; the queue list is guarded by the VM's
 *       shared monitor). Every helper method is itself main-thread.
 *       Executor tasks must use {@link #snapshotPlaylist()}, {@link #prefs()},
 *       {@link #executor()} and the plain LiveData getters instead.</li>
 *   <li>Every queue mutation happens inside {@link #withQueue(Runnable)} so the
 *       single shared monitor (the VM instance) stays the source of truth.</li>
 * </ul>
 *
 * <p>Helpers reach each other ONLY through this facade (the VM is the composition
 * root); no helper holds a reference to another helper.
 */
public interface MusicViewModelCore {

    // ── Queue (live state — main thread, use inside withQueue) ─────────────
    /** The live mutable queue list. Read/mutate ONLY inside {@link #withQueue}. */
    List<Song> queueList();

    /** Thread-safe copy of the live queue. Iterate this, never the raw list. */
    List<Song> snapshotPlaylist();

    int currentIndex();
    void setCurrentIndex(int index);

    /** Replace the entire queue list (main thread, inside {@link #withQueue}). */
    void setQueue(List<Song> queue);

    List<Song> originalBeforeShuffle();
    void setOriginalBeforeShuffle(List<Song> list);

    /** Run {@code action} on the shared queue monitor. Use for every mutation. */
    void withQueue(Runnable action);

    /** User has started playback / curated the queue — aborts pending session restore. */
    boolean userInteractedWithPlayback();
    void markUserInteracted();

    /** Publish a fresh queue snapshot to LiveData and persist the IDs. */
    void updateQueueUI();

    /** Resolve {@code index} against the queue and persist last index+URI (off-thread write). */
    void persistLastIndex(int index);

    /** Persist the playback position (off-thread; safe after executor shutdown). */
    void persistLastPosition(long position);

    /** Main-thread: build a MediaItem for {@code song} (data-saver aware). */
    MediaItem createMediaItem(Song song);

    // ── Live player (main-thread only) ────────────────────────────────────
    Player player();

    // ── Shared LiveData (exposed as mutable so helpers can postValue/setValue;
    //    the VM owns these instances, helpers are same-package) ───────────
    MutableLiveData<List<Song>>   songsLive();
    MutableLiveData<List<Song>>   allOnlineSongsLive();
    MutableLiveData<Song>         currentSongLive();
    MutableLiveData<Boolean>      isPlayingLive();
    MutableLiveData<Long>        progressLive();
    MutableLiveData<Long>        durationLive();
    MutableLiveData<Integer>     repeatModeLive();
    MutableLiveData<Boolean>     shuffleModeLive();
    MutableLiveData<Boolean>     onlineSongBlockedLive();
    MutableLiveData<Boolean>     endOfQueueLive();
    MutableLiveData<Boolean>     showEndDialogLive();
    MutableLiveData<String>      nowPlayingSourceLive();
    MutableLiveData<Boolean>     isDataSaverModeLive();
    MutableLiveData<Integer>     libraryModeLive();
    boolean autoplayEnabled();

    // ── Shared infrastructure ─────────────────────────────────────────────
    PrefsStore prefs();
    ExecutorService executor();
    android.app.Application application();
    android.content.Context context();

    // ── Cross-concern actions (VM composes the helpers) ───────────────────
    /** Record a song that just started playing (listening stats + lyrics reset). */
    void onSongStarted(Song song);

    /** Abort an in-flight sleep fade and restore the pre-fade volume. */
    void cancelSleepFade();

    /**
     * Remove a permanently-deleted song from every piece of in-memory state
     * (library, listening stats, favorites, and the live queue + player).
     * Implemented by the VM so it can reach all the helpers.
     */
    void removeFromState(Song song);

    /** Record a locally-deleted song id so the library refresh skips it. */
    void persistDeletedId(long songId);

    /** The persisted set of locally-deleted ids (read to filter the library refresh). */
    java.util.Set<Long> deletedIds();

    /** (Re)start the 16ms progress-poll loop (VM owns the Handler + Runnable). */
    void restartProgressPolling();

    /** Reload the playlists for the newly-selected library mode (main thread). */
    void onLibraryModeChanged(int mode);
}
