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
			// LRCLIB API Endpoint
			String urlString = "https://lrclib.net/api/search?track_name="
					+ URLEncoder.encode(title, "UTF-8")
					+ "&artist_name=" + URLEncoder.encode(artist, "UTF-8");
			
			URL url = new URL(urlString);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			// LRCLIB requires a User-Agent header
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
				
				// Parse the JSON Array returned by LRCLIB
				JSONArray jsonArray = new JSONArray(response.toString());
				if (jsonArray.length() > 0) {
					// Get the best match (the first result)
					JSONObject bestMatch = jsonArray.getJSONObject(0);
					
					// Try to get synced lyrics first
					String syncedLyrics = bestMatch.optString("syncedLyrics", null);
					if (syncedLyrics != null && !syncedLyrics.equals("null") && !syncedLyrics.trim().isEmpty()) {
						return syncedLyrics;
					}
					
					// Fallback to plain lyrics if synced isn't available
					String plainLyrics = bestMatch.optString("plainLyrics", null);
					if (plainLyrics != null && !plainLyrics.equals("null")) {
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