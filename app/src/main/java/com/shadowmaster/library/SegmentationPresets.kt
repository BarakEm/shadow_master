package com.shadowmaster.library

import com.shadowmaster.data.model.SegmentMode
import com.shadowmaster.data.model.SegmentationConfig

object SegmentationPresets {
    val DEFAULT = SegmentationConfig(
        id = "default-config",
        name = "Standard Sentences",
        minSegmentDurationMs = 500,
        maxSegmentDurationMs = 8000,
        silenceThresholdMs = 700,
        preSpeechBufferMs = 200,
        segmentMode = SegmentMode.SENTENCE
    )

    val SHORT_PHRASES = SegmentationConfig(
        id = "short-phrases",
        name = "Short Phrases",
        minSegmentDurationMs = 500,
        maxSegmentDurationMs = 3000,
        silenceThresholdMs = 500,
        preSpeechBufferMs = 200,
        segmentMode = SegmentMode.WORD
    )

    val LONG_SENTENCES = SegmentationConfig(
        id = "long-sentences",
        name = "Long Sentences",
        minSegmentDurationMs = 1000,
        maxSegmentDurationMs = 12000,
        silenceThresholdMs = 1000,
        preSpeechBufferMs = 300,
        segmentMode = SegmentMode.SENTENCE
    )

    val WORD_BY_WORD = SegmentationConfig(
        id = "word-by-word",
        name = "Word by Word",
        minSegmentDurationMs = 300,
        maxSegmentDurationMs = 2000,
        silenceThresholdMs = 400,
        preSpeechBufferMs = 150,
        segmentMode = SegmentMode.WORD
    )

    fun getAllPresets() = listOf(
        DEFAULT,
        SHORT_PHRASES,
        LONG_SENTENCES,
        WORD_BY_WORD
    )
}
