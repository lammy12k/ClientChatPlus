# Changelog

All notable changes to **ClientChatPlus** will be documented in this file.

## [0.1.0-SNAPSHOT] - 2026-11-25

### Added
* **Cross-Loader Single-JAR Architecture:** Unified Fabric and NeoForge support for Minecraft 26.3 in a single universal JAR.
* **Smooth Chat Animations:** Frame-rate-independent slide and push animations using monotonic nanosecond timing.
* **Markdown & MiniMessage Formatting:**
  * Full Markdown support for headings (`#`, `##`, `###`), subtext (`-#`), bullet lists (`-`), bold (`**`), italic (`*`), underline (`__`), strikethrough (`~~`), inline code (`` ` ``), and links (`[label](url)`).
  * Optional Kyori Adventure MiniMessage support (`<red>`, `<gradient>`, etc.).
  * Syntax-highlighted fenced codeblocks (` ```lang ... ``` `) with language headers, one-click `Copy` buttons, and automatic merging for multi-packet DiscordSRV codeblocks.
  * Multi-line chat composition via `Shift + Enter`.
* **Async Media Cache & Previews:**
  * Inline image rendering and animated GIF playback, including automatic resolution for Google Images, Tenor, and Klipy share URLs.
  * Compact `` mode with hover preview popups.
  * Website hover preview cards displaying page title, description, and thumbnail or screenshot.
  * Universal file attachment cards for Discord, GitHub raw, and direct file links, including inline syntax-highlighted code previews and `Copy` buttons for plaintext files.
* **Three-Tier Video Playback (`.mp4`, `.mov`, `.webm`, `.mkv`):**
  * Automatic system VLC (`libVLC` + `vlcj`) detection as the primary playback backend.
  * Optional WaterMedia mod integration as a secondary fallback.
  * Clean fallback to a compact clickable attachment card when neither backend is present.
  * Paused initial state with interactive Play/Pause, timeline scrubber, timestamp display, and volume slider controls.
* **Chat Management & Persistence:**
  * Infinite scrollback with clamped top scrolling and a red `↓ New Messages` jump-to-bottom indicator.
  * Live message filtering via `Ctrl + F` or the top search bar.
  * Hover message actions to Bookmark (`★`), Copy (`📋`), Save to File (`💾`), or Delete (`✕`).
  * Bookmark filtering (`All` / `★ Bookmarks`) with native OS file dialog Export and Import.
  * Optional automatic daily chat logging to `clientchatplus/logs/YYYY-MM-DD.txt`.
* **In-Game Configuration:** Yet Another Config Lib (YACL) settings screen accessible through Mod Menu (Fabric) and the Mods screen (NeoForge).
