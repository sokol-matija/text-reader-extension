const textInput = document.getElementById('textInput');
const readBtn = document.getElementById('readBtn');
const stopBtn = document.getElementById('stopBtn');
const voiceSelect = document.getElementById('voiceSelect');
const statusDiv = document.getElementById('status');
const statusBadge = document.getElementById('status-badge');

// Restore saved text and voice on open
chrome.storage.local.get(['savedText'], (result) => {
  if (result && result.savedText) {
    textInput.value = result.savedText;
  }
});

chrome.storage.sync.get(['voice'], (result) => {
  if (result && result.voice) {
    voiceSelect.value = result.voice;
  }
});

// Save text as user types
textInput.addEventListener('input', () => {
  chrome.storage.local.set({ savedText: textInput.value });
});

// Save voice preference on change
voiceSelect.addEventListener('change', () => {
  chrome.storage.sync.set({ voice: voiceSelect.value });
});

readBtn.addEventListener('click', async () => {
  const text = textInput.value.trim();
  if (!text) {
    showStatus('Please enter some text', 'error');
    return;
  }

  readBtn.disabled = true;
  stopBtn.disabled = false;
  updateStatusBadge('loading', 'Generating');
  showStatus('Generating audio...', 'loading');

  try {
    // Route through background → offscreen so audio survives popup close
    const response = await chrome.runtime.sendMessage({
      action: 'speakTextOffscreen',
      text: text,
      voice: voiceSelect.value
    });

    if (response && response.success) {
      showStatus('Playing...', 'success');
      updateStatusBadge('ready', 'Playing');
    } else {
      showStatus(response?.error || 'Failed to generate audio', 'error');
      readBtn.disabled = false;
      stopBtn.disabled = true;
      updateStatusBadge('error', 'Error');
    }
  } catch (error) {
    showStatus(`Error: ${error.message}`, 'error');
    readBtn.disabled = false;
    stopBtn.disabled = true;
    updateStatusBadge('error', 'Error');
  }
});

stopBtn.addEventListener('click', () => {
  chrome.runtime.sendMessage({ action: 'stopAudio' });
  readBtn.disabled = false;
  stopBtn.disabled = true;
  showStatus('Stopped', 'error');
  updateStatusBadge('ready', 'Ready');
  setTimeout(() => statusDiv.classList.add('hidden'), 1500);
});

// Listen for playback finished from background
chrome.runtime.onMessage.addListener((message) => {
  if (message.action === 'playbackFinished') {
    readBtn.disabled = false;
    stopBtn.disabled = true;
    showStatus('✓ Finished', 'success');
    updateStatusBadge('ready', 'Ready');
    setTimeout(() => statusDiv.classList.add('hidden'), 2000);
  }
});

function showStatus(message, type) {
  statusDiv.textContent = message;
  statusDiv.className = `status ${type}`;
}

function updateStatusBadge(type, text) {
  statusBadge.className = `status-badge ${type}`;
  statusBadge.querySelector('.status-text').textContent = text;
}

textInput.focus();
