package com.podcastai.tts;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.podcastai.ai.Speaker;
import com.podcastai.config.AppProperties;

/**
 * Voz con OpenAI (gpt-4o-mini-tts). La clave solo vive en el backend.
 */
public class OpenAiTtsEngine implements TtsEngine {

    private static final URI ENDPOINT = URI.create("https://api.openai.com/v1/audio/speech");
    private static final int MAX_ATTEMPTS = 3;

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
                if (status == 200) {
                    audio = response.body();
                } else if (status == 429 || status >= 500) {
                    lastError = "HTTP " + status;
                    pause(attempt);
                } else {
                    throw new TtsException("La voz ha fallado (HTTP " + status + ").");
                }
            } catch (IOException e) {
                lastError = e.getMessage();
                pause(attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TtsException("Generación de voz interrumpida.");
            }
        }
        if (audio == null) {
            throw new TtsException("La voz no responde ahora mismo (" + lastError + ").");
        }
        return audio;
    }

    private static void pause(int attempt) {
        try {
            Thread.sleep(500L * attempt * attempt);
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
