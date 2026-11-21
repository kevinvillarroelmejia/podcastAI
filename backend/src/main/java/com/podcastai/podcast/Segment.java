package com.podcastai.podcast;

import java.util.UUID;

import com.podcastai.ai.Speaker;

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
 * Una intervención del guion, con sus páginas citadas y su posición dentro del audio del bloque.
 */
@Entity
@Table(name = "segments", indexes = @Index(name = "idx_segments_podcast", columnList = "podcastId,blockIndex,position"))
public class Segment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID podcastId;

    @Column(nullable = false)
    private int blockIndex;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Speaker speaker;

    @Column(nullable = false, length = 2000)
    private String text;

    @Column(length = 200)
    private String pages;

    private long startMs;
    private long endMs;

    protected Segment() {
    }

    public Segment(UUID podcastId, int blockIndex, int position, Speaker speaker, String text, String pages,
                   long startMs, long endMs) {
        this.podcastId = podcastId;
        this.blockIndex = blockIndex;
        this.position = position;
        this.speaker = speaker;
        this.text = text;
        this.pages = pages;
        this.startMs = startMs;
        this.endMs = endMs;
    }

    public UUID getPodcastId() {
        return podcastId;
    }

    public int getBlockIndex() {
        return blockIndex;
    }

    public int getPosition() {
        return position;
    }

    public Speaker getSpeaker() {
        return speaker;
    }

    public String getText() {
        return text;
    }

    public String getPages() {
        return pages;
    }

    public long getStartMs() {
        return startMs;
    }

    public long getEndMs() {
        return endMs;
    }
}
