package com.example.mymusic.utils;

import android.content.Context;
import android.net.Uri;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class LyricsUtils {
	
	public static String extractLyrics(Context context, Uri uri) {
		File tempFile = null;
		try {
			// Copy to a temp file so JAudioTagger can read it without Scoped Storage issues
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
				// Extracts USLT or SYLT lyrics frame
				String lyrics = tag.getFirst(FieldKey.LYRICS);
				if (lyrics != null && !lyrics.trim().isEmpty()) {
					return lyrics;
				}
			}
		} catch (Exception e) {
			AppLog.e(AppLog.REPO, "Failed to extract lyrics: " + e.getMessage());
		} finally {
			// Always clean up the temp file to prevent memory leaks
			if (tempFile != null && tempFile.exists()) {
				tempFile.delete();
			}
		}
		return null;
	}
}