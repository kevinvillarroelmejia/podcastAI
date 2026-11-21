package com.podcastai.ai;

/**
 * Fallo al generar el guion que se puede explicar al usuario.
 */
public class ScriptGenerationException extends RuntimeException {

    public ScriptGenerationException(String message) {
        super(message);
    }
}
