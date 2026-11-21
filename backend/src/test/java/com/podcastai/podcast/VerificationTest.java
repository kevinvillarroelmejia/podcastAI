package com.podcastai.podcast;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.podcastai.ai.ScriptModels.Turn;
import com.podcastai.ai.ScriptModels.VerificationResult;
import com.podcastai.ai.ScriptModels.Verdict;

class VerificationTest {

    private final List<Turn> turns = List.of(
            new Turn("LIA", "¿Qué es una base de datos?", List.of(1)),
            new Turn("MARCO", "Un conjunto organizado de datos, inventado en 1492.", List.of(1)),
            new Turn("MARCO", "Y Java la inventó Shakespeare.", List.of(2)));

    @Test
    void removesUnsupportedAndRewritesPartial() {
        VerificationResult result = new VerificationResult(List.of(
                new Verdict(0, "respaldada", ""),
                new Verdict(1, "parcial", "Un conjunto organizado de datos."),
                new Verdict(2, "sin_respaldo", "")));
        PodcastPipeline.Verified verified = PodcastPipeline.applyVerification(turns, result);
        assertThat(verified.turns()).extracting(Turn::text)
                .containsExactly("¿Qué es una base de datos?", "Un conjunto organizado de datos.");
        assertThat(verified.removed()).isEqualTo(1);
        assertThat(verified.rewritten()).isEqualTo(1);
    }

    @Test
    void keepsTurnsWithoutVerdict() {
        PodcastPipeline.Verified verified = PodcastPipeline.applyVerification(turns, new VerificationResult(List.of()));
        assertThat(verified.turns()).hasSize(3);
    }
}
