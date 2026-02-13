/**
 * Speech-to-Text and Text-to-Speech Integration
 *
 * This module provides STT and TTS capabilities using:
 * 1. Web Speech API (browser-native, works offline)
 * 2. Backend providers (future: Google Cloud, Whisper, etc.)
 */

class SpeechToText {
    constructor() {
        this.recognition = null;
        this.isListening = false;
        this.onResult = null;
        this.onError = null;

        // Check browser support
        if ('webkitSpeechRecognition' in window) {
            this.recognition = new webkitSpeechRecognition();
            this.setupRecognition();
        } else if ('SpeechRecognition' in window) {
            this.recognition = new SpeechRecognition();
            this.setupRecognition();
        }
    }

    setupRecognition() {
        if (!this.recognition) return;

        this.recognition.continuous = false;
        this.recognition.interimResults = false;
        this.recognition.maxAlternatives = 1;

        this.recognition.onresult = (event) => {
            const result = event.results[0];
            const transcript = result[0].transcript;
            const confidence = result[0].confidence;

            if (this.onResult) {
                this.onResult({ transcript, confidence });
            }
        };

        this.recognition.onerror = (event) => {
            console.error('Speech recognition error:', event.error);
            this.isListening = false;
            if (this.onError) {
                this.onError(event.error);
            }
        };

        this.recognition.onend = () => {
            this.isListening = false;
        };
    }

    isSupported() {
        return this.recognition !== null;
    }

    setLanguage(languageCode) {
        if (this.recognition) {
            this.recognition.lang = languageCode;
        }
    }

    start(languageCode = 'en-US') {
        if (!this.recognition || this.isListening) return false;

        this.setLanguage(languageCode);
        this.isListening = true;

        try {
            this.recognition.start();
            return true;
        } catch (error) {
            console.error('Failed to start recognition:', error);
            this.isListening = false;
            return false;
        }
    }

    stop() {
        if (this.recognition && this.isListening) {
            this.recognition.stop();
            this.isListening = false;
        }
    }

    /**
     * Transcribe a segment audio (using backend if available)
     */
    async transcribeSegment(audioUrl, languageCode = 'en-US') {
        // For now, return a note that manual transcription is needed
        // In the future, this could call a backend STT service
        return {
            success: false,
            note: 'Automatic transcription not implemented. Use live STT or manual entry.',
            transcript: ''
        };
    }
}

class TextToSpeech {
    constructor() {
        this.synth = window.speechSynthesis;
        this.voices = [];
        this.selectedVoice = null;

        // Load voices
        this.loadVoices();

        // Some browsers need a delay before voices are available
        if (speechSynthesis.onvoiceschanged !== undefined) {
            speechSynthesis.onvoiceschanged = () => this.loadVoices();
        }
    }

    loadVoices() {
        this.voices = this.synth.getVoices();
    }

    isSupported() {
        return 'speechSynthesis' in window;
    }

    getVoices(languageCode = null) {
        if (languageCode) {
            return this.voices.filter(voice =>
                voice.lang.startsWith(languageCode.substring(0, 2))
            );
        }
        return this.voices;
    }

    setVoice(voiceURI) {
        this.selectedVoice = this.voices.find(v => v.voiceURI === voiceURI) || null;
    }

    speak(text, options = {}) {
        if (!this.isSupported()) {
            console.error('Text-to-speech not supported');
            return false;
        }

        // Cancel any ongoing speech
        this.synth.cancel();

        const utterance = new SpeechSynthesisUtterance(text);

        // Apply options
        if (this.selectedVoice) {
            utterance.voice = this.selectedVoice;
        }
        if (options.rate) utterance.rate = options.rate; // 0.1 to 10
        if (options.pitch) utterance.pitch = options.pitch; // 0 to 2
        if (options.volume) utterance.volume = options.volume; // 0 to 1
        if (options.lang) utterance.lang = options.lang;

        // Callbacks
        if (options.onEnd) utterance.onend = options.onEnd;
        if (options.onError) utterance.onerror = options.onError;

        this.synth.speak(utterance);
        return true;
    }

    stop() {
        this.synth.cancel();
    }

    pause() {
        this.synth.pause();
    }

    resume() {
        this.synth.resume();
    }

    isPaused() {
        return this.synth.paused;
    }

    isSpeaking() {
        return this.synth.speaking;
    }
}

// Global instances
const stt = new SpeechToText();
const tts = new TextToSpeech();
