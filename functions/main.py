"""
MyMusic Cloud Function — Add Song via JioSaavn URL

Deploy via Google Cloud Console → Cloud Functions → Create Function
  Runtime: Python 3.10+
  Trigger: HTTP
  Timeout: 120 seconds
  Memory: 512 MB

After deploy, copy the trigger URL and paste it into the app's
CLOUD_FUNCTION_URL constant in MusicViewModel.java.
"""

import os
import re
import json
import tempfile
import logging

import requests
import yt_dlp
from mutagen.mp3 import MP3
from mutagen.id3 import ID3, APIC

import firebase_admin
from firebase_admin import firestore
import cloudinary
import cloudinary.uploader

# ── Config ────────────────────────────────────────────────────────
LRCLIB_API = "https://lrclib.net/api/search"

cloudinary.config(
    cloud_name=os.environ.get("CLOUDINARY_CLOUD", "dpfiik07i"),
    api_key=os.environ.get("CLOUDINARY_KEY", "882392473694426"),
    api_secret=os.environ.get("CLOUDINARY_SECRET", "q3J4RSaZPC6s3FhiN1KoXnfIykc"),
    secure=True,
)

# Initialise Firebase Admin once (cold-start reuse)
if not firebase_admin._apps:
    firebase_admin.initialize_app()

db = firestore.client()
log = logging.getLogger(__name__)


# ── Helpers ───────────────────────────────────────────────────────

def safe_filename(artist: str, title: str) -> str:
    raw = f"{artist}_{title}".lower().replace(" ", "_")
    return re.sub(r"[^a-z0-9_]", "", raw)


def fetch_synced_lyrics(title: str, artist: str):
    """Fetch synchronized .lrc lyrics from lrclib.net."""
    log.info("Searching lyrics: %s — %s", title, artist)
    try:
        resp = requests.get(
            LRCLIB_API,
            params={"track_name": title, "artist_name": artist},
            timeout=10,
        )
        results = resp.json()
        if isinstance(results, list):
            for r in results:
                if r.get("syncedLyrics"):
                    return r["syncedLyrics"]
    except Exception as exc:
        log.warning("Lyrics fetch failed: %s", exc)
    return None


def extract_cover(filepath: str, out_dir: str, name: str) -> str:
    """Pull the embedded album art out of an MP3 and save as jpg."""
    try:
        tags = ID3(filepath)
        for tag in tags.values():
            if isinstance(tag, APIC):
                cover_path = os.path.join(out_dir, f"{name}.jpg")
                with open(cover_path, "wb") as f:
                    f.write(tag.data)
                return cover_path
    except Exception as exc:
        log.warning("Cover extraction failed: %s", exc)
    return ""


def upload_cloudinary(filepath: str, resource_type: str, folder: str, public_id: str) -> str:
    """Upload a file to Cloudinary and return the secure URL."""
    try:
        res = cloudinary.uploader.upload(
            filepath,
            resource_type=resource_type,
            folder=folder,
            public_id=public_id,
            overwrite=True,
            invalidate=True,
        )
        return res.get("secure_url", "")
    except Exception as exc:
        log.error("Cloudinary upload failed: %s", exc)
        return ""


def download_audio(url: str, dest_dir: str):
    """
    Use yt-dlp to extract info + download audio from a JioSaavn URL.
    No FFmpeg needed — JioSaavn already serves MP3.
    Returns (info_dict, filepath_to_mp3).
    """
    outtmpl = os.path.join(dest_dir, "%(title)s.%(ext)s")
    ydl_opts = {
        "format": "bestaudio/best",
        "outtmpl": outtmpl,
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        # Skip FFmpeg postprocessors — JioSaavn audio is already MP3
    }

    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(url, download=True)
        base = ydl.prepare_filename(info)
        # yt-dlp may keep .webm/.m4a — find whatever was downloaded
        if os.path.exists(base):
            mp3_path = base
        else:
            # Try common extensions
            for ext in ["mp3", "webm", "m4a", "ogg"]:
                candidate = base.rsplit(".", 1)[0] + f".{ext}"
                if os.path.exists(candidate):
                    mp3_path = candidate
                    break
            else:
                # Fallback: find any file in dest_dir
                files = os.listdir(dest_dir)
                mp3_path = os.path.join(dest_dir, files[0]) if files else ""
        return info, mp3_path


# ── Main entry point ─────────────────────────────────────────────

def addSong(request):
    """HTTP Cloud Function — POST { "url": "<jiosaavn_link>", "language": "Telugu" }"""
    # CORS
    headers = {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "POST, OPTIONS",
        "Access-Control-Allow-Headers": "Content-Type",
    }

    if request.method == "OPTIONS":
        return ("", 204, headers)

    if request.method != "POST":
        return (json.dumps({"error": "POST required"}), 405, headers)

    try:
        body = request.get_json(silent=True) or {}
    except Exception:
        return (json.dumps({"error": "Invalid JSON body"}), 400, headers)

    url = (body.get("url") or "").strip()
    language = (body.get("language") or "Telugu").strip()

    if not url:
        return (json.dumps({"error": "Missing 'url' field"}), 400, headers)

    # ── 1. Determine next song ID ────────────────────────────────
    doc_ref = db.collection("online_library").document("metadata")
    doc = doc_ref.get()
    existing = doc.to_dict().get("songs", []) if doc.exists else []
    next_id = (max((s.get("id", 1000) for s in existing), default=999) + 1) if existing else 1000

    # ── 2. Download via yt-dlp ────────────────────────────────────
    tmp_dir = tempfile.mkdtemp()
    try:
        info, mp3_path = download_audio(url, tmp_dir)
    except Exception as exc:
        log.error("Download failed: %s", exc)
        return (json.dumps({"error": f"Download failed: {exc}"}), 502, headers)

    if not mp3_path or not os.path.exists(mp3_path):
        return (json.dumps({"error": "Audio file not found after download"}), 502, headers)

    title = info.get("track") or info.get("title") or "Unknown"
    artist = info.get("artist") or info.get("creator") or "Unknown"
    album = info.get("album") or "Single"
    main_artist = artist.split(",")[0].strip() if artist else ""

    safe_name = safe_filename(artist, title)

    # ── 3. Extract cover art ──────────────────────────────────────
    cover_path = extract_cover(mp3_path, tmp_dir, safe_name)

    # ── 4. Upload audio to Cloudinary ─────────────────────────────
    audio_url = upload_cloudinary(mp3_path, "video", "songs", safe_name)
    if not audio_url:
        return (json.dumps({"error": "Audio upload to Cloudinary failed"}), 502, headers)

    # ── 5. Upload cover to Cloudinary ─────────────────────────────
    cover_url = ""
    if cover_path and os.path.exists(cover_path):
        cover_url = upload_cloudinary(cover_path, "image", "covers", safe_name)

    # ── 6. Fetch lyrics ───────────────────────────────────────────
    lyrics = fetch_synced_lyrics(title, main_artist)

    # ── 7. Compute duration ───────────────────────────────────────
    try:
        audio_info = MP3(mp3_path)
        duration_ms = int(audio_info.info.length * 1000)
    except Exception:
        duration_ms = info.get("duration", 0) * 1000 if info.get("duration") else 0

    # ── 8. Write to Firestore ─────────────────────────────────────
    song_entry = {
        "id": next_id,
        "title": title,
        "artist": artist,
        "album": album,
        "duration": duration_ms,
        "audioUrl": audio_url,
        "coverUrl": cover_url,
        "language": language,
    }
    if lyrics:
        song_entry["lyrics"] = lyrics

    try:
        doc_ref.update({"songs": firestore.ArrayUnion([song_entry])})
    except Exception as exc:
        log.error("Firestore write failed: %s", exc)
        return (json.dumps({"error": f"Firestore write failed: {exc}"}), 500, headers)

    # ── 9. Cleanup temp files ─────────────────────────────────────
    try:
        for f in os.listdir(tmp_dir):
            os.remove(os.path.join(tmp_dir, f))
        os.rmdir(tmp_dir)
    except Exception:
        pass

    log.info("Song added: %s — %s (id=%d)", artist, title, next_id)
    return (json.dumps({"ok": True, "song": song_entry}), 200, headers)
