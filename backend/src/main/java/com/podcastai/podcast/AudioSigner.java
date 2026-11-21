package com.podcastai.podcast;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.podcastai.config.AppProperties;

/**
 * Firma las URLs de audio (HMAC-SHA256 con caducidad), porque el reproductor no puede enviar cabeceras.
 */
@Component
public class AudioSigner {

    private static final Logger log = LoggerFactory.getLogger(AudioSigner.class);
    private static final long VALID_SECONDS = 6 * 3600;

    private final byte[] secret;

    public AudioSigner(AppProperties props) {
        String configured = props.signingSecret();
        if (configured == null || configured.length() < 32) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            this.secret = random;
            log.warn("SIGNING_SECRET no definido (o con menos de 32 caracteres): se usa uno aleatorio y las URLs "
                    + "de audio caducan al reiniciar.");
        } else {
            this.secret = configured.getBytes(StandardCharsets.UTF_8);
        }
    }

    public String signedQuery(UUID podcastId, int blockIndex) {
        long expires = Instant.now().getEpochSecond() + VALID_SECONDS;
        return "exp=" + expires + "&sig=" + signature(podcastId, blockIndex, expires);
    }

    public boolean isValid(UUID podcastId, int blockIndex, long expires, String sig) {
        boolean notExpired = expires >= Instant.now().getEpochSecond();
        byte[] expected = signature(podcastId, blockIndex, expires).getBytes(StandardCharsets.US_ASCII);
        byte[] given = sig == null ? new byte[0] : sig.getBytes(StandardCharsets.US_ASCII);
        return notExpired && MessageDigest.isEqual(expected, given);
    }

    private String signature(UUID podcastId, int blockIndex, long expires) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] raw = mac.doFinal((podcastId + ":" + blockIndex + ":" + expires).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
