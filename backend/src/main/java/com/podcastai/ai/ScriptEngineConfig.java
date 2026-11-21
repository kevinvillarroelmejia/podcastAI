package com.podcastai.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.podcastai.config.AppProperties;

/**
 * Elige el guionista: Claude si hay clave (o si se fuerza con AI_PROVIDER=claude), el de prueba si no.
 */
@Configuration
public class ScriptEngineConfig {

    private static final Logger log = LoggerFactory.getLogger(ScriptEngineConfig.class);

    @Bean
    public ScriptEngine scriptEngine(AppProperties props, ObjectMapper mapper) {
        AppProperties.Ai ai = props.ai();
        boolean hasKey = ai.anthropicApiKey() != null && !ai.anthropicApiKey().isBlank();
        boolean useClaude = "claude".equals(ai.provider()) || ("auto".equals(ai.provider()) && hasKey);
        ScriptEngine engine;
        if (useClaude) {
            engine = new ClaudeScriptEngine(ai, mapper);
            log.info("Guion con Claude: mapa={}, guion={}, verificación={}", ai.mapModel(), ai.scriptModel(),
                    ai.verifyModel());
        } else {
            engine = new MockScriptEngine();
            log.warn("Sin ANTHROPIC_API_KEY: el guion se genera en modo de prueba, sin IA.");
        }
        return engine;
    }
}
