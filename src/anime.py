import random
import time
from typing import Optional

import requests

from .api_base import BaseDownloaderAPI


class CategoryDownloaderAPI(BaseDownloaderAPI):
    """Base class for APIs that take a free-text category/tag."""

    def __init__(self, category: str = "", api_key: str = "") -> None:
        super().__init__()
        self.category = (category or "").strip().lower()
        self.api_key = api_key

    def set_category(self, category: str) -> None:
        self.category = (category or "").strip().lower()


class NekosDownloaderAPI(CategoryDownloaderAPI):
    def __init__(self, category: str = "", api_key: str = "") -> None:
        super().__init__(category, api_key)
        self.endpoint = "https://api.nekosapi.com/v4"
        self._session = requests.Session()
        self._session.headers.update({
            "User-Agent": "CatgirlDownloaderWeb/1.0 (+https://github.com/nyarchlinux/catgirldownloader)"
        })

    def get_image_url(self, nsfw_mode: str = "BLOCK_NSFW") -> Optional[str]:
        rating = None
        if nsfw_mode == "BLOCK_NSFW":
            rating = "safe"
        elif nsfw_mode == "ONLY_NSFW":
            rating = "explicit"
        params = {"limit": 1}
        if rating:
            params["rating"] = rating
        if self.category:
            params["tags"] = self.category
        try:
            r = self._session.get(f"{self.endpoint}/images/random", params=params, timeout=10)
            if r.status_code != 200:
                return None
            data = r.json()
            if isinstance(data, list) and data:
                self.info = data[0]
                return data[0].get("url")
        except Exception as e:
            print(f"Nekos API error: {e}")
        return None

    def get_artist(self, info: Optional[dict] = None) -> Optional[str]:
        data = info if info else self.info
        if not data:
            return None
        try:
            return data.get("artist_name")
        except Exception:
            return None

    def get_link(self, info: Optional[dict] = None) -> Optional[str]:
        data = info if info else self.info
        if not data:
            return None
        try:
            src = data.get("source_url")
            if src:
                return src
            return f"{self.endpoint}/images/{data.get('id')}"
        except Exception:
            return None

    def get_filename_suggestion(self, extension: Optional[str], info: Optional[dict] = None) -> str:
        data = info if info else self.info
        if not data:
            image_id = str(int(time.time()))
        else:
            image_id = str(data.get("id", int(time.time())))
        if extension:
            return f"nekosapi_{image_id}.{extension}"
        return f"nekosapi_{image_id}"

    def get_metadata(self) -> dict:
        data = self.info
        if not data:
            return {}
        meta = {}
        if data.get("artist_name"):
            meta["artist"] = data["artist_name"]
        if data.get("rating"):
            meta["rating"] = data["rating"]
            meta["nsfw"] = data["rating"] in ("borderline", "explicit")
        if data.get("tags"):
            meta["tags"] = data["tags"]
        if data.get("source_url"):
            meta["source"] = data["source_url"]
        return meta


class PurrbotDownloaderAPI(CategoryDownloaderAPI):
    def __init__(self, category: str = "", api_key: str = "") -> None:
        super().__init__(category, api_key)
        self.endpoint = "https://api.purrbot.site/v2"

    def get_image_url(self, nsfw_mode: str = "BLOCK_NSFW") -> Optional[str]:
        path = "sfw"
        if nsfw_mode == "ONLY_NSFW":
            path = "nsfw"
        elif nsfw_mode == "SHOW_EVERYTHING":
            path = random.choice(["sfw", "nsfw"])
        category = self.category or "neko"
        # Prefer static images (img) over GIFs; among NSFW only "neko" supports img.
        if path == "nsfw":
            formats = ["img", "gif"] if category == "neko" else ["gif"]
        else:
            formats = ["img", "gif"]
        for fmt in formats:
            try:
                r = requests.get(
                    f"{self.endpoint}/img/{path}/{category}/{fmt}", timeout=10
                )
                if r.status_code != 200:
                    continue
                data = r.json()
                if data.get("error"):
                    return None
                self.info = data
                return data.get("link")
            except Exception as e:
                print(f"Purrbot error: {e}")
                return None
        return None

    def get_artist(self, info: Optional[dict] = None) -> Optional[str]:
        return None

    def get_link(self, info: Optional[dict] = None) -> Optional[str]:
        data = info if info else self.info
        if not data:
            return None
        try:
            return data.get("link")
        except Exception:
            return None

    def get_filename_suggestion(self, extension: Optional[str], info: Optional[dict] = None) -> str:
        data = info if info else self.info
        if not data:
            image_id = str(int(time.time()))
        else:
            link = data.get("link", "")
            image_id = link.rsplit("/", 1)[-1].rsplit(".", 1)[0] or str(int(time.time()))
        if extension:
            return f"purrbot_{image_id}.{extension}"
        return f"purrbot_{image_id}"

    def get_metadata(self) -> dict:
        return {"category": self.category or "neko"}


class FluxpointDownloaderAPI(CategoryDownloaderAPI):
    def __init__(self, category: str = "", api_key: str = "") -> None:
        super().__init__(category, api_key)
        self.endpoint = "https://api.fluxpoint.dev"

    def get_image_url(self, nsfw_mode: str = "BLOCK_NSFW") -> Optional[str]:
        if not self.api_key:
            return None
        path = "sfw"
        if nsfw_mode == "ONLY_NSFW":
            path = "nsfw"
        elif nsfw_mode == "SHOW_EVERYTHING":
            path = random.choice(["sfw", "nsfw"])
        category = self.category or "neko"
        # Try static image first, fall back to GIF (some categories are gif-only).
        for fmt in ("img", "gif"):
            try:
                r = requests.get(
                    f"{self.endpoint}/{path}/{fmt}/{category}",
                    headers={"Authorization": self.api_key},
                    timeout=10,
                )
                if r.status_code == 200:
                    data = r.json()
                    if not data.get("success", True):
                        return None
                    self.info = data
                    return data.get("file")
                if r.status_code != 404:
                    return None
            except Exception as e:
                print(f"Fluxpoint error: {e}")
                return None
        return None

    def get_artist(self, info: Optional[dict] = None) -> Optional[str]:
        return None

    def get_link(self, info: Optional[dict] = None) -> Optional[str]:
        data = info if info else self.info
        if not data:
            return None
        try:
            return data.get("file")
        except Exception:
            return None

    def get_filename_suggestion(self, extension: Optional[str], info: Optional[dict] = None) -> str:
        data = info if info else self.info
        if not data:
            image_id = str(int(time.time()))
        else:
            image_id = str(data.get("id", int(time.time())))
        if extension:
            return f"fluxpoint_{image_id}.{extension}"
        return f"fluxpoint_{image_id}"

    def get_metadata(self) -> dict:
        return {"category": self.category or "neko"}
