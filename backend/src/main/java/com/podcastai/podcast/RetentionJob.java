package com.podcastai.podcast;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.podcastai.config.AppProperties;

/**
 * Borra cada noche el texto de los PDF y sus mapas pasados 30 días. Los audios se quedan hasta que el usuario los borre.
 */
@Component
public class RetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

    private final DocumentTextRepository documents;
    private final MapCacheRepository mapCache;
    private final AppProperties props;

    public RetentionJob(DocumentTextRepository documents, MapCacheRepository mapCache, AppProperties props) {
        this.documents = documents;
        this.mapCache = mapCache;
        this.props = props;
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void purge() {
        Instant limit = Instant.now().minus(Duration.ofDays(props.retentionDays()));
        long docs = documents.deleteByCreatedAtBefore(limit);
        long maps = mapCache.deleteByCreatedAtBefore(limit);
        log.info("Retención: {} textos de PDF y {} mapas borrados", docs, maps);
    }
}
