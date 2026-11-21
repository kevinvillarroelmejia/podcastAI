package com.podcastai.ai;

/**
 * Los dos personajes del podcast.
 */
public enum Speaker {
    LIA("Lía"),
    MARCO("Marco");

    private final String displayName;

    Speaker(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Interpreta lo que devuelve el modelo ("LIA", "Lía", "marco"...). Por defecto, Lía. */
    public static Speaker parse(String raw) {
        String value = raw == null ? "" : raw.strip().toUpperCase().replace('Í', 'I');
        return value.startsWith("MARCO") ? MARCO : LIA;
    }
}
