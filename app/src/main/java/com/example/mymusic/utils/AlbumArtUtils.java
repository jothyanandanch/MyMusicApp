package com.example.mymusic.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

public class AlbumArtUtils {
	
	public static Bitmap getEmbeddedAlbumArt(Context context, Uri songUri) {
		MediaMetadataRetriever retriever = new MediaMetadataRetriever();
		try {
			retriever.setDataSource(context, songUri);
			byte[] art = retriever.getEmbeddedPicture(); // reads ID3 APIC frame
			if (art != null) {
				return BitmapFactory.decodeByteArray(art, 0, art.length);
			}
		} catch (Exception e) {
			AppLog.e(AppLog.REPO, "Failed to get art for: " + songUri);
		} finally {
			try { retriever.release(); } catch (Exception ignored) {}
		}
		return null; // fallback to placeholder in UI
	}
}