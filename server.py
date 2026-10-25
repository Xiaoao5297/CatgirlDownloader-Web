#!/usr/bin/env python3
"""Catgirl Downloader - Web Backend Server (entry point)"""

import argparse
import os

from flask import Flask
from flask_cors import CORS

from webapp.routes import register_routes


def create_app() -> Flask:
    app = Flask(__name__, static_folder="static", static_url_path="")
    CORS(app)
    register_routes(app)
    return app


app = create_app()


def main() -> None:
    parser = argparse.ArgumentParser(description="Catgirl Downloader Web")
    parser.add_argument(
        "--debug",
        action="store_true",
        help="run Flask in debug mode (development only)",
    )
    parser.add_argument("--host", default=None, help="bind host")
    parser.add_argument("--port", type=int, default=None, help="bind port")
    args = parser.parse_args()

    host = args.host or os.environ.get("HOST", "0.0.0.0")
    port = args.port if args.port is not None else int(os.environ.get("PORT", 5000))

    print(f"Catgirl Downloader Web — http://{host}:{port}")
    app.run(host=host, port=port, debug=args.debug)


if __name__ == "__main__":
    main()
