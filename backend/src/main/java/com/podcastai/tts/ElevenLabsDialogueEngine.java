package com.podcastai.tts;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.podcastai.ai.Speaker;
import com.podcastai.config.AppProperties;

/**
 * Voz premium con ElevenLabs v3 (Text to Dialogue): genera la conversación del bloque de una vez, con
 * entonación de diálogo real, y devuelve dónde empieza cada intervención para sincronizar la transcripción.
 */
public class ElevenLabsDialogueEngine {

    private static final Logger log = LoggerFactory.getLogger(ElevenLabsDialogueEngine.class);
    private static final URI ENDPOINT = URI.create(
            "https://api.elevenlabs.io/v1/text-to-dialogue/with-timestamps?output_format=mp3_44100_128");
    /** ElevenLabs recomienda no pasar de 2.000 caracteres por petición. */
    private static final int MAX_CHARS_PER_REQUEST = 1800;
    private static final int MAX_ATTEMPTS = 4;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final AppProperties.ElevenLabs config;
    private final ObjectMapper mapper;

    public ElevenLabsDialogueEngine(AppProperties.ElevenLabs config, ObjectMapper mapper) {
        this.config = config;
        this.mapper = mapper;
    }

    public boolean isConfigured() {
        return config != null && config.apiKey() != null && !config.apiKey().isBlank();
    }

    /**
     * @param texts intervenciones (pueden llevar marcas como "[laughs]")
     */
    public BlockAudio synthesize(List<String> texts, List<Speaker> speakers, String language) {
        List<byte[]> parts = new ArrayList<>();
        List<Long> starts = new ArrayList<>();
        List<Long> ends = new ArrayList<>();
        long offset = 0;
        int from = 0;
        while (from < texts.size()) {
            int to = chunkEnd(texts, from);
            ChunkResult chunk = requestChunk(texts.subList(from, to), speakers.subList(from, to), language);
            for (int i = 0; i < chunk.startMs().size(); i++) {
                starts.add(offset + chunk.startMs().get(i));
                ends.add(offset + chunk.endMs().get(i));
            }
            parts.add(chunk.audio());
            offset += Mp3.durationMs(chunk.audio());
            from = to;
        }
        if (!ends.isEmpty()) {
            ends.set(ends.size() - 1, offset);
        }
        return new BlockAudio(Mp3.concat(parts), starts, ends);
    }

    /** Agrupa intervenciones seguidas sin pasar del máximo de caracteres (al menos una por grupo). */
    static int chunkEnd(List<String> texts, int from) {
        int end = from + 1;
        int chars = texts.get(from).length();
        boolean fits = true;
        while (fits && end < texts.size()) {
            int next = chars + texts.get(end).length();
            fits = next <= MAX_CHARS_PER_REQUEST;
            if (fits) {
                chars = next;
                end++;
            }
        }
        return end;
    }

    private ChunkResult requestChunk(List<String> texts, List<Speaker> speakers, String language) {
        List<Map<String, String>> inputs = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            String voice = speakers.get(i) == Speaker.LIA ? config.hostVoice() : config.expertVoice();
            inputs.add(Map.of("text", texts.get(i), "voice_id", voice));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", inputs);
        body.put("model_id", config.model());
        body.put("language_code", language);
        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(Duration.ofSeconds(120))
                .header("xi-api-key", config.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
                .build();
        ChunkResult result = null;
        String lastError = "";
        for (int attempt = 1; result == null && attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();
                if (status == 200) {
                    result = parse(response.body(), texts.size());
                } else if (status == 401) {
                    throw new TtsException("La clave de ElevenLabs no es válida. Revisa ELEVENLABS_API_KEY en el .env.");
                } else if (status == 429 || status >= 500) {
                    lastError = "HTTP " + status;
                    log.warn("ElevenLabs respondió {} (intento {}/{}): {}", status, attempt, MAX_ATTEMPTS,
                            abbreviate(response.body()));
                    pause(2000L * attempt * attempt);
                } else {
                    log.warn("ElevenLabs respondió {}: {}", status, abbreviate(response.body()));
                    throw new TtsException(friendly(status, response.body()));
                }
            } catch (IOException e) {
                lastError = e.getMessage();
                pause(1000L * attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TtsException("Generación de voz interrumpida.");
            }
        }
        if (result == null) {
            throw new TtsException("La voz de ElevenLabs no responde ahora mismo (" + lastError + ").");
        }
        return result;
    }

    /** Lee el audio y, de voice_segments, el inicio y fin de cada intervención (dialogue_input_index). */
    ChunkResult parse(String json, int inputCount) {
        JsonNode root = readTree(json);
        byte[] audio = Base64.getDecoder().decode(root.path("audio_base64").asText(""));
        long[] start = new long[inputCount];
        long[] end = new long[inputCount];
        java.util.Arrays.fill(start, Long.MAX_VALUE);
        for (JsonNode segment : root.path("voice_segments")) {
            int index = segment.path("dialogue_input_index").asInt(-1);
            if (index >= 0 && index < inputCount) {
                start[index] = Math.min(start[index], Math.round(segment.path("start_time_seconds").asDouble() * 1000));
                end[index] = Math.max(end[index], Math.round(segment.path("end_time_seconds").asDouble() * 1000));
            }
        }
        List<Long> starts = new ArrayList<>();
        List<Long> ends = new ArrayList<>();
        long previousEnd = 0;
        for (int i = 0; i < inputCount; i++) {
            long s = start[i] == Long.MAX_VALUE ? previousEnd : start[i];
            long e = Math.max(s, end[i]);
            starts.add(s);
            ends.add(e);
            previousEnd = e;
        }
        return new ChunkResult(audio, starts, ends);
    }

    record ChunkResult(byte[] audio, List<Long> startMs, List<Long> endMs) {
    }

    private static String friendly(int status, String body) {
        String message = "La voz de ElevenLabs ha fallado (HTTP " + status + ").";
        if (body != null && (body.contains("quota") || body.contains("credits"))) {
            message = "Tu cuenta de ElevenLabs no tiene créditos suficientes.";
        } else if (body != null && body.contains("voice_not_found")) {
            message = "No existe una de las voces de ElevenLabs configuradas (ELEVENLABS_HOST_VOICE / ELEVENLABS_EXPERT_VOICE).";
        }
        return message;
    }

    private static String abbreviate(String text) {
        String oneLine = text == null ? "" : text.replaceAll("\\s+", " ");
        return oneLine.length() > 300 ? oneLine.substring(0, 300) : oneLine;
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private JsonNode readTree(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new TtsException("Respuesta de ElevenLabs no válida.");
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
