---
name: backend-preload-queue
description: Background preload queue pattern for Flask/Sanic/FastAPI web apps to prefetch and cache resources for instant subsequent access
source: auto-skill
extracted_at: '2026-07-19T07:15:17.100Z'
---

# Backend Preload Queue Pattern

When a web app fetches external resources (images, API data, files) that take 1-3 seconds each, adding a preload queue eliminates wait time for subsequent requests. The pattern: serve from a pre-filled queue on next request, then replenish in background.

## When to use

- A `/fetch` endpoint downloads an external resource and the user will likely request another one soon
- Response time is dominated by upstream network latency (1-5s typical)
- The upstream source has a random/surprise element (the user doesn't pick exactly *which* item)

## Pattern

### Backend (Flask example)

```python
import threading

# In-memory cache + preload queue
_image_cache: dict[str, dict] = {}
_preload_queue: list[str] = []
_cache_lock = threading.Lock()
_preload_lock = threading.Lock()
_MAX_PRELOAD = 5

def _fetch_and_cache(api, source: str) -> Optional[str]:
    """Fetch resource, cache it, return cache key."""
    url = api.get_url()
    data = api.download(url)
    if not data:
        return None
    key = str(uuid.uuid4())
    with _cache_lock:
        _image_cache[key] = {
            "bytes": data,
            # ... other metadata
        }
        # Enforce cache size limit
        while len(_image_cache) > 50:
            oldest = next(iter(_image_cache))
            del _image_cache[oldest]
    return key

def _preload_resources(source: str):
    """Background thread: fill preload queue up to MAX_PRELOAD."""
    with _preload_lock:
        needed = _MAX_PRELOAD - len(_preload_queue)
    for _ in range(needed):
        try:
            api = _get_api(source)
            key = _fetch_and_cache(api, source)
            if key:
                with _preload_lock:
                    _preload_queue.append(key)
        except Exception as e:
            print(f"Preload error: {e}")
            break

@app.route("/api/fetch")
def api_fetch():
    # 1. Try preload queue first (instant)
    with _preload_lock:
        if _preload_queue:
            key = _preload_queue.pop(0)
            # Replenish in background
            threading.Thread(target=_preload_resources, args=(source,), daemon=True).start()
            return jsonify({"key": key, ...})

    # 2. Normal fetch (slow)
    # ... do full fetch ...
    # 3. Trigger background preload
    threading.Thread(target=_preload_resources, args=(source,), daemon=True).start()
    return jsonify({...})
```

### Key design points

| Concern | Solution |
|---|---|
| **Thread safety** | Separate locks for cache (`_cache_lock`) and queue (`_preload_lock`) |
| **Queue replenish on consume** | After popping from queue, spawn a background thread to refill |
| **Stale queue on config change** | Clear `_preload_queue` when source/filter params change |
| **Background error resilience** | Wrap preload in try/except, break on first failure |
| **Cache bounds** | Enforce max cache size alongside preload queue |

### Frontend

No frontend changes needed — the `/fetch` endpoint becomes naturally faster after the first call.

### Cleanup / invalidation

When the user changes source or filter parameters:

```python
with _preload_lock:
    _preload_queue.clear()
```

This ensures stale preloaded entries from the old configuration are never served.
