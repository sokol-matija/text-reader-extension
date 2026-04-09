const KOKORO_URL = 'http://sokol.falcon-parore.ts.net:8880/v1/audio/speech';

// Message listener for popup
chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  if (request.action === 'speakTextOffscreen') {
    speakText(request.text, request.voice).then(async (result) => {
      if (result.success) {
        // Respond immediately so popup shows "Playing..." while audio runs
        sendResponse({ success: true });
        // Play audio and broadcast "done" when it finishes
        await playAudioInOffscreen(result.audioUrl);
        notifyPopupPlaybackFinished();
      } else {
        sendResponse(result);
      }
    });
    return true;
  }

  if (request.action === 'stopAudio') {
    chrome.runtime.sendMessage({ action: 'stopAudioOffscreen' }).catch(() => {});
    return false;
  }
});

function notifyPopupPlaybackFinished() {
  chrome.runtime.sendMessage({ action: 'playbackFinished' }).catch(() => {
    // Popup may be closed — that's fine
  });
}

async function speakText(text, voice = 'default') {
  try {
    const response = await fetch(KOKORO_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        model: 'tts-1',
        input: text,
        voice: (!voice || voice === 'default') ? 'af_sky' : voice,
        response_format: 'mp3'
      })
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }

    const blob = await response.blob();
    const reader = new FileReader();

    return new Promise((resolve) => {
      reader.onload = () => resolve({ success: true, audioUrl: reader.result });
      reader.onerror = () => resolve({ success: false, error: 'Failed to read audio blob' });
      reader.readAsDataURL(blob);
    });
  } catch (error) {
    console.error('TTS Error:', error);
    return { success: false, error: error.message };
  }
}

// Ensure offscreen document exists before sending messages
async function ensureOffscreenDocument() {
  const offscreenUrl = chrome.runtime.getURL('offscreen.html');
  const existingContexts = await chrome.runtime.getContexts({
    contextTypes: ['OFFSCREEN_DOCUMENT'],
    documentUrls: [offscreenUrl]
  });

  if (existingContexts.length === 0) {
    console.log('Creating offscreen document');
    await chrome.offscreen.createDocument({
      url: 'offscreen.html',
      reasons: ['AUDIO_PLAYBACK'],
      justification: 'Playing TTS audio from context menu selections'
    });
  }
}

async function playAudioInOffscreen(audioUrl) {
  try {
    await ensureOffscreenDocument();
    await chrome.runtime.sendMessage({
      action: 'playAudioOffscreen',
      audioUrl: audioUrl
    });
  } catch (error) {
    console.error('Error playing in offscreen:', error);
  }
}

// Context menu - only create on install
chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: 'readSelection',
    title: 'Read Aloud',
    contexts: ['selection']
  });
});

chrome.contextMenus.onClicked.addListener((info, tab) => {
  if (info.menuItemId === 'readSelection') {
    const selectedText = info.selectionText;
    console.log('Reading selected text:', selectedText.substring(0, 50));

    speakText(selectedText).then((result) => {
      if (result.success) {
        playAudioInOffscreen(result.audioUrl);
      } else {
        console.error('TTS failed:', result.error);
      }
    });
  }
});
