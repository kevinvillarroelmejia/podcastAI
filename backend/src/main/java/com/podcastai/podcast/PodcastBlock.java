package com.podcastai.podcast;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Un bloque de unos 60 segundos: un capítulo del reproductor y un archivo de audio.
 */
@Entity
@Table(name = "podcast_blocks", indexes = @Index(name = "idx_blocks_podcast", columnList = "podcastId,blockIndex"))
public class PodcastBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID podcastId;

    @Column(nullable = false)
    private int blockIndex;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BlockStatus status = BlockStatus.PENDING;

    @Column(length = 300)
    private String audioPath;

    private long durationMs;

    protected PodcastBlock() {
    }

    public PodcastBlock(UUID podcastId, int blockIndex, String title) {
        this.podcastId = podcastId;
        this.blockIndex = blockIndex;
        this.title = title;
    }

    public Long getId() {
        return id;
    }

    public UUID getPodcastId() {
        return podcastId;
    }

    public int getBlockIndex() {
        return blockIndex;
    }

    public String getTitle() {
        return title;
    }

    public BlockStatus getStatus() {
        return status;
    }

    public void setStatus(BlockStatus status) {
        this.status = status;
    }

    public String getAudioPath() {
        return audioPath;
    }

    public void setAudioPath(String audioPath) {
        this.audioPath = audioPath;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
}
