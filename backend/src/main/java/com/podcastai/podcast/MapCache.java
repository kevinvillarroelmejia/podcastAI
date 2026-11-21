package com.podcastai.podcast;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * Mapa del contenido ya calculado para un PDF, modo e idioma.
 */
@Entity
@Table(name = "map_cache")
public class MapCache {

    @Id
    @Column(length = 100)
    private String cacheKey;

    @Lob
    @Column(nullable = false)
    private String mapJson;

    @Column(nullable = false)
    private Instant createdAt;

    protected MapCache() {
    }

    public MapCache(String cacheKey, String mapJson) {
        this.cacheKey = cacheKey;
        this.mapJson = mapJson;
        this.createdAt = Instant.now();
    }

    public static String keyOf(String sha256, String mode, String language) {
        return sha256 + ":" + mode + ":" + language;
    }

    public String getMapJson() {
        return mapJson;
    }
}
