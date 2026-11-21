package com.podcastai.tts;

import com.podcastai.ai.Speaker;

/**
 * Convierte una intervención en audio MP3 con la voz de su personaje.
 */
public interface TtsEngine {

    byte[] synthesize(String text, Speaker speaker, String language);

    String name();
}
