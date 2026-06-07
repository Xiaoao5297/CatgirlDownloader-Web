# Catgirl Downloader Web

A web-based image browser for catgirl, waifu, and Danbooru artwork. Originally a Linux GTK4 desktop app, now ported to the browser — works on any device.

## Features

- 🖼️ Browse random images from three sources:
  - **Catgirl** — [nekos.moe](https://nekos.moe)
  - **Waifu** — [waifu.im](https://waifu.im)
  - **Danbooru** — [danbooru.donmai.us](https://danbooru.donmai.us) (with custom tags)
- 🔞 NSFW filter (block / only / show all)
- ⏱️ Auto-reload with configurable interval
- 💾 One-click download with smart filenames
- 📱 Responsive design — mobile, tablet, desktop
- 🌙 Dark theme
- ⌨️ Keyboard shortcuts: `R` refresh, `S` save

## Quick Start

### Prerequisites

- Python 3.8+
- pip

### Run

```bash
cd web
# Linux
bash start.sh
# Windows
.\start.sh
```

Or manually:

```bash
cd web
pip install -r requirements.txt
python3 server.py
```

Then open **http://localhost:5000** in your browser.

### Windows

Double-click `start.bat` or right-click `start.ps1` → Run with PowerShell.

## Access from Other Devices

The server listens on `0.0.0.0:5000`. Find your local IP (`ipconfig` on Windows, `ip a` on Linux) and visit `http://YOUR_IP:5000` from another device on the same network.

## Project Structure

```
web/
├── server.py              # Flask backend
├── requirements.txt       # Python dependencies
├── start.sh               # Linux / macOS launcher
├── start.bat              # Windows CMD launcher
├── start.ps1              # Windows PowerShell launcher
├── README.md
├── src/                   # Reused API layer
│   ├── api_base.py
│   ├── catgirl.py
│   ├── waifu.py
│   ├── danbooru.py
│   └── types.py
└── static/                # Frontend
    ├── index.html
    ├── style.css
    └── app.js
```

## API Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /` | Frontend SPA |
| `GET /api/config` | Read preferences |
| `PUT /api/config` | Save preferences |
| `GET /api/fetch?source=&nsfw=` | Fetch random image |
| `GET /api/image/<key>` | Display cached image |
| `GET /api/download/<key>` | Download image |

## Credits

Based on [Catgirl Downloader](https://github.com/nyarchlinux/catgirldownloader) by NyarchLinux.
