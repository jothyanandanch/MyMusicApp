package com.nandu.mymusic.viewmodel;

import android.app.PendingIntent;
import android.os.Build;
import android.provider.MediaStore;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.nandu.mymusic.model.Song;
import com.nandu.mymusic.utils.AppLog;

import android.content.IntentSender;

import java.util.ArrayList;
import java.util.List;
import android.net.Uri;

/**
 * Permanently deleting a local song: direct {@code ContentResolver.delete} with a
 * MediaStore pending-intent fallback on Android 10–13. When the deletion is
 * confirmed, the song is cleared from all in-memory state (library, stats,
 * favorites, queue) via the core facade.
 */
public final class SongDeletion {

    private final MusicViewModelCore core;

    private final MutableLiveData<IntentSender> deleteIntentSenderLiveData = new MutableLiveData<>();
    private Song pendingDeleteSong = null;

    public SongDeletion(MusicViewModelCore core) {
        this.core = core;
    }

    public LiveData<IntentSender> getDeleteIntentSender() { return deleteIntentSenderLiveData; }

    public void clearDeleteIntent() { deleteIntentSenderLiveData.postValue(null); }

    public void deleteSong(Song song) {
        if (song == null) return;
        try {
            int deletedRows = core.application().getContentResolver().delete(song.getUri(), null, null);
            if (deletedRows > 0) {
                AppLog.d(AppLog.PERMISSION, "Song permanently deleted from device storage.");
                core.persistDeletedId(song.getId());
                core.removeFromState(song);
            }
        } catch (SecurityException e) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                List<Uri> uris = new ArrayList<>();
                uris.add(song.getUri());
                PendingIntent pi = MediaStore.createDeleteRequest(core.application().getContentResolver(), uris);
                pendingDeleteSong = song;
                deleteIntentSenderLiveData.postValue(pi.getIntentSender());
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                if (e instanceof android.app.RecoverableSecurityException) {
                    android.app.RecoverableSecurityException rse = (android.app.RecoverableSecurityException) e;
                    pendingDeleteSong = song;
                    deleteIntentSenderLiveData.postValue(rse.getUserAction().getActionIntent().getIntentSender());
                } else {
                    AppLog.e(AppLog.PERMISSION, "SecurityException: " + e.getMessage());
                }
            } else {
                AppLog.e(AppLog.PERMISSION, "Cannot physically delete file. " + e.getMessage());
            }
        }
    }

    public void confirmPendingDeletion() {
        if (pendingDeleteSong != null) {
            core.persistDeletedId(pendingDeleteSong.getId());
            core.removeFromState(pendingDeleteSong);
            pendingDeleteSong = null;
        }
    }
}
