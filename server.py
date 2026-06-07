#!/usr/bin/env python3
"""Catgirl Downloader - Web Backend Server"""

import os
import json
import uuid
import mimetypes
from typing import Optional

from flask import Flask, jsonify, request, send_file, render_template
from flask_cors import CORS
import io

from src.catgirl import CatgirlDownloaderAPI
from src.waifu import WaifuDownloaderAPI
from src.danbooru import DanbooruDownloaderAPI

app = Flask(__name__, static_folder="static", static_url_path="")
CORS(app)

# ── In-memory image cache ──────────────────────────────────────────
# Maps cache_key -> {"bytes": bytes, "mime": str, "filename": str,
#                    "artist": Optional[str], "link": Optional[str],
#                    "source": str}
_image_cache: dict[str, dict] = {}

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
}


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


@app.route("/api/fetch")
def api_fetch():
    source = request.args.get("source", "catgirl")
    nsfw = request.args.get("nsfw", "BLOCK_NSFW")

    if source not in SOURCES:
        return jsonify({"error": f"Unknown source: {source}"}), 400

    api = _get_api(source)
    if not api:
        return jsonify({"error": "Failed to create API instance"}), 500

    image_url = api.get_image_url(nsfw)
    if not image_url:
        return jsonify({"error": "Failed to get image URL from source"}), 502

    image_data = api.get_image(image_url)
    if not image_data:
        return jsonify({"error": "Failed to download image data"}), 502

    # Determine mime type from URL extension
    url_lower = image_url.lower()
    mime_map = {
        ".jpg": "image/jpeg",
        ".jpeg": "image/jpeg",
        ".png": "image/png",
        ".gif": "image/gif",
        ".webp": "image/webp",
    }
    ext = None
    for e, m in mime_map.items():
        if url_lower.endswith(e):
            mime = m
            ext = e.lstrip(".")
            break
    else:
        mime = "image/jpeg"
        ext = "jpg"

    # Get metadata
    artist = api.get_artist()
    link = api.get_link()
    filename = api.get_filename_suggestion(ext)

    # Cache
    cache_key = str(uuid.uuid4())
    _image_cache[cache_key] = {
        "bytes": image_data,
        "mime": mime,
        "filename": filename,
        "artist": artist,
        "link": link,
        "source": source,
    }

    # Clean old cache entries (> 50)
    while len(_image_cache) > 50:
        oldest = next(iter(_image_cache))
        del _image_cache[oldest]

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
    entry = _image_cache.get(key)
    if not entry:
        return jsonify({"error": "Image not found or expired"}), 404

    return send_file(
        io.BytesIO(entry["bytes"]),
        mimetype=entry["mime"],
        as_attachment=True,
        download_name=entry["filename"],
    )


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
