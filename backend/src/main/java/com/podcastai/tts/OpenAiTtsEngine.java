package com.podcastai.tts;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.podcastai.ai.Speaker;
import com.podcastai.config.AppProperties;

/**
 * Voz con OpenAI (gpt-4o-mini-tts). La clave solo vive en el backend.
 */
public class OpenAiTtsEngine implements TtsEngine {

    private static final Logger log = LoggerFactory.getLogger(OpenAiTtsEngine.class);

    private static final URI ENDPOINT = URI.create("https://api.openai.com/v1/audio/speech");
    private static final int MAX_ATTEMPTS = 5;

    private static final String HOST_STYLE = "Presentadora de podcast curiosa y cercana. Ritmo vivo, tono natural "
            + "y con humor ligero, como si charlara con un amigo.";
    private static final String EXPERT_STYLE = "Experto cercano y entusiasta. Algo más pausado, explica con claridad "
            + "y calidez, nunca suena a libro de texto.";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final AppProperties.Tts config;
    private final ObjectMapper mapper;

    public OpenAiTtsEngine(AppProperties.Tts config, ObjectMapper mapper) {
        this.config = config;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public byte[] synthesize(String text, Speaker speaker, String language) {
        boolean host = speaker == Speaker.LIA;
        Map<String, Object> body = Map.of(
                "model", config.model(),
                "voice", host ? config.hostVoice() : config.expertVoice(),
                "input", text,
                "instructions", host ? HOST_STYLE : EXPERT_STYLE,
                "response_format", "mp3");
        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + config.openaiApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
                .build();
        byte[] audio = null;
        String lastError = "";
        for (int attempt = 1; audio == null && attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                int status = response.statusCode();
                String error = status == 200 ? "" : new String(response.body(), StandardCharsets.UTF_8);
                if (status == 200) {
                    audio = response.body();
                } else if (status == 401) {
                    throw new TtsException("La clave de OpenAI no es válida. Revisa OPENAI_API_KEY en el .env.");
                } else if (status == 429 && error.contains("insufficient_quota")) {
                    throw new TtsException("Tu cuenta de OpenAI no tiene saldo. Añade crédito en "
                            + "platform.openai.com/settings/organization/billing.");
                } else if (status == 429 || status >= 500) {
                    lastError = "HTTP " + status;
                    log.warn("OpenAI TTS respondió {} (intento {}/{}): {}", status, attempt, MAX_ATTEMPTS,
                            abbreviate(error));
                    pause(retryAfterMillis(response, attempt));
                } else {
                    log.warn("OpenAI TTS respondió {}: {}", status, abbreviate(error));
                    throw new TtsException("La voz ha fallado (HTTP " + status + ").");
                }
            } catch (IOException e) {
                lastError = e.getMessage();
                pause(1000L * attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TtsException("Generación de voz interrumpida.");
            }
        }
        if (audio == null) {
            throw new TtsException("La voz de OpenAI está saturada o limita tus peticiones (" + lastError
                    + "). Espera un minuto y vuelve a intentarlo.");
        }
        return audio;
    }

    /** Respeta la cabecera Retry-After si viene; si no, espera cada vez más (2 s, 8 s, 18 s...). */
    private static long retryAfterMillis(HttpResponse<?> response, int attempt) {
        long fallback = 2000L * attempt * attempt;
        long wait = response.headers().firstValue("retry-after")
                .map(value -> parseSeconds(value, fallback))
                .orElse(fallback);
        return Math.min(wait, 30_000L);
    }

    private static long parseSeconds(String value, long fallback) {
        long millis;
        try {
            millis = Math.round(Double.parseDouble(value.strip()) * 1000);
        } catch (NumberFormatException e) {
            millis = fallback;
        }
        return millis;
    }

    private static String abbreviate(String text) {
        String oneLine = text.replaceAll("\\s+", " ");
        return oneLine.length() > 300 ? oneLine.substring(0, 300) : oneLine;
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
