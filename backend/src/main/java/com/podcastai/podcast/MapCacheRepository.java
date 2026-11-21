package com.podcastai.podcast;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface MapCacheRepository extends JpaRepository<MapCache, String> {

    @Transactional
    long deleteByCreatedAtBefore(Instant limit);
}
