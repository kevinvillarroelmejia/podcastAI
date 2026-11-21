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
        boolean devPremium,
        Ai ai,
        Tts tts,
        ElevenLabs elevenlabs,
        Limits limits) {

    public record Ai(String provider, String anthropicApiKey, String mapModel, String scriptModel, String verifyModel) {
    }

    public record Tts(String provider, String openaiApiKey, String model, String hostVoice, String expertVoice,
                      int concurrency) {
    }

    /** Voz premium (ElevenLabs v3, modo diálogo). Solo se usa para cuentas Premium. */
    public record ElevenLabs(String apiKey, String model, String hostVoice, String expertVoice) {
    }

    public record Limits(int maxPdfPages, int maxSourceChars, int freePodcasts, int monthlyMinutesPremium,
                         int uploadsPerHour) {
    }
}
