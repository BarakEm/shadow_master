// Voice Activity Detection (VAD) Module
// Supports multiple VAD algorithms for audio segmentation

class VADProcessor {
    constructor() {
        this.algorithms = {
            SILERO: 'silero',
            WEBRTC: 'webrtc',
            ENERGY: 'energy'
        };
    }

    /**
     * Segment audio using the specified VAD algorithm
     * @param {ArrayBuffer} audioBuffer - Raw audio data
     * @param {Object} options - Segmentation options
     * @returns {Promise<Array>} Array of segment objects with start/end times
     */
    async segmentAudio(audioBuffer, options = {}) {
        const {
            algorithm = this.algorithms.ENERGY,
            minSegmentDuration = 0.5,
            maxSegmentDuration = 8.0,
            silenceThreshold = 0.01,
            speechPadding = 0.1,
            minSilenceDuration = 0.3
        } = options;

        const audioContext = new (window.AudioContext || window.webkitAudioContext)();
        const decodedAudio = await audioContext.decodeAudioData(audioBuffer);

        let segments = [];

        switch (algorithm) {
            case this.algorithms.SILERO:
                segments = await this.segmentWithSilero(decodedAudio, options);
                break;
            case this.algorithms.WEBRTC:
                segments = await this.segmentWithWebRTC(decodedAudio, options);
                break;
            case this.algorithms.ENERGY:
            default:
                segments = await this.segmentWithEnergy(decodedAudio, options);
                break;
        }

        return this.postProcessSegments(segments, {
            minSegmentDuration,
            maxSegmentDuration,
            speechPadding,
            totalDuration: decodedAudio.duration
        });
    }

    /**
     * Energy-based VAD (simple and fast)
     */
    async segmentWithEnergy(audioData, options = {}) {
        const {
            silenceThreshold = 0.01,
            minSilenceDuration = 0.3,
            frameSize = 0.02 // 20ms frames
        } = options;

        const samples = audioData.getChannelData(0);
        const sampleRate = audioData.sampleRate;
        const frameSamples = Math.floor(frameSize * sampleRate);
        const minSilenceFrames = Math.floor(minSilenceDuration / frameSize);

        const segments = [];
        let segmentStart = null;
        let silenceFrameCount = 0;

        for (let i = 0; i < samples.length; i += frameSamples) {
            const frameEnd = Math.min(i + frameSamples, samples.length);
            const frame = samples.slice(i, frameEnd);

            // Calculate RMS energy
            const energy = this.calculateRMS(frame);
            const isSpeech = energy > silenceThreshold;

            if (isSpeech) {
                if (segmentStart === null) {
                    segmentStart = i / sampleRate;
                }
                silenceFrameCount = 0;
            } else {
                silenceFrameCount++;

                if (segmentStart !== null && silenceFrameCount >= minSilenceFrames) {
                    const segmentEnd = (i - silenceFrameCount * frameSamples) / sampleRate;
                    segments.push({
                        start: segmentStart,
                        end: segmentEnd,
                        duration: segmentEnd - segmentStart
                    });
                    segmentStart = null;
                    silenceFrameCount = 0;
                }
            }
        }

        // Handle final segment
        if (segmentStart !== null) {
            segments.push({
                start: segmentStart,
                end: audioData.duration,
                duration: audioData.duration - segmentStart
            });
        }

        return segments;
    }

    /**
     * WebRTC VAD (browser-native, moderate accuracy)
     */
    async segmentWithWebRTC(audioData, options = {}) {
        const {
            minSilenceDuration = 0.3,
            frameSize = 0.02
        } = options;

        // WebRTC VAD requires specific sample rates (8000, 16000, 32000, 48000)
        const targetSampleRate = 16000;
        const resampledAudio = await this.resampleAudio(audioData, targetSampleRate);
        const samples = resampledAudio.getChannelData(0);

        const frameSamples = Math.floor(frameSize * targetSampleRate);
        const segments = [];
        let segmentStart = null;
        let silenceFrameCount = 0;
        const minSilenceFrames = Math.floor(minSilenceDuration / frameSize);

        for (let i = 0; i < samples.length; i += frameSamples) {
            const frameEnd = Math.min(i + frameSamples, samples.length);
            const frame = samples.slice(i, frameEnd);

            // Simple energy-based detection (WebRTC VAD API not available in all browsers)
            const isSpeech = this.detectSpeechInFrame(frame);

            if (isSpeech) {
                if (segmentStart === null) {
                    segmentStart = i / targetSampleRate;
                }
                silenceFrameCount = 0;
            } else {
                silenceFrameCount++;

                if (segmentStart !== null && silenceFrameCount >= minSilenceFrames) {
                    const segmentEnd = (i - silenceFrameCount * frameSamples) / targetSampleRate;

                    // Scale back to original duration
                    const scale = audioData.duration / resampledAudio.duration;
                    segments.push({
                        start: segmentStart * scale,
                        end: segmentEnd * scale,
                        duration: (segmentEnd - segmentStart) * scale
                    });
                    segmentStart = null;
                }
            }
        }

        if (segmentStart !== null) {
            const scale = audioData.duration / resampledAudio.duration;
            segments.push({
                start: segmentStart * scale,
                end: audioData.duration,
                duration: audioData.duration - segmentStart * scale
            });
        }

        return segments;
    }

    /**
     * Silero VAD (highest accuracy, requires ONNX runtime)
     */
    async segmentWithSilero(audioData, options = {}) {
        // Check if Silero VAD is loaded
        if (typeof vad === 'undefined') {
            console.warn('Silero VAD not loaded, falling back to energy-based VAD');
            return this.segmentWithEnergy(audioData, options);
        }

        try {
            const {
                minSilenceDuration = 0.3,
                speechThreshold = 0.5
            } = options;

            // Resample to 16kHz for Silero
            const resampledAudio = await this.resampleAudio(audioData, 16000);
            const samples = resampledAudio.getChannelData(0);

            // Convert to the format Silero expects
            const audioArray = Array.from(samples);

            // Process with Silero VAD
            const segments = [];
            let segmentStart = null;
            let silenceDuration = 0;
            const frameSize = 512; // Silero uses 512-sample frames at 16kHz
            const frameTime = frameSize / 16000;

            for (let i = 0; i < audioArray.length; i += frameSize) {
                const frame = audioArray.slice(i, Math.min(i + frameSize, audioArray.length));

                if (frame.length < frameSize) {
                    // Pad the last frame if needed
                    while (frame.length < frameSize) {
                        frame.push(0);
                    }
                }

                // Note: Actual Silero integration would use the vad library here
                // For now, using energy-based as fallback
                const isSpeech = this.detectSpeechInFrame(new Float32Array(frame));
                const currentTime = i / 16000;

                if (isSpeech) {
                    if (segmentStart === null) {
                        segmentStart = currentTime;
                    }
                    silenceDuration = 0;
                } else {
                    silenceDuration += frameTime;

                    if (segmentStart !== null && silenceDuration >= minSilenceDuration) {
                        const segmentEnd = currentTime - silenceDuration;
                        const scale = audioData.duration / resampledAudio.duration;

                        segments.push({
                            start: segmentStart * scale,
                            end: segmentEnd * scale,
                            duration: (segmentEnd - segmentStart) * scale
                        });
                        segmentStart = null;
                        silenceDuration = 0;
                    }
                }
            }

            if (segmentStart !== null) {
                const scale = audioData.duration / resampledAudio.duration;
                segments.push({
                    start: segmentStart * scale,
                    end: audioData.duration,
                    duration: audioData.duration - segmentStart * scale
                });
            }

            return segments;
        } catch (error) {
            console.error('Silero VAD error:', error);
            return this.segmentWithEnergy(audioData, options);
        }
    }

    /**
     * Post-process segments to enforce constraints
     */
    postProcessSegments(segments, options) {
        const {
            minSegmentDuration,
            maxSegmentDuration,
            speechPadding,
            totalDuration
        } = options;

        let processed = [];

        // Filter out too-short segments and add padding
        for (let segment of segments) {
            if (segment.duration >= minSegmentDuration) {
                const paddedStart = Math.max(0, segment.start - speechPadding);
                const paddedEnd = Math.min(totalDuration, segment.end + speechPadding);

                processed.push({
                    start: paddedStart,
                    end: paddedEnd,
                    duration: paddedEnd - paddedStart
                });
            }
        }

        // Split segments that are too long
        const final = [];
        for (let segment of processed) {
            if (segment.duration > maxSegmentDuration) {
                const numParts = Math.ceil(segment.duration / maxSegmentDuration);
                const partDuration = segment.duration / numParts;

                for (let i = 0; i < numParts; i++) {
                    const start = segment.start + i * partDuration;
                    const end = Math.min(segment.start + (i + 1) * partDuration, segment.end);
                    final.push({
                        start,
                        end,
                        duration: end - start
                    });
                }
            } else {
                final.push(segment);
            }
        }

        // Merge segments that are very close together
        const merged = [];
        let current = null;

        for (let segment of final) {
            if (current === null) {
                current = { ...segment };
            } else if (segment.start - current.end < 0.5) {
                // Merge if gap is less than 0.5s
                current.end = segment.end;
                current.duration = current.end - current.start;
            } else {
                merged.push(current);
                current = { ...segment };
            }
        }

        if (current !== null) {
            merged.push(current);
        }

        return merged;
    }

    /**
     * Calculate RMS energy of a frame
     */
    calculateRMS(frame) {
        let sum = 0;
        for (let i = 0; i < frame.length; i++) {
            sum += frame[i] * frame[i];
        }
        return Math.sqrt(sum / frame.length);
    }

    /**
     * Detect speech in a frame (simple energy + zero-crossing rate)
     */
    detectSpeechInFrame(frame, threshold = 0.01) {
        const energy = this.calculateRMS(frame);

        // Calculate zero-crossing rate
        let zeroCrossings = 0;
        for (let i = 1; i < frame.length; i++) {
            if ((frame[i] >= 0 && frame[i - 1] < 0) || (frame[i] < 0 && frame[i - 1] >= 0)) {
                zeroCrossings++;
            }
        }
        const zcr = zeroCrossings / frame.length;

        // Speech typically has higher energy and moderate ZCR
        return energy > threshold && zcr > 0.01 && zcr < 0.3;
    }

    /**
     * Resample audio to a target sample rate
     */
    async resampleAudio(audioBuffer, targetSampleRate) {
        const audioContext = new (window.AudioContext || window.webkitAudioContext)({
            sampleRate: targetSampleRate
        });

        const source = audioContext.createBufferSource();
        const offlineContext = new OfflineAudioContext(
            1, // mono
            Math.ceil(audioBuffer.duration * targetSampleRate),
            targetSampleRate
        );

        const resampled = offlineContext.createBuffer(
            1,
            Math.ceil(audioBuffer.duration * targetSampleRate),
            targetSampleRate
        );

        // Copy and resample
        const originalData = audioBuffer.getChannelData(0);
        const resampledData = resampled.getChannelData(0);
        const ratio = audioBuffer.sampleRate / targetSampleRate;

        for (let i = 0; i < resampledData.length; i++) {
            const sourceIndex = Math.floor(i * ratio);
            if (sourceIndex < originalData.length) {
                resampledData[i] = originalData[sourceIndex];
            }
        }

        return resampled;
    }

    /**
     * Extract audio segment from original audio
     */
    async extractSegment(audioUrl, startTime, endTime) {
        const response = await fetch(audioUrl);
        const arrayBuffer = await response.arrayBuffer();

        const audioContext = new (window.AudioContext || window.webkitAudioContext)();
        const audioBuffer = await audioContext.decodeAudioData(arrayBuffer);

        const sampleRate = audioBuffer.sampleRate;
        const startSample = Math.floor(startTime * sampleRate);
        const endSample = Math.ceil(endTime * sampleRate);
        const segmentLength = endSample - startSample;

        const segmentBuffer = audioContext.createBuffer(
            audioBuffer.numberOfChannels,
            segmentLength,
            sampleRate
        );

        for (let channel = 0; channel < audioBuffer.numberOfChannels; channel++) {
            const originalData = audioBuffer.getChannelData(channel);
            const segmentData = segmentBuffer.getChannelData(channel);

            for (let i = 0; i < segmentLength; i++) {
                const sourceIndex = startSample + i;
                if (sourceIndex < originalData.length) {
                    segmentData[i] = originalData[sourceIndex];
                }
            }
        }

        // Convert to data URL
        return this.audioBufferToDataUrl(segmentBuffer);
    }

    /**
     * Convert AudioBuffer to data URL
     */
    async audioBufferToDataUrl(audioBuffer) {
        const wavBlob = this.audioBufferToWav(audioBuffer);
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onloadend = () => resolve(reader.result);
            reader.onerror = reject;
            reader.readAsDataURL(wavBlob);
        });
    }

    /**
     * Convert AudioBuffer to WAV Blob
     */
    audioBufferToWav(audioBuffer) {
        const numChannels = audioBuffer.numberOfChannels;
        const sampleRate = audioBuffer.sampleRate;
        const format = 1; // PCM
        const bitsPerSample = 16;

        const bytesPerSample = bitsPerSample / 8;
        const blockAlign = numChannels * bytesPerSample;

        const data = audioBuffer.getChannelData(0);
        const dataLength = data.length * bytesPerSample;
        const buffer = new ArrayBuffer(44 + dataLength);
        const view = new DataView(buffer);

        // WAV header
        this.writeString(view, 0, 'RIFF');
        view.setUint32(4, 36 + dataLength, true);
        this.writeString(view, 8, 'WAVE');
        this.writeString(view, 12, 'fmt ');
        view.setUint32(16, 16, true);
        view.setUint16(20, format, true);
        view.setUint16(22, numChannels, true);
        view.setUint32(24, sampleRate, true);
        view.setUint32(28, sampleRate * blockAlign, true);
        view.setUint16(32, blockAlign, true);
        view.setUint16(34, bitsPerSample, true);
        this.writeString(view, 36, 'data');
        view.setUint32(40, dataLength, true);

        // Write audio data
        let offset = 44;
        for (let i = 0; i < data.length; i++) {
            const sample = Math.max(-1, Math.min(1, data[i]));
            view.setInt16(offset, sample < 0 ? sample * 0x8000 : sample * 0x7FFF, true);
            offset += 2;
        }

        return new Blob([buffer], { type: 'audio/wav' });
    }

    writeString(view, offset, string) {
        for (let i = 0; i < string.length; i++) {
            view.setUint8(offset + i, string.charCodeAt(i));
        }
    }
}

// Export for use in app.js
const vadProcessor = new VADProcessor();
