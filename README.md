# Text Reader

A Chrome extension that reads any selected text aloud using a self-hosted [Kokoro TTS](https://github.com/remsky/Kokoro-FastAPI) service over [Tailscale](https://tailscale.com).

![Chrome Extension](https://img.shields.io/badge/Chrome-Extension-blue?logo=googlechrome)
![Manifest V3](https://img.shields.io/badge/Manifest-V3-green)

## Features

- **Popup reader** — Paste any text and click Read Aloud
- **Context menu** — Select text on any webpage, right-click → "Read Aloud"
- **Persisted text** — Textarea content is saved between popup opens
- **CSP-safe playback** — Uses Chrome's offscreen document API so audio works on pages with strict Content Security Policy (e.g. GitHub)
- **Multiple voices** — Kokoro supports 70+ language voices
- **Default voice** — `af_sky`

## Requirements

- Chrome 109+
- A [Kokoro TTS](https://github.com/remsky/Kokoro-FastAPI) instance accessible over Tailscale
- The Tailscale endpoint reachable at `http://<your-machine>:8880`

## Installation

1. Clone or download this repo
2. Open Chrome and go to `chrome://extensions/`
3. Enable **Developer mode** (toggle in top right)
4. Click **Load unpacked** and select this folder
5. The extension icon appears in your toolbar

## Usage

### Popup
1. Click the extension icon
2. Paste or type text into the textarea
3. Click **Read Aloud**
4. Click **Stop** to cancel playback

### Context Menu
1. Select any text on a webpage
2. Right-click → **Read Aloud**
3. Audio plays in the background — closing the popup won't stop it

## Configuration

Edit `KOKORO_URL` in `background.js` to point to your own Kokoro instance:

```js
const KOKORO_URL = 'http://your-machine.tailnet.ts.net:8880/v1/audio/speech';
```

Also update `host_permissions` in `manifest.json` to match your endpoint.

## Project Structure

```
text-reader-extension/
├── manifest.json      # Extension config (permissions, icons)
├── background.js      # Service worker: TTS fetch + context menu handler
├── offscreen.html     # Offscreen document shell (CSP-safe audio context)
├── offscreen.js       # Audio playback in isolated extension context
├── popup.html         # Popup UI
├── popup.js           # Popup logic + storage persistence
├── styles.css         # Dark theme styles
├── icons/             # Extension icons (16/48/128px)
└── android/           # Android companion app (camera + OCR + same Kokoro backend)
```

## Android companion app

An Android app lives under [`android/`](android/README.md). Point the camera
at text, tap the shutter, and the extracted text is read aloud through the
same Kokoro server. Uses Google ML Kit for on-device OCR and Media3 for
background playback with lock-screen controls. See
[`android/README.md`](android/README.md) for build and install instructions.

## How It Works

1. User triggers Read Aloud (popup or context menu)
2. `background.js` POSTs text to Kokoro TTS → receives MP3 as base64 data URL
3. Background creates an [offscreen document](https://developer.chrome.com/docs/extensions/reference/api/offscreen) (isolated from any webpage's CSP)
4. Offscreen document plays the audio via an `<audio>` element
5. Closing the popup has no effect — playback continues in the offscreen context

## Troubleshooting

| Problem | Fix |
|---|---|
| No audio from context menu | Reload extension in `chrome://extensions/` |
| Connection timeout | Check Kokoro is running and Tailscale is connected |
| Audio stops on popup close | Should not happen — uses offscreen document |
| CSP error in console | Confirm you're on the latest version (offscreen API fixes this) |
