package com.podcastai.pdf;

import java.util.List;

/**
 * Resultado de leer un PDF: su huella SHA-256, un título orientativo y el texto por página.
 */
public record ExtractedPdf(String sha256, String title, List<PageText> pages) {

    public long emptyPages() {
        return pages.stream().filter(PageText::isEmpty).count();
    }
}
