package com.nandu.mymusic.viewmodel;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * Simple persisted user settings: data-saver quality, Apple Music "Autoplay"
 * opt-in, and the last-used sort order. Each is a prefs-backed LiveData the UI
 * observes.
 */
public final class SettingsManager {

    private final MusicViewModelCore core;
    private final MutableLiveData<Boolean> isDataSaverModeLiveData;
    private final MutableLiveData<Boolean> isAutoplayEnabledLiveData;
    private final MutableLiveData<String> sortTypeLiveData;

    public SettingsManager(MusicViewModelCore core, boolean initialDataSaver,
                           boolean initialAutoplay, String initialSort) {
        this.core = core;
        this.isDataSaverModeLiveData = new MutableLiveData<>(initialDataSaver);
        this.isAutoplayEnabledLiveData = new MutableLiveData<>(initialAutoplay);
        this.sortTypeLiveData = new MutableLiveData<>(initialSort);
    }

    public LiveData<Boolean> getDataSaverMode() { return isDataSaverModeLiveData; }
    public LiveData<Boolean> getIsAutoplayEnabled() { return isAutoplayEnabledLiveData; }
    public LiveData<String> getSortType() { return sortTypeLiveData; }

    /** Mutable accessors (same-package) so the VM can re-expose them to the core facade. */
    public MutableLiveData<Boolean> dataSaverMode() { return isDataSaverModeLiveData; }
    public MutableLiveData<Boolean> autoplayEnabled() { return isAutoplayEnabledLiveData; }
    public MutableLiveData<String> sortType() { return sortTypeLiveData; }

    /** Current autoplay preference (read for queue autoplay). */
    public boolean isAutoplayOn() {
        return Boolean.TRUE.equals(isAutoplayEnabledLiveData.getValue());
    }

    public void setDataSaverMode(boolean isEnabled) {
        isDataSaverModeLiveData.setValue(isEnabled);
        core.executor().execute(() -> {
            core.application().getSharedPreferences(PrefsStore.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(PrefsStore.KEY_DATA_SAVER, isEnabled).commit();
        });
    }

    /** Apple Music "Autoplay" — opt-in. When off, the queue simply ends (dialog). */
    public void setAutoplayEnabled(boolean isEnabled) {
        isAutoplayEnabledLiveData.setValue(isEnabled);
        core.executor().execute(() -> {
            core.application().getSharedPreferences(PrefsStore.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putBoolean(PrefsStore.KEY_AUTOPLAY_ENABLED, isEnabled).commit();
        });
    }

    public void setSortType(String sortTypeStr) {
        sortTypeLiveData.setValue(sortTypeStr);
        core.executor().execute(() -> {
            core.application().getSharedPreferences(PrefsStore.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putString(PrefsStore.KEY_SORT_TYPE, sortTypeStr).commit();
        });
    }
}
