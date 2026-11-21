package com.podcastai.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Hilos virtuales para el pipeline: cada podcast y cada llamada de voz son tareas lentas de E/S.
 */
@Configuration
public class ExecutorConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService pipelineExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
