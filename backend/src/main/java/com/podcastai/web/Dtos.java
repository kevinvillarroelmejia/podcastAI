package com.podcastai.web;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.podcastai.account.Account;
import com.podcastai.podcast.Podcast;
import com.podcastai.podcast.PodcastBlock;
import com.podcastai.podcast.Segment;

/**
 * Objetos que la API devuelve a la app.
 */
public final class Dtos {

    private Dtos() {
    }

    public record PodcastSummary(UUID id, String title, String mode, String language, String status, int totalBlocks,
                                 int readyBlocks, long durationMs, String errorMessage, Instant createdAt) {

        public static PodcastSummary of(Podcast p) {
            return new PodcastSummary(p.getId(), p.getTitle(), p.getMode().name(), p.getLanguage(), p.getStatus().name(),
                    p.getTotalBlocks(), p.getReadyBlocks(), p.getDurationMs(), p.getErrorMessage(), p.getCreatedAt());
        }
    }

    public record SegmentView(int position, String speaker, String speakerName, String text, List<Integer> pages,
                              long startMs, long endMs) {

        public static SegmentView of(Segment s) {
            List<Integer> pages = s.getPages() == null || s.getPages().isBlank() ? List.of()
                    : Arrays.stream(s.getPages().split(",")).map(Integer::valueOf).toList();
            return new SegmentView(s.getPosition(), s.getSpeaker().name(), s.getSpeaker().displayName(), s.getText(),
                    pages, s.getStartMs(), s.getEndMs());
        }
    }

    public record BlockView(int index, String title, String status, long durationMs, String audioUrl,
                            List<SegmentView> segments) {

        public static BlockView of(PodcastBlock b, String audioUrl, List<SegmentView> segments) {
            return new BlockView(b.getBlockIndex(), b.getTitle(), b.getStatus().name(), b.getDurationMs(), audioUrl,
                    segments);
        }
    }

    public record Quality(int keptTurns, int removedTurns, int rewrittenTurns, Long firstAudioMs) {
    }

    public record PodcastDetail(PodcastSummary podcast, int pageCount, Quality quality, List<BlockView> blocks) {

        public static PodcastDetail of(Podcast p, List<BlockView> blocks) {
            Long firstAudio = p.getFirstAudioAt() == null ? null
                    : p.getFirstAudioAt().toEpochMilli() - p.getCreatedAt().toEpochMilli();
            Quality quality = new Quality(p.getTotalTurns(), p.getRemovedTurns(), p.getRewrittenTurns(), firstAudio);
            return new PodcastDetail(PodcastSummary.of(p), p.getPageCount(), quality, blocks);
        }
    }

    public record AccountView(String plan, double minutesUsed, int monthlyMinutes, int freePodcastsLeft,
                              String periodStart, String scriptEngine, String voiceEngine) {

        public static AccountView of(Account a, int monthlyMinutes, int freePodcasts, String scriptEngine,
                                     String voiceEngine) {
            return new AccountView(a.getPlan().name(), Math.round(a.getMinutesUsed() * 10) / 10.0, monthlyMinutes,
                    Math.max(0, freePodcasts - a.getFreePodcastsUsed()), a.getPeriodStart().toString(), scriptEngine,
                    voiceEngine);
        }
    }
}
