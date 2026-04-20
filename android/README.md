# Text Reader — Android app

A companion to the Chrome extension in the parent directory. Point your camera
at text, tap the shutter, and the extracted text is read aloud through the
same self-hosted **Kokoro TTS** server the extension uses.

- **OCR:** Google ML Kit Text Recognition v2 (on-device, offline).
- **TTS:** Kokoro HTTP API (reached over Tailscale) — identical request
  contract to `background.js` in the extension.
- **Background playback:** Media3 `MediaSessionService` + ExoPlayer, so audio
  keeps playing when you background the app or the screen turns off.

## Prerequisites on the phone

1. Samsung Galaxy S25 Ultra (or any Android 8.0+ device).
2. Install **Tailscale** from the Play Store and sign into the same tailnet as
   the Kokoro server (default hostname `sokol.falcon-parore.ts.net:8880`).
3. Enable **Developer options → USB debugging** for sideloading.

## Build & install

### Option A — Download the APK from GitHub Actions (easiest, no SDK needed)

Every push to this branch runs `.github/workflows/android.yml`, which builds
a debug APK on a Linux runner and uploads it as an artifact.

1. Open **Actions → Android build** on GitHub.
2. Click the latest successful run for your branch.
3. Download **`text-reader-debug-apk`** from the Artifacts section.
4. Unzip → transfer `app-debug.apk` to your phone (USB, Drive, etc.).
5. On the phone: open the file, allow "Install unknown apps" for your file
   manager if prompted, tap install.

### Option B — Android Studio

1. Open Android Studio → **Open** → select the `android/` folder.
2. Studio syncs (may prompt to install SDK components).
3. Plug in the phone, pick it in the device dropdown, hit **Run**.

### Option C — Command line

Requires the Android SDK on `ANDROID_HOME`.

```bash
cd android
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## First run

1. Grant **Camera** and (on Android 13+) **Notifications** permissions.
2. Tap the gear icon → verify **Kokoro base URL** (default
   `http://sokol.falcon-parore.ts.net:8880`), pick a voice, tap **Test voice**.
   You should hear a short confirmation clip within ~2s.
3. Point camera at printed text, tap the shutter. A bottom sheet slides up
   with the extracted text. Tap **Read aloud** → audio starts, and a
   notification appears with lock-screen controls.

## Kokoro request contract

Mirrors `background.js:32-61` in the extension:

```http
POST {baseUrl}/v1/audio/speech
Content-Type: application/json

{
  "model": "tts-1",
  "input": "<text>",
  "voice": "af_sky",
  "response_format": "mp3"
}
```

The response body is raw MP3 bytes.

## Project layout

```
android/
├── build.gradle.kts              root
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/*.properties
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/…
        └── java/net/falconparore/textreader/
            ├── MainActivity.kt      camera + OCR + controls
            ├── SettingsActivity.kt  Kokoro URL / voice / model
            ├── OcrRepository.kt     ML Kit wrapper
            ├── KokoroTtsClient.kt   OkHttp POST → mp3 bytes
            ├── TtsPlaybackService.kt  MediaSessionService + ExoPlayer
            ├── Settings.kt          SharedPreferences wrapper
            └── Voices.kt            voice catalogue
```

## Colors

The palette is copied verbatim from `../styles.css` so the Android UI and the
Chrome popup are visually identical. See
`app/src/main/res/values/colors.xml`.

## Troubleshooting

| Symptom | Fix |
|--------|-----|
| "Can't reach Kokoro server" | Tailscale off, or URL wrong. Open Settings → Test voice. |
| No text recognised | Poor lighting or blur. Try again holding the phone steady, good contrast. |
| Audio cuts when screen locks | Make sure the notification is visible (POST_NOTIFICATIONS granted) — the service is foreground while it's up. |
| ML Kit model download on first run | First OCR takes a few seconds while Play Services downloads the Latin text model. Subsequent OCRs are instant and offline. |
