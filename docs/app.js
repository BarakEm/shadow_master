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
        targetLanguage: 'en',
        backendUrl: 'http://localhost:8765'
    },
    backendAvailable: false,
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
let currentAudioForSegmentation = null;

function createPlaylistFromAudio(audioId) {
    const audio = state.importedAudio.find(a => a.id === audioId);
    if (!audio) return;

    currentAudioForSegmentation = audio;
    showSegmentationModal();
}

function showSegmentationModal() {
    const modal = document.getElementById('segmentationModal');
    modal.style.display = 'flex';

    // Load saved settings or defaults
    const segmentMode = state.settings.segmentMode || 'sentence';
    document.getElementById('segmentMode').value = segmentMode;
    document.getElementById('vadAlgorithm').value = state.settings.vadAlgorithm || 'energy';
    document.getElementById('silenceThreshold').value = state.settings.silenceThreshold || 0.01;
    document.getElementById('silenceThresholdValue').textContent = state.settings.silenceThreshold || 0.01;
    document.getElementById('minSilenceDuration').value = state.settings.minSilenceDuration || 0.3;
    document.getElementById('minSilenceDurationValue').textContent = (state.settings.minSilenceDuration || 0.3) + 's';
    document.getElementById('speechPadding').value = state.settings.speechPadding || 0.1;
    document.getElementById('speechPaddingValue').textContent = (state.settings.speechPadding || 0.1) + 's';

    updateSegmentModeUI();

    // Add event listeners for sliders
    document.getElementById('silenceThreshold').oninput = function() {
        document.getElementById('silenceThresholdValue').textContent = this.value;
    };

    document.getElementById('minSilenceDuration').oninput = function() {
        document.getElementById('minSilenceDurationValue').textContent = this.value + 's';
    };

    document.getElementById('speechPadding').oninput = function() {
        document.getElementById('speechPaddingValue').textContent = this.value + 's';
    };

    document.getElementById('segmentMode').onchange = updateSegmentModeUI;
}

function updateSegmentModeUI() {
    const mode = document.getElementById('segmentMode').value;
    const customSettings = document.getElementById('customRangeSettings');

    if (mode === 'custom') {
        customSettings.style.display = 'block';
    } else {
        customSettings.style.display = 'none';
    }
}

function closeSegmentationModal() {
    document.getElementById('segmentationModal').style.display = 'none';
    currentAudioForSegmentation = null;
}

async function startSegmentation() {
    if (!currentAudioForSegmentation) return;

    // Get configuration
    const vadAlgorithm = document.getElementById('vadAlgorithm').value;
    const segmentMode = document.getElementById('segmentMode').value;
    const silenceThreshold = parseFloat(document.getElementById('silenceThreshold').value);
    const minSilenceDuration = parseFloat(document.getElementById('minSilenceDuration').value);
    const speechPadding = parseFloat(document.getElementById('speechPadding').value);

    let minSegmentDuration, maxSegmentDuration;

    if (segmentMode === 'word') {
        minSegmentDuration = 0.5;
        maxSegmentDuration = 2.0;
    } else if (segmentMode === 'sentence') {
        minSegmentDuration = 1.0;
        maxSegmentDuration = 8.0;
    } else {
        minSegmentDuration = parseFloat(document.getElementById('minDuration').value) || 0.5;
        maxSegmentDuration = parseFloat(document.getElementById('maxDuration').value) || 8.0;
    }

    // Save settings for next time
    state.settings.vadAlgorithm = vadAlgorithm;
    state.settings.segmentMode = segmentMode;
    state.settings.silenceThreshold = silenceThreshold;
    state.settings.minSilenceDuration = minSilenceDuration;
    state.settings.speechPadding = speechPadding;
    saveState();

    // Show progress
    document.getElementById('segmentationProgress').style.display = 'block';
    document.getElementById('segmentButtonText').textContent = 'Processing...';

    try {
        // Fetch audio data
        const response = await fetch(currentAudioForSegmentation.data);
        const arrayBuffer = await response.arrayBuffer();

        // Update progress
        document.getElementById('segmentProgressText').textContent = 'Analyzing audio...';
        document.getElementById('segmentProgressFill').style.width = '30%';

        // Run VAD segmentation
        const segments = await vadProcessor.segmentAudio(arrayBuffer, {
            algorithm: vadAlgorithm,
            minSegmentDuration,
            maxSegmentDuration,
            silenceThreshold,
            speechPadding,
            minSilenceDuration
        });

        document.getElementById('segmentProgressText').textContent = 'Creating segments...';
        document.getElementById('segmentProgressFill').style.width = '60%';

        // Extract each segment
        const playlistSegments = [];
        for (let i = 0; i < segments.length; i++) {
            const seg = segments[i];
            const segmentUrl = await vadProcessor.extractSegment(
                currentAudioForSegmentation.data,
                seg.start,
                seg.end
            );

            playlistSegments.push({
                id: generateId(),
                audioUrl: segmentUrl,
                duration: seg.duration,
                transcription: '',
                translation: '',
                startTime: seg.start,
                endTime: seg.end
            });

            const progress = 60 + (40 * (i + 1) / segments.length);
            document.getElementById('segmentProgressFill').style.width = progress + '%';
            document.getElementById('segmentProgressText').textContent =
                `Creating segment ${i + 1} of ${segments.length}...`;
        }

        document.getElementById('segmentProgressFill').style.width = '100%';

        // Prompt for playlist name
        const playlistName = prompt(
            `Found ${playlistSegments.length} segments. Enter playlist name:`,
            currentAudioForSegmentation.name.replace(/\.[^/.]+$/, '')
        );

        if (!playlistName) {
            closeSegmentationModal();
            return;
        }

        // Create playlist
        const playlist = {
            id: generateId(),
            name: playlistName,
            language: state.settings.targetLanguage,
            segments: playlistSegments,
            createdAt: Date.now(),
            sourceAudioId: currentAudioForSegmentation.id,
            vadSettings: {
                algorithm: vadAlgorithm,
                segmentMode,
                minSegmentDuration,
                maxSegmentDuration
            }
        };

        state.playlists.push(playlist);
        saveState();

        closeSegmentationModal();
        renderPlaylists();
        switchTab('playlists');

        alert(`Playlist created with ${playlistSegments.length} segments!`);
    } catch (error) {
        console.error('Segmentation error:', error);
        alert('Error during segmentation: ' + error.message);
    } finally {
        document.getElementById('segmentationProgress').style.display = 'none';
        document.getElementById('segmentButtonText').textContent = 'Create Playlist';
        document.getElementById('segmentProgressFill').style.width = '0%';
    }
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

async function exportPlaylist(playlistId) {
    const playlist = state.playlists.find(p => p.id === playlistId);
    if (!playlist) return;

    if (!playlist.segments || playlist.segments.length === 0) {
        alert('No segments to export');
        return;
    }

    try {
        // Download each segment as a separate audio file
        for (let i = 0; i < playlist.segments.length; i++) {
            const seg = playlist.segments[i];
            const paddedIndex = String(i + 1).padStart(3, '0');

            // Create filename from transcription or index
            let filename = `${playlist.name}_${paddedIndex}`;
            if (seg.transcription && seg.transcription.trim()) {
                const cleanTranscription = seg.transcription
                    .trim()
                    .substring(0, 50)
                    .replace(/[^a-zA-Z0-9\s-]/g, '')
                    .replace(/\s+/g, '_');
                if (cleanTranscription) {
                    filename = `${playlist.name}_${paddedIndex}_${cleanTranscription}`;
                }
            }

            // Download the segment
            const a = document.createElement('a');
            a.href = seg.audioUrl;
            a.download = `${filename}.wav`;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);

            // Small delay between downloads to avoid browser blocking
            if (i < playlist.segments.length - 1) {
                await new Promise(resolve => setTimeout(resolve, 100));
            }
        }

        alert(`Exported ${playlist.segments.length} segments from "${playlist.name}"!`);
    } catch (error) {
        console.error('Export error:', error);
        alert('Error exporting playlist: ' + error.message);
    }
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

    // Ask if user wants to segment the recording
    const shouldSegment = confirm(
        'Would you like to automatically detect speech segments?\n\n' +
        'Yes = Create multiple segments (recommended)\n' +
        'No = Keep as single recording'
    );

    if (!shouldSegment) {
        // Save as single segment
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
    } else {
        // Save to imported audio and trigger segmentation
        const blob = new Blob(state.recordedChunks, { type: 'audio/webm' });
        const reader = new FileReader();

        reader.onload = async function(e) {
            const importedAudio = {
                id: generateId(),
                name: 'Microphone Recording ' + new Date().toLocaleString(),
                duration: audio.duration || 0,
                data: e.target.result,
                createdAt: Date.now()
            };

            state.importedAudio.push(importedAudio);
            saveState();

            discardRecording();

            // Trigger segmentation
            currentAudioForSegmentation = importedAudio;
            showSegmentationModal();
        };

        reader.readAsDataURL(blob);
    }
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

// Backend Detection & YouTube Integration
async function checkBackend() {
    const url = state.settings.backendUrl || 'http://localhost:8765';
    try {
        const response = await fetch(url + '/api/health', { signal: AbortSignal.timeout(2000) });
        const data = await response.json();
        if (data.status === 'ok') {
            state.backendAvailable = true;
            onBackendDetected();
            return true;
        }
    } catch (e) {
        state.backendAvailable = false;
    }
    return false;
}

function onBackendDetected() {
    // Show YouTube card on home screen
    const ytCard = document.getElementById('youtubeCard');
    if (ytCard) ytCard.style.display = '';

    // Show backend status
    const statusEl = document.getElementById('backendStatus');
    if (statusEl) {
        statusEl.style.display = 'block';
        statusEl.innerHTML = '<span style="color:#4ecdc4;">Backend connected</span>';
    }

    // Show backend settings section
    const backendSettings = document.getElementById('backendSettings');
    if (backendSettings) backendSettings.style.display = '';

    // Update connection status in settings
    const connStatus = document.getElementById('backendConnectionStatus');
    if (connStatus) connStatus.textContent = 'Status: Connected';
}

async function processYouTube() {
    const url = document.getElementById('youtubeUrl').value.trim();
    if (!url) {
        alert('Please enter a YouTube URL');
        return;
    }

    if (!state.backendAvailable) {
        alert('Backend not available. Start it with: python3 shadow_cli/server.py');
        return;
    }

    const backendUrl = state.settings.backendUrl || 'http://localhost:8765';
    const progressEl = document.getElementById('ytProgress');
    const resultEl = document.getElementById('ytResult');
    progressEl.style.display = 'block';
    resultEl.style.display = 'none';

    document.getElementById('ytProgressFill').style.width = '20%';
    document.getElementById('ytProgressText').textContent = 'Downloading...';

    try {
        // Step 1: Download
        const dlResponse = await fetch(backendUrl + '/api/youtube/download', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                url: url,
                start: document.getElementById('ytStart').value || null,
                end: document.getElementById('ytEnd').value || null
            })
        });

        if (!dlResponse.ok) {
            const err = await dlResponse.json();
            throw new Error(err.detail || 'Download failed');
        }

        const dlResult = await dlResponse.json();
        document.getElementById('ytProgressFill').style.width = '50%';
        document.getElementById('ytProgressText').textContent = 'Processing segments...';

        // Show title and subtitles
        document.getElementById('ytTitle').textContent = dlResult.title;
        if (dlResult.subtitles && Object.keys(dlResult.subtitles).length > 0) {
            const langs = Object.keys(dlResult.subtitles);
            document.getElementById('ytSubtitles').innerHTML =
                'Subtitles: ' + langs.map(l => `<span style="color:#4ecdc4;">${l}</span>`).join(', ');
        }

        // Step 2: Process
        const processResponse = await fetch(backendUrl + '/api/process', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                audio_id: dlResult.id,
                preset: document.getElementById('ytPreset').value,
                speed: parseFloat(document.getElementById('ytSpeed').value),
                playback_repeats: parseInt(document.getElementById('ytPlaybackRepeats').value),
                user_repeats: parseInt(document.getElementById('ytUserRepeats').value),
                format: document.getElementById('ytFormat').value,
                subtitle_lang: Object.keys(dlResult.subtitles || {})[0] || null
            })
        });

        if (!processResponse.ok) {
            const err = await processResponse.json();
            throw new Error(err.detail || 'Processing failed');
        }

        const processResult = await processResponse.json();
        const audioId = processResult.audio_id || dlResult.id;
        document.getElementById('ytProgressFill').style.width = '100%';
        document.getElementById('ytProgressText').textContent = 'Done!';

        // Show segments
        const segmentsHtml = processResult.segments.map((seg, i) => {
            const text = seg.text ? `<span style="color:#8892b0;"> - ${seg.text}</span>` : '';
            return `<div style="padding:8px;border-bottom:1px solid rgba(255,255,255,0.05);">
                <span style="color:#E94560;">[${i+1}]</span>
                ${(seg.start/1000).toFixed(1)}s - ${(seg.end/1000).toFixed(1)}s${text}
            </div>`;
        }).join('');
        document.getElementById('ytSegments').innerHTML = segmentsHtml;

        // Build playlist from segments
        const playlistSegments = processResult.segments.map((seg, i) => ({
            id: generateId(),
            audioUrl: `${backendUrl}/api/segment/${audioId}/${i}`,
            duration: (seg.end - seg.start) / 1000,
            transcription: seg.text || '',
            translation: '',
            startTime: seg.start / 1000,
            endTime: seg.end / 1000
        }));

        const playlist = {
            id: generateId(),
            name: dlResult.title,
            language: state.settings.targetLanguage,
            segments: playlistSegments,
            createdAt: Date.now(),
            source: 'youtube'
        };

        state.playlists.push(playlist);
        saveState();

        // Show result with both options
        resultEl.style.display = 'block';
        resultEl.innerHTML += `
            <button class="btn-primary" style="margin-top:15px;margin-right:10px;"
                onclick="openPlaylist('${playlist.id}')">
                Practice Now
            </button>
            <a href="${backendUrl}/api/download/${processResult.output_file}"
               class="btn-primary" style="display:inline-block;text-decoration:none;margin-top:15px;"
               download>
                Download MP3
            </a>
        `;

        setTimeout(() => { progressEl.style.display = 'none'; }, 1500);

    } catch (error) {
        alert('Error: ' + error.message);
        progressEl.style.display = 'none';
    }
}

// Initialize
loadState();
renderLibrary();

// Only check backend on desktop (not mobile) to avoid network permission prompts
const isMobile = /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent);
if (!isMobile) {
    checkBackend();
}
