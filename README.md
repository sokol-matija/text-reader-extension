# Text Reader Chrome Extension

A Chrome extension that reads any text aloud using your local Kokoro TTS service over Tailscale.

## Features

- **Paste & Read** — Paste text into the popup and click "Read Aloud"
- **Context Menu** — Right-click any selected text and choose "Read Aloud"
- **Multiple Voices** — Switch between different voice options
- **Stop Control** — Stop playback mid-speech
- **Tailscale Support** — Works with your remote Kokoro service over Tailscale

## Installation

1. Open Chrome and go to `chrome://extensions/`
2. Enable **Developer mode** (toggle in top right)
3. Click **Load unpacked**
4. Select the `text-reader-extension` folder
5. The extension should now appear in your Chrome toolbar

## Usage

### Popup Method
1. Click the extension icon in your toolbar
2. Paste text into the textarea
3. Click "🔊 Read Aloud"
4. Click "⏹ Stop" to stop playback

### Context Menu Method
1. Select any text on a webpage
2. Right-click and choose "Read Aloud"
3. Audio plays automatically in the background

## Configuration

The extension connects to:
```
http://sokol.falcon-parore.ts.net:8880/v1/audio/speech
```

To change the Kokoro endpoint, edit the `KOKORO_URL` in `background.js`.

## Voice Options

Available voices (from Kokoro/OpenAI API):
- `alloy` — Default voice
- `echo` — Male voice
- `fable` — Female voice
- `onyx` — Deep voice
- `nova` — Bright voice
- `shimmer` — Soft voice

Edit `popup.html` to add more voice options to the dropdown.

## Troubleshooting

**Extension won't connect?**
- Ensure Kokoro is running on your Tailscale machine
- Check that `sokol.falcon-parore.ts.net:8880` is reachable
- Open DevTools (right-click → Inspect) and check the Console for errors

**No audio playing?**
- Check Chrome's microphone/speaker permissions
- Try a different voice from the dropdown
- Check browser console for error messages

## Files

- `manifest.json` — Extension configuration
- `popup.html` — Popup UI
- `popup.js` — Popup logic
- `background.js` — TTS request handler and context menu
