#!/usr/bin/env bash
set -e

cd "$(dirname "$0")"

echo "Catgirl Downloader Web"
echo ""

# Check Python
if ! command -v python3 &>/dev/null; then
  echo " E Python 3 is not installed."
  echo " * Install it from https://www.python.org/downloads/"
  exit 1
fi

echo " ✓ Python $(python3 --version | cut -d' ' -f2) found"

# Check / install dependencies
if ! python3 -c "import flask" 2>/dev/null; then
  echo " → Installing Python dependencies..."
  python3 -m pip install -r requirements.txt --quiet
  echo " ✓ Dependencies installed"
else
  echo " ✓ Dependencies satisfied"
fi

echo ""
echo " * Starting server..."
echo " * Open http://localhost:5000 in your browser"
echo ""

exec python3 server.py
