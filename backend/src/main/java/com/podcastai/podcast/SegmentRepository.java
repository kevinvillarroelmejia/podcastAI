package com.podcastai.podcast;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface SegmentRepository extends JpaRepository<Segment, Long> {

    List<Segment> findByPodcastIdOrderByBlockIndexAscPositionAsc(UUID podcastId);

    @Transactional
    long deleteByPodcastId(UUID podcastId);
}
