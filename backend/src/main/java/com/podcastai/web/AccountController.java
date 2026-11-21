package com.podcastai.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.podcastai.account.Account;
import com.podcastai.account.AccountService;
import com.podcastai.account.Plan;
import com.podcastai.ai.ScriptEngine;
import com.podcastai.config.AppProperties;
import com.podcastai.podcast.PodcastService;
import com.podcastai.web.Dtos.AccountView;

@RestController
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);
    private static final Set<String> ACTIVATE = Set.of("INITIAL_PURCHASE", "RENEWAL", "UNCANCELLATION",
            "PRODUCT_CHANGE", "SUBSCRIPTION_EXTENDED");
    private static final Set<String> DEACTIVATE = Set.of("EXPIRATION");

    private final AccountService accounts;
    private final PodcastService podcasts;
    private final AppProperties props;
    private final ScriptEngine scriptEngine;

    public AccountController(AccountService accounts, PodcastService podcasts, AppProperties props,
                             ScriptEngine scriptEngine) {
        this.accounts = accounts;
        this.podcasts = podcasts;
        this.props = props;
        this.scriptEngine = scriptEngine;
    }

    @GetMapping("/api/me")
    public AccountView me(@RequestHeader(PodcastController.DEVICE_HEADER) String deviceId) {
        Account account = accounts.getOrCreate(deviceId);
        return AccountView.of(account, accounts.monthlyLimit(account), props.limits().freePodcasts(),
                scriptEngine.name(), podcasts.voiceFor(account));
    }

    /** Borra la cuenta y todos sus podcasts y audios. */
    @DeleteMapping("/api/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@RequestHeader(PodcastController.DEVICE_HEADER) String deviceId) {
        podcasts.deleteAccount(deviceId);
    }

    /**
     * Webhook de RevenueCat. El plan solo cambia por aquí, nunca por lo que diga la app.
     * RevenueCat envía en Authorization el valor que se configura en su panel (REVENUECAT_WEBHOOK_SECRET).
     */
    @PostMapping("/api/webhooks/revenuecat")
    public void revenueCat(@RequestHeader(name = "Authorization", required = false) String authorization,
                           @RequestBody JsonNode body) {
        String secret = props.revenuecatWebhookSecret();
        boolean configured = secret != null && !secret.isBlank();
        boolean authorized = configured && authorization != null && MessageDigest.isEqual(
                authorization.getBytes(StandardCharsets.UTF_8), ("Bearer " + secret).getBytes(StandardCharsets.UTF_8));
        if (!authorized) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "No autorizado.");
        }
        JsonNode event = body.path("event");
        String type = event.path("type").asText("");
        String appUserId = event.path("app_user_id").asText("");
        if (ACTIVATE.contains(type)) {
            accounts.setPlan(appUserId, Plan.PREMIUM);
            log.info("Plan Premium activado ({})", type);
        } else if (DEACTIVATE.contains(type)) {
            accounts.setPlan(appUserId, Plan.FREE);
            log.info("Plan Premium terminado ({})", type);
        }
    }
}
