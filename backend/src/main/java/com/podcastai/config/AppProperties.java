package com.podcastai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de la aplicación (prefijo {@code podcastai} en application.yml).
 */
@ConfigurationProperties(prefix = "podcastai")
public record AppProperties(
        String publicBaseUrl,
        String storageDir,
        String signingSecret,
        String corsOrigins,
        String revenuecatWebhookSecret,
        int retentionDays,
        Ai ai,
        Tts tts,
        Limits limits) {

    public record Ai(String provider, String anthropicApiKey, String mapModel, String scriptModel, String verifyModel) {
    }

    public record Tts(String provider, String openaiApiKey, String model, String hostVoice, String expertVoice,
                      int concurrency) {
    }

    public record Limits(int maxPdfPages, int maxSourceChars, int freePodcasts, int monthlyMinutesPremium,
                         int uploadsPerHour) {
    }
}
