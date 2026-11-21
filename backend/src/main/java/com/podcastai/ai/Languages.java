package com.podcastai.ai;

import java.util.Map;

/**
 * Idiomas admitidos (código ISO 639-1 y nombre para el prompt).
 */
public final class Languages {

    private static final Map<String, String> NAMES = Map.of(
            "es", "español",
            "en", "English",
            "ca", "català",
            "gl", "galego",
            "eu", "euskara",
            "fr", "français",
            "de", "Deutsch",
            "it", "italiano",
            "pt", "português");

    private Languages() {
    }

    public static boolean isSupported(String code) {
        return code != null && NAMES.containsKey(code);
    }

    public static String nameOf(String code) {
        return NAMES.getOrDefault(code, code);
    }
}
