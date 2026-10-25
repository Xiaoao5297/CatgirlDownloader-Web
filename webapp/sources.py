"""Source registry, API factory and tag fetching."""

import threading
import time

import requests

from src.catgirl import CatgirlDownloaderAPI
from src.waifu import WaifuDownloaderAPI
from src.danbooru import DanbooruDownloaderAPI
from src.anime import (
    NekosDownloaderAPI,
    PurrbotDownloaderAPI,
    FluxpointDownloaderAPI,
)
from webapp.config import load_config

SOURCES = {
    "catgirl": {
        "name": "Catgirl (nekos.moe)",
        "has_tags": False,
        "tag_picker": True,
        "tags_config": "catgirl_tags",
    },
    "waifu": {
        "name": "Waifu (waifu.im)",
        "has_tags": False,
        "tag_picker": True,
        "tags_config": "waifu_tags",
    },
    "danbooru": {
        "name": "Danbooru",
        "has_tags": True,
        "tags_label": "Danbooru Tags",
        "tag_picker": True,
        "tags_config": "danbooru_tags",
        "tag_dynamic": True,
    },
    "nekos": {
        "name": "Nekos API",
        "has_tags": True,
        "tags_label": "Category",
    },
    "purrbot": {
        "name": "PurrBot",
        "has_tags": True,
        "tags_label": "Category",
        "tag_picker": True,
        "tags_config": "category",
        "tag_single": True,
    },
    "fluxpoint": {
        "name": "Fluxpoint",
        "has_tags": True,
        "tags_label": "Category",
        "needs_key": True,
        "tag_picker": True,
        "tags_config": "category",
        "tag_single": True,
    },
}

NSFW_MODES = ["BLOCK_NSFW", "ONLY_NSFW", "SHOW_EVERYTHING"]

# ── Per-source tag lists ───────────────────────────────────────────
_tag_cache: dict = {}
_TAG_TTL = 3600
_TAG_SEARCH_TTL = 60

FLUXPOINT_CATEGORIES = [
    # SFW img
    "waifu", "neko", "hug", "cuddle", "kiss", "blush", "cry", "pat",
    "smug", "wave", "dance", "poke", "wink", "smile",
    # SFW gif
    "baka", "bite", "feed", "fluff", "grab", "handhold", "highfive",
    "laugh", "lick", "punch", "shrug", "slap", "stare", "tickle", "wag",
    "wasted",
    # NSFW img
    "cum", "feet", "femdom", "futa", "gasm", "holo", "kitsune",
    "pantyhose", "peeing", "petplay", "pussy", "slime", "solo", "girl",
    # NSFW gif
    "anal", "ass", "bdsm", "blowjob", "boobjob", "boobs", "handjob",
    "hentai", "kuni", "wank", "spank", "tentacle", "toys", "yuri",
]

PURRBOT_CATEGORIES = [
    # SFW
    "angry", "background", "bite", "blush", "comfy", "cry", "cuddle",
    "dance", "eevee", "fluff", "holo", "hug", "icon", "kiss", "kitsune",
    "lay", "lick", "neko", "okami", "pat", "poke", "pout", "senko",
    "shiro", "slap", "smile", "tail", "tickle",
    # NSFW
    "anal", "blowjob", "cum", "fuck", "pussylick", "solo", "solo_male",
    "threesome_fff", "threesome_ffm", "threesome_mmf", "yaoi", "yuri",
]


def get_api(source: str):
    cfg = load_config()
    if source == "catgirl":
        return CatgirlDownloaderAPI(cfg.get("catgirl_tags", ""))
    elif source == "waifu":
        return WaifuDownloaderAPI(cfg.get("waifu_tags", ""))
    elif source == "danbooru":
        api = DanbooruDownloaderAPI()
        api.set_tags(cfg.get("danbooru_tags", ""))
        return api
    elif source == "nekos":
        return NekosDownloaderAPI(cfg.get("category", ""))
    elif source == "purrbot":
        return PurrbotDownloaderAPI(cfg.get("category", ""))
    elif source == "fluxpoint":
        return FluxpointDownloaderAPI(cfg.get("category", ""), cfg.get("fluxpoint_key", ""))
    return None


def _fetch_tags(source: str, q: str = ""):
    """Returns a list of {name, slug} tags, or None on failure."""
    try:
        if source == "catgirl":
            r = requests.get("https://nekos.moe/api/v1/tags", timeout=10)
            if r.status_code != 200:
                return None
            return [{"name": t, "slug": t} for t in r.json().get("tags", [])]
        elif source == "waifu":
            r = requests.get("https://api.waifu.im/tags?limit=50", timeout=10)
            if r.status_code != 200:
                return None
            return [
                {"name": t.get("name", ""), "slug": t.get("slug", "")}
                for t in r.json().get("items", [])
                if t.get("slug")
            ]
        elif source == "purrbot":
            return [
                {"name": c, "slug": c}
                for c in PURRBOT_CATEGORIES
            ]
        elif source == "fluxpoint":
            return [
                {"name": c, "slug": c}
                for c in FLUXPOINT_CATEGORIES
            ]
        elif source == "danbooru":
            prefix = f"{q}*" if q else "a*"
            r = requests.get(
                "https://danbooru.donmai.us/tags.json",
                params={
                    "search[name_matches]": prefix,
                    "search[order]": "count",
                    "limit": 20,
                },
                headers={"User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"},
                timeout=10,
            )
            if r.status_code != 200:
                return None
            return [
                {"name": t.get("name", ""), "slug": t.get("name", "")}
                for t in r.json()
                if t.get("name")
            ]
    except Exception as e:
        print(f"Tags fetch error ({source}): {e}")
    return None


def fetch_tags_cached(source: str, q: str = ""):
    cache_key = (source, q)
    now = time.time()
    ttl = _TAG_SEARCH_TTL if q else _TAG_TTL
    entry = _tag_cache.get(cache_key)
    if not entry or now - entry["ts"] > ttl:
        tags = _fetch_tags(source, q)
        if tags is not None:
            _tag_cache[cache_key] = {"tags": tags, "ts": now}
    entry = _tag_cache.get(cache_key)
    return entry["tags"] if entry else []
