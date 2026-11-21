package com.podcastai.web;

import org.springframework.http.HttpStatus;

/**
 * Error con un mensaje apto para mostrar al usuario.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
