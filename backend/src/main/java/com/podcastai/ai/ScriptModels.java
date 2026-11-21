package com.podcastai.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Estructuras que devuelven los modelos (salida estructurada con JSON Schema).
 */
public final class ScriptModels {

    private ScriptModels() {
    }

    public record ContentMap(
            @JsonPropertyDescription("Título corto del episodio, en el idioma de salida") String title,
            @JsonPropertyDescription("Temas y conceptos del documento") List<Topic> topics,
            @JsonPropertyDescription("Plan de bloques de unos 60 segundos, en orden narrativo") List<PlannedBlock> blocks) {
    }

    public record Topic(
            String name,
            @JsonPropertyDescription("Resumen fiel de 1 o 2 frases") String summary,
            @JsonPropertyDescription("true si es un concepto imprescindible") boolean key,
            @JsonPropertyDescription("Páginas del PDF donde aparece") List<Integer> pages) {
    }

    public record PlannedBlock(
            String title,
            @JsonPropertyDescription("Qué debe entender el oyente al acabar el bloque") String goal,
            @JsonPropertyDescription("Páginas del PDF en las que se apoya") List<Integer> pages) {
    }

    public record BlockScript(List<Turn> turns) {
    }

    public record Turn(
            @JsonPropertyDescription("LIA o MARCO") String speaker,
            @JsonPropertyDescription("Lo que dice, escrito para el oído") String text,
            @JsonPropertyDescription("Páginas del PDF que respaldan la intervención") List<Integer> pages) {
    }

    public record VerificationResult(List<Verdict> verdicts) {
    }

    public record Verdict(
            @JsonPropertyDescription("Índice de la intervención, empezando en 0") int index,
            @JsonPropertyDescription("respaldada, parcial o sin_respaldo") String status,
            @JsonPropertyDescription("Solo si es parcial: versión corregida y fiel; si no, cadena vacía") String correctedText) {
    }
}
