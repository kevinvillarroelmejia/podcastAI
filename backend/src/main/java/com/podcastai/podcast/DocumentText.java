package com.podcastai.podcast;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * Texto extraído de un PDF, guardado por su huella para no repetir la extracción.
 * El PDF original nunca se guarda; este texto se borra a los 30 días.
 */
@Entity
@Table(name = "documents")
public class DocumentText {

    @Id
    @Column(length = 64)
    private String sha256;

    @Column(nullable = false)
    private int pageCount;

    @Lob
    @Column(nullable = false)
    private String pagesJson;

    @Column(nullable = false)
    private Instant createdAt;

    protected DocumentText() {
    }

    public DocumentText(String sha256, int pageCount, String pagesJson) {
        this.sha256 = sha256;
        this.pageCount = pageCount;
        this.pagesJson = pagesJson;
        this.createdAt = Instant.now();
    }

    public String getSha256() {
        return sha256;
    }

    public int getPageCount() {
        return pageCount;
    }

    public String getPagesJson() {
        return pagesJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
