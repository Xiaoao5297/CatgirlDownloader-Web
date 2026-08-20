import random
import requests
import json
from typing import Optional

from .api_base import BaseDownloaderAPI


class CatgirlDownloaderAPI(BaseDownloaderAPI):
    def __init__(self, tags: str = "", settings=None) -> None:
        super().__init__()
        self.endpoint = "https://nekos.moe/api/v1"
        self.set_tags(tags or "")

    def set_tags(self, tags: str) -> None:
        # Tags may contain spaces ("cat ears"); use "|" as the delimiter.
        self.tags = [t.strip() for t in (tags or "").split("|") if t.strip()]

    def _random_images(self, nsfw_mode: str) -> Optional[dict]:
        url = f"{self.endpoint}/random/image"
        if nsfw_mode == "ONLY_NSFW":
            url += "?nsfw=true"
        elif nsfw_mode == "BLOCK_NSFW":
            url += "?nsfw=false"
        try:
            r = requests.get(url, timeout=10)
            if r.status_code != 200:
                return None
            return json.loads(r.text)
        except Exception as e:
            print(f"Catgirl random error: {e}")
            return None

    def _search_images(self, nsfw_mode: str) -> Optional[dict]:
        taglist = self.tags
        body = {"tags": taglist, "limit": 25, "sort": "relevance"}
        if nsfw_mode == "ONLY_NSFW":
            body["nsfw"] = True
        elif nsfw_mode == "BLOCK_NSFW":
            body["nsfw"] = False
        # SHOW_EVERYTHING: no nsfw field
        try:
            r = requests.post(f"{self.endpoint}/images/search", json=body, timeout=10)
            if r.status_code != 200:
                return None
            data = json.loads(r.text)
            images = data.get("images", [])
            if not images:
                return None
            return {"images": [random.choice(images)]}
        except Exception as e:
            print(f"Catgirl search error: {e}")
            return None

    def get_image_url(self, nsfw_mode: str = "BLOCK_NSFW") -> Optional[str]:
        if self.tags:
            data = self._search_images(nsfw_mode)
        else:
            data = self._random_images(nsfw_mode)
        if not data:
            return None
        self.info = data
        image_id = data["images"][0]["id"]
        return "https://nekos.moe/image/" + image_id

    def get_artist(self, info: Optional[dict] = None) -> Optional[str]:
        data = info if info else self.info
        if not data:
            return None
        try:
            return data['images'][0]['artist']
        except Exception:
            return None

    def get_link(self, info: Optional[dict] = None) -> Optional[str]:
        data = info if info else self.info
        if not data:
            return None
        try:
            return "https://nekos.moe/post/" + data['images'][0]['id']
        except Exception:
            return None

    def get_filename_suggestion(self, extension: Optional[str], info: Optional[dict] = None) -> str:
        data = info if info else self.info
        if not data:
            import time
            image_id = str(int(time.time()))
        else:
            try:
                image_id = data["images"][0]["id"]
            except Exception:
                import time
                image_id = str(int(time.time()))

        if extension:
            return f"nekos.moe_{image_id}.{extension}"
        return f"nekos.moe_{image_id}"

    def get_metadata(self) -> dict:
        data = self.info
        if not data:
            return {}
        img = data["images"][0]
        meta = {}
        if img.get("artist"):
            meta["artist"] = img["artist"]
        if "nsfw" in img:
            meta["nsfw"] = bool(img["nsfw"])
        if "likes" in img:
            meta["likes"] = img["likes"]
        if "favorites" in img:
            meta["favorites"] = img["favorites"]
        uploader = img.get("uploader")
        if isinstance(uploader, dict) and uploader.get("username"):
            meta["uploader"] = uploader["username"]
        elif uploader and not isinstance(uploader, dict):
            meta["uploader"] = str(uploader)
        if img.get("tags"):
            meta["tags"] = img["tags"]
        if img.get("createdAt"):
            meta["created_at"] = img["createdAt"]
        return meta
