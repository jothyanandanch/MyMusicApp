package com.nandu.mymusic.viewmodel;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.nandu.mymusic.model.Song;

/**
 * Library mode (Local / Cloud / Hybrid) and the "online song blocked by Local mode"
 * state. Switching to Local while an online song plays pauses it and blocks the
 * online queue; the service's ForwardingPlayer reads the persisted block flag to
 * drop playback commands.
 */
public final class LibraryModeController {

    private final MusicViewModelCore core;
    private final MutableLiveData<Integer> libraryModeLiveData;
    private final MutableLiveData<Boolean> onlineSongBlockedLiveData = new MutableLiveData<>(false);
    private boolean wasPlayingBeforeBlock = false;

    public LibraryModeController(MusicViewModelCore core, int initialMode) {
        this.core = core;
        this.libraryModeLiveData = new MutableLiveData<>(initialMode);
    }

    public LiveData<Integer> getLibraryMode() { return libraryModeLiveData; }
    public LiveData<Boolean> getOnlineSongBlocked() { return onlineSongBlockedLiveData; }

    /** Mutable accessors (same-package) so the VM can re-expose them to the core facade. */
    public MutableLiveData<Integer> libraryMode() { return libraryModeLiveData; }
    public MutableLiveData<Boolean> onlineSongBlocked() { return onlineSongBlockedLiveData; }

    public void setLibraryMode(int mode) {
        boolean wasBlocked = Boolean.TRUE.equals(onlineSongBlockedLiveData.getValue());
        libraryModeLiveData.setValue(mode);

        if (mode == 0 && core.currentSongLive().getValue() != null) {
            Song current = core.currentSongLive().getValue();
            String scheme = current.getUri().getScheme();
            boolean isOnlineSong = "http".equals(scheme) || "https".equals(scheme);

            if (isOnlineSong) {
                wasPlayingBeforeBlock = core.player() != null && core.player().isPlaying();
                if (core.player() != null) {
                    core.persistLastPosition(core.player().getCurrentPosition());
                    core.player().pause();
                }
                core.isPlayingLive().setValue(false);
                onlineSongBlockedLiveData.setValue(true);
            } else {
                onlineSongBlockedLiveData.setValue(false);
            }

            core.executor().execute(() -> {
                boolean isBlocked = Boolean.TRUE.equals(onlineSongBlockedLiveData.getValue());
                core.application().getSharedPreferences(PrefsStore.PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putInt(PrefsStore.KEY_LIBRARY_MODE, mode)
                        .putBoolean(PrefsStore.KEY_ONLINE_BLOCKED, isBlocked)
                        .apply();
            });
            core.onLibraryModeChanged(mode);
            return;
        }

        onlineSongBlockedLiveData.setValue(false);

        if (wasBlocked && wasPlayingBeforeBlock && mode != 0 && core.player() != null) {
            core.player().play();
        }
        wasPlayingBeforeBlock = false;

        core.executor().execute(() -> {
            boolean isBlocked = Boolean.TRUE.equals(onlineSongBlockedLiveData.getValue());
            core.application().getSharedPreferences(PrefsStore.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(PrefsStore.KEY_LIBRARY_MODE, mode)
                    .putBoolean(PrefsStore.KEY_ONLINE_BLOCKED, isBlocked)
                    .apply();

            core.onLibraryModeChanged(mode);
        });
    }
}
