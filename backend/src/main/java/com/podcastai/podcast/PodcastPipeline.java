package com.podcastai.podcast;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.anthropic.errors.AnthropicException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.podcastai.account.AccountService;
import com.podcastai.ai.BlockRequest;
import com.podcastai.ai.ScriptEngine;
import com.podcastai.ai.ScriptGenerationException;
import com.podcastai.ai.ScriptModels.ContentMap;
import com.podcastai.ai.ScriptModels.PlannedBlock;
import com.podcastai.ai.ScriptModels.Turn;
import com.podcastai.ai.ScriptModels.VerificationResult;
import com.podcastai.ai.ScriptModels.Verdict;
import com.podcastai.ai.Speaker;
import com.podcastai.config.AppProperties;
import com.podcastai.pdf.PageText;
import com.podcastai.tts.Mp3;
import com.podcastai.tts.TtsEngine;
import com.podcastai.tts.TtsException;

/**
 * El pipeline del podcast: mapa → por cada bloque (guion → verificación → voz) → audio listo.
 * Cada bloque se guarda en cuanto está, así la app empieza a reproducir sin esperar al final.
 */
@Service
public class PodcastPipeline {

    private static final Logger log = LoggerFactory.getLogger(PodcastPipeline.class);
    private static final int TAIL_TURNS = 3;
    private static final int MAX_TURN_CHARS = 2000;

    private final PodcastRepository podcasts;
    private final PodcastBlockRepository blocks;
    private final SegmentRepository segments;
    private final DocumentTextRepository documents;
    private final MapCacheRepository mapCache;
    private final ScriptEngine engine;
    private final TtsEngine tts;
    private final AudioStorage storage;
    private final AccountService accounts;
    private final ObjectMapper mapper;
    private final ExecutorService executor;
    private final int ttsConcurrency;

    public PodcastPipeline(PodcastRepository podcasts, PodcastBlockRepository blocks, SegmentRepository segments,
                           DocumentTextRepository documents, MapCacheRepository mapCache, ScriptEngine engine,
                           TtsEngine tts, AudioStorage storage, AccountService accounts, ObjectMapper mapper,
                           ExecutorService pipelineExecutor, AppProperties props) {
        this.podcasts = podcasts;
        this.blocks = blocks;
        this.segments = segments;
        this.documents = documents;
        this.mapCache = mapCache;
        this.engine = engine;
        this.tts = tts;
        this.storage = storage;
        this.accounts = accounts;
        this.mapper = mapper;
        this.executor = pipelineExecutor;
        this.ttsConcurrency = Math.max(1, props.tts().concurrency());
    }

    public void submit(UUID podcastId) {
        executor.submit(() -> run(podcastId));
    }

    /** Si el servidor se reinició a mitad de una generación, esos podcasts se marcan como fallidos. */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterrupted() {
        List<Podcast> stuck = podcasts.findByStatusIn(
                List.of(PodcastStatus.QUEUED, PodcastStatus.MAPPING, PodcastStatus.GENERATING));
        for (Podcast p : stuck) {
            fail(p, "La generación se interrumpió. Vuelve a intentarlo.");
        }
    }

    void run(UUID podcastId) {
        Podcast podcast = podcasts.findById(podcastId).orElse(null);
        if (podcast != null) {
            long started = System.currentTimeMillis();
            try {
                generate(podcast);
                podcast.setStatus(PodcastStatus.READY);
                podcast.setFinishedAt(Instant.now());
                saveProgress(podcast);
                log.info("Podcast {} listo: {} bloques, {} s de audio, {} de {} intervenciones eliminadas, en {} ms",
                        podcast.getId(), podcast.getReadyBlocks(), podcast.getDurationMs() / 1000,
                        podcast.getRemovedTurns(), podcast.getTotalTurns() + podcast.getRemovedTurns(),
                        System.currentTimeMillis() - started);
            } catch (PodcastDeletedException e) {
                log.info("Podcast {} borrado durante la generación", podcastId);
                storage.deletePodcast(podcastId);
            } catch (RuntimeException e) {
                log.error("Podcast {} ha fallado", podcastId, e);
                fail(podcast, friendlyMessage(e));
            }
            accounts.addMinutes(podcast.getAccountId(), podcast.getDurationMs() / 60000.0);
        }
    }

    private void generate(Podcast podcast) {
        List<PageText> pages = loadPages(podcast.getDocumentSha256());
        podcast.setStatus(PodcastStatus.MAPPING);
        saveProgress(podcast);

        ContentMap map = mapFor(podcast, pages);
        List<PlannedBlock> plan = normalizePlan(map, pages, podcast);
        if (map.title() != null && !map.title().isBlank()) {
            podcast.setTitle(truncate(map.title().strip(), 200));
        }
        for (int i = 0; i < plan.size(); i++) {
            blocks.save(new PodcastBlock(podcast.getId(), i, truncate(plan.get(i).title(), 200)));
        }
        podcast.setTotalBlocks(plan.size());
        podcast.setStatus(PodcastStatus.GENERATING);
        saveProgress(podcast);

        Semaphore ttsPermits = new Semaphore(ttsConcurrency);
        List<Turn> tail = List.of();
        for (int i = 0; i < plan.size(); i++) {
            tail = generateBlock(podcast, map, plan, i, pages, tail, ttsPermits);
        }
        if (podcast.getReadyBlocks() == 0) {
            throw new ScriptGenerationException("No se ha podido generar ningún bloque con contenido del PDF.");
        }
    }

    private List<Turn> generateBlock(Podcast podcast, ContentMap map, List<PlannedBlock> plan, int index,
                                     List<PageText> pages, List<Turn> tail, Semaphore ttsPermits) {
        PlannedBlock planned = plan.get(index);
        List<PageText> source = pagesFor(planned, pages);
        BlockRequest request = new BlockRequest(map, planned, index, plan.size(), podcast.getMode(),
                podcast.getLanguage(), source, tail);
        List<Turn> written = clean(engine.writeBlock(request).turns());
        VerificationResult verification = written.isEmpty()
                ? new VerificationResult(List.of())
                : engine.verify(written, source, podcast.getLanguage());
        Verified verified = applyVerification(written, verification);

        PodcastBlock block = blocks.findByPodcastIdAndBlockIndex(podcast.getId(), index).orElseThrow();
        List<Turn> newTail = tail;
        if (verified.turns().isEmpty()) {
            block.setStatus(BlockStatus.SKIPPED);
            blocks.save(block);
        } else {
            List<byte[]> audios = synthesizeAll(verified.turns(), podcast.getLanguage(), ttsPermits);
            List<Segment> blockSegments = new ArrayList<>();
            long cursor = 0;
            for (int i = 0; i < audios.size(); i++) {
                Turn turn = verified.turns().get(i);
                long duration = Mp3.durationMs(audios.get(i));
                blockSegments.add(new Segment(podcast.getId(), index, i, Speaker.parse(turn.speaker()), turn.text(),
                        joinPages(turn.pages()), cursor, cursor + duration));
                cursor += duration;
            }
            byte[] audio = Mp3.concat(audios);
            ensureNotDeleted(podcast.getId());
            block.setAudioPath(storage.save(podcast.getId(), index, audio));
            block.setDurationMs(cursor);
            block.setStatus(BlockStatus.READY);
            segments.saveAll(blockSegments);
            blocks.save(block);

            podcast.setReadyBlocks(podcast.getReadyBlocks() + 1);
            podcast.setDurationMs(podcast.getDurationMs() + cursor);
            if (podcast.getFirstAudioAt() == null) {
                podcast.setFirstAudioAt(Instant.now());
                log.info("Podcast {}: primer bloque listo en {} ms", podcast.getId(),
                        podcast.getFirstAudioAt().toEpochMilli() - podcast.getCreatedAt().toEpochMilli());
            }
            List<Turn> kept = verified.turns();
            newTail = kept.subList(Math.max(0, kept.size() - TAIL_TURNS), kept.size());
        }
        podcast.setTotalTurns(podcast.getTotalTurns() + verified.turns().size());
        podcast.setRemovedTurns(podcast.getRemovedTurns() + verified.removed());
        podcast.setRewrittenTurns(podcast.getRewrittenTurns() + verified.rewritten());
        saveProgress(podcast);
        return newTail;
    }

    /** Pide la voz de todas las intervenciones en paralelo (hilos virtuales) y las devuelve en orden. */
    private List<byte[]> synthesizeAll(List<Turn> turns, String language, Semaphore permits) {
        List<Future<byte[]>> futures = new ArrayList<>();
        for (Turn turn : turns) {
            futures.add(executor.submit(() -> synthesizeOne(turn, language, permits)));
        }
        List<byte[]> audios = new ArrayList<>();
        try {
            for (Future<byte[]> future : futures) {
                audios.add(future.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TtsException("Generación de voz interrumpida.");
        } catch (ExecutionException e) {
            futures.forEach(f -> f.cancel(true));
            throw e.getCause() instanceof RuntimeException re ? re : new TtsException("La voz ha fallado.");
        }
        return audios;
    }

    private byte[] synthesizeOne(Turn turn, String language, Semaphore permits) throws InterruptedException {
        permits.acquire();
        try {
            return tts.synthesize(turn.text(), Speaker.parse(turn.speaker()), language);
        } finally {
            permits.release();
        }
    }

    private ContentMap mapFor(Podcast podcast, List<PageText> pages) {
        String key = MapCache.keyOf(podcast.getDocumentSha256(), podcast.getMode().name(), podcast.getLanguage());
        ContentMap map = mapCache.findById(key).map(c -> fromJson(c.getMapJson(), ContentMap.class)).orElse(null);
        if (map == null) {
            map = engine.buildMap(podcast.getTitle(), pages, podcast.getMode(), podcast.getLanguage());
            mapCache.save(new MapCache(key, toJson(map)));
        }
        return map;
    }

    /** Asegura un plan usable: páginas que existen, entre el mínimo y el máximo de bloques del modo. */
    List<PlannedBlock> normalizePlan(ContentMap map, List<PageText> pages, Podcast podcast) {
        Set<Integer> valid = pages.stream().filter(p -> !p.isEmpty()).map(PageText::page).collect(Collectors.toSet());
        List<PlannedBlock> plan = new ArrayList<>();
        List<PlannedBlock> proposed = map.blocks() == null ? List.of() : map.blocks();
        for (PlannedBlock b : proposed) {
            List<Integer> blockPages = b.pages() == null ? List.of()
                    : b.pages().stream().filter(valid::contains).distinct().sorted().toList();
            String title = b.title() == null || b.title().isBlank() ? "Parte " + (plan.size() + 1) : b.title();
            plan.add(new PlannedBlock(title, b.goal() == null ? "" : b.goal(), blockPages));
        }
        int max = podcast.getMode().maxBlocks();
        if (plan.size() > max) {
            List<PlannedBlock> trimmed = new ArrayList<>(plan.subList(0, max - 1));
            trimmed.add(plan.get(plan.size() - 1));
            plan = trimmed;
        }
        if (plan.isEmpty()) {
            List<Integer> all = valid.stream().sorted().toList();
            plan.add(new PlannedBlock("Introducción", "Presentar las ideas principales del documento", all));
        }
        return plan;
    }

    private static List<PageText> pagesFor(PlannedBlock block, List<PageText> pages) {
        List<PageText> selected = block.pages().isEmpty()
                ? pages.stream().filter(p -> !p.isEmpty()).toList()
                : pages.stream().filter(p -> block.pages().contains(p.page())).toList();
        return selected;
    }

    private static List<Turn> clean(List<Turn> turns) {
        List<Turn> raw = turns == null ? List.of() : turns;
        return raw.stream()
                .filter(t -> t != null && t.text() != null && !t.text().isBlank())
                .map(t -> new Turn(Speaker.parse(t.speaker()).name(), truncate(t.text().strip(), MAX_TURN_CHARS),
                        t.pages() == null ? List.of() : t.pages()))
                .toList();
    }

    static Verified applyVerification(List<Turn> turns, VerificationResult result) {
        Map<Integer, Verdict> byIndex = new HashMap<>();
        if (result != null && result.verdicts() != null) {
            result.verdicts().forEach(v -> byIndex.put(v.index(), v));
        }
        List<Turn> kept = new ArrayList<>();
        int removed = 0;
        int rewritten = 0;
        for (int i = 0; i < turns.size(); i++) {
            Turn turn = turns.get(i);
            Verdict verdict = byIndex.get(i);
            String status = verdict == null || verdict.status() == null ? "respaldada" : verdict.status().strip();
            boolean hasCorrection = verdict != null && verdict.correctedText() != null
                    && !verdict.correctedText().isBlank();
            if ("sin_respaldo".equals(status)) {
                removed++;
            } else if ("parcial".equals(status) && hasCorrection) {
                kept.add(new Turn(turn.speaker(), truncate(verdict.correctedText().strip(), MAX_TURN_CHARS),
                        turn.pages()));
                rewritten++;
            } else if ("parcial".equals(status)) {
                removed++;
            } else {
                kept.add(turn);
            }
        }
        return new Verified(kept, removed, rewritten);
    }

    record Verified(List<Turn> turns, int removed, int rewritten) {
    }

    private void fail(Podcast podcast, String message) {
        if (podcasts.existsById(podcast.getId())) {
            podcast.setStatus(PodcastStatus.FAILED);
            podcast.setErrorMessage(truncate(message, 300));
            podcast.setFinishedAt(Instant.now());
            if (podcast.getReadyBlocks() == 0 && podcast.isUsedFreeQuota()) {
                accounts.refundFree(podcast.getAccountId());
                podcast.setUsedFreeQuota(false);
            }
            podcasts.save(podcast);
        }
    }

    private void saveProgress(Podcast podcast) {
        ensureNotDeleted(podcast.getId());
        podcasts.save(podcast);
    }

    private void ensureNotDeleted(UUID podcastId) {
        if (!podcasts.existsById(podcastId)) {
            throw new PodcastDeletedException();
        }
    }

    private List<PageText> loadPages(String sha256) {
        DocumentText doc = documents.findById(sha256)
                .orElseThrow(() -> new ScriptGenerationException("El texto del PDF ya no está disponible."));
        return fromJson(doc.getPagesJson(), new TypeReference<List<PageText>>() { });
    }

    static String friendlyMessage(RuntimeException e) {
        String message;
        if (e instanceof ScriptGenerationException || e instanceof TtsException) {
            message = e.getMessage();
        } else if (e instanceof AnthropicException) {
            message = "El servicio de guion no está disponible ahora mismo. Inténtalo en unos minutos.";
        } else {
            message = "Algo ha fallado al generar el podcast. Inténtalo de nuevo.";
        }
        return message;
    }

    private static String joinPages(List<Integer> pages) {
        return truncate(pages.stream().distinct().sorted().map(String::valueOf).collect(Collectors.joining(",")), 200);
    }

    private static String truncate(String value, int max) {
        String safe = value == null ? "" : value;
        return safe.length() > max ? safe.substring(0, max) : safe;
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    static class PodcastDeletedException extends RuntimeException {
    }
}
