import requests
import json
from typing import Optional

from .api_base import BaseDownloaderAPI


class WaifuDownloaderAPI(BaseDownloaderAPI):
    def __init__(self, tags: str = "", settings=None) -> None:
        super().__init__()
        self.endpoint = "https://api.waifu.im/images"
        self.set_tags(tags or "")

    def set_tags(self, tags: str) -> None:
        # Tag slugs are single words; accept "|" or comma as delimiters.
        parts = (tags or "").replace(",", "|").split("|")
        self.tags = [p.strip() for p in parts if p.strip()]

    def get_page(self, nsfw: Optional[bool] = None, tags: Optional[list] = None) -> Optional[str]:
        try:
            params = {}
            if nsfw is None:
                params["IsNsfw"] = "All"
            elif nsfw:
                params["IsNsfw"] = "True"
            else:
                params["IsNsfw"] = "False"
            if tags:
                params["IncludedTags"] = ",".join(tags)
            r = requests.get(self.endpoint, params=params, timeout=10)
            if r.status_code == 200:
                return r.text
            else:
                return None
        except Exception as e:
            print(e)
            return None

    def get_page_url(self, response: Optional[str]) -> Optional[str]:
        if not response:
            return None
        try:
            data = json.loads(response)
            self.info = data
            return data["items"][0]['url']
        except Exception as e:
            print(e)
            return None

    def get_image_url(self, nsfw_mode: str = "BLOCK_NSFW") -> Optional[str]:
        nsfw = False
        if nsfw_mode == "ONLY_NSFW":
            nsfw = True
        elif nsfw_mode == "SHOW_EVERYTHING":
            nsfw = None
        return self.get_page_url(self.get_page(nsfw, self.tags))

    def get_artist(self, info: Optional[dict] = None) -> Optional[str]:
        data = info if info else self.info
        if not data:
            return None
        try:
            artists = data['items'][0].get('artists')
            if isinstance(artists, list) and artists:
                return artists[0].get('name')
            return None
        except Exception:
            return None

    def get_link(self, info: Optional[dict] = None) -> Optional[str]:
        data = info if info else self.info
        if not data:
            return None
        try:
            return data['items'][0].get('source')
        except Exception:
            return None

    def get_filename_suggestion(self, extension: Optional[str], info: Optional[dict] = None) -> str:
        data = info if info else self.info
        try:
            if data:
                image_id = data['items'][0].get('id', 'unknown')
            else:
                raise Exception("No info")
        except Exception:
            import time
            image_id = str(int(time.time()))

        if extension:
            return f"waifu.im_{image_id}.{extension}"
        return f"waifu.im_{image_id}"

    def get_metadata(self) -> dict:
        data = self.info
        if not data:
            return {}
        item = data["items"][0]
        meta = {}
        artists = item.get("artists")
        if isinstance(artists, list) and artists and artists[0].get("name"):
            meta["artist"] = artists[0]["name"]
        if item.get("source"):
            meta["source"] = item["source"]
        if item.get("uploadedAt"):
            meta["created_at"] = item["uploadedAt"]
        if "isNsfw" in item:
            meta["nsfw"] = bool(item["isNsfw"])
        if "isAnimated" in item:
            meta["gif"] = bool(item["isAnimated"])
        if item.get("width") and item.get("height"):
            meta["width"] = item["width"]
            meta["height"] = item["height"]
        if item.get("byteSize") is not None:
            meta["size"] = item["byteSize"]
        if item.get("favorites") is not None:
            meta["favorites"] = item["favorites"]
        if item.get("tags"):
            meta["tags"] = [t.get("name", "") for t in item["tags"] if t.get("name")]
        return meta
