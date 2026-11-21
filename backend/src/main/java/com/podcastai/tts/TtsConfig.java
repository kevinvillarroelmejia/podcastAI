package com.podcastai.tts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.podcastai.config.AppProperties;

@Configuration
public class TtsConfig {

    private static final Logger log = LoggerFactory.getLogger(TtsConfig.class);

    @Bean
    public TtsEngine ttsEngine(AppProperties props, ObjectMapper mapper) {
        AppProperties.Tts tts = props.tts();
        boolean hasKey = tts.openaiApiKey() != null && !tts.openaiApiKey().isBlank();
        boolean useOpenAi = "openai".equals(tts.provider()) || ("auto".equals(tts.provider()) && hasKey);
        TtsEngine engine;
        if (useOpenAi) {
            engine = new OpenAiTtsEngine(tts, mapper);
            log.info("Voz con OpenAI {} (Lía: {}, Marco: {})", tts.model(), tts.hostVoice(), tts.expertVoice());
        } else {
            engine = new MockTtsEngine();
            log.warn("Sin OPENAI_API_KEY: la voz se genera en modo de prueba (silencio).");
        }
        return engine;
    }
}
