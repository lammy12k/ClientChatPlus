# Changelog

All notable changes to **ClientChatPlus** will be documented in this file.

## - 2026-11-25

### Added
* **Click-to-Expand Media Lightbox:** Click any inline image, animated GIF, or video frame to open a centered fullscreen viewer with dimmed backdrop, full video playback controls, and `Esc` or click-outside dismissal.
* **@Mention Highlighting & Ping Notifications:**
  * Highlights messages mentioning your username (including self-mentions), `@everyone`, or `@here` with a warm amber tint and left accent bar.
  * Plays a subtle configurable notification sound when pinged.
  * Added a dedicated **`@ Mentions`** filter tab to the top chat toolbar.
* **Markdown Blockquotes (`> `):** Lines starting with `> ` now render with a vertical gray accent bar and indented muted gray text.
* **Dual Search Modes (`🔍 Text` & `👤 User`):**
  * Filter messages by message text or strictly by sender username.
  * Includes a live autocomplete dropdown of online players and recent chat senders with `Tab` completion.
* **New YACL Configuration Options:**
  * **Video Playback Backend:** Choose between `AUTO`, `VLC_ONLY`, `WATERMEDIA_ONLY`, or `DISABLED`.
  * **Idle Chat Fade Duration:** Configure unfocused fade-out time from `1s` to `60s`, or set to `0` (`Never`).
  * **Highlight Mentions** and **Mention Ping Sound** toggles.

### Fixed
* **NeoForge JPMS Module Startup Crash:** Merged service provider files and excluded un-relocated `META-INF/services/uk.co.caprica.*` descriptors from the shaded JAR so NeoForge's `ModuleFinder` loads the universal JAR cleanly.

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
