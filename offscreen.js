// Offscreen document: plays audio outside page context, bypasses page CSP
const audio = document.getElementById('offscreenAudio');

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.action === 'playAudioOffscreen') {
    audio.src = message.audioUrl;

    audio.onended = () => {
      sendResponse({ success: true });
    };

    audio.onerror = (e) => {
      console.error('Offscreen audio error:', e);
      sendResponse({ success: false });
    };

    audio.play()
      .then(() => console.log('Offscreen: playing'))
      .catch(err => {
        console.error('Offscreen play() failed:', err);
        sendResponse({ success: false, error: err.message });
      });

    return true; // keep channel open
  }

  if (message.action === 'stopAudioOffscreen') {
    audio.pause();
    audio.src = '';
    sendResponse({ success: true });
  }
});
