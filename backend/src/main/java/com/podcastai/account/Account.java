package com.podcastai.account;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Cuenta de un usuario. En el MVP se identifica por un id anónimo de dispositivo;
 * en la fase 2 será el id de Supabase Auth.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @Column(length = 64)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Plan plan = Plan.FREE;

    @Column(nullable = false)
    private double minutesUsed;

    @Column(nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false)
    private int freePodcastsUsed;

    @Column(length = 8)
    private String language = "es";

    @Column(nullable = false)
    private Instant createdAt;

    protected Account() {
    }

    public Account(String id, LocalDate today) {
        this.id = id;
        this.periodStart = today;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public Plan getPlan() {
        return plan;
    }

    public void setPlan(Plan plan) {
        this.plan = plan;
    }

    public double getMinutesUsed() {
        return minutesUsed;
    }

    public void setMinutesUsed(double minutesUsed) {
        this.minutesUsed = minutesUsed;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public int getFreePodcastsUsed() {
        return freePodcastsUsed;
    }

    public void setFreePodcastsUsed(int freePodcastsUsed) {
        this.freePodcastsUsed = freePodcastsUsed;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
