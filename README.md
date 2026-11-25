# ClientChatPlus

**ClientChatPlus** is a lightweight, client-side chat overhaul for **Minecraft 26.3** that brings Discord-style formatting, rich media embeds, smooth animations, and chat history tools directly into the vanilla chat HUD, packaged as a **single universal JAR** for both **Fabric** and **NeoForge**.

---

## Features

### Smooth Animations & Modern Layout
* **Frame-Rate-Independent Slide & Push:** Incoming messages glide smoothly from the bottom using monotonic nanosecond timing.
* **Infinite Scrollback & Unread Indicator:** Scroll freely through chat history without losing your place; a **`↓ New Messages`** jump pill tracks incoming messages while scrolled up.
* **Customizable Scale & Offset:** Adjust chat scale, X/Y screen margins, line spacing, and message spacing.

### Rich Text & DiscordSRV Support
* **Full Markdown:** Supports `#`, `##`, `###` headings, `-#` subtext, `-` bullet lists, `**bold**`, `*italic*`, `__underline__`, `~~strikethrough~~`, inline `` `code` ``, and `[label](url)` links.
* **Optional MiniMessage:** Supports Kyori Adventure MiniMessage tags (`<red>`, `<gradient>`, etc.) alongside Markdown.
* **Syntax-Highlighted Codeblocks:** Renders fenced ` ```lang ... ``` ` blocks with language headers and a one-click **`Copy`** button, including automatic merging for multi-line **DiscordSRV** bridged codeblocks.
* **Multi-Line Chat Input:** Press **`Shift + Enter`** while typing to compose multi-line messages.

### Inline Media, Previews & Video Playback
* **Inline Images & Animated GIFs:** Automatically loads and renders `.png`, `.jpg`, `.webp`, `.gif`, Google Images, **Tenor**, and **Klipy** links inline (or as compact `` hover previews).
* **Website Hover Cards:** Hovering over links displays a rich preview popup with the page's title, description, and thumbnail/screenshot.
* **File Attachment Cards & Code Previews:** Recognizes Discord, GitHub raw, and direct file URLs, rendering plaintext files (`.txt`, `.json`, `.java`, `.py`, `.yml`, etc.) with inline syntax-highlighted previews and a **`Copy`** button.
* **Three-Tier Video Player (`.mp4`, `.mov`, `.webm`, `.mkv`):**
  1. **VLC (`libVLC` + `vlcj`):** Automatically uses your system's installed VLC media player if detected.
  2. **WaterMedia (Optional):** Automatically falls back to the WaterMedia mod if installed.
  3. **Compact Attachment Card:** Cleanly falls back to a clickable attachment card when neither backend is present.
  * Includes paused start, **Play/Pause**, **Timeline Scrubber**, **Timestamp**, and **Volume Slider** controls.

### Chat Management & Persistence
* **Live Search (`Ctrl + F`):** Filter visible chat messages in real time.
* **Hover Message Actions:** Hover over any message to **Bookmark (`★`)**, **Copy (`📋`)**, **Save to File (`💾`)**, or **Delete (`✕`)**.
* **Bookmark Export & Import:** Filter by bookmarked messages (`All` / `★ Bookmarks`) and export/import bookmarks via your OS file dialog.
* **Daily Logs:** Optionally saves chat history automatically to `clientchatplus/logs/YYYY-MM-DD.txt`.

---

## Requirements & Optional Integrations

| Dependency | Loader | Required? | Notes |
| :--- | :--- | :--- | :--- |
| **Minecraft 26.3** | Fabric / NeoForge | **Required** | Single JAR works on both loaders |
| **Java 25** | Both | **Required** | Built for Java 25 |
| **Yet Another Config Lib (YACL)** | Fabric / NeoForge | **Required** | Powers the in-game settings screen |
| **Fabric API** | Fabric | **Required** (Fabric) | Standard Fabric hooks |
| **Mod Menu** | Fabric | Optional | Access config screen from Mods list |
| **VLC Media Player** | System App | Optional | Enables native `libVLC` video playback |
| **WaterMedia** | Fabric / NeoForge | Optional | Secondary fallback for video playback |

---

## Installation

1. Install **Fabric Loader** or **NeoForge** for **Minecraft 26.3**.
2. Download **Yet Another Config Lib (YACL)** for your loader and place it in your `mods/` folder.
3. Download **`ClientChatPlus-<version>.jar`** and place it in your `mods/` folder.
4. *(Optional)* Install **VLC Media Player** on your system or add **WaterMedia** to `mods/` for inline `.mp4`/`.webm`/`.mov` video playback.

---

## Configuration

Open the configuration screen via **Mod Menu** (Fabric) or the **Mods** screen (NeoForge).

* **Formatting:** Toggle Markdown, MiniMessage, and codeblock rendering.
* **Media & Previews:** Toggle inline images, compact `` mode, website hover previews, and file attachment cards.
* **Layout & Logging:** Customize chat scale, X/Y offset, line spacing, message spacing, and automatic daily chat logging.

---

## Building from Source

```bash
git clone https://github.com/lammy12k/ClientChatPlus
cd ClientChatPlus
./gradlew clean build shadowJar
```

The universal cross-loader JAR will be generated at:
`build/libs/ClientChatPlus-<version>.jar`

---

## License

This project is licensed under the terms of the `LICENSE` file included in this repository.
