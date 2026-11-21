package com.podcastai.web;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.podcastai.config.AppProperties;
import com.podcastai.podcast.PodcastService;
import com.podcastai.web.Dtos.PodcastDetail;
import com.podcastai.web.Dtos.PodcastSummary;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class PodcastController {

    static final String DEVICE_HEADER = "X-Device-Id";

    private final PodcastService service;
    private final RateLimiter rateLimiter;
    private final AppProperties props;

    public PodcastController(PodcastService service, RateLimiter rateLimiter, AppProperties props) {
        this.service = service;
        this.rateLimiter = rateLimiter;
        this.props = props;
    }

    @PostMapping(path = "/api/podcasts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PodcastSummary create(@RequestHeader(DEVICE_HEADER) String deviceId,
                                 @RequestParam("file") MultipartFile file,
                                 @RequestParam(name = "mode", defaultValue = "RESUMEN") String mode,
                                 @RequestParam(name = "language", defaultValue = "es") String language,
                                 HttpServletRequest request) {
        rateLimiter.checkUpload(deviceId, request.getRemoteAddr());
        return service.create(deviceId, file, mode, language);
    }

    @GetMapping("/api/podcasts")
    public List<PodcastSummary> list(@RequestHeader(DEVICE_HEADER) String deviceId) {
        return service.list(deviceId);
    }

    @GetMapping("/api/podcasts/{id}")
    public PodcastDetail detail(@RequestHeader(DEVICE_HEADER) String deviceId, @PathVariable UUID id) {
        return service.detail(deviceId, id, baseUrl());
    }

    @DeleteMapping("/api/podcasts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(DEVICE_HEADER) String deviceId, @PathVariable UUID id) {
        service.delete(deviceId, id);
    }

    /** Audio de un bloque. Va firmado en la URL porque el reproductor no envía cabeceras. Admite peticiones Range. */
    @GetMapping("/api/audio/{id}/{index}")
    public ResponseEntity<Resource> audio(@PathVariable UUID id, @PathVariable int index,
                                          @RequestParam long exp, @RequestParam String sig) {
        Path file = service.audioFile(id, index, exp, sig);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .cacheControl(CacheControl.noStore())
                .body(new FileSystemResource(file));
    }

    private String baseUrl() {
        String configured = props.publicBaseUrl();
        return configured == null || configured.isBlank()
                ? ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
                : configured.replaceAll("/+$", "");
    }
}
