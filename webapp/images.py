"""In-memory image cache and background preload queue."""

import io
import threading
import uuid
from typing import Optional

from flask import send_file

from webapp.sources import get_api

# ── In-memory image cache ──────────────────────────────────────────
_image_cache: dict[str, dict] = {}
_cache_lock = threading.Lock()

# ── Preload queue ──────────────────────────────────────────────────
# Keyed by (source, nsfw_mode) so one source's preloaded images are
# never served to a request for a different source.
_preload_queue: dict[tuple[str, str], list[str]] = {}
_MAX_PRELOAD = 5
_preload_lock = threading.Lock()

_MIME_MAP = {
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".png": "image/png",
    ".gif": "image/gif",
    ".webp": "image/webp",
}


def resolve_mime_ext(url: str) -> tuple[str, str]:
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

    mime, ext = resolve_mime_ext(image_url)
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
            "metadata": api.get_metadata(),
        }
        while len(_image_cache) > 50:
            oldest = next(iter(_image_cache))
            del _image_cache[oldest]

    return cache_key


def _preload_images(source: str, nsfw: str):
    """Background task: fill the preload queue for this (source, nsfw) pair."""
    key = (source, nsfw)
    with _preload_lock:
        queue = _preload_queue.setdefault(key, [])
        needed = _MAX_PRELOAD - len(queue)
    for _ in range(needed):
        try:
            api = get_api(source)
            if not api:
                break
            cache_key = _fetch_and_cache(api, source, nsfw)
            if cache_key:
                with _preload_lock:
                    _preload_queue.setdefault(key, []).append(cache_key)
        except Exception as e:
            print(f"Preload error: {e}")
            break


def clear_preload_queue() -> None:
    with _preload_lock:
        _preload_queue.clear()


def start_preload(source: str, nsfw: str) -> None:
    threading.Thread(target=_preload_images, args=(source, nsfw), daemon=True).start()


def pop_preloaded(source: str, nsfw: str) -> Optional[dict]:
    """Pop an entry from the preload queue, or None if empty."""
    key = (source, nsfw)
    with _preload_lock:
        queue = _preload_queue.get(key)
        cache_key = queue.pop(0) if queue else None
    if cache_key is None:
        return None
    with _cache_lock:
        entry = _image_cache.get(cache_key)
    if entry is None:
        return None
    return {
        "key": cache_key,
        "artist": entry["artist"],
        "link": entry["link"],
        "filename": entry["filename"],
        "source": entry["source"],
        "mime": entry["mime"],
        "metadata": entry.get("metadata", {}),
    }


def fetch_new_image(source: str, nsfw: str) -> Optional[dict]:
    """Fetch a fresh image from the source, cache it, and return its metadata."""
    api = get_api(source)
    if not api:
        return None

    image_url = api.get_image_url(nsfw)
    if not image_url:
        return None

    image_data = api.get_image(image_url)
    if not image_data:
        return None

    mime, ext = resolve_mime_ext(image_url)
    artist = api.get_artist()
    link = api.get_link()
    filename = api.get_filename_suggestion(ext)

    cache_key = str(uuid.uuid4())
    metadata = api.get_metadata()
    with _cache_lock:
        _image_cache[cache_key] = {
            "bytes": image_data,
            "mime": mime,
            "filename": filename,
            "artist": artist,
            "link": link,
            "source": source,
            "metadata": metadata,
        }
        while len(_image_cache) > 50:
            oldest = next(iter(_image_cache))
            del _image_cache[oldest]

    return {
        "key": cache_key,
        "artist": artist,
        "link": link,
        "filename": filename,
        "source": source,
        "mime": mime,
        "metadata": metadata,
    }


def get_cached_entry(key: str) -> Optional[dict]:
    with _cache_lock:
        return _image_cache.get(key)


def serve_image(key: str):
    entry = get_cached_entry(key)
    if not entry:
        return None
    return send_file(
        io.BytesIO(entry["bytes"]),
        mimetype=entry["mime"],
        as_attachment=False,
    )


def serve_image_attachment(key: str):
    entry = get_cached_entry(key)
    if not entry:
        return None
    return send_file(
        io.BytesIO(entry["bytes"]),
        mimetype=entry["mime"],
        as_attachment=True,
        download_name=entry["filename"],
    )
