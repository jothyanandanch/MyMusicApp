package com.nandu.mymusic.utils;

import android.content.Context;
import android.net.Uri;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class LyricsUtils {
	
	// We added title and artist parameters so we can use them to search online!
	// Notice the 5th parameter: boolean allowOnlineFetch
	public static String extractLyrics(Context context, Uri uri, String title, String artist, boolean allowOnlineFetch) {
		String scheme = uri.getScheme();
		
		// 1. If it's an online song, skip JAudioTagger and fetch from Web API directly
		if ("http".equals(scheme) || "https".equals(scheme)) {
			return fetchOnlineLyrics(title, artist);
		}
		
		// 2. If it's local, extract from the file's ID3 tags
		File tempFile = null;
		try {
			tempFile = File.createTempFile("temp_lyrics", ".mp3", context.getCacheDir());
			
			try (InputStream input = context.getContentResolver().openInputStream(uri);
				 FileOutputStream output = new FileOutputStream(tempFile)) {
				if (input != null) {
					byte[] buffer = new byte[4096];
					int read;
					while ((read = input.read(buffer)) != -1) {
						output.write(buffer, 0, read);
					}
				}
			}
			
			AudioFile audioFile = AudioFileIO.read(tempFile);
			Tag tag = audioFile.getTag();
			if (tag != null) {
				String lyrics = tag.getFirst(FieldKey.LYRICS);
				if (lyrics != null && !lyrics.trim().isEmpty()) {
					return lyrics;
				}
			}
		} catch (Exception e) {
			AppLog.e(AppLog.REPO, "Failed to extract local lyrics: " + e.getMessage());
		} finally {
			if (tempFile != null && tempFile.exists()) tempFile.delete();
		}
		
		// 3. Fallback: Only hit the online API if allowOnlineFetch is true!
		if (allowOnlineFetch) {
			return fetchOnlineLyrics(title, artist);
		}
		
		// Return null if local failed and online is not allowed
		return null;
	}
	
	// Fetches lyrics from a free public API
// Inside LyricsUtils.java
	private static String fetchOnlineLyrics(String title, String artist) {
		try {
			// 1. URL Encode and replace "+" with "%20" to match Python's requests library behavior
			String encTitle = URLEncoder.encode(title, "UTF-8").replace("+", "%20");
			String encArtist = URLEncoder.encode(artist, "UTF-8").replace("+", "%20");
			
			// LRCLIB API Endpoint
			String urlString = "https://lrclib.net/api/search?track_name=" + encTitle + "&artist_name=" + encArtist;
			
			URL url = new URL(urlString);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			// LRCLIB requires a User-Agent header (Python requests sends one automatically)
			connection.setRequestProperty("User-Agent", "MyMusicApp/1.0");
			connection.setConnectTimeout(5000);
			connection.setReadTimeout(5000);
			
			if (connection.getResponseCode() == 200) {
				BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				StringBuilder response = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) {
					response.append(line);
				}
				reader.close();
				
				JSONArray jsonArray = new JSONArray(response.toString());
				
				// 2. Loop through ALL results looking for syncedLyrics (Just like the Python script)
				for (int i = 0; i < jsonArray.length(); i++) {
					JSONObject result = jsonArray.getJSONObject(i);
					String syncedLyrics = result.optString("syncedLyrics", null);
					
					if (syncedLyrics != null && !syncedLyrics.equals("null") && !syncedLyrics.trim().isEmpty()) {
						return syncedLyrics; // Found synced lyrics!
					}
				}
				
				// 3. Fallback: If no synced lyrics are found anywhere, try to get plain lyrics from the first match
				if (jsonArray.length() > 0) {
					String plainLyrics = jsonArray.getJSONObject(0).optString("plainLyrics", null);
					if (plainLyrics != null && !plainLyrics.equals("null") && !plainLyrics.trim().isEmpty()) {
						return plainLyrics;
					}
				}
			}
		} catch (Exception e) {
			AppLog.e(AppLog.LYRICS, "Error fetching from LRCLIB: " + e.getMessage());
		}
		return null;
	}
}