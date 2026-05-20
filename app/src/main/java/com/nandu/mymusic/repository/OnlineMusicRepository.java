package com.nandu.mymusic.repository;

import android.net.Uri;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.nandu.mymusic.model.Song;
import com.nandu.mymusic.utils.AppLog;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OnlineMusicRepository {
	private final FirebaseFirestore db = FirebaseFirestore.getInstance();
	
	public interface OnMusicFetchedListener {
		void onSuccess(List<Song> songs);
		void onError(Exception e);
	}
	
	// ✅ Phase 2: Fetch the entire library EXACTLY ONCE
	public void fetchAllOnlineSongs(OnMusicFetchedListener listener) {
		AppLog.d(AppLog.REPO, "Fetching online library from Firestore...");
		
		db.collection("online_library").document("metadata")
				.get()
				.addOnCompleteListener(task -> {
					if (task.isSuccessful()) {
						DocumentSnapshot document = task.getResult();
						List<Song> allSongs = new ArrayList<>();
						
						if (document != null && document.exists()) {
							// Extract the "songs" array from the document
							List<Map<String, Object>> songsArray = (List<Map<String, Object>>) document.get("songs");
							
							if (songsArray != null) {
								for (Map<String, Object> songMap : songsArray) {
									try {
										long id = ((Number) songMap.get("id")).longValue();
										String title = (String) songMap.get("title");
										String artist = (String) songMap.get("artist");
										String album = (String) songMap.get("album");
										String audioUrl = (String) songMap.get("audioUrl");
										String coverUrl = (String) songMap.get("coverUrl");
										long duration = ((Number) songMap.get("duration")).longValue();
										
										Song song = new Song(
												id,
												artist,
												title,
												album,
												Uri.parse(audioUrl),
												duration,
												Uri.parse(coverUrl) // ✅ We now have exact cover URLs!
										);
										allSongs.add(song);
									} catch (Exception e) {
										AppLog.e(AppLog.REPO, "Error parsing a song: " + e.getMessage());
									}
								}
							}
						}
						listener.onSuccess(allSongs);
					} else {
						listener.onError(task.getException());
					}
				});
	}
}