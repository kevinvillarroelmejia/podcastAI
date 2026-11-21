package com.podcastai.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import com.podcastai.ai.ScriptModels.BlockScript;
import com.podcastai.ai.ScriptModels.ContentMap;
import com.podcastai.ai.ScriptModels.PlannedBlock;
import com.podcastai.ai.ScriptModels.Topic;
import com.podcastai.ai.ScriptModels.Turn;
import com.podcastai.ai.ScriptModels.VerificationResult;
import com.podcastai.ai.ScriptModels.Verdict;
import com.podcastai.pdf.PageText;

/**
 * Guionista de prueba, sin IA: arma una conversación con frases literales del PDF.
 * Sirve para probar la app de principio a fin sin claves de API.
 */
public class MockScriptEngine implements ScriptEngine {

    private static final int SENTENCES_PER_BLOCK = 4;

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public ContentMap buildMap(String title, List<PageText> pages, PodcastMode mode, String language) {
        List<PageText> withText = pages.stream().filter(p -> !p.isEmpty()).toList();
        int blockCount = Math.max(1, Math.min(mode.minBlocks(), withText.size()));
        List<PlannedBlock> blocks = new ArrayList<>();
        List<Topic> topics = new ArrayList<>();
        for (int b = 0; b < blockCount; b++) {
            int from = b * withText.size() / blockCount;
            int to = (b + 1) * withText.size() / blockCount;
            List<Integer> blockPages = withText.subList(from, to).stream().map(PageText::page).toList();
            String blockTitle = firstWords(withText.get(from).text(), 6);
            blocks.add(new PlannedBlock(blockTitle, blockTitle, blockPages));
            topics.add(new Topic(blockTitle, firstWords(withText.get(from).text(), 20), true, blockPages));
        }
        return new ContentMap(title, topics, blocks);
    }

    @Override
    public BlockScript writeBlock(BlockRequest r) {
        boolean en = "en".equals(r.language());
        List<Integer> pages = r.block().pages();
        List<String> sentences = sentencesOf(r.sourcePages());
        List<Turn> turns = new ArrayList<>();
        if (r.isFirst()) {
            turns.add(new Turn("LIA", en
                    ? "Welcome to podcastAI! Today we're talking about " + r.map().title() + "."
                    : "¡Bienvenidos a podcastAI! Hoy hablamos de " + r.map().title() + ".", pages));
        }
        String[] questions = en
                ? new String[] {"So, where do we start?", "Okay, and what comes next?", "Wait, explain that again.",
                    "Interesting. Anything else?"}
                : new String[] {"Vale, ¿por dónde empezamos?", "¿Y qué viene después?", "Espera, explícame eso.",
                    "Interesante. ¿Algo más?"};
        int count = Math.min(SENTENCES_PER_BLOCK, sentences.size());
        for (int i = 0; i < count; i++) {
            turns.add(new Turn("LIA", questions[(r.index() + i) % questions.length], pages));
            turns.add(new Turn("MARCO", limitWords(sentences.get(i), 45), pages));
        }
        if (r.isLast()) {
            turns.add(new Turn("LIA", en ? "That's all for today. Thanks for listening!"
                    : "Hasta aquí el episodio. ¡Gracias por escuchar!", pages));
        }
        return new BlockScript(turns);
    }

    @Override
    public VerificationResult verify(List<Turn> turns, List<PageText> sourcePages, String language) {
        List<Verdict> verdicts = IntStream.range(0, turns.size())
                .mapToObj(i -> new Verdict(i, "respaldada", ""))
                .toList();
        return new VerificationResult(verdicts);
    }

    static List<String> sentencesOf(List<PageText> pages) {
        String all = String.join(" ", pages.stream().map(PageText::text).toList()).replaceAll("\\s+", " ");
        return Arrays.stream(all.split("(?<=[.!?])\\s+"))
                .map(String::strip)
                .filter(s -> s.split(" ").length >= 4)
                .toList();
    }

    static String firstWords(String text, int n) {
        String[] words = text.strip().split("\\s+");
        String joined = String.join(" ", Arrays.copyOf(words, Math.min(n, words.length)));
        return joined.replaceAll("[.,;:]+$", "");
    }

    static String limitWords(String text, int n) {
        String[] words = text.split("\\s+");
        String result = text;
        if (words.length > n) {
            result = String.join(" ", Arrays.copyOf(words, n)) + "…";
        }
        return result;
    }
}
