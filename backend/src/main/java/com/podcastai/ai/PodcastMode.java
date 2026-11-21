package com.podcastai.ai;

/**
 * Modos de guion del MVP.
 */
public enum PodcastMode {
    RESUMEN(5, 7, 8),
    A_FONDO(10, 16, 20);

    private final int minBlocks;
    private final int maxBlocks;
    private final int estimatedMinutes;

    PodcastMode(int minBlocks, int maxBlocks, int estimatedMinutes) {
        this.minBlocks = minBlocks;
        this.maxBlocks = maxBlocks;
        this.estimatedMinutes = estimatedMinutes;
    }

    public int minBlocks() {
        return minBlocks;
    }

    public int maxBlocks() {
        return maxBlocks;
    }

    /** Minutos que se reservan del plan antes de generar (el máximo esperado). */
    public int estimatedMinutes() {
        return estimatedMinutes;
    }
}
