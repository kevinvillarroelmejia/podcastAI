package com.podcastai.podcast;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface PodcastBlockRepository extends JpaRepository<PodcastBlock, Long> {

    List<PodcastBlock> findByPodcastIdOrderByBlockIndex(UUID podcastId);

    Optional<PodcastBlock> findByPodcastIdAndBlockIndex(UUID podcastId, int blockIndex);

    @Transactional
    long deleteByPodcastId(UUID podcastId);
}
