"""Persistent configuration and favorites storage."""

import json
import os
import threading
from datetime import datetime, timezone

CONFIG_DIR = os.path.join(os.path.expanduser("~"), ".config", "catgirldownloader-web")
CONFIG_FILE = os.path.join(CONFIG_DIR, "config.json")
_config_lock = threading.Lock()

_DEFAULT_CONFIG = {
    "lang": "auto",
    "source": "catgirl",
    "nsfw_mode": "BLOCK_NSFW",
    "auto_reload": False,
    "auto_reload_interval": 30,
    "danbooru_tags": "",
    "category": "",
    "catgirl_tags": "",
    "waifu_tags": "",
    "fluxpoint_key": "",
    "keyboard_enabled": True,
    "key_next": "Enter",
    "key_prev": "ArrowLeft",
    "key_download": "Space",
    "key_favorite": "KeyF",
    "action_bar_position": "bottom",
}

FAVORITES_DIR = os.path.join(CONFIG_DIR, "favorites")
FAVORITES_FILE = os.path.join(FAVORITES_DIR, "favorites.json")
_favorites_lock = threading.Lock()
os.makedirs(FAVORITES_DIR, exist_ok=True)


def load_config() -> dict:
    with _config_lock:
        try:
            if os.path.exists(CONFIG_FILE):
                with open(CONFIG_FILE, "r") as f:
                    cfg = json.load(f)
                    return {**_DEFAULT_CONFIG, **cfg}
        except Exception as e:
            print(f"Error loading config: {e}")
    return dict(_DEFAULT_CONFIG)


def save_config(cfg: dict) -> None:
    with _config_lock:
        os.makedirs(CONFIG_DIR, exist_ok=True)
        merged = {**_DEFAULT_CONFIG, **cfg}
        with open(CONFIG_FILE, "w") as f:
            json.dump(merged, f, indent=2)


def default_config() -> dict:
    return dict(_DEFAULT_CONFIG)


def load_favorites() -> list[dict]:
    with _favorites_lock:
        try:
            if os.path.exists(FAVORITES_FILE):
                with open(FAVORITES_FILE, "r") as f:
                    return json.load(f)
        except Exception as e:
            print(f"Error loading favorites: {e}")
    return []


def save_favorites(favs: list[dict]) -> None:
    with _favorites_lock:
        with open(FAVORITES_FILE, "w") as f:
            json.dump(favs, f, indent=2, ensure_ascii=False)
