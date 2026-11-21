package com.podcastai.account;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los contadores de uso se cambian con UPDATE atómicos para que dos peticiones a la vez no se salten el límite.
 */
public interface AccountRepository extends JpaRepository<Account, String> {

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update Account a set a.freePodcastsUsed = a.freePodcastsUsed + 1 "
            + "where a.id = :id and a.freePodcastsUsed < :limit")
    int claimFreePodcast(@Param("id") String id, @Param("limit") int limit);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update Account a set a.freePodcastsUsed = a.freePodcastsUsed - 1 "
            + "where a.id = :id and a.freePodcastsUsed > 0")
    int refundFreePodcast(@Param("id") String id);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update Account a set a.minutesUsed = a.minutesUsed + :minutes where a.id = :id")
    int addMinutes(@Param("id") String id, @Param("minutes") double minutes);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update Account a set a.minutesUsed = 0, a.periodStart = :today "
            + "where a.id = :id and a.periodStart <= :limit")
    int resetPeriodIfDue(@Param("id") String id, @Param("today") LocalDate today, @Param("limit") LocalDate limit);
}
