package com.podcastai.podcast;

import java.time.Instant;
import java.util.UUID;

import com.podcastai.ai.PodcastMode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "podcasts", indexes = @Index(name = "idx_podcasts_account", columnList = "accountId"))
public class Podcast {

    @Id
    private UUID id;

    @Column(nullable = false, length = 64)
    private String accountId;

    @Column(nullable = false, length = 64)
    private String documentSha256;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PodcastMode mode;

    @Column(nullable = false, length = 8)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PodcastStatus status = PodcastStatus.QUEUED;

    private int pageCount;
    private int totalBlocks;
    private int readyBlocks;
    private long durationMs;
    private int totalTurns;
    private int removedTurns;
    private int rewrittenTurns;
    private boolean usedFreeQuota;

    /** Voz con la que se genera: "elevenlabs" (Premium) o la estándar ("openai" o "mock"). */
    @Column(length = 16)
    private String voice;

    @Column(length = 300)
    private String errorMessage;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant firstAudioAt;
    private Instant finishedAt;

    protected Podcast() {
    }

    public Podcast(String accountId, String documentSha256, String title, PodcastMode mode, String language,
                   int pageCount) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.documentSha256 = documentSha256;
        this.title = title;
        this.mode = mode;
        this.language = language;
        this.pageCount = pageCount;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getDocumentSha256() {
        return documentSha256;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public PodcastMode getMode() {
        return mode;
    }

    public String getLanguage() {
        return language;
    }

    public PodcastStatus getStatus() {
        return status;
    }

    public void setStatus(PodcastStatus status) {
        this.status = status;
    }

    public int getPageCount() {
        return pageCount;
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    public void setTotalBlocks(int totalBlocks) {
        this.totalBlocks = totalBlocks;
    }

    public int getReadyBlocks() {
        return readyBlocks;
    }

    public void setReadyBlocks(int readyBlocks) {
        this.readyBlocks = readyBlocks;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public int getTotalTurns() {
        return totalTurns;
    }

    public void setTotalTurns(int totalTurns) {
        this.totalTurns = totalTurns;
    }

    public int getRemovedTurns() {
        return removedTurns;
    }

    public void setRemovedTurns(int removedTurns) {
        this.removedTurns = removedTurns;
    }

    public int getRewrittenTurns() {
        return rewrittenTurns;
    }

    public void setRewrittenTurns(int rewrittenTurns) {
        this.rewrittenTurns = rewrittenTurns;
    }

    public boolean isUsedFreeQuota() {
        return usedFreeQuota;
    }

    public void setUsedFreeQuota(boolean usedFreeQuota) {
        this.usedFreeQuota = usedFreeQuota;
    }

    public String getVoice() {
        return voice;
    }

    public void setVoice(String voice) {
        this.voice = voice;
    }

    public boolean usesExpressiveVoice() {
        return "elevenlabs".equals(voice);
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFirstAudioAt() {
        return firstAudioAt;
    }

    public void setFirstAudioAt(Instant firstAudioAt) {
        this.firstAudioAt = firstAudioAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }
}
