package com.nandu.mymusic.viewmodel;

import android.app.Application;
import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.nandu.mymusic.model.Song;
import com.nandu.mymusic.repository.LocalMusicRepository;
import com.nandu.mymusic.repository.OnlineMusicRepository;
import com.nandu.mymusic.utils.AppLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The music libraries themselves: the local MediaStore repository + the online
 * Firestore repository, their LiveData caches, in-app search, and the refresh /
 * cache-first fetch flows. Owns {@code allOnlineSongsLiveData}, the refresh
 * indicator, and the search results; the ViewModel re-exposes them.
 *
 * <p>Loading a library also refreshes other concerns (listening stats, favorites,
 * playlists, session restore). Those live in their own helpers, so this class does
 * NOT call them. Instead it reports each event to the ViewModel through
 * {@link Dispatcher}, which fans the event out to the right helpers. The three
 * online paths (cache-seed / startup-fetch / pull-to-refresh) fan out differently,
 * so the dispatcher carries that distinction.
 */
public final class LibraryData {

    /** Implemented by the ViewModel (the composition root) to fan library events out. */
    public interface Dispatcher {
        MutableLiveData<Boolean> isLoading();
        Set<Long> deletedIds();

        /** A local library load finished (list already published to the VM's LiveData). */
        void localLoaded(List<Song> local);

        /** The on-disk cache was loaded at startup (stats only, no fetch yet). */
        void onlineCached(List<Song> online);

        /** An online fetch finished. {@code isRefresh} = pull-to-refresh (immediate
         *  language-playlist generation, no mixes / no session restore);
         *  {@code !isRefresh} = startup fetch (generates mixes + the deferred
         *  language-playlist / match / restore block). */
        void onlineFetched(List<Song> online, boolean isRefresh);
    }

    private final MusicViewModelCore core;
    private final Dispatcher dispatcher;
    private final LocalMusicRepository repository;
    private final OnlineMusicRepository onlineRepository = new OnlineMusicRepository();

    private final MutableLiveData<List<Song>> allOnlineSongsLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isRefreshingOnlineLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<List<Song>> onlineSearchResults = new MutableLiveData<>(new ArrayList<>());

    public LibraryData(MusicViewModelCore core, Dispatcher dispatcher, LocalMusicRepository repository) {
        this.core = core;
        this.dispatcher = dispatcher;
        this.repository = repository;
    }

    // ── LiveData (re-exposed by the VM) ───────────────────────────────────

    public LiveData<List<Song>> getAllOnlineSongs() { return allOnlineSongsLiveData; }
    public LiveData<Boolean> getIsRefreshingOnline() { return isRefreshingOnlineLiveData; }
    public LiveData<List<Song>> getOnlineSearchResults() { return onlineSearchResults; }

    /** The live online-songs LiveData (same instance the VM re-exposes). */
    public MutableLiveData<List<Song>> allOnlineSongs() { return allOnlineSongsLiveData; }

    /** Seed from the on-disk cache at startup; fetch from Firestore if missing/expired. */
    public void seedFromCache(Application app) {
        List<Song> cached = onlineRepository.loadCachedSongs(app);
        if (!cached.isEmpty()) {
            allOnlineSongsLiveData.postValue(cached);
            dispatcher.onlineCached(cached);
        }
        if (cached.isEmpty() || onlineRepository.isCacheExpired(app)) {
            AppLog.d(AppLog.REPO, "Cache missing or expired — fetching from Firestore");
            fetch(app, false);
        } else {
            AppLog.d(AppLog.REPO, "Cache is fresh — skipping Firestore fetch");
        }
    }

    private void fetch(Application app, boolean isRefresh) {
        onlineRepository.fetchAllOnlineSongs(new OnlineMusicRepository.OnMusicFetchedListener() {
            @Override
            public void onSuccess(List<Song> songs) {
                allOnlineSongsLiveData.postValue(songs);
                onlineRepository.saveCache(app, songs);
                if (isRefresh) isRefreshingOnlineLiveData.postValue(false);
                dispatcher.onlineFetched(songs, isRefresh);
            }

            @Override
            public void onError(Exception e) {
                AppLog.e(AppLog.REPO, "Failed to fetch online songs: " + e.getMessage());
            }
        });
    }

    // ── Search ────────────────────────────────────────────────────────────

    public void searchOnlineMusic(String query) {
        if (query == null || query.trim().isEmpty()) {
            onlineSearchResults.postValue(new ArrayList<>());
            return;
        }
        String queryLower = query.toLowerCase().trim();
        List<Song> allOnline = allOnlineSongsLiveData.getValue();
        List<Song> matches = new ArrayList<>();
        if (allOnline != null) {
            for (Song song : allOnline) {
                boolean matchTitle = song.getTitle() != null && song.getTitle().toLowerCase().contains(queryLower);
                boolean matchArtist = song.getArtist() != null && song.getArtist().toLowerCase().contains(queryLower);
                boolean matchAlbum = song.getAlbum() != null && song.getAlbum().toLowerCase().contains(queryLower);
                if (matchTitle || matchArtist || matchAlbum) matches.add(song);
            }
        }
        onlineSearchResults.postValue(matches);
    }

    // ── Online refresh (pull-to-refresh) ──────────────────────────────────

    public void refreshOnlineMusic() {
        isRefreshingOnlineLiveData.postValue(true);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (Boolean.TRUE.equals(isRefreshingOnlineLiveData.getValue())) {
                AppLog.e(AppLog.REPO, "Refresh timed out — resetting indicator");
                isRefreshingOnlineLiveData.postValue(false);
            }
        }, 10_000);
        try {
            fetch(core.application(), true);
        } catch (Exception e) {
            AppLog.e(AppLog.REPO, "refreshOnlineMusic exception: " + e.getMessage());
            isRefreshingOnlineLiveData.postValue(false);
        }
    }

    // ── Local load ────────────────────────────────────────────────────────

    public void loadLocalMusic() {
        dispatcher.isLoading().postValue(true);
        core.executor().execute(() -> {
            List<Song> local = repository.fetchLocalSongs();
            // Filter out songs that were previously deleted but not yet removed from MediaStore
            Set<Long> deleted = dispatcher.deletedIds();
            if (!deleted.isEmpty()) {
                List<Song> filtered = new ArrayList<>();
                for (Song s : local) {
                    if (!deleted.contains(s.getId())) {
                        filtered.add(s);
                    }
                }
                local = filtered;
            }
            core.songsLive().postValue(local);
            dispatcher.isLoading().postValue(false);
            dispatcher.localLoaded(local);
        });
    }
}
