"""Flask route registration."""

from flask import Flask, jsonify, request

from webapp import favorites
from webapp import images
from webapp.config import load_config, save_config, default_config
from webapp.sources import (
    SOURCES,
    fetch_tags_cached,
    get_api,
)


def register_routes(app: Flask) -> None:
    # ── API: Sources ────────────────────────────────────────────────
    @app.route("/api/sources")
    def api_sources():
        return jsonify([{"key": k, **v} for k, v in SOURCES.items()])

    # ── API: Tags (per-source tag lists) ───────────────────────────
    @app.route("/api/tags")
    def api_tags():
        source = request.args.get("source", "catgirl")
        q = (request.args.get("q", "") or "").strip()
        return jsonify({
            "source": source,
            "tags": fetch_tags_cached(source, q),
        })

    # ── API: Config ────────────────────────────────────────────────
    @app.route("/api/config", methods=["GET"])
    def api_get_config():
        return jsonify(load_config())

    @app.route("/api/config", methods=["PUT"])
    def api_set_config():
        data = request.get_json(force=True)
        cfg = load_config()
        for key in default_config():
            if key in data:
                cfg[key] = data[key]
        save_config(cfg)

        # Clear preload queue when source/nsfw/tags/category/key change
        if any(k in data for k in ("source", "nsfw_mode", "danbooru_tags", "category", "catgirl_tags", "waifu_tags", "fluxpoint_key")):
            images.clear_preload_queue()

        # If Danbooru tags changed, update the API instance
        if "danbooru_tags" in data:
            danbooru_instance = get_api("danbooru")
            danbooru_instance.set_tags(data["danbooru_tags"])

        return jsonify(cfg)

    # ── API: Fetch image ───────────────────────────────────────────
    @app.route("/api/fetch")
    def api_fetch():
        source = request.args.get("source", "catgirl")
        nsfw = request.args.get("nsfw", "BLOCK_NSFW")

        if source not in SOURCES:
            return jsonify({"error": f"Unknown source: {source}"}), 400

        # Preload queue hit — return instantly
        entry = images.pop_preloaded(source, nsfw)
        if entry is not None:
            images.start_preload(source, nsfw)
            return jsonify(entry)

        # Normal fetch
        result = images.fetch_new_image(source, nsfw)
        if result is None:
            return jsonify({"error": "Failed to fetch image from source"}), 502

        # Trigger background preload
        images.start_preload(source, nsfw)
        return jsonify(result)

    # ── API: Serve / download image ────────────────────────────────
    @app.route("/api/image/<key>")
    def api_serve_image(key):
        resp = images.serve_image(key)
        if resp is None:
            return jsonify({"error": "Image not found or expired"}), 404
        return resp

    @app.route("/api/download/<key>")
    def api_download_image(key):
        resp = images.serve_image_attachment(key)
        if resp is None:
            return jsonify({"error": "Image not found or expired"}), 404
        return resp

    # ── API: Favorites ─────────────────────────────────────────────
    @app.route("/api/favorites", methods=["GET"])
    def api_list_favorites():
        from webapp.config import load_favorites
        return jsonify(load_favorites())

    @app.route("/api/favorites/<cache_key>", methods=["POST"])
    def api_add_favorite(cache_key: str):
        return favorites.add_favorite(cache_key)

    @app.route("/api/favorites/<fav_id>/image")
    def api_serve_favorite(fav_id: str):
        return favorites.serve_favorite_image(fav_id)

    @app.route("/api/favorites/<fav_id>/download")
    def api_download_favorite(fav_id: str):
        return favorites.download_favorite(fav_id)

    @app.route("/api/favorites/<fav_id>", methods=["DELETE"])
    def api_delete_favorite(fav_id: str):
        return favorites.delete_favorite(fav_id)

    # ── Serve frontend ─────────────────────────────────────────────
    @app.route("/")
    def index():
        return app.send_static_file("index.html")
