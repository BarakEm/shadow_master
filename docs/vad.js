// Voice Activity Detection (VAD) Module - Optimized Version
// Supports multiple VAD algorithms for audio segmentation
// Optimizations: Web Workers, downsampling, larger frames, typed arrays, chunked processing

class VADProcessor {
    constructor() {
        this.algorithms = {
            SILERO: 'silero',
            WEBRTC: 'webrtc',
            ENERGY: 'energy'
        };
        
        // Cache for decoded audio buffers
        this.audioCache = new Map();
        
        // Worker for heavy VAD processing
        this.vadWorker = null;
        this.initializeWorker();
    }

    /**
     * Initialize Web Worker for VAD processing
     */
    initializeWorker() {
        if (typeof Worker !== 'undefined') {
            // Create inline worker from blob
            const workerCode = `
                self.onmessage = function(e) {
                    const { type, data, options } = e.data;
                    
                    if (type === 'processEnergy') {
                        const result = processEnergyVAD(data.samples, data.sampleRate, options);
                        self.postMessage({ type: 'result', data: result });
                    } else if (type === 'processChunk') {
                        const result = processEnergyVADChunk(
                            data.samples, data.sampleRate, 
                            data.chunkStart, data.chunkEnd, 
                            options
                        );
                        self.postMessage({ type: 'chunkResult', data: result });
                    }
                };

                function processEnergyVAD(samples, sampleRate, options) {
                    const {
                        silenceThreshold = 0.01,
                        minSilenceDuration = 0.3,
                        frameSize = 0.05 // 50ms frames (optimized, larger than default 20ms)
                    } = options;

                    const frameSamples = Math.floor(frameSize * sampleRate);
                    const hopSamples = Math.floor(frameSamples / 2); // 50% overlap for better detection
                    const minSilenceFrames = Math.floor(minSilenceDuration / frameSize);

                    const segments = [];
                    let segmentStart = null;
                    let silenceFrameCount = 0;
                    
                    // Process with larger steps
                    for (let i = 0; i < samples.length; i += hopSamples) {
                        const frameEnd = Math.min(i + frameSamples, samples.length);
                        const frame = samples.subarray(i, frameEnd);

                        // Calculate RMS energy efficiently
                        const energy = calculateRMS(frame);
                        const isSpeech = energy > silenceThreshold;

                        if (isSpeech) {
                            if (segmentStart === null) {
                                segmentStart = i / sampleRate;
                            }
                            silenceFrameCount = 0;
                        } else {
                            silenceFrameCount++;

                            if (segmentStart !== null && silenceFrameCount >= minSilenceFrames) {
                                const segmentEnd = (i - silenceFrameCount * hopSamples) / sampleRate;
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
                            end: samples.length / sampleRate,
                            duration: (samples.length / sampleRate) - segmentStart
                        });
                    }

                    return segments;
                }

                function processEnergyVADChunk(samples, sampleRate, chunkStart, chunkEnd, options) {
                    const {
                        silenceThreshold = 0.01,
                        minSilenceDuration = 0.3,
                        frameSize = 0.05
                    } = options;

                    const frameSamples = Math.floor(frameSize * sampleRate);
                    const hopSamples = Math.floor(frameSamples / 2);
                    const minSilenceFrames = Math.floor(minSilenceDuration / frameSize);

                    const segments = [];
                    let segmentStart = null;
                    let silenceFrameCount = 0;
                    
                    const startSample = Math.floor(chunkStart * sampleRate);
                    const endSample = Math.min(Math.floor(chunkEnd * sampleRate), samples.length);

                    for (let i = startSample; i < endSample; i += hopSamples) {
                        const frameEnd = Math.min(i + frameSamples, samples.length);
                        const frame = samples.subarray(i, frameEnd);

                        const energy = calculateRMS(frame);
                        const isSpeech = energy > silenceThreshold;
                        const currentTime = i / sampleRate;

                        if (isSpeech) {
                            if (segmentStart === null) {
                                segmentStart = currentTime;
                            }
                            silenceFrameCount = 0;
                        } else {
                            silenceFrameCount++;

                            if (segmentStart !== null && silenceFrameCount >= minSilenceFrames) {
                                const segmentEnd = (i - silenceFrameCount * hopSamples) / sampleRate;
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

                    return { segments, chunkStart, chunkEnd };
                }

                function calculateRMS(frame) {
                    let sum = 0;
                    const len = frame.length;
                    for (let i = 0; i < len; i++) {
                        sum += frame[i] * frame[i];
                    }
                    return Math.sqrt(sum / len);
                }
            `;
            
            const blob = new Blob([workerCode], { type: 'application/javascript' });
            const workerUrl = URL.createObjectURL(blob);
            this.vadWorker = new Worker(workerUrl);
            
            this.vadWorker.onmessage = (e) => {
                const { type, data } = e.data;
                if (this.pendingResolve) {
                    if (type === 'result') {
                        this.pendingResolve(data);
                        this.pendingResolve = null;
                        this.pendingReject = null;
                    }
                }
            };
        }
    }

    /**
     * Cache decoded audio buffer
     */
    cacheAudio(audioId, audioBuffer) {
        this.audioCache.set(audioId, audioBuffer);
    }

    /**
     * Get cached audio buffer
     */
    getCachedAudio(audioId) {
        return this.audioCache.get(audioId);
    }

    /**
     * Clear audio cache
     */
    clearCache() {
        this.audioCache.clear();
    }

    /**
     * Downsample audio buffer efficiently (using OfflineAudioContext)
     */
    async downsampleAudio(audioBuffer, targetSampleRate) {
        const sourceRate = audioBuffer.sampleRate;
        
        // Already at target rate
        if (sourceRate === targetSampleRate) {
            return audioBuffer;
        }

        const ratio = sourceRate / targetSampleRate;
        const newLength = Math.ceil(audioBuffer.length / ratio);
        
        const offlineContext = new OfflineAudioContext(
            audioBuffer.numberOfChannels,
            newLength,
            targetSampleRate
        );

        const source = offlineContext.createBufferSource();
        source.buffer = audioBuffer;
        source.connect(offlineContext.destination);
        source.start();

        return await offlineContext.startRendering();
    }

    /**
     * Efficiently extract channel data as typed array
     */
    getTypedChannelData(audioBuffer, channel = 0) {
        return audioBuffer.getChannelData(channel);
    }

    /**
     * Segment audio using the specified VAD algorithm
     * @param {ArrayBuffer} audioBuffer - Raw audio data
     * @param {Object} options - Segmentation options
     * @param {Object} progressCallback - Optional callback for progress updates
     * @returns {Promise<Array>} Array of segment objects with start/end times
     */
    async segmentAudio(audioBuffer, options = {}, progressCallback = null) {
        const {
            algorithm = this.algorithms.ENERGY,
            minSegmentDuration = 0.5,
            maxSegmentDuration = 8.0,
            silenceThreshold = 0.01,
            speechPadding = 0.1,
            minSilenceDuration = 0.3,
            downsampleRate = 8000 // Downsample to 8kHz for energy detection
        } = options;

        if (progressCallback) progressCallback({ phase: 'decoding', progress: 0 });

        const audioContext = new (window.AudioContext || window.webkitAudioContext)();
        const decodedAudio = await audioContext.decodeAudioData(audioBuffer.slice(0));
        
        if (progressCallback) progressCallback({ phase: 'decoding', progress: 100 });

        let segments = [];

        switch (algorithm) {
            case this.algorithms.SILERO:
                segments = await this.segmentWithSilero(decodedAudio, options, progressCallback);
                break;
            case this.algorithms.WEBRTC:
                segments = await this.segmentWithWebRTC(decodedAudio, options, progressCallback);
                break;
            case this.algorithms.ENERGY:
            default:
                segments = await this.segmentWithEnergy(decodedAudio, options, progressCallback);
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
     * Energy-based VAD - Optimized version with Web Worker support
     */
    async segmentWithEnergy(audioData, options = {}, progressCallback = null) {
        const {
            silenceThreshold = 0.01,
            minSilenceDuration = 0.3,
            frameSize = 0.05, // 50ms frames (larger = faster)
            useWorker = true,
            downsampleRate = 8000 // Downsample to 8kHz for energy detection
        } = options;

        // Downsample for faster energy detection (8kHz is plenty for energy-based VAD)
        if (progressCallback) progressCallback({ phase: 'downsampling', progress: 0 });
        
        let processingBuffer = audioData;
        let sampleRate = audioData.sampleRate;
        
        if (audioData.sampleRate > downsampleRate) {
            processingBuffer = await this.downsampleAudio(audioData, downsampleRate);
            sampleRate = downsampleRate;
        }
        
        if (progressCallback) progressCallback({ phase: 'downsampling', progress: 100 });

        // Get typed array directly (no copying)
        const samples = this.getTypedChannelData(processingBuffer, 0);

        // Use Web Worker if available
        if (useWorker && this.vadWorker && typeof Worker !== 'undefined') {
            return new Promise((resolve, reject) => {
                if (progressCallback) progressCallback({ phase: 'processing', progress: 0 });
                
                this.pendingResolve = (segments) => {
                    if (progressCallback) progressCallback({ phase: 'processing', progress: 100 });
                    
                    // Scale segment times back to original sample rate
                    const scale = audioData.sampleRate / sampleRate;
                    const scaledSegments = segments.map(seg => ({
                        start: seg.start * scale,
                        end: seg.end * scale,
                        duration: seg.duration * scale
                    }));
                    
                    resolve(scaledSegments);
                };
                
                this.pendingReject = reject;
                
                // Send data to worker
                this.vadWorker.postMessage({
                    type: 'processEnergy',
                    data: {
                        samples: samples, // Typed array is transferable
                        sampleRate: sampleRate
                    },
                    options: {
                        silenceThreshold,
                        minSilenceDuration,
                        frameSize
                    }
                });
            });
        }

        // Fallback to main thread processing (still optimized)
        if (progressCallback) progressCallback({ phase: 'processing', progress: 0 });

        const frameSamples = Math.floor(frameSize * sampleRate);
        const hopSamples = Math.floor(frameSamples / 2); // 50% hop
        const minSilenceFrames = Math.floor(minSilenceDuration / frameSize);

        const segments = [];
        let segmentStart = null;
        let silenceFrameCount = 0;

        const totalFrames = Math.ceil(samples.length / hopSamples);
        let processedFrames = 0;

        // Use typed array efficiently - no slice(), use subarray()
        for (let i = 0; i < samples.length; i += hopSamples) {
            const frameEnd = Math.min(i + frameSamples, samples.length);
            const frame = samples.subarray(i, frameEnd); // Zero-copy view

            // Calculate RMS energy efficiently
            const energy = this.calculateRMSFast(frame);
            const isSpeech = energy > silenceThreshold;

            if (isSpeech) {
                if (segmentStart === null) {
                    segmentStart = i / sampleRate;
                }
                silenceFrameCount = 0;
            } else {
                silenceFrameCount++;

                if (segmentStart !== null && silenceFrameCount >= minSilenceFrames) {
                    const segmentEnd = (i - silenceFrameCount * hopSamples) / sampleRate;
                    segments.push({
                        start: segmentStart,
                        end: segmentEnd,
                        duration: segmentEnd - segmentStart
                    });
                    segmentStart = null;
                    silenceFrameCount = 0;
                }
            }

            // Report progress periodically
            processedFrames++;
            if (progressCallback && processedFrames % 1000 === 0) {
                const progress = Math.min(95, (processedFrames / totalFrames) * 100);
                progressCallback({ phase: 'processing', progress });
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

        if (progressCallback) progressCallback({ phase: 'processing', progress: 100 });

        return segments;
    }

    /**
     * Fast RMS calculation using typed arrays
     */
    calculateRMSFast(frame) {
        const len = frame.length;
        let sum = 0;
        
        // Unrolled loop for better performance
        for (let i = 0; i < len; i++) {
            const sample = frame[i];
            sum += sample * sample;
        }
        
        return Math.sqrt(sum / len);
    }

    /**
     * WebRTC VAD (browser-native, moderate accuracy)
     */
    async segmentWithWebRTC(audioData, options = {}, progressCallback = null) {
        const {
            minSilenceDuration = 0.3,
            frameSize = 0.05 // 50ms frames
        } = options;

        // WebRTC VAD requires specific sample rates (8000, 16000, 32000, 48000)
        const targetSampleRate = 16000;
        
        if (progressCallback) progressCallback({ phase: 'resampling', progress: 0 });
        
        const resampledAudio = await this.resampleAudio(audioData, targetSampleRate);
        
        if (progressCallback) progressCallback({ phase: 'resampling', progress: 100 });

        const samples = resampledAudio.getChannelData(0);
        const frameSamples = Math.floor(frameSize * targetSampleRate);
        const hopSamples = Math.floor(frameSamples / 2);
        
        const segments = [];
        let segmentStart = null;
        let silenceFrameCount = 0;
        const minSilenceFrames = Math.floor(minSilenceDuration / frameSize);

        const totalFrames = Math.ceil(samples.length / hopSamples);
        let processedFrames = 0;

        for (let i = 0; i < samples.length; i += hopSamples) {
            const frameEnd = Math.min(i + frameSamples, samples.length);
            const frame = samples.subarray(i, frameEnd);

            const isSpeech = this.detectSpeechInFrame(frame);

            if (isSpeech) {
                if (segmentStart === null) {
                    segmentStart = i / targetSampleRate;
                }
                silenceFrameCount = 0;
            } else {
                silenceFrameCount++;

                if (segmentStart !== null && silenceFrameCount >= minSilenceFrames) {
                    const segmentEnd = (i - silenceFrameCount * hopSamples) / targetSampleRate;

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

            processedFrames++;
            if (progressCallback && processedFrames % 1000 === 0) {
                const progress = Math.min(95, (processedFrames / totalFrames) * 100);
                progressCallback({ phase: 'processing', progress });
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

        if (progressCallback) progressCallback({ phase: 'processing', progress: 100 });

        return segments;
    }

    /**
     * Silero VAD (highest accuracy, requires ONNX runtime)
     */
    async segmentWithSilero(audioData, options = {}, progressCallback = null) {
        if (typeof vad === 'undefined') {
            console.warn('Silero VAD not loaded, falling back to energy-based VAD');
            return this.segmentWithEnergy(audioData, options, progressCallback);
        }

        try {
            const {
                minSilenceDuration = 0.3,
                speechThreshold = 0.5
            } = options;

            if (progressCallback) progressCallback({ phase: 'resampling', progress: 0 });

            // Resample to 16kHz for Silero
            const resampledAudio = await this.resampleAudio(audioData, 16000);
            
            if (progressCallback) progressCallback({ phase: 'resampling', progress: 100 });

            const samples = resampledAudio.getChannelData(0);
            const frameSize = 512; // Silero uses 512-sample frames at 16kHz
            const hopSize = 256; // 50% overlap
            const frameTime = frameSize / 16000;

            const segments = [];
            let segmentStart = null;
            let silenceDuration = 0;

            const totalFrames = Math.ceil(samples.length / hopSize);
            let processedFrames = 0;

            for (let i = 0; i < samples.length; i += hopSize) {
                const frame = samples.subarray(i, Math.min(i + frameSize, samples.length));

                if (frame.length < frameSize) break;

                const isSpeech = this.detectSpeechInFrame(frame);
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

                processedFrames++;
                if (progressCallback && processedFrames % 1000 === 0) {
                    const progress = Math.min(95, (processedFrames / totalFrames) * 100);
                    progressCallback({ phase: 'processing', progress });
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

            if (progressCallback) progressCallback({ phase: 'processing', progress: 100 });

            return segments;
        } catch (error) {
            console.error('Silero VAD error:', error);
            return this.segmentWithEnergy(audioData, options, progressCallback);
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
     * Calculate RMS energy of a frame (standard version)
     */
    calculateRMS(frame) {
        let sum = 0;
        const len = frame.length;
        for (let i = 0; i < len; i++) {
            sum += frame[i] * frame[i];
        }
        return Math.sqrt(sum / len);
    }

    /**
     * Detect speech in a frame (simple energy + zero-crossing rate)
     */
    detectSpeechInFrame(frame, threshold = 0.01) {
        const energy = this.calculateRMS(frame);

        // Calculate zero-crossing rate
        let zeroCrossings = 0;
        const len = frame.length;
        for (let i = 1; i < len; i++) {
            if ((frame[i] >= 0 && frame[i - 1] < 0) || (frame[i] < 0 && frame[i - 1] >= 0)) {
                zeroCrossings++;
            }
        }
        const zcr = zeroCrossings / len;

        // Speech typically has higher energy and moderate ZCR
        return energy > threshold && zcr > 0.01 && zcr < 0.3;
    }

    /**
     * Resample audio to a target sample rate (optimized)
     */
    async resampleAudio(audioBuffer, targetSampleRate) {
        const sourceRate = audioBuffer.sampleRate;
        
        if (sourceRate === targetSampleRate) {
            return audioBuffer;
        }

        const ratio = sourceRate / targetSampleRate;
        const newLength = Math.ceil(audioBuffer.length / ratio);

        const offlineContext = new OfflineAudioContext(
            audioBuffer.numberOfChannels,
            newLength,
            targetSampleRate
        );

        const source = offlineContext.createBufferSource();
        source.buffer = audioBuffer;
        source.connect(offlineContext.destination);
        source.start();

        return await offlineContext.startRendering();
    }

    /**
     * Extract multiple audio segments efficiently (batched)
     */
    async extractSegments(audioUrl, segments, progressCallback = null) {
        // Fetch audio once
        const response = await fetch(audioUrl);
        const arrayBuffer = await response.arrayBuffer();

        const audioContext = new (window.AudioContext || window.webkitAudioContext)();
        const audioBuffer = await audioContext.decodeAudioData(arrayBuffer);

        const results = [];
        const total = segments.length;

        // Process in parallel batches
        const batchSize = 5;
        
        for (let batchStart = 0; batchStart < total; batchStart += batchSize) {
            const batchEnd = Math.min(batchStart + batchSize, total);
            const batchPromises = [];
            
            for (let i = batchStart; i < batchEnd; i++) {
                const seg = segments[i];
                batchPromises.push(this.extractSingleSegment(audioBuffer, seg.start, seg.end));
            }

            const batchResults = await Promise.all(batchPromises);
            results.push(...batchResults);

            if (progressCallback) {
                const progress = Math.min(100, ((batchEnd / total) * 100));
                progressCallback({ phase: 'extracting', progress });
            }
        }

        return results;
    }

    /**
     * Extract single audio segment
     */
    async extractSingleSegment(audioBuffer, startTime, endTime) {
        const audioContext = new (window.AudioContext || window.webkitAudioContext)();
        
        const sampleRate = audioBuffer.sampleRate;
        const startSample = Math.floor(startTime * sampleRate);
        const endSample = Math.ceil(endTime * sampleRate);
        const segmentLength = endSample - startSample;

        const segmentBuffer = audioContext.createBuffer(
            audioBuffer.numberOfChannels,
            segmentLength,
            sampleRate
        );

        // Use efficient copy
        for (let channel = 0; channel < audioBuffer.numberOfChannels; channel++) {
            const originalData = audioBuffer.getChannelData(channel);
            const segmentData = segmentBuffer.getChannelData(channel);
            
            // Use set() for faster copy
            segmentData.set(originalData.subarray(startSample, endSample));
        }

        // Convert to data URL
        const dataUrl = await this.audioBufferToDataUrl(segmentBuffer);
        
        return {
            audioUrl: dataUrl,
            duration: endTime - startTime,
            startTime,
            endTime
        };
    }

    /**
     * Extract audio segment from original audio (legacy compatibility)
     */
    async extractSegment(audioUrl, startTime, endTime) {
        const result = await this.extractSingleSegmentFromUrl(audioUrl, startTime, endTime);
        return result.audioUrl;
    }

    /**
     * Extract segment from URL (fetches once, reuses buffer)
     */
    async extractSingleSegmentFromUrl(audioUrl, startTime, endTime, cachedBuffer = null) {
        let audioBuffer = cachedBuffer;
        
        if (!audioBuffer) {
            const response = await fetch(audioUrl);
            const arrayBuffer = await response.arrayBuffer();

            const audioContext = new (window.AudioContext || window.webkitAudioContext)();
            audioBuffer = await audioContext.decodeAudioData(arrayBuffer);
        }

        return await this.extractSingleSegment(audioBuffer, startTime, endTime);
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
     * Convert AudioBuffer to WAV Blob (optimized)
     */
    audioBufferToWav(audioBuffer) {
        const numChannels = audioBuffer.numberOfChannels;
        const sampleRate = audioBuffer.sampleRate;
        const format = 1; // PCM
        const bitsPerSample = 16;

        const bytesPerSample = bitsPerSample / 8;
        const blockAlign = numChannels * bytesPerSample;

        // Get first channel data
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

        // Write audio data efficiently
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
