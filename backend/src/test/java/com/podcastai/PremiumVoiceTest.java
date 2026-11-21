package com.podcastai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.podcastai.tts.BlockAudio;
import com.podcastai.tts.ElevenLabsDialogueEngine;
import com.podcastai.tts.Mp3;
import com.podcastai.tts.VoiceAccountException;

/**
 * Con clave de ElevenLabs, todos los podcasts usan su voz; sin créditos fallan con un mensaje claro.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:premium;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
    "podcastai.storage-dir=target/test-audio-premium",
    "podcastai.ai.provider=mock",
    "podcastai.tts.provider=mock",
    "podcastai.revenuecat-webhook-secret=secreto"
})
class PremiumVoiceTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @MockitoBean
    private ElevenLabsDialogueEngine elevenLabs;

    @Test
    void everyPodcastUsesElevenLabsWhenConfigured() throws Exception {
        when(elevenLabs.isConfigured()).thenReturn(true);
        when(elevenLabs.synthesize(anyList(), anyList(), anyString())).thenAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            List<Long> starts = new ArrayList<>();
            List<Long> ends = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                starts.add(i * 1000L);
                ends.add((i + 1) * 1000L);
            }
            return new BlockAudio(Mp3.silence(texts.size() * 1000L), starts, ends);
        });
        MockMultipartFile pdf = new MockMultipartFile("file", "tema.pdf", "application/pdf", TestPdfs.withPages(
                "La memoria cache guarda copias de datos usados con frecuencia.\nAsi el procesador accede mas rapido.",
                "La memoria RAM es volatil y pierde su contenido sin corriente.\nEl disco duro conserva los datos."));

        String free = UUID.randomUUID().toString();
        mvc.perform(get("/api/me").header("X-Device-Id", free)).andExpect(jsonPath("$.voiceEngine").value("elevenlabs"));

        String premium = UUID.randomUUID().toString();
        mvc.perform(post("/api/webhooks/revenuecat").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer secreto")
                .content("{\"event\":{\"type\":\"INITIAL_PURCHASE\",\"app_user_id\":\"" + premium + "\"}}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/me").header("X-Device-Id", premium)).andExpect(jsonPath("$.voiceEngine").value("elevenlabs"));

        String created = mvc.perform(multipart("/api/podcasts").file(pdf).header("X-Device-Id", premium))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(created).path("id").asText();
        JsonNode detail = null;
        boolean done = false;
        for (int i = 0; i < 100 && !done; i++) {
            detail = mapper.readTree(mvc.perform(get("/api/podcasts/" + id).header("X-Device-Id", premium))
                    .andReturn().getResponse().getContentAsString());
            done = "READY".equals(detail.path("podcast").path("status").asText());
            Thread.sleep(done ? 0 : 100);
        }
        assertThat(detail.path("podcast").path("status").asText()).isEqualTo("READY");
        JsonNode segment = detail.path("blocks").get(0).path("segments").get(1);
        assertThat(segment.path("startMs").asLong()).isEqualTo(1000L);
        verify(elevenLabs, atLeastOnce()).synthesize(anyList(), anyList(), anyString());
    }

    @Test
    void podcastFailsClearlyWhenElevenLabsNeedsPayment() throws Exception {
        when(elevenLabs.isConfigured()).thenReturn(true);
        when(elevenLabs.synthesize(anyList(), anyList(), anyString()))
                .thenThrow(new VoiceAccountException("ElevenLabs pide un plan de pago o más créditos."));
        MockMultipartFile pdf = new MockMultipartFile("file", "tema.pdf", "application/pdf", TestPdfs.withPages(
                "Los hilos virtuales son ligeros y los gestiona la JVM.\nPermiten miles de tareas a la vez.",
                "Un semaforo limita cuantas tareas acceden a un recurso.\nSe libera al terminar."));

        String premium = UUID.randomUUID().toString();
        mvc.perform(post("/api/webhooks/revenuecat").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer secreto")
                .content("{\"event\":{\"type\":\"INITIAL_PURCHASE\",\"app_user_id\":\"" + premium + "\"}}"))
                .andExpect(status().isOk());
        String created = mvc.perform(multipart("/api/podcasts").file(pdf).header("X-Device-Id", premium))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(created).path("id").asText();
        JsonNode detail = null;
        boolean finished = false;
        for (int i = 0; i < 100 && !finished; i++) {
            detail = mapper.readTree(mvc.perform(get("/api/podcasts/" + id).header("X-Device-Id", premium))
                    .andReturn().getResponse().getContentAsString());
            String state = detail.path("podcast").path("status").asText();
            finished = "READY".equals(state) || "FAILED".equals(state);
            Thread.sleep(finished ? 0 : 100);
        }
        assertThat(detail.path("podcast").path("status").asText()).isEqualTo("FAILED");
        assertThat(detail.toString()).contains("ElevenLabs pide un plan de pago");
    }
}
