package com.podcastai.podcast;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface PodcastRepository extends JpaRepository<Podcast, UUID> {

    List<Podcast> findByAccountIdOrderByCreatedAtDesc(String accountId);

    List<Podcast> findByStatusIn(List<PodcastStatus> statuses);

    @Transactional
    long deleteByAccountId(String accountId);
}
