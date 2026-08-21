# Catgirl Downloader — Android

A native Android client for browsing random **catgirl / waifu / Danbooru** artwork,
ported from [CatgirlDownloader-Web](../README.md) and the original
[Catgirl Downloader](https://github.com/nyarchlinux/catgirldownloader) GTK app.

The app talks directly to the upstream image APIs — **no Flask server is needed**.

## Features

- 🖼️ Six image sources:
  - **Catgirl** — [nekos.moe](https://nekos.moe)
  - **Waifu** — [waifu.im](https://waifu.im)
  - **Danbooru** — [danbooru.donmai.us](https://danbooru.donmai.us) (dynamic tag search)
  - **Nekos API** — [nekosapi.com](https://nekosapi.com)
  - **PurrBot** — [purrbot.site](https://purrbot.site) (category picker)
  - **Fluxpoint** — [fluxpoint.dev](https://fluxpoint.dev) (category picker + API key)
- 🔞 NSFW filter: block / only / show all
- 🏷️ Tag picker with chips and search suggestions (danbooru uses live tag search)
- 🔍 Pinch-to-zoom, pan and double-tap on images
- ⏱️ Auto-reload with a configurable interval and countdown bar
- 💾 One-click save to `Pictures/CatgirlDownloader` (MediaStore, API 29+)
- ❤️ Local favorites with a grid view
- ⏪ History navigation (previous / next)
- 📋 Rich "about this art" dialog with artist, rating, score, tags…
- 🌙 Material 3 design with a light & dark theme
- 🌐 中文 / English (in-app language switch)

## Building

Open the `android/` folder in **Android Studio** (Hedgehog or newer) and let it sync.
Gradle will download the dependencies automatically.

- Compile SDK: 34 · Min SDK: 24 · Target SDK: 34
- AGP 8.5.2 · Gradle 8.7 · Kotlin 1.9.24

Then press **Run ▶**.

## Project structure

```
android/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/catgirldownloader/android/
│       │   ├── MainActivity.kt             # screen, actions, dialogs
│       │   ├── data/
│       │   │   ├── ApiClient.kt            # OkHttp wrapper + ext resolver
│       │   │   ├── Sources.kt              # 6 ImageSource implementations
│       │   │   ├── Models.kt               # ImageInfo / Tag / Favorite
│       │   │   ├── Prefs.kt                # SharedPreferences settings
│       │   │   └── FavoritesRepository.kt  # local favorites storage
│       │   └── ui/
│       │       ├── MainViewModel.kt        # state, history, favorites, reload
│       │       ├── ZoomableImageView.kt    # pinch zoom / pan / double-tap
│       │       ├── SettingsSheet.kt        # bottom sheet settings + tag picker
│       │       ├── FavoritesDialog.kt      # favorites grid
│       │       └── ArtInfoDialog.kt        # image metadata dialog
│       └── res/                            # layouts, strings (zh/en), icons
```

## API key

**Fluxpoint** requires an API key. Open **Settings** → set the source to
*Fluxpoint* and paste your key into the *API Key* field.

## Credits

Based on [Catgirl Downloader](https://github.com/nyarchlinux/catgirldownloader)
by NyarchLinux.
