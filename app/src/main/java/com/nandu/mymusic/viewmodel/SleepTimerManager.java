package com.nandu.mymusic.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.Player;

/**
 * Apple Music sleep timer: time-based, track-based, and end-of-queue, with a
 * ~5s graceful fade-out before pausing (Apple Music fades instead of hard-
 * pausing). Owns its own state and the two {@link android.os.CountDownTimer}s.
 *
 * <p>The player listener (in the VM) calls the small hooks
 * {@link #onAutoTrackTransition(int)} and {@link #onEnded(int)} so the exact
 * early-return behavior of the original inline logic is preserved.
 */
public final class SleepTimerManager {

    /** Apple Music: ~5s graceful fade before the sleep timer pauses playback. */
    private static final long SLEEP_FADE_MS = 5000L;
    private static final long SLEEP_FADE_TICK_MS = 100L;

    private final MusicViewModelCore core;

    private android.os.CountDownTimer sleepTimer;
    /** Fade-out timer used at the sleep timer's end. */
    private android.os.CountDownTimer sleepFadeTimer;
    /** Player volume before a sleep fade, restored on pause/cancel. */
    private volatile float volBeforeSleepFade = 1f;
    /** True while a sleep fade-out is running. */
    private volatile boolean sleepFadeActive = false;

    private final MutableLiveData<Boolean> isSleepTimerActive = new MutableLiveData<>(false);
    private final MutableLiveData<Long> sleepTimerRemainingLiveData = new MutableLiveData<>(0L);
    private final MutableLiveData<Integer> sleepTimerTracksRemainingLiveData = new MutableLiveData<>(-1);
    private int sleepTrackCounter = -1; // -1 means inactive
    private boolean stopAtQueueEnd = false;
    /** True when the track-based timer fired on this song's completion. */
    private boolean trackTimerFired = false;

    public SleepTimerManager(MusicViewModelCore core) {
        this.core = core;
    }

    public LiveData<Boolean> getIsSleepTimerActive() { return isSleepTimerActive; }
    public LiveData<Long> getSleepTimerRemaining() { return sleepTimerRemainingLiveData; }
    public LiveData<Integer> getSleepTimerTracksRemaining() { return sleepTimerTracksRemainingLiveData; }

    // ── Player-listener hooks (called from the VM) ─────────────────────────

    /**
     * From onMediaItemTransition: under REPEAT_MODE_OFF / ALL a new item
     * beginning means the previous song completed a pass. The VM only calls
     * this for AUTO transitions. Decrement the "tracks remaining" counter so
     * "When Current Song Ends" lands even when the queue advances.
     */
    public void onAutoTrackTransition(int reason) {
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            completeOneSongPass();
        }
    }

    /**
     * From the VM's progress poll: under REPEAT_MODE_ONE no transition and no
     * STATE_ENDED ever fires (the same item loops in place), so the only signal
     * that "the current song ended" is the playback position wrapping back to
     * the start. The VM detects that wrap and calls this.
     */
    public void onPositionWrap() {
        completeOneSongPass();
    }

    private void completeOneSongPass() {
        if (sleepTrackCounter > 0) {
            sleepTrackCounter--;
            sleepTimerTracksRemainingLiveData.postValue(sleepTrackCounter);
            if (sleepTrackCounter == 0) {
                if (stopAtQueueEnd) {
                    triggerSleepTimerAction();
                } else {
                    stopImmediately();
                }
            }
        }
    }

    /** Track-based sleep timer: pause immediately when the last track completes. */
    private void stopImmediately() {
        Player exoPlayer = core.player();
        if (exoPlayer == null) { cancelSleepTimer(); trackTimerFired = true; return; }
        exoPlayer.pause();
        core.isPlayingLive().postValue(false);
        cancelSleepTimer();
        trackTimerFired = true; // set after cancelSleepTimer resets it
    }

    /**
     * From onPlaybackStateChanged(STATE_ENDED): a song ended with nothing left
     * to auto-advance to (last song of the queue, REPEAT_MODE_OFF). That is
     * also a completed pass, so decrement; then handle the end-of-queue timer.
     * Returns true when the sleep timer took the end-of-queue path, so the VM's
     * normal autoplay/end-dialog flow is skipped.
     */
    public boolean onEnded(int repeatMode) {
        completeOneSongPass();
        if (stopAtQueueEnd) {
            triggerSleepTimerAction();
            return true;
        }
        // Track-based timer: suppress autoplay/end-dialog if we just stopped.
        boolean fired = trackTimerFired;
        trackTimerFired = false;
        return fired;
    }

    // ── Timer control (public API) ─────────────────────────────────────────

    // 1. Time-Based Timer
    public void startTimeSleepTimer(int minutes) {
        cancelSleepTimer();
        isSleepTimerActive.postValue(true);

        long timeMs = minutes * 60 * 1000L;
        sleepTimer = new android.os.CountDownTimer(timeMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                sleepTimerRemainingLiveData.postValue(millisUntilFinished);
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
        trackTimerFired = false;
        isSleepTimerActive.postValue(false);
        sleepTimerRemainingLiveData.postValue(0L);
        sleepTimerTracksRemainingLiveData.postValue(-1);
    }

    private void triggerSleepTimerAction() {
        // Don't cancel the timer yet: the fade must finish cleanly either way.
        Player exoPlayer = core.player();
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
    public void cancelSleepFade() {
        if (sleepFadeTimer != null) {
            sleepFadeTimer.cancel();
            sleepFadeTimer = null;
        }
        if (sleepFadeActive) {
            sleepFadeActive = false;
            Player exoPlayer = core.player();
            if (exoPlayer != null) exoPlayer.setVolume(volBeforeSleepFade);
        }
    }
}