package com.nandu.mymusic.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.Player;

/**
 * A-B point-to-point loop: the user marks a start (A) and end (B) position on the
 * current song and the player bounces between them repeatedly, like a DJ cue
 * repeat. Structurally mirrors {@link SleepTimerManager}: it owns its own state +
 * LiveData and exposes a small clamp hook that the VM's 16 ms progress poll calls.
 *
 * <p>The loop is scoped to a single song: {@link #clearForNewSong()} (called from
 * the VM's {@code onMediaItemTransition}) turns it off when the track changes.
 */
public final class AbLoopManager {

    /** UI state: 0 = off, 1 = A set (waiting for B), 2 = actively looping. */
    private static final int OFF = 0;
    private static final int A_SET = 1;
    private static final int LOOPING = 2;

    private final MusicViewModelCore core;

    private long pointA = -1;
    private long pointB = -1;
    private int state = OFF;

    private final MutableLiveData<Integer> stateLiveData = new MutableLiveData<>(OFF);

    public AbLoopManager(MusicViewModelCore core) {
        this.core = core;
    }

    public LiveData<Integer> getState() { return stateLiveData; }

    public int getStateValue() { return state; }
    public long getPointA() { return pointA; }
    public long getPointB() { return pointB; }
    public boolean isLooping() { return state == LOOPING; }

    // ── Point setters (main thread, called from the VM) ────────────────────

    /** Mark A at the current playback position. If B was set, it's now stale. */
    public void setPointA() {
        Player p = core.player();
        if (p == null) return;
        pointA = p.getCurrentPosition();
        if (pointB <= pointA) {
            pointB = -1;
            state = A_SET;
        }
        stateLiveData.postValue(state);
    }

    /** Mark B at the current playback position; only valid when B > A. */
    public void setPointB() {
        Player p = core.player();
        if (p == null) return;
        long b = p.getCurrentPosition();
        if (pointA >= 0 && b > pointA) {
            pointB = b;
            state = LOOPING;
            stateLiveData.postValue(state);
        }
    }

    /** Turn the loop off and discard both points. */
    public void stop() {
        pointA = -1;
        pointB = -1;
        state = OFF;
        stateLiveData.postValue(state);
    }

    /** Called from onMediaItemTransition: the loop never spans songs. */
    public void clearForNewSong() {
        if (state == OFF) return;
        pointA = -1;
        pointB = -1;
        state = OFF;
        stateLiveData.postValue(state);
    }

    /**
     * Called from the VM's 16 ms progress poll (main thread). While looping, bounce
     * the position back to A the moment it reaches B.
     */
    public void clampPosition() {
        if (state != LOOPING) return;
        Player p = core.player();
        if (p == null || pointB <= pointA) return;
        if (p.getCurrentPosition() >= pointB) {
            p.seekTo(pointA);
        }
    }
}
