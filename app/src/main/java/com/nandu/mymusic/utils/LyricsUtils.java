package com.nandu.mymusic.utils;

import android.content.Context;
import android.net.Uri;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
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
	public static String extractLyrics(Context context, Uri uri, String title, String artist) {
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
		
		// 3. Fallback: If local file had no lyrics, try fetching online anyway
		return fetchOnlineLyrics(title, artist);
	}
	
	// Fetches lyrics from a free public API
	private static String fetchOnlineLyrics(String title, String artist) {
		if (title == null || artist == null || title.contains("Unknown") || artist.contains("Unknown")) {
			return null; // Skip if metadata is missing
		}
		
		try {
			// Clean up titles like "Song Title (Official Music Video)"
			String cleanTitle = title.replaceAll("(?i)\\(.*?video.*?\\)|\\(.*?lyrics.*?\\)|\\[.*?]", "").trim();
			String cleanArtist = artist.trim();
			
			String urlString = "https://api.lyrics.ovh/v1/" +
					URLEncoder.encode(cleanArtist, "UTF-8") + "/" +
					URLEncoder.encode(cleanTitle, "UTF-8");
			
			URL url = new URL(urlString);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			connection.setConnectTimeout(5000);
			connection.setReadTimeout(5000);
			
			if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
				BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				StringBuilder response = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) {
					response.append(line).append("\n");
				}
				reader.close();
				
				JSONObject jsonObject = new JSONObject(response.toString());
				if (jsonObject.has("lyrics")) {
					return jsonObject.getString("lyrics").trim();
				}
			}
		} catch (Exception e) {
			AppLog.e(AppLog.REPO, "Online lyrics fetch failed: " + e.getMessage());
		}
		return null;
	}
}