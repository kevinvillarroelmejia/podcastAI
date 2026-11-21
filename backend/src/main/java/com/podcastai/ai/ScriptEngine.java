package com.podcastai.ai;

import java.util.List;

import com.podcastai.ai.ScriptModels.BlockScript;
import com.podcastai.ai.ScriptModels.ContentMap;
import com.podcastai.ai.ScriptModels.Turn;
import com.podcastai.ai.ScriptModels.VerificationResult;
import com.podcastai.pdf.PageText;

/**
 * Las tres fases de IA del pipeline: mapa, guion por bloque y verificación.
 */
public interface ScriptEngine {

    ContentMap buildMap(String title, List<PageText> pages, PodcastMode mode, String language);

    BlockScript writeBlock(BlockRequest request);

    VerificationResult verify(List<Turn> turns, List<PageText> sourcePages, String language);

    String name();
}
