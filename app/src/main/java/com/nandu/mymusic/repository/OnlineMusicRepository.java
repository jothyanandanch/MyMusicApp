package com.nandu.mymusic.repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
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
	private static final String KEY_ONLINE_CACHE_TIMESTAMP = "online_songs_cache_timestamp";
	private static final long CACHE_TTL_MS = 24 * 60 * 60 * 1000L; // 24 hours

	private final FirebaseFirestore db = FirebaseFirestore.getInstance();

	public interface OnMusicFetchedListener {
		void onSuccess(List<Song> songs);
		void onError(Exception e);
	}

	public void fetchAllOnlineSongs(OnMusicFetchedListener listener) {
		AppLog.d(AppLog.REPO, "Fetching online library from Firestore...");
		try {
			db.collection("online_library").document("metadata")
					.get()
					.addOnCompleteListener(task -> {
						if (task.isSuccessful()) {
							DocumentSnapshot document = task.getResult();
							AppLog.d(AppLog.REPO, "Firestore fetch: exists=" + document.exists()
									+ ", hasData=" + (document.getData() != null));
							List<Song> allSongs = parseSongsFromDocument(document);
							AppLog.d(AppLog.REPO, "Parsed " + allSongs.size() + " songs from Firestore");
							listener.onSuccess(allSongs);
						} else {
							Exception ex = task.getException();
							AppLog.e(AppLog.REPO, "Firestore fetch failed: " + (ex != null ? ex.getMessage() : "unknown"));
							listener.onError(ex != null ? ex : new Exception("Firestore fetch failed"));
						}
					});
		} catch (Exception e) {
			AppLog.e(AppLog.REPO, "Firestore not available: " + e.getMessage());
			listener.onError(e);
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

	public boolean isCacheExpired(Context context) {
		SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		long cachedTimestamp = prefs.getLong(KEY_ONLINE_CACHE_TIMESTAMP, 0);
		if (cachedTimestamp == 0) return true;
		long ageMs = System.currentTimeMillis() - cachedTimestamp;
		boolean expired = ageMs > CACHE_TTL_MS;
		AppLog.d(AppLog.REPO, "Cache age: " + (ageMs / 3600000) + "h, expired=" + expired);
		return expired;
	}

	public void saveCache(Context context, List<Song> songs) {
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
			prefs.edit()
					.putString(KEY_ONLINE_CACHE, arr.toString())
					.putLong(KEY_ONLINE_CACHE_TIMESTAMP, System.currentTimeMillis())
					.apply();
			AppLog.d(AppLog.REPO, "Cache saved: " + songs.size() + " songs, timestamp=" + System.currentTimeMillis());
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
