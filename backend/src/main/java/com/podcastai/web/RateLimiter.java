package com.podcastai.web;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.podcastai.config.AppProperties;

/**
 * Límite sencillo de subidas por hora, por cuenta y por IP (ventana deslizante en memoria).
 */
@Component
public class RateLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
    private final int perHour;

    public RateLimiter(AppProperties props) {
        this.perHour = props.limits().uploadsPerHour();
    }

    public void checkUpload(String accountId, String ip) {
        boolean allowed = tryAcquire("acc:" + accountId, perHour) & tryAcquire("ip:" + ip, perHour * 3);
        if (!allowed) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Has creado muchos podcasts en poco tiempo. Prueba dentro de un rato.");
        }
    }

    private boolean tryAcquire(String key, int limit) {
        Deque<Instant> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        boolean allowed;
        synchronized (deque) {
            Instant now = Instant.now();
            Instant from = now.minus(WINDOW);
            while (!deque.isEmpty() && deque.peekFirst().isBefore(from)) {
                deque.pollFirst();
            }
            allowed = deque.size() < limit;
            if (allowed) {
                deque.addLast(now);
            }
        }
        return allowed;
    }
}
