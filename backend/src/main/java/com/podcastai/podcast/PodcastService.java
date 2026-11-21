package com.podcastai.podcast;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.podcastai.account.Account;
import com.podcastai.account.AccountService;
import com.podcastai.ai.Languages;
import com.podcastai.ai.PodcastMode;
import com.podcastai.pdf.ExtractedPdf;
import com.podcastai.pdf.PdfTextExtractor;
import com.podcastai.web.ApiException;
import com.podcastai.web.Dtos.BlockView;
import com.podcastai.web.Dtos.PodcastDetail;
import com.podcastai.web.Dtos.PodcastSummary;
import com.podcastai.web.Dtos.SegmentView;

/**
 * Casos de uso de los podcasts: crear, consultar y borrar. Cada usuario solo ve los suyos.
 */
@Service
public class PodcastService {

    /** Índice reservado para firmar la descarga del podcast completo. */
    private static final int FULL_AUDIO = -1;

    private final PdfTextExtractor extractor;
    private final AccountService accounts;
    private final PodcastRepository podcasts;
    private final PodcastBlockRepository blocks;
    private final SegmentRepository segments;
    private final DocumentTextRepository documents;
    private final PodcastPipeline pipeline;
    private final AudioStorage storage;
    private final AudioSigner signer;
    private final ObjectMapper mapper;
    private final com.podcastai.tts.TtsEngine tts;
    private final com.podcastai.tts.ElevenLabsDialogueEngine elevenLabs;

    public PodcastService(PdfTextExtractor extractor, AccountService accounts, PodcastRepository podcasts,
                          PodcastBlockRepository blocks, SegmentRepository segments, DocumentTextRepository documents,
                          PodcastPipeline pipeline, AudioStorage storage, AudioSigner signer, ObjectMapper mapper,
                          com.podcastai.tts.TtsEngine tts, com.podcastai.tts.ElevenLabsDialogueEngine elevenLabs) {
        this.tts = tts;
        this.elevenLabs = elevenLabs;
        this.extractor = extractor;
        this.accounts = accounts;
        this.podcasts = podcasts;
        this.blocks = blocks;
        this.segments = segments;
        this.documents = documents;
        this.pipeline = pipeline;
        this.storage = storage;
        this.signer = signer;
        this.mapper = mapper;
    }

    public PodcastSummary create(String accountId, MultipartFile file, String modeRaw, String language) {
        PodcastMode mode = parseMode(modeRaw);
        if (!Languages.isSupported(language)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Idioma no admitido.");
        }
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Elige un PDF.");
        }
        Account account = accounts.getOrCreate(accountId);
        ExtractedPdf pdf = extractor.extract(readBytes(file), file.getOriginalFilename());
        if (!documents.existsById(pdf.sha256())) {
            documents.save(new DocumentText(pdf.sha256(), pdf.pages().size(), toJson(pdf.pages())));
        }
        boolean usedFree = accounts.reserve(account, mode);
        Podcast podcast = new Podcast(account.getId(), pdf.sha256(), pdf.title(), mode, language, pdf.pages().size());
        podcast.setUsedFreeQuota(usedFree);
        podcast.setVoice(voiceFor(account));
        podcasts.save(podcast);
        pipeline.submit(podcast.getId());
        return PodcastSummary.of(podcast);
    }

    public List<PodcastSummary> list(String accountId) {
        return podcasts.findByAccountIdOrderByCreatedAtDesc(AccountService.requireValidId(accountId)).stream()
                .map(PodcastSummary::of)
                .toList();
    }

    public PodcastDetail detail(String accountId, UUID podcastId, String baseUrl) {
        Podcast podcast = owned(accountId, podcastId);
        Map<Integer, List<SegmentView>> segmentsByBlock = segments
                .findByPodcastIdOrderByBlockIndexAscPositionAsc(podcastId).stream()
                .collect(Collectors.groupingBy(Segment::getBlockIndex,
                        Collectors.mapping(SegmentView::of, Collectors.toList())));
        List<BlockView> blockViews = blocks.findByPodcastIdOrderByBlockIndex(podcastId).stream()
                .map(b -> BlockView.of(b, audioUrl(baseUrl, b), segmentsByBlock.getOrDefault(b.getBlockIndex(), List.of())))
                .toList();
        boolean anyReady = blockViews.stream().anyMatch(b -> b.audioUrl() != null);
        String downloadUrl = anyReady
                ? baseUrl + "/api/audio/" + podcastId + "/completo?" + signer.signedQuery(podcastId, FULL_AUDIO)
                : null;
        return PodcastDetail.of(podcast, blockViews, downloadUrl);
    }

    public void delete(String accountId, UUID podcastId) {
        Podcast podcast = owned(accountId, podcastId);
        deleteInternal(podcast.getId());
    }

    /** Borra todos los datos de la cuenta (derecho de supresión del RGPD). */
    public void deleteAccount(String accountId) {
        String id = AccountService.requireValidId(accountId);
        podcasts.findByAccountIdOrderByCreatedAtDesc(id).forEach(p -> deleteInternal(p.getId()));
        accounts.delete(id);
    }

    /** El podcast entero en un solo MP3 (los bloques listos, en orden), para descargarlo. */
    public DownloadedAudio fullAudio(UUID podcastId, long expires, String sig) {
        if (!signer.isValid(podcastId, FULL_AUDIO, expires, sig)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Enlace de descarga caducado.");
        }
        Podcast podcast = podcasts.findById(podcastId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Podcast no encontrado."));
        List<byte[]> parts = new java.util.ArrayList<>();
        for (PodcastBlock block : blocks.findByPodcastIdOrderByBlockIndex(podcastId)) {
            if (block.getStatus() == BlockStatus.READY && block.getAudioPath() != null) {
                parts.add(readFile(storage.resolve(block.getAudioPath())));
            }
        }
        if (parts.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Todavía no hay audio para descargar.");
        }
        String safeName = podcast.getTitle().replaceAll("[\\\\/:*?\"<>|]+", " ").strip();
        return new DownloadedAudio((safeName.isEmpty() ? "podcast" : safeName) + ".mp3", com.podcastai.tts.Mp3.concat(parts));
    }

    public record DownloadedAudio(String fileName, byte[] data) {
    }

    private static byte[] readFile(java.nio.file.Path file) {
        try {
            return java.nio.file.Files.readAllBytes(file);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Audio no disponible.");
        }
    }

    public java.nio.file.Path audioFile(UUID podcastId, int blockIndex, long expires, String sig) {
        if (!signer.isValid(podcastId, blockIndex, expires, sig)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Enlace de audio caducado.");
        }
        PodcastBlock block = blocks.findByPodcastIdAndBlockIndex(podcastId, blockIndex)
                .filter(b -> b.getStatus() == BlockStatus.READY && b.getAudioPath() != null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Audio no disponible."));
        return storage.resolve(block.getAudioPath());
    }

    private void deleteInternal(UUID podcastId) {
        pipeline.deleteSafely(podcastId, () -> {
            segments.deleteByPodcastId(podcastId);
            blocks.deleteByPodcastId(podcastId);
            podcasts.deleteById(podcastId);
            storage.deletePodcast(podcastId);
        });
    }

    /** Con clave de ElevenLabs, todos los podcasts (Resumen y A fondo) usan su voz; si no, la voz estándar. */
    public String voiceFor(Account account) {
        return elevenLabs.isConfigured() ? "elevenlabs" : tts.name();
    }

    private Podcast owned(String accountId, UUID podcastId) {
        String id = AccountService.requireValidId(accountId);
        return podcasts.findById(podcastId)
                .filter(p -> p.getAccountId().equals(id))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Podcast no encontrado."));
    }

    private String audioUrl(String baseUrl, PodcastBlock block) {
        String url = null;
        if (block.getStatus() == BlockStatus.READY) {
            url = baseUrl + "/api/audio/" + block.getPodcastId() + "/" + block.getBlockIndex() + "?"
                    + signer.signedQuery(block.getPodcastId(), block.getBlockIndex());
        }
        return url;
    }

    private static PodcastMode parseMode(String raw) {
        PodcastMode mode;
        try {
            mode = PodcastMode.valueOf(raw == null ? "" : raw.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Modo no válido. Usa RESUMEN o A_FONDO.");
        }
        return mode;
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No se ha podido recibir el archivo.");
        }
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
