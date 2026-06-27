package com.nandu.mymusic.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.nandu.mymusic.model.Song;
import com.nandu.mymusic.utils.AppLog;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OnlineMusicRepository {
	private static final String PREFS_NAME = "MyMusicPrefs";
	private static final String KEY_ONLINE_CACHE = "online_songs_cache";

	private final FirebaseFirestore db = FirebaseFirestore.getInstance();
	private ListenerRegistration snapshotListener;

	public interface OnMusicFetchedListener {
		void onSuccess(List<Song> songs);
		void onError(Exception e);
	}

	public void fetchAllOnlineSongs(OnMusicFetchedListener listener) {
		AppLog.d(AppLog.REPO, "Fetching online library from Firestore...");

		db.collection("online_library").document("metadata")
				.get()
				.addOnCompleteListener(task -> {
					if (task.isSuccessful()) {
						DocumentSnapshot document = task.getResult();
						List<Song> allSongs = parseSongsFromDocument(document);
						listener.onSuccess(allSongs);
					} else {
						listener.onError(task.getException());
					}
				});
	}

	public void listenForUpdates(Context context, OnMusicFetchedListener listener) {
		if (snapshotListener != null) return;

		AppLog.d(AppLog.REPO, "Starting Firestore snapshot listener...");
		snapshotListener = db.collection("online_library").document("metadata")
				.addSnapshotListener((documentSnapshot, e) -> {
					if (e != null) {
						AppLog.e(AppLog.REPO, "Snapshot listener error: " + e.getMessage());
						return;
					}
					if (documentSnapshot != null && documentSnapshot.exists()) {
						List<Song> songs = parseSongsFromDocument(documentSnapshot);
						saveCache(context, songs);
						listener.onSuccess(songs);
					}
				});
	}

	public void stopListening() {
		if (snapshotListener != null) {
			snapshotListener.remove();
			snapshotListener = null;
		}
	}

	public List<Song> loadCachedSongs(Context context) {
		SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		String json = prefs.getString(KEY_ONLINE_CACHE, null);
		if (json == null || json.isEmpty()) return new ArrayList<>();

		List<Song> songs = new ArrayList<>();
		try {
			JSONArray arr = new JSONArray(json);
			for (int i = 0; i < arr.length(); i++) {
				JSONObject obj = arr.getJSONObject(i);
				songs.add(new Song(
						obj.getLong("id"),
						obj.getString("artist"),
						obj.getString("title"),
						obj.getString("album"),
						Uri.parse(obj.getString("audioUrl")),
						obj.getLong("duration"),
						Uri.parse(obj.getString("coverUrl")),
						obj.optString("language", "Unknown")
				));
			}
		} catch (Exception e) {
			AppLog.e(AppLog.REPO, "Error loading cached songs: " + e.getMessage());
		}
		return songs;
	}

	private void saveCache(Context context, List<Song> songs) {
		try {
			JSONArray arr = new JSONArray();
			for (Song s : songs) {
				JSONObject obj = new JSONObject();
				obj.put("id", s.getId());
				obj.put("title", s.getTitle());
				obj.put("artist", s.getArtist());
				obj.put("album", s.getAlbum());
				obj.put("audioUrl", s.getUri().toString());
				obj.put("coverUrl", s.getAlbumArtUri() != null ? s.getAlbumArtUri().toString() : "");
				obj.put("duration", s.getDuration());
				obj.put("language", s.getLanguage());
				arr.put(obj);
			}
			SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
			prefs.edit().putString(KEY_ONLINE_CACHE, arr.toString()).apply();
		} catch (Exception e) {
			AppLog.e(AppLog.REPO, "Error saving cache: " + e.getMessage());
		}
	}

	private List<Song> parseSongsFromDocument(DocumentSnapshot document) {
		List<Song> allSongs = new ArrayList<>();
		if (document == null || !document.exists()) return allSongs;

		List<Map<String, Object>> songsArray = (List<Map<String, Object>>) document.get("songs");
		if (songsArray == null) return allSongs;

		for (Map<String, Object> songMap : songsArray) {
			try {
				long id = ((Number) songMap.get("id")).longValue();
				String title = (String) songMap.get("title");
				String artist = (String) songMap.get("artist");
				String album = (String) songMap.get("album");
				String audioUrl = (String) songMap.get("audioUrl");
				String coverUrl = (String) songMap.get("coverUrl");
				long duration = ((Number) songMap.get("duration")).longValue();
				String language = songMap.containsKey("language") ? (String) songMap.get("language") : "Unknown";

				allSongs.add(new Song(id, artist, title, album, Uri.parse(audioUrl), duration, Uri.parse(coverUrl), language));
			} catch (Exception e) {
				AppLog.e(AppLog.REPO, "Error parsing a song: " + e.getMessage());
			}
		}
		return allSongs;
	}
}
