# Movie-Recapee

An Android video studio for creators: import a video **you own or are licensed to use**, get timed subtitles, a Chinese / English / Myanmar translation, an AI-read voice track, and export a finished video with burned-in captions. Dark Adobe-style workspace built with Jetpack Compose.

## What it does
- **Import** `.mp4`, `.mkv`, `.mov` (and other video) from local storage with the system file picker. There is no web downloader.
- **Listen (speech to text)** – Groq Whisper (free tier), automatic fallback to Gemini. Long videos are processed in 10-minute pieces.
- **Translate** – Chinese ↔ English ↔ Myanmar. Groq → Gemini → MyMemory (no key) fallback chain.
- **Subtitle editor** – edit text and times, add/delete lines, show original, translated, or both (translation on top). Save `.srt`.
- **Caption style** – size, colour, bold, outline, background box, padding, position, alignment. The preview uses the same renderer as the export.
- **Voice** – the phone's own text-to-speech (free, offline) or Gemini voice; speed and pitch; voice replaces or plays over the (lowered) original sound.
- **Export** – Media3 Transformer renders H.264/AAC with captions burned in and the voice mixed; saved to `Movies/Movie-Recapee`.
- **Persistence** – API keys are stored with `EncryptedSharedPreferences`; projects, subtitles, styles and history live in a Room database and survive restarts.

## Build
```bash
gradle assembleDebug        # or open in Android Studio (Koala+); JDK 17
gradle testDebugUnitTest
```
CI (`.github/workflows/android_build.yml`) builds the debug APK on every push to `main` or `develop` and uploads it as the `Movie-Recapee-debug-apk` artifact with a SHA-256 checksum.

## Free API keys
Paste a Groq key and/or a Gemini key in **Settings**. Model names are editable there in case a free model is renamed.

## Notes and limits
- Video export uses **Media3 Transformer**, not FFmpeg-Kit: FFmpeg-Kit was retired and its official binaries were removed from Maven.
- Myanmar captions are drawn with Android's own text engine, so they render correctly on any phone with a Myanmar font. Myanmar voice on the device needs the Burmese voice installed (Settings → Text-to-speech); otherwise add a Gemini key.
- The voice track is built in memory at 24 kHz: very long videos (over about one hour) may be slow or run out of memory.
- Exports keep running only while the app is open (the screen is kept on during a job).
- Free AI tiers have rate limits; the app retries and falls back, and tells you when a limit is reached.
- Only use footage you have the rights to use.
