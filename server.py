#!/usr/bin/env python3
"""Catgirl Downloader - Web Backend Server"""

import os
import json
import uuid
import shutil
import threading
from datetime import datetime, timezone
from typing import Optional

from flask import Flask, jsonify, request, send_file
from flask_cors import CORS
import io

from src.catgirl import CatgirlDownloaderAPI
from src.waifu import WaifuDownloaderAPI
from src.danbooru import DanbooruDownloaderAPI

app = Flask(__name__, static_folder="static", static_url_path="")
CORS(app)

# ── In-memory image cache ──────────────────────────────────────────
_image_cache: dict[str, dict] = {}
_cache_lock = threading.Lock()

# ── Preload queue ──────────────────────────────────────────────────
_preload_queue: list[str] = []
_MAX_PRELOAD = 5
_preload_lock = threading.Lock()

# ── Source registry ─────────────────────────────────────────────────
SOURCES = {
    "catgirl": {
        "name": "Catgirl (nekos.moe)",
        "has_tags": False,
    },
    "waifu": {
        "name": "Waifu (waifu.im)",
        "has_tags": False,
    },
    "danbooru": {
        "name": "Danbooru",
        "has_tags": True,
    },
}

NSFW_MODES = ["BLOCK_NSFW", "ONLY_NSFW", "SHOW_EVERYTHING"]

# ── Preferences file ────────────────────────────────────────────────
CONFIG_DIR = os.path.join(os.path.expanduser("~"), ".config", "catgirldownloader-web")
CONFIG_FILE = os.path.join(CONFIG_DIR, "config.json")

_DEFAULT_CONFIG = {
    "lang": "auto",
    "source": "catgirl",
    "nsfw_mode": "BLOCK_NSFW",
    "auto_reload": False,
    "auto_reload_interval": 30,
    "danbooru_tags": "",
    "keyboard_enabled": True,
    "key_next": "Enter",
    "key_prev": "ArrowLeft",
    "key_download": "Space",
    "key_favorite": "KeyF",
}

# ── Favorites storage ──────────────────────────────────────────────
FAVORITES_DIR = os.path.join(CONFIG_DIR, "favorites")
FAVORITES_FILE = os.path.join(FAVORITES_DIR, "favorites.json")
os.makedirs(FAVORITES_DIR, exist_ok=True)


def _load_config() -> dict:
    try:
        if os.path.exists(CONFIG_FILE):
            with open(CONFIG_FILE, "r") as f:
                cfg = json.load(f)
                return {**_DEFAULT_CONFIG, **cfg}
    except Exception as e:
        print(f"Error loading config: {e}")
    return dict(_DEFAULT_CONFIG)


def _save_config(cfg: dict) -> None:
    os.makedirs(CONFIG_DIR, exist_ok=True)
    merged = {**_DEFAULT_CONFIG, **cfg}
    with open(CONFIG_FILE, "w") as f:
        json.dump(merged, f, indent=2)


# ── API: Sources ────────────────────────────────────────────────────
@app.route("/api/sources")
def api_sources():
    return jsonify(list(SOURCES.values()))


# ── API: Config ─────────────────────────────────────────────────────
@app.route("/api/config", methods=["GET"])
def api_get_config():
    return jsonify(_load_config())


@app.route("/api/config", methods=["PUT"])
def api_set_config():
    data = request.get_json(force=True)
    cfg = _load_config()
    for key in _DEFAULT_CONFIG:
        if key in data:
            cfg[key] = data[key]
    _save_config(cfg)

    # Clear preload queue when source/nsfw/tags change
    if any(k in data for k in ("source", "nsfw_mode", "danbooru_tags")):
        with _preload_lock:
            _preload_queue.clear()

    # If Danbooru tags changed, update the API instance
    if "danbooru_tags" in data:
        danbooru_instance = _get_api("danbooru")
        danbooru_instance.set_tags(data["danbooru_tags"])

    return jsonify(cfg)


# ── API: Fetch image ────────────────────────────────────────────────
def _get_api(source: str):
    if source == "catgirl":
        return CatgirlDownloaderAPI()
    elif source == "waifu":
        return WaifuDownloaderAPI()
    elif source == "danbooru":
        api = DanbooruDownloaderAPI()
        cfg = _load_config()
        api.set_tags(cfg.get("danbooru_tags", ""))
        return api
    return None


# ── Helpers ─────────────────────────────────────────────────────────
_MIME_MAP = {
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".png": "image/png",
    ".gif": "image/gif",
    ".webp": "image/webp",
}


def _resolve_mime_ext(url: str) -> tuple[str, str]:
    url_lower = url.lower()
    for e, m in _MIME_MAP.items():
        if url_lower.endswith(e):
            return m, e.lstrip(".")
    return "image/jpeg", "jpg"


def _fetch_and_cache(api, source: str, nsfw: str = "BLOCK_NSFW") -> Optional[str]:
    """Fetch one image from an API instance and cache it. Returns cache_key or None."""
    image_url = api.get_image_url(nsfw)
    if not image_url:
        return None
    image_data = api.get_image(image_url)
    if not image_data:
        return None

    mime, ext = _resolve_mime_ext(image_url)
    artist = api.get_artist()
    link = api.get_link()
    filename = api.get_filename_suggestion(ext)

    cache_key = str(uuid.uuid4())
    with _cache_lock:
        _image_cache[cache_key] = {
            "bytes": image_data,
            "mime": mime,
            "filename": filename,
            "artist": artist,
            "link": link,
            "source": source,
        }
        while len(_image_cache) > 50:
            oldest = next(iter(_image_cache))
            del _image_cache[oldest]

    return cache_key


def _preload_images(source: str, nsfw: str):
    """Background task: fill preload queue up to _MAX_PRELOAD."""
    with _preload_lock:
        needed = _MAX_PRELOAD - len(_preload_queue)
    for _ in range(needed):
        try:
            api = _get_api(source)
            if not api:
                break
            cache_key = _fetch_and_cache(api, source, nsfw)
            if cache_key:
                with _preload_lock:
                    _preload_queue.append(cache_key)
        except Exception as e:
            print(f"Preload error: {e}")
            break


# ── API: Fetch image ────────────────────────────────────────────────
@app.route("/api/fetch")
def api_fetch():
    source = request.args.get("source", "catgirl")
    nsfw = request.args.get("nsfw", "BLOCK_NSFW")

    if source not in SOURCES:
        return jsonify({"error": f"Unknown source: {source}"}), 400

    # Preload queue hit — return instantly
    with _preload_lock:
        if _preload_queue:
            cache_key = _preload_queue.pop(0)
            with _cache_lock:
                entry = _image_cache.get(cache_key)
            if entry:
                threading.Thread(target=_preload_images, args=(source, nsfw), daemon=True).start()
                return jsonify({
                    "key": cache_key,
                    "artist": entry["artist"],
                    "link": entry["link"],
                    "filename": entry["filename"],
                    "source": entry["source"],
                    "mime": entry["mime"],
                })

    # Normal fetch
    api = _get_api(source)
    if not api:
        return jsonify({"error": "Failed to create API instance"}), 500

    image_url = api.get_image_url(nsfw)
    if not image_url:
        return jsonify({"error": "Failed to get image URL from source"}), 502

    image_data = api.get_image(image_url)
    if not image_data:
        return jsonify({"error": "Failed to download image data"}), 502

    mime, ext = _resolve_mime_ext(image_url)
    artist = api.get_artist()
    link = api.get_link()
    filename = api.get_filename_suggestion(ext)

    cache_key = str(uuid.uuid4())
    with _cache_lock:
        _image_cache[cache_key] = {
            "bytes": image_data,
            "mime": mime,
            "filename": filename,
            "artist": artist,
            "link": link,
            "source": source,
        }
        while len(_image_cache) > 50:
            oldest = next(iter(_image_cache))
            del _image_cache[oldest]

    # Trigger background preload
    threading.Thread(target=_preload_images, args=(source, nsfw), daemon=True).start()

    return jsonify({
        "key": cache_key,
        "artist": artist,
        "link": link,
        "filename": filename,
        "source": source,
        "mime": mime,
    })


# ── API: Serve image ────────────────────────────────────────────────
@app.route("/api/image/<key>")
def api_serve_image(key):
    with _cache_lock:
        entry = _image_cache.get(key)
    if not entry:
        return jsonify({"error": "Image not found or expired"}), 404

    return send_file(
        io.BytesIO(entry["bytes"]),
        mimetype=entry["mime"],
        as_attachment=False,
    )


# ── API: Download image ─────────────────────────────────────────────
@app.route("/api/download/<key>")
def api_download_image(key):
    with _cache_lock:
        entry = _image_cache.get(key)
    if not entry:
        return jsonify({"error": "Image not found or expired"}), 404

    return send_file(
        io.BytesIO(entry["bytes"]),
        mimetype=entry["mime"],
        as_attachment=True,
        download_name=entry["filename"],
    )


# ── Favorites helpers ──────────────────────────────────────────────
def _load_favorites() -> list[dict]:
    try:
        if os.path.exists(FAVORITES_FILE):
            with open(FAVORITES_FILE, "r") as f:
                return json.load(f)
    except Exception as e:
        print(f"Error loading favorites: {e}")
    return []


def _save_favorites(favs: list[dict]) -> None:
    with open(FAVORITES_FILE, "w") as f:
        json.dump(favs, f, indent=2, ensure_ascii=False)


# ── API: Favorites ─────────────────────────────────────────────────
@app.route("/api/favorites", methods=["GET"])
def api_list_favorites():
    return jsonify(_load_favorites())


@app.route("/api/favorites/<cache_key>", methods=["POST"])
def api_add_favorite(cache_key: str):
    with _cache_lock:
        entry = _image_cache.get(cache_key)
    if not entry:
        return jsonify({"error": "Image not found in cache"}), 404

    fav_id = str(uuid.uuid4())
    ext = entry["filename"].rsplit(".", 1)[-1] if "." in entry["filename"] else "jpg"
    file_path = os.path.join(FAVORITES_DIR, f"{fav_id}.{ext}")

    with open(file_path, "wb") as f:
        f.write(entry["bytes"])

    fav_entry = {
        "id": fav_id,
        "filename": entry["filename"],
        "ext": ext,
        "artist": entry["artist"],
        "link": entry["link"],
        "source": entry["source"],
        "mime": entry["mime"],
        "saved_at": datetime.now(timezone.utc).isoformat(),
    }

    favs = _load_favorites()
    favs.insert(0, fav_entry)
    _save_favorites(favs)

    return jsonify(fav_entry)


@app.route("/api/favorites/<fav_id>/image")
def api_serve_favorite(fav_id: str):
    favs = _load_favorites()
    fav = next((f for f in favs if f["id"] == fav_id), None)
    if not fav:
        return jsonify({"error": "Favorite not found"}), 404

    file_path = os.path.join(FAVORITES_DIR, f"{fav_id}.{fav['ext']}")
    if not os.path.exists(file_path):
        return jsonify({"error": "Favorite file missing"}), 404

    return send_file(file_path, mimetype=fav["mime"])


@app.route("/api/favorites/<fav_id>", methods=["DELETE"])
def api_delete_favorite(fav_id: str):
    favs = _load_favorites()
    fav = next((f for f in favs if f["id"] == fav_id), None)
    if not fav:
        return jsonify({"error": "Favorite not found"}), 404

    file_path = os.path.join(FAVORITES_DIR, f"{fav_id}.{fav['ext']}")
    if os.path.exists(file_path):
        os.remove(file_path)

    favs = [f for f in favs if f["id"] != fav_id]
    _save_favorites(favs)
    return jsonify({"ok": True})


# ── Serve frontend ──────────────────────────────────────────────────
@app.route("/")
def index():
    return app.send_static_file("index.html")


# ── Main ────────────────────────────────────────────────────────────
if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5000))
    host = os.environ.get("HOST", "0.0.0.0")
    print(f"Catgirl Downloader Web — http://{host}:{port}")
    app.run(host=host, port=port, debug=True)
