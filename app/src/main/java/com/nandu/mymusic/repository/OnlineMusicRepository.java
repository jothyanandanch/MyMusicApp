package com.nandu.mymusic.repository;

import android.net.Uri;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.nandu.mymusic.model.Song;
import java.util.ArrayList;
import java.util.List;

public class OnlineMusicRepository {
	private final FirebaseFirestore db = FirebaseFirestore.getInstance();
	
	public interface OnMusicFetchedListener {
		void onSuccess(List<Song> songs);
		void onError(Exception e);
	}
	
	// Inside OnlineMusicRepository.java
	public void fetchAllOnlineSongs(OnMusicFetchedListener listener) {
		db.collection("songs")
				.get()
				.addOnCompleteListener(task -> {
					if (task.isSuccessful()) {
						List<Song> allSongs = new ArrayList<>();
						for (QueryDocumentSnapshot document : task.getResult()) {
							
							long id = document.getString("audioUrl") != null
									? Math.abs((long) document.getString("audioUrl").hashCode())
									: System.currentTimeMillis();
							String title = document.getString("title");
							String artist = document.getString("artist");
							String audioUrl = document.getString("audioUrl");
							
							long duration = 0L;
							Object durationObj = document.get("duration");
							if (durationObj instanceof Number) {
								duration = ((Number) durationObj).longValue();
							} else if (durationObj instanceof String) {
								try { duration = Long.parseLong((String) durationObj); }
								catch (NumberFormatException e) { duration = 0L; }
							}
							
							if (audioUrl != null) {
								Song song = new Song(
										id,
										artist != null ? artist : "Unknown Artist",
										title != null ? title : "Unknown Title",
										"Online Single",
										Uri.parse(audioUrl),
										duration,
										null
								);
								allSongs.add(song);
							}
						}
						listener.onSuccess(allSongs);
					} else {
						listener.onError(task.getException());
					}
				});
	}
	

}