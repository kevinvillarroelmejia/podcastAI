package com.podcastai.tts;

/** La cuenta del proveedor de voz no puede atender la petición (clave inválida, sin créditos o plan de pago). */
public class VoiceAccountException extends TtsException {

    public VoiceAccountException(String message) {
        super(message);
    }
}
