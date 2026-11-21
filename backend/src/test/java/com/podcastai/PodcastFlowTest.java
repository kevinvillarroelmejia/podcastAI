package com.podcastai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Flujo completo en modo de prueba (sin claves): subir PDF → bloques listos → audio → límites → aislamiento.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:flow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
    "podcastai.storage-dir=target/test-audio",
    "podcastai.ai.provider=mock",
    "podcastai.tts.provider=mock",
    "podcastai.revenuecat-webhook-secret=test-webhook-secret"
})
class PodcastFlowTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    private static String newDevice() {
        return UUID.randomUUID().toString();
    }

    private static MockMultipartFile samplePdf() throws Exception {
        byte[] pdf = TestPdfs.withPages(
                "Una base de datos es un conjunto organizado de informacion.\nSe gestiona con un SGBD como PostgreSQL.",
                "JDBC es la API de Java para conectarse a bases de datos.\nPermite ejecutar consultas SQL desde el codigo.",
                "JPA mapea clases Java a tablas relacionales.\nHibernate es la implementacion mas conocida de JPA.");
        return new MockMultipartFile("file", "Tema 2 acceso a datos.pdf", "application/pdf", pdf);
    }

    private JsonNode waitUntilFinished(String device, String id) throws Exception {
        JsonNode detail = null;
        boolean finished = false;
        for (int i = 0; i < 100 && !finished; i++) {
            String body = mvc.perform(get("/api/podcasts/" + id).header("X-Device-Id", device))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            detail = mapper.readTree(body);
            String st = detail.path("podcast").path("status").asText();
            finished = "READY".equals(st) || "FAILED".equals(st);
            if (!finished) {
                Thread.sleep(100);
            }
        }
        return detail;
    }

    @Test
    void freeUserGetsOneSummaryEndToEnd() throws Exception {
        String device = newDevice();
        String created = mvc.perform(multipart("/api/podcasts").file(samplePdf())
                        .param("mode", "RESUMEN").param("language", "es").header("X-Device-Id", device))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(created).path("id").asText();

        JsonNode detail = waitUntilFinished(device, id);
        assertThat(detail.path("podcast").path("status").asText()).isEqualTo("READY");
        assertThat(detail.path("blocks").size()).isGreaterThan(0);
        JsonNode first = detail.path("blocks").get(0);
        assertThat(first.path("segments").get(0).path("text").asText()).contains("podcastAI");
        assertThat(first.path("durationMs").asLong()).isPositive();

        URI audio = URI.create(first.path("audioUrl").asText());
        mvc.perform(get(audio.getPath() + "?" + audio.getQuery()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mpeg"));
        mvc.perform(get(audio.getPath() + "?exp=9999999999&sig=falsa")).andExpect(status().isForbidden());

        URI download = URI.create(detail.path("downloadUrl").asText());
        byte[] full = mvc.perform(get(download.getPath() + "?" + download.getQuery()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mpeg"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn().getResponse().getContentAsByteArray();
        long totalMs = 0;
        for (JsonNode b : detail.path("blocks")) {
            totalMs += b.path("durationMs").asLong();
        }
        assertThat(com.podcastai.tts.Mp3.durationMs(full)).isBetween(totalMs - 100, totalMs + 100);
        mvc.perform(get(download.getPath() + "?exp=9999999999&sig=falsa")).andExpect(status().isForbidden());

        // Segundo podcast: ya no quedan gratis.
        mvc.perform(multipart("/api/podcasts").file(samplePdf()).param("mode", "RESUMEN")
                        .header("X-Device-Id", device))
                .andExpect(status().isPaymentRequired());

        mvc.perform(get("/api/me").header("X-Device-Id", device))
                .andExpect(jsonPath("$.plan").value("FREE"))
                .andExpect(jsonPath("$.freePodcastsLeft").value(0));
    }

    @Test
    void otherUsersCannotSeeMyPodcast() throws Exception {
        String owner = newDevice();
        String created = mvc.perform(multipart("/api/podcasts").file(samplePdf()).header("X-Device-Id", owner))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(created).path("id").asText();
        String intruder = newDevice();
        mvc.perform(get("/api/podcasts/" + id).header("X-Device-Id", intruder)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/podcasts/" + id).header("X-Device-Id", intruder)).andExpect(status().isNotFound());
        mvc.perform(get("/api/podcasts").header("X-Device-Id", intruder))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deepModeNeedsPremiumGrantedOnlyByWebhook() throws Exception {
        String device = newDevice();
        mvc.perform(multipart("/api/podcasts").file(samplePdf()).param("mode", "A_FONDO")
                        .header("X-Device-Id", device))
                .andExpect(status().isPaymentRequired());

        String event = "{\"event\":{\"type\":\"INITIAL_PURCHASE\",\"app_user_id\":\"" + device + "\"}}";
        mvc.perform(post("/api/webhooks/revenuecat").contentType(MediaType.APPLICATION_JSON).content(event)
                        .header("Authorization", "Bearer otra"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/webhooks/revenuecat").contentType(MediaType.APPLICATION_JSON).content(event)
                        .header("Authorization", "Bearer test-webhook-secret"))
                .andExpect(status().isOk());

        String created = mvc.perform(multipart("/api/podcasts").file(samplePdf()).param("mode", "A_FONDO")
                        .header("X-Device-Id", device))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        JsonNode detail = waitUntilFinished(device, mapper.readTree(created).path("id").asText());
        assertThat(detail.path("podcast").path("status").asText()).isEqualTo("READY");
        mvc.perform(get("/api/me").header("X-Device-Id", device))
                .andExpect(jsonPath("$.plan").value("PREMIUM"))
                .andExpect(jsonPath("$.monthlyMinutes").value(120));
    }

    @Test
    void rejectsFilesThatAreNotPdf() throws Exception {
        MockMultipartFile fake = new MockMultipartFile("file", "virus.pdf", "application/pdf", "MZ hola".getBytes());
        mvc.perform(multipart("/api/podcasts").file(fake).header("X-Device-Id", newDevice()))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(get("/api/podcasts").header("X-Device-Id", "corto")).andExpect(status().isUnauthorized());
    }

    @Test
    void deleteAccountRemovesEverything() throws Exception {
        String device = newDevice();
        mvc.perform(multipart("/api/podcasts").file(samplePdf()).header("X-Device-Id", device))
                .andExpect(status().isAccepted());
        mvc.perform(delete("/api/me").header("X-Device-Id", device)).andExpect(status().isNoContent());
        mvc.perform(get("/api/podcasts").header("X-Device-Id", device)).andExpect(jsonPath("$.length()").value(0));
    }
}
