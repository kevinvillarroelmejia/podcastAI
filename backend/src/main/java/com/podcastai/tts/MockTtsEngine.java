package com.podcastai.tts;

import com.podcastai.ai.Speaker;

/**
 * Voz de prueba: devuelve silencio de la duración que tendría la frase leída (unas 150 palabras por minuto).
 */
public class MockTtsEngine implements TtsEngine {

    @Override
    public byte[] synthesize(String text, Speaker speaker, String language) {
        int words = text.isBlank() ? 1 : text.strip().split("\\s+").length;
        return Mp3.silence(Math.max(800, words * 400L));
    }

    @Override
    public String name() {
        return "mock";
    }
}
