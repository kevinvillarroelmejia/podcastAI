package com.podcastai.ai;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.podcastai.ai.ScriptModels.BlockScript;
import com.podcastai.ai.ScriptModels.ContentMap;
import com.podcastai.ai.ScriptModels.Turn;
import com.podcastai.ai.ScriptModels.VerificationResult;
import com.podcastai.config.AppProperties;
import com.podcastai.pdf.PageText;

/**
 * Guionista con la API de Claude: Haiku para el mapa y la verificación, Sonnet para el guion.
 */
public class ClaudeScriptEngine implements ScriptEngine {

    private final AnthropicClient client;
    private final AppProperties.Ai config;
    private final ObjectMapper mapper;

    public ClaudeScriptEngine(AppProperties.Ai config, ObjectMapper mapper) {
        this.client = AnthropicOkHttpClient.builder().apiKey(config.anthropicApiKey()).build();
        this.config = config;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "claude";
    }

    @Override
    public ContentMap buildMap(String title, List<PageText> pages, PodcastMode mode, String language) {
        String user = "Idioma de salida: " + Languages.nameOf(language) + "\n"
                + Prompts.modeInstructions(mode) + "\n"
                + "Nombre del archivo: " + title + "\n\n"
                + Prompts.sources(pages);
        return ask(config.mapModel(), Prompts.MAP_SYSTEM, user, ContentMap.class, 16000L, OutputConfig.Effort.LOW);
    }

    @Override
    public BlockScript writeBlock(BlockRequest r) {
        StringBuilder user = new StringBuilder();
        user.append("Idioma de salida: ").append(Languages.nameOf(r.language())).append('\n');
        user.append(Prompts.modeInstructions(r.mode())).append("\n\n");
        user.append("Mapa del episodio:\n").append(toJson(r.map())).append("\n\n");
        user.append("Bloque ").append(r.index() + 1).append(" de ").append(r.total()).append(": ")
                .append(r.block().title()).append('\n');
        user.append("Objetivo: ").append(r.block().goal()).append('\n');
        if (r.isFirst()) {
            user.append("Es el primer bloque: Lía da la bienvenida a podcastAI y presenta el tema en una o dos frases.\n");
        }
        if (r.isLast()) {
            user.append("Es el último bloque: cerrad el episodio con una despedida breve.\n");
        }
        if (!r.previousTail().isEmpty()) {
            user.append("\nFinal del bloque anterior (continúa desde aquí):\n").append(formatTurns(r.previousTail()));
        }
        if (r.expressive()) {
            user.append("\n").append(Prompts.EXPRESSIVE_RULES);
        }
        user.append("\n\n").append(Prompts.sources(r.sourcePages()));
        return ask(config.scriptModel(), Prompts.SCRIPT_SYSTEM, user.toString(), BlockScript.class, 16000L,
                OutputConfig.Effort.MEDIUM);
    }

    @Override
    public VerificationResult verify(List<Turn> turns, List<PageText> sourcePages, String language) {
        String numbered = IntStream.range(0, turns.size())
                .mapToObj(i -> i + ". [" + turns.get(i).speaker() + "] " + turns.get(i).text()
                        + " (páginas " + turns.get(i).pages() + ")")
                .collect(Collectors.joining("\n"));
        String user = "Idioma de salida: " + Languages.nameOf(language) + "\n\nIntervenciones:\n" + numbered
                + "\n\n" + Prompts.sources(sourcePages);
        return ask(config.verifyModel(), Prompts.VERIFY_SYSTEM, user, VerificationResult.class, 8000L,
                OutputConfig.Effort.LOW);
    }

    private <T> T ask(String model, String system, String user, Class<T> type, long maxTokens,
                      OutputConfig.Effort effort) {
        StructuredOutputConfig<T> output = StructuredOutputConfig.<T>builder().format(type).effort(effort).build();
        StructuredMessageCreateParams<T> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .system(system)
                .outputConfig(output)
                .addUserMessage(user)
                .build();
        StructuredMessage<T> message = client.messages().create(params);
        StopReason stop = message.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stop)) {
            throw new ScriptGenerationException("El modelo ha rechazado generar este contenido.");
        }
        if (StopReason.MAX_TOKENS.equals(stop)) {
            throw new ScriptGenerationException("La respuesta del modelo se ha cortado.");
        }
        return message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text())
                .findFirst()
                .orElseThrow(() -> new ScriptGenerationException("El modelo no ha devuelto contenido."));
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String formatTurns(List<Turn> turns) {
        return turns.stream().map(t -> t.speaker() + ": " + t.text()).collect(Collectors.joining("\n"));
    }
}
