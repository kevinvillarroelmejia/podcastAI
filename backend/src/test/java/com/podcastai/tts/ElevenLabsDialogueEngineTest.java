package com.podcastai.tts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.podcastai.config.AppProperties;

class ElevenLabsDialogueEngineTest {

    private final ElevenLabsDialogueEngine engine = new ElevenLabsDialogueEngine(
            new AppProperties.ElevenLabs("clave", "eleven_v3", "lia", "marco"), new ObjectMapper());

    @Test
    void readsTurnTimesFromVoiceSegments() {
        String audio = Base64.getEncoder().encodeToString(Mp3.silence(3000));
        String json = """
                {"audio_base64":"%s","voice_segments":[
                  {"voice_id":"lia","start_time_seconds":0.0,"end_time_seconds":1.2,"dialogue_input_index":0},
                  {"voice_id":"marco","start_time_seconds":1.25,"end_time_seconds":2.0,"dialogue_input_index":1},
                  {"voice_id":"marco","start_time_seconds":2.0,"end_time_seconds":2.9,"dialogue_input_index":1}
                ]}""".formatted(audio);
        ElevenLabsDialogueEngine.ChunkResult result = engine.parse(json, 3);
        assertThat(result.startMs()).containsExactly(0L, 1250L, 2900L);
        assertThat(result.endMs()).containsExactly(1200L, 2900L, 2900L);
        assertThat(Mp3.durationMs(result.audio())).isBetween(2950L, 3050L);
    }

    @Test
    void splitsLongBlocksUnderTheCharacterLimit() {
        String turn = "x".repeat(700);
        List<String> texts = List.of(turn, turn, turn, turn);
        assertThat(ElevenLabsDialogueEngine.chunkEnd(texts, 0)).isEqualTo(2);
        assertThat(ElevenLabsDialogueEngine.chunkEnd(texts, 2)).isEqualTo(4);
        assertThat(ElevenLabsDialogueEngine.chunkEnd(List.of("y".repeat(3000)), 0)).isEqualTo(1);
    }

    @Test
    void stripsAudioTagsForTranscript() {
        assertThat(AudioTags.strip("[laughs] Vale, ¿y eso? [jumping in] Espera-"))
                .isEqualTo("Vale, ¿y eso? Espera-");
    }
}
