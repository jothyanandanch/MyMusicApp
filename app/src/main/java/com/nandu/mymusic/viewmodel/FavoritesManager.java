package com.nandu.mymusic.viewmodel;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.nandu.mymusic.model.Song;
import com.nandu.mymusic.service.MusicPlaybackService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Liked Songs (favorites): the id set + a Song map for O(1) lookups, persisted to
 * prefs and kept in sync with the system media notification's heart action.
 */
public final class FavoritesManager {

    private final MusicViewModelCore core;

    private final Map<Long, Song> favoritesMap = new LinkedHashMap<>();
    private final MutableLiveData<Set<Long>> favoriteIdsLiveData = new MutableLiveData<>(new LinkedHashSet<>());

    public FavoritesManager(MusicViewModelCore core) {
        this.core = core;
    }

    public LiveData<Set<Long>> getFavoriteIds() { return favoriteIdsLiveData; }

    /** Initialize from persisted prefs (called from the VM constructor). */
    public void loadFromPrefs() {
        loadFavoritesFromPrefs();
    }

    private void loadFavoritesFromPrefs() {
        SharedPreferences prefs = core.prefs().prefs();
        Set<String> saved = prefs.getStringSet(PrefsStore.KEY_FAV_IDS, new LinkedHashSet<>());
        Set<Long> restoredIds = new LinkedHashSet<>();
        if (saved != null) {
            for (String idStr : saved) {
                try { restoredIds.add(Long.parseLong(idStr)); } catch (NumberFormatException ignored) {}
            }
        }
        favoriteIdsLiveData.postValue(restoredIds);
    }

    /**
     * Re-read favorites from prefs (shared storage, same process as the media
     * session). Called when the notification's heart action toggles a song so
     * in-app UI stays authoritative.
     */
    public void refreshFromPrefs() {
        loadFavoritesFromPrefs();
        List<Song> songs = core.songsLive().getValue();
        if (songs != null && !songs.isEmpty()) syncFavoritesWithSongs(songs);
    }

    private void syncFavoritesWithSongs(List<Song> songs) {
        Set<Long> persistedIds = favoriteIdsLiveData.getValue();
        if (persistedIds == null || persistedIds.isEmpty()) return;
        Map<Long, Song> songById = new LinkedHashMap<>();
        for (Song s : songs) songById.put(s.getId(), s);
        favoritesMap.clear();
        for (Long id : persistedIds) {
            Song s = songById.get(id);
            if (s != null) favoritesMap.put(id, s);
        }
    }

    @SuppressLint("ApplySharedPref")
    public void toggleFavorite(Song song) {
        Set<Long> ids = new LinkedHashSet<>(favoriteIdsLiveData.getValue() != null ? favoriteIdsLiveData.getValue() : new LinkedHashSet<>());
        if (ids.contains(song.getId())) {
            ids.remove(song.getId());
            favoritesMap.remove(song.getId());
        } else {
            ids.add(song.getId());
            favoritesMap.put(song.getId(), song);
        }
        favoriteIdsLiveData.setValue(ids);
        persistFavorites(ids);
    }

    /**
     * Add/remove many songs from favorites in one pass. Bulk callers (Library
     * "select all" / "remove all") previously looped over toggleFavorite, which
     * fired one executor task per song. This collapses that to a single write.
     */
    @SuppressLint("ApplySharedPref")
    public void setFavoriteBatch(List<Song> songs, boolean favorite) {
        if (songs == null || songs.isEmpty()) return;
        Set<Long> ids = new LinkedHashSet<>(favoriteIdsLiveData.getValue() != null ? favoriteIdsLiveData.getValue() : new LinkedHashSet<>());
        boolean changed = false;
        for (Song song : songs) {
            if (favorite) {
                if (ids.add(song.getId())) {
                    favoritesMap.put(song.getId(), song);
                    changed = true;
                }
            } else {
                if (ids.remove(song.getId())) {
                    favoritesMap.remove(song.getId());
                    changed = true;
                }
            }
        }
        if (!changed) return;
        favoriteIdsLiveData.setValue(ids);
        persistFavorites(ids);
    }

    private void persistFavorites(Set<Long> ids) {
        core.executor().execute(() -> {
            Set<String> stringSet = new LinkedHashSet<>();
            for (Long id : ids) stringSet.add(String.valueOf(id));
            core.application().getSharedPreferences(PrefsStore.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putStringSet(PrefsStore.KEY_FAV_IDS, stringSet).commit();
            // Android 13+ SystemUI heart button reads prefs — refresh its icon.
            MusicPlaybackService.notifyMediaStateChange();
        });
    }

    // ── Read accessors used by other concerns (mixins, deletion) ─────────

    /** Fresh snapshot of the favorite Song objects (for personalized-mixes generation). */
    public List<Song> favorites() { return new ArrayList<>(favoritesMap.values()); }

    public boolean containsFavorite(long songId) { return favoritesMap.containsKey(songId); }

    /** Sync the favorites map with the local library (call after the library loads). */
    public void syncWithSongs(List<Song> songs) { syncFavoritesWithSongs(songs); }
}
