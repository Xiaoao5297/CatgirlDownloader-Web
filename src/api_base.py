from abc import ABC, abstractmethod
from typing import Any, Optional
import requests


class BaseDownloaderAPI(ABC):
    def __init__(self) -> None:
        self.endpoint: str = ""
        self.info: Optional[dict[str, Any]] = None

    @abstractmethod
    def get_image_url(self, nsfw_mode: str = "BLOCK_NSFW") -> Optional[str]:
        pass

    @abstractmethod
    def get_artist(self, info: Optional[dict] = None) -> Optional[str]:
        pass

    @abstractmethod
    def get_link(self, info: Optional[dict] = None) -> Optional[str]:
        pass

    def get_image(self, url: str) -> Optional[bytes]:
        try:
            headers = {"User-Agent": "CatgirlDownloaderWeb/1.0"}
            r = requests.get(url, timeout=30, headers=headers)
            if r.status_code == 200:
                return r.content
            print(f"Image download failed: HTTP {r.status_code} for {url[:60]}")
            return None
        except Exception as e:
            print(f"Error downloading image: {e}")
            return None

    @abstractmethod
    def get_filename_suggestion(self, extension: Optional[str], info: Optional[dict] = None) -> str:
        pass
