package com.podcastai.ai;

import java.util.List;

import com.podcastai.ai.ScriptModels.ContentMap;
import com.podcastai.ai.ScriptModels.PlannedBlock;
import com.podcastai.ai.ScriptModels.Turn;
import com.podcastai.pdf.PageText;

/**
 * Todo lo que necesita el guionista para escribir un bloque.
 */
public record BlockRequest(
        ContentMap map,
        PlannedBlock block,
        int index,
        int total,
        PodcastMode mode,
        String language,
        List<PageText> sourcePages,
        List<Turn> previousTail,
        boolean expressive) {

    public boolean isFirst() {
        return index == 0;
    }

    public boolean isLast() {
        return index == total - 1;
    }
}
