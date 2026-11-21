package com.podcastai.tts;

import java.util.List;

/**
 * Audio de un bloque y el instante en que empieza y acaba cada intervención (en milisegundos).
 */
public record BlockAudio(byte[] audio, List<Long> startMs, List<Long> endMs) {

    public long durationMs() {
        return endMs.isEmpty() ? 0 : endMs.get(endMs.size() - 1);
    }
}
