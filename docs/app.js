// State Management
const state = {
    currentScreen: 'homeScreen',
    previousScreen: null,
    playlists: [],
    importedAudio: [],
    currentPlaylist: null,
    currentSegmentIndex: 0,
    isPracticing: false,
    isPaused: false,
    playbackRepeats: 2,
    currentRepeat: 0,
    userRepeats: 1,
    settings: {
        playbackSpeed: 1.0,
        playbackRepeats: 2,
        userRepeats: 1,
        busMode: false,
        practiceMode: 'standard',
        audioFeedback: true,
        beepVolume: 50,
        targetLanguage: 'en'
    },
    mediaRecorder: null,
    recordedChunks: [],
    recordingStartTime: null,
    recordingInterval: null
};

// Load state from localStorage
function loadState() {
    try {
        const saved = localStorage.getItem('shadowMasterState');
        if (saved) {
            const parsed = JSON.parse(saved);
            state.playlists = parsed.playlists || [];
            state.importedAudio = parsed.importedAudio || [];
            state.settings = { ...state.settings, ...parsed.settings };
        }
    } catch (e) {
        console.error('Error loading state:', e);
    }
}

// Save state to localStorage
function saveState() {
    try {
        const toSave = {
            playlists: state.playlists,
            importedAudio: state.importedAudio,
            settings: state.settings
        };
        localStorage.setItem('shadowMasterState', JSON.stringify(toSave));
    } catch (e) {
        console.error('Error saving state:', e);
    }
}

// Navigation
function navigateTo(screenId) {
    state.previousScreen = state.currentScreen;
    state.currentScreen = screenId;

    document.querySelectorAll('.screen').forEach(screen => {
        screen.classList.remove('active');
    });

    const targetScreen = document.getElementById(screenId);
    if (targetScreen) {
        targetScreen.classList.add('active');
    }

    if (screenId === 'libraryScreen') {
        renderLibrary();
    } else if (screenId === 'settingsScreen') {
        loadSettings();
    }
}

function navigateBack() {
    if (state.previousScreen) {
        navigateTo(state.previousScreen);
    } else {
        navigateTo('homeScreen');
    }
}

// Tab Switching
function switchTab(tabName) {
    document.querySelectorAll('.tab').forEach(tab => {
        tab.classList.remove('active');
    });

    document.querySelectorAll('.tab-content').forEach(content => {
        content.classList.remove('active');
    });

    if (tabName === 'playlists') {
        document.querySelector('.tab:first-child').classList.add('active');
        document.getElementById('playlistsTab').classList.add('active');
        renderPlaylists();
    } else if (tabName === 'imported') {
        document.querySelector('.tab:last-child').classList.add('active');
        document.getElementById('importedTab').classList.add('active');
        renderImportedAudio();
    }
}

// Library Rendering
function renderLibrary() {
    renderPlaylists();
    renderImportedAudio();
}

function renderPlaylists() {
    const playlistList = document.getElementById('playlistList');
    const emptyState = document.getElementById('emptyPlaylists');

    if (state.playlists.length === 0) {
        playlistList.innerHTML = '';
        emptyState.style.display = 'block';
        return;
    }

    emptyState.style.display = 'none';
    playlistList.innerHTML = state.playlists.map(playlist => `
        <div class="playlist-card" onclick="openPlaylist('${playlist.id}')">
            <h4>${playlist.name}</h4>
            <p>${playlist.segments.length} segments</p>
            <p>Language: ${playlist.language}</p>
            <div class="actions" onclick="event.stopPropagation()">
                <button onclick="deletePlaylist('${playlist.id}')">🗑️ Delete</button>
                <button onclick="exportPlaylist('${playlist.id}')">📥 Export</button>
            </div>
        </div>
    `).join('');
}

function renderImportedAudio() {
    const importedList = document.getElementById('importedAudioList');
    const emptyState = document.getElementById('emptyImported');

    if (state.importedAudio.length === 0) {
        importedList.innerHTML = '';
        emptyState.style.display = 'block';
        return;
    }

    emptyState.style.display = 'none';
    importedList.innerHTML = state.importedAudio.map(audio => `
        <div class="imported-card">
            <h4>${audio.name}</h4>
            <p>Duration: ${formatDuration(audio.duration)}</p>
            <div class="actions">
                <button onclick="createPlaylistFromAudio('${audio.id}')">➕ Create Playlist</button>
                <button onclick="deleteImportedAudio('${audio.id}')">🗑️ Delete</button>
            </div>
        </div>
    `).join('');
}

// Audio Import
async function importAudioFile(input) {
    const file = input.files[0];
    if (!file) return;

    try {
        const audioData = await readAudioFile(file);
        const importedAudio = {
            id: generateId(),
            name: file.name,
            duration: audioData.duration,
            data: audioData.url,
            createdAt: Date.now()
        };

        state.importedAudio.push(importedAudio);
        saveState();
        renderImportedAudio();

        input.value = '';
    } catch (e) {
        alert('Error importing audio file: ' + e.message);
    }
}

function readAudioFile(file) {
    return new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = function(e) {
            const audio = new Audio();
            audio.onloadedmetadata = function() {
                resolve({
                    url: e.target.result,
                    duration: audio.duration
                });
            };
            audio.onerror = reject;
            audio.src = e.target.result;
        };
        reader.onerror = reject;
        reader.readAsDataURL(file);
    });
}

// Playlist Management
function createPlaylistFromAudio(audioId) {
    const audio = state.importedAudio.find(a => a.id === audioId);
    if (!audio) return;

    const playlistName = prompt('Enter playlist name:', audio.name.replace(/\.[^/.]+$/, ''));
    if (!playlistName) return;

    const playlist = {
        id: generateId(),
        name: playlistName,
        language: state.settings.targetLanguage,
        segments: [{
            id: generateId(),
            audioUrl: audio.data,
            duration: audio.duration,
            transcription: '',
            translation: ''
        }],
        createdAt: Date.now()
    };

    state.playlists.push(playlist);
    saveState();
    renderPlaylists();
}

function openPlaylist(playlistId) {
    const playlist = state.playlists.find(p => p.id === playlistId);
    if (!playlist) return;

    state.currentPlaylist = playlist;
    state.currentSegmentIndex = 0;
    navigateTo('practiceScreen');
    initializePractice();
}

function deletePlaylist(playlistId) {
    if (!confirm('Delete this playlist?')) return;

    state.playlists = state.playlists.filter(p => p.id !== playlistId);
    saveState();
    renderPlaylists();
}

function deleteImportedAudio(audioId) {
    if (!confirm('Delete this audio file?')) return;

    state.importedAudio = state.importedAudio.filter(a => a.id !== audioId);
    saveState();
    renderImportedAudio();
}

function exportPlaylist(playlistId) {
    alert('Export functionality would download the playlist as an audio file. This requires additional audio processing capabilities.');
}

// Recording
async function toggleRecording() {
    if (!state.mediaRecorder || state.mediaRecorder.state === 'inactive') {
        await startRecording();
    } else {
        stopRecording();
    }
}

async function startRecording() {
    try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        state.mediaRecorder = new MediaRecorder(stream);
        state.recordedChunks = [];

        state.mediaRecorder.ondataavailable = function(e) {
            if (e.data.size > 0) {
                state.recordedChunks.push(e.data);
            }
        };

        state.mediaRecorder.onstop = function() {
            const blob = new Blob(state.recordedChunks, { type: 'audio/webm' });
            const url = URL.createObjectURL(blob);
            const audio = document.getElementById('recordedAudio');
            audio.src = url;
            document.getElementById('recordingPreview').style.display = 'block';
        };

        state.mediaRecorder.start();
        state.recordingStartTime = Date.now();

        const recordButton = document.getElementById('recordButton');
        recordButton.innerHTML = '<span class="record-icon">⏹️</span> Stop Recording';
        document.getElementById('recordingStatus').textContent = 'Recording...';

        state.recordingInterval = setInterval(updateRecordingDuration, 100);
    } catch (e) {
        alert('Error accessing microphone: ' + e.message);
    }
}

function stopRecording() {
    if (state.mediaRecorder && state.mediaRecorder.state !== 'inactive') {
        state.mediaRecorder.stop();
        state.mediaRecorder.stream.getTracks().forEach(track => track.stop());

        clearInterval(state.recordingInterval);

        const recordButton = document.getElementById('recordButton');
        recordButton.innerHTML = '<span class="record-icon">⏺️</span> Start Recording';
        document.getElementById('recordingStatus').textContent = 'Recording complete';
    }
}

function updateRecordingDuration() {
    if (!state.recordingStartTime) return;

    const elapsed = Date.now() - state.recordingStartTime;
    const seconds = Math.floor(elapsed / 1000);
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;

    document.getElementById('recordingDuration').textContent =
        `${String(minutes).padStart(2, '0')}:${String(remainingSeconds).padStart(2, '0')}`;
}

async function saveRecording() {
    const audio = document.getElementById('recordedAudio');
    const playlistName = prompt('Enter playlist name:', 'Microphone Recording');
    if (!playlistName) return;

    const blob = new Blob(state.recordedChunks, { type: 'audio/webm' });
    const reader = new FileReader();

    reader.onload = function(e) {
        const playlist = {
            id: generateId(),
            name: playlistName,
            language: state.settings.targetLanguage,
            segments: [{
                id: generateId(),
                audioUrl: e.target.result,
                duration: audio.duration || 0,
                transcription: '',
                translation: ''
            }],
            createdAt: Date.now()
        };

        state.playlists.push(playlist);
        saveState();

        discardRecording();
        navigateTo('libraryScreen');
        switchTab('playlists');
    };

    reader.readAsDataURL(blob);
}

function discardRecording() {
    state.recordedChunks = [];
    state.recordingStartTime = null;
    document.getElementById('recordingPreview').style.display = 'none';
    document.getElementById('recordingDuration').textContent = '00:00';
    document.getElementById('recordingStatus').textContent = 'Ready to record';
}

// Practice Mode
function initializePractice() {
    if (!state.currentPlaylist) return;

    const titleEl = document.getElementById('practiceTitle');
    titleEl.textContent = state.currentPlaylist.name;

    state.currentSegmentIndex = 0;
    state.currentRepeat = 0;
    state.isPracticing = false;
    state.isPaused = false;

    updatePracticeUI();
    loadCurrentSegment();

    document.getElementById('startButton').style.display = 'block';
    document.getElementById('pauseButton').style.display = 'none';
    document.getElementById('skipButton').style.display = 'none';
}

function loadCurrentSegment() {
    if (!state.currentPlaylist) return;

    const segment = state.currentPlaylist.segments[state.currentSegmentIndex];
    if (!segment) return;

    document.getElementById('segmentTranscription').textContent =
        segment.transcription || 'No transcription available';
    document.getElementById('segmentTranslation').textContent =
        segment.translation || '';
    document.getElementById('segmentDuration').textContent =
        'Duration: ' + formatDuration(segment.duration);

    const audio = document.getElementById('practiceAudio');
    audio.src = segment.audioUrl;
    audio.playbackRate = state.settings.playbackSpeed;

    updateProgress();
}

function startPractice() {
    state.isPracticing = true;
    state.currentRepeat = 0;

    document.getElementById('startButton').style.display = 'none';
    document.getElementById('pauseButton').style.display = 'block';
    document.getElementById('skipButton').style.display = 'block';

    playCurrentSegment();
}

function playCurrentSegment() {
    if (!state.currentPlaylist || state.isPaused) return;

    const segment = state.currentPlaylist.segments[state.currentSegmentIndex];
    if (!segment) return;

    document.getElementById('practiceState').textContent =
        `Playing (${state.currentRepeat + 1}/${state.settings.playbackRepeats})`;

    const audio = document.getElementById('practiceAudio');
    audio.play();
}

function handleAudioEnded() {
    if (!state.isPracticing || state.isPaused) return;

    state.currentRepeat++;

    if (state.currentRepeat < state.settings.playbackRepeats) {
        setTimeout(() => playCurrentSegment(), 500);
    } else {
        if (!state.settings.busMode) {
            startUserRecording();
        } else {
            moveToNextSegment();
        }
    }
}

async function startUserRecording() {
    document.getElementById('practiceState').textContent = 'Your turn - Recording...';

    if (state.settings.audioFeedback) {
        playBeep();
    }

    try {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        const recorder = new MediaRecorder(stream);
        const chunks = [];

        recorder.ondataavailable = e => chunks.push(e.data);
        recorder.onstop = function() {
            stream.getTracks().forEach(track => track.stop());
            moveToNextSegment();
        };

        recorder.start();

        setTimeout(() => {
            if (recorder.state !== 'inactive') {
                recorder.stop();
            }
        }, 5000);
    } catch (e) {
        console.error('Recording error:', e);
        moveToNextSegment();
    }
}

function moveToNextSegment() {
    state.currentSegmentIndex++;
    state.currentRepeat = 0;

    if (state.currentSegmentIndex >= state.currentPlaylist.segments.length) {
        finishPractice();
    } else {
        loadCurrentSegment();
        setTimeout(() => playCurrentSegment(), 1000);
    }
}

function skipSegment() {
    moveToNextSegment();
}

function togglePause() {
    state.isPaused = !state.isPaused;

    const pauseButton = document.getElementById('pauseButton');
    const audio = document.getElementById('practiceAudio');

    if (state.isPaused) {
        audio.pause();
        pauseButton.textContent = '▶️ Resume';
        document.getElementById('practiceState').textContent = 'Paused';
    } else {
        pauseButton.textContent = '⏸️ Pause';
        playCurrentSegment();
    }
}

function finishPractice() {
    state.isPracticing = false;
    document.getElementById('practiceState').textContent = 'Completed!';
    document.getElementById('segmentTranscription').textContent = 'Great job! You completed the playlist.';

    document.getElementById('startButton').style.display = 'block';
    document.getElementById('startButton').textContent = '🔄 Practice Again';
    document.getElementById('pauseButton').style.display = 'none';
    document.getElementById('skipButton').style.display = 'none';
}

function stopPractice() {
    state.isPracticing = false;
    const audio = document.getElementById('practiceAudio');
    audio.pause();
    audio.currentTime = 0;
    navigateTo('libraryScreen');
}

function updateProgress() {
    if (!state.currentPlaylist) return;

    const total = state.currentPlaylist.segments.length;
    const current = state.currentSegmentIndex + 1;
    const percentage = (current / total) * 100;

    document.getElementById('progressFill').style.width = percentage + '%';
    document.getElementById('progressText').textContent = `${current} / ${total}`;
}

function updateSpeed(value) {
    state.settings.playbackSpeed = parseFloat(value);
    document.getElementById('speedValue').textContent = value + 'x';

    const audio = document.getElementById('practiceAudio');
    audio.playbackRate = state.settings.playbackSpeed;

    saveState();
}

// Settings
function loadSettings() {
    document.getElementById('settingsSpeed').value = state.settings.playbackSpeed;
    document.getElementById('settingsSpeedValue').textContent = state.settings.playbackSpeed + 'x';
    document.getElementById('playbackRepeats').value = state.settings.playbackRepeats;
    document.getElementById('userRepeats').value = state.settings.userRepeats;
    document.getElementById('busMode').checked = state.settings.busMode;
    document.getElementById('practiceMode').value = state.settings.practiceMode;
    document.getElementById('audioFeedback').checked = state.settings.audioFeedback;
    document.getElementById('beepVolume').value = state.settings.beepVolume;
    document.getElementById('beepVolumeValue').textContent = state.settings.beepVolume + '%';
    document.getElementById('targetLanguage').value = state.settings.targetLanguage;
}

function updateSettingSpeed(value) {
    state.settings.playbackSpeed = parseFloat(value);
    document.getElementById('settingsSpeedValue').textContent = value + 'x';
    saveState();
}

function updateSetting(key, value) {
    if (key === 'beepVolume') {
        document.getElementById('beepVolumeValue').textContent = value + '%';
    }

    state.settings[key] = key === 'busMode' || key === 'audioFeedback' ? value :
                          (key === 'playbackRepeats' || key === 'userRepeats' || key === 'beepVolume') ?
                          parseInt(value) : value;
    saveState();
}

// Utilities
function generateId() {
    return Date.now().toString(36) + Math.random().toString(36).substr(2);
}

function formatDuration(seconds) {
    if (!seconds || isNaN(seconds)) return '0:00';
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${mins}:${String(secs).padStart(2, '0')}`;
}

function playBeep() {
    const audioContext = new (window.AudioContext || window.webkitAudioContext)();
    const oscillator = audioContext.createOscillator();
    const gainNode = audioContext.createGain();

    oscillator.connect(gainNode);
    gainNode.connect(audioContext.destination);

    oscillator.frequency.value = 800;
    oscillator.type = 'sine';

    gainNode.gain.value = state.settings.beepVolume / 100;

    oscillator.start();
    oscillator.stop(audioContext.currentTime + 0.1);
}

function updatePracticeUI() {
    const startButton = document.getElementById('startButton');
    startButton.textContent = '▶️ Start';
    startButton.style.display = 'block';
}

// Initialize
loadState();
renderLibrary();
