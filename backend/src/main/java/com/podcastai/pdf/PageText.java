package com.podcastai.pdf;

/**
 * Texto de una página del PDF (las páginas empiezan en 1).
 */
public record PageText(int page, String text) {

    public boolean isEmpty() {
        return text == null || text.isBlank();
    }
}
