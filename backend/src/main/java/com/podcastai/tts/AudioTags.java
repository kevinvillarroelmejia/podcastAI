package com.podcastai.tts;

import java.util.regex.Pattern;

/**
 * Marcas de interpretación de ElevenLabs v3 ("[laughs]", "[jumping in]"...). Se envían a la voz,
 * pero nunca se muestran en la transcripción ni se mandan a otras voces.
 */
public final class AudioTags {

    private static final Pattern TAG = Pattern.compile("\\[[^\\]]{1,40}\\]");

    private AudioTags() {
    }

    public static String strip(String text) {
        String cleaned = text == null ? "" : TAG.matcher(text).replaceAll("");
        return cleaned.replaceAll("\\s{2,}", " ").strip();
    }
}
