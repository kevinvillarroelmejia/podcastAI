package com.podcastai.podcast;

public enum PodcastStatus {
    QUEUED,
    MAPPING,
    GENERATING,
    READY,
    FAILED;

    public boolean isFinished() {
        return this == READY || this == FAILED;
    }
}
