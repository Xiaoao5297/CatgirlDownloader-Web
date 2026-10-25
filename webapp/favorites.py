"""Favorites storage: images on disk + JSON index."""

import os
import uuid
from datetime import datetime, timezone

from flask import jsonify, send_file

from webapp.config import FAVORITES_DIR, load_favorites, save_favorites
from webapp.images import get_cached_entry


def add_favorite(cache_key: str):
    entry = get_cached_entry(cache_key)
    if not entry:
        return jsonify({"error": "Image not found in cache"}), 404

    fav_id = str(uuid.uuid4())
    ext = entry["filename"].rsplit(".", 1)[-1] if "." in entry["filename"] else "jpg"
    file_path = os.path.join(FAVORITES_DIR, f"{fav_id}.{ext}")

    with open(file_path, "wb") as f:
        f.write(entry["bytes"])

    fav_entry = {
        "id": fav_id,
        "cacheKey": cache_key,
        "filename": entry["filename"],
        "ext": ext,
        "artist": entry["artist"],
        "link": entry["link"],
        "source": entry["source"],
        "mime": entry["mime"],
        "meta": entry.get("metadata", {}),
        "saved_at": datetime.now(timezone.utc).isoformat(),
    }

    favs = load_favorites()
    favs.insert(0, fav_entry)
    save_favorites(favs)

    return jsonify(fav_entry)


def _find_favorite(fav_id: str):
    favs = load_favorites()
    return next((f for f in favs if f["id"] == fav_id), None)


def _favorite_path(fav: dict) -> str:
    return os.path.join(FAVORITES_DIR, f"{fav['id']}.{fav['ext']}")


def serve_favorite_image(fav_id: str):
    fav = _find_favorite(fav_id)
    if not fav:
        return jsonify({"error": "Favorite not found"}), 404

    file_path = _favorite_path(fav)
    if not os.path.exists(file_path):
        return jsonify({"error": "Favorite file missing"}), 404

    return send_file(file_path, mimetype=fav["mime"])


def download_favorite(fav_id: str):
    fav = _find_favorite(fav_id)
    if not fav:
        return jsonify({"error": "Favorite not found"}), 404

    file_path = _favorite_path(fav)
    if not os.path.exists(file_path):
        return jsonify({"error": "Favorite file missing"}), 404

    return send_file(
        file_path,
        mimetype=fav["mime"],
        as_attachment=True,
        download_name=fav["filename"],
    )


def delete_favorite(fav_id: str):
    favs = load_favorites()
    fav = next((f for f in favs if f["id"] == fav_id), None)
    if not fav:
        return jsonify({"error": "Favorite not found"}), 404

    file_path = _favorite_path(fav)
    if os.path.exists(file_path):
        os.remove(file_path)

    favs = [f for f in favs if f["id"] != fav_id]
    save_favorites(favs)
    return jsonify({"ok": True})
