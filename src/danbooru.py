import requests
import time
import base64
from typing import Optional

from .api_base import BaseDownloaderAPI

_FORBIDDEN_TAG_1 = base64.b64decode('c2hvdGE='.encode('utf-8')).decode('utf-8')
_FORBIDDEN_TAG_2 = base64.b64decode('bG9saQ=='.encode('utf-8')).decode('utf-8')


class DanbooruDownloaderAPI(BaseDownloaderAPI):
    def __init__(self, settings=None) -> None:
        super().__init__()
        self.endpoint = "https://danbooru.donmai.us"
        self.tags = ""
        self._session = requests.Session()
        self._session.headers.update({
            "User-Agent": "CatgirlDownloaderWeb/1.0 (+https://github.com/nyarchlinux/catgirldownloader)"
        })

    def set_tags(self, tags: str) -> None:
        # Filter forbidden tags
        taglist = tags.lower().split()
        taglist = [t for t in taglist if t not in (_FORBIDDEN_TAG_1, _FORBIDDEN_TAG_2)]
        self.tags = ' '.join(taglist)

    def get_tags(self) -> str:
        return self.tags

    def _build_tags_query(self, nsfw_mode: str) -> str:
        tags = self.tags.strip() if self.tags else ""

        if nsfw_mode == "BLOCK_NSFW":
            rating_tag = "rating:general"
        elif nsfw_mode == "ONLY_NSFW":
            rating_tag = "rating:explicit"
        else:
            rating_tag = None

        if tags and rating_tag:
            return f"{tags} {rating_tag}"
        elif rating_tag:
            return rating_tag
        return tags

    def get_random_post(self, nsfw_mode: str = "BLOCK_NSFW", max_retries: int = 5) -> Optional[dict]:
        for attempt in range(max_retries):
            try:
                tags = self._build_tags_query(nsfw_mode)
                params = {
                    "limit": 1,
                    "random": "true"
                }
                if tags:
                    params["tags"] = tags
                r = self._session.get(f"{self.endpoint}/posts.json", params=params, timeout=10)
                if r.status_code != 200:
                    print(f"Danbooru API returned status {r.status_code}")
                    return None
            except Exception as e:
                print(f"Danbooru request error: {e}")
                return None
            try:
                data = r.json()
                if isinstance(data, list) and len(data) > 0:
                    post = data[0]
                    post_tags = post.get('tag_string', '').split()
                    if _FORBIDDEN_TAG_1 in post_tags or _FORBIDDEN_TAG_2 in post_tags:
                        print(f'Attempt {attempt + 1}: Forbidden tags in post, retrying...')
                        continue

                    self.info = post
                    return post
                return None
            except Exception as e:
                print(f"Danbooru parse error: {e}")
                return None
        print(f'Could not find suitable post after {max_retries} attempts')
        return None

    def get_image_url(self, nsfw_mode: str = "BLOCK_NSFW") -> Optional[str]:
        post = self.get_random_post(nsfw_mode)
        if post:
            return post.get("file_url")
        return None

    def get_artist(self, info: Optional[dict] = None) -> Optional[str]:
        data = info if info else self.info
        if not data:
            return None
        try:
            artist_tags = data.get("tag_string_artist", "")
            if artist_tags:
                return artist_tags.split(" ")[0]
            return None
        except Exception:
            return None

    def get_link(self, info: Optional[dict] = None) -> Optional[str]:
        data = info if info else self.info
        if not data:
            return None
        try:
            post_id = data.get("id")
            if post_id:
                return f"{self.endpoint}/posts/{post_id}"
            return None
        except Exception:
            return None

    def get_image(self, url: str) -> Optional[bytes]:
        """Override to use session with proper headers for Danbooru CDN."""
        try:
            r = self._session.get(url, timeout=30)
            if r.status_code == 200:
                return r.content
            print(f"Danbooru image download failed: HTTP {r.status_code}")
            return None
        except Exception as e:
            print(f"Danbooru image download error: {e}")
            return None

    def get_filename_suggestion(self, extension: Optional[str], info: Optional[dict] = None) -> str:
        data = info if info else self.info
        if not data:
            post_id = str(int(time.time()))
        else:
            try:
                post_id = str(data.get("id", int(time.time())))
            except Exception:
                post_id = str(int(time.time()))

        if extension:
            return f"danbooru_{post_id}.{extension}"
        return f"danbooru_{post_id}"
