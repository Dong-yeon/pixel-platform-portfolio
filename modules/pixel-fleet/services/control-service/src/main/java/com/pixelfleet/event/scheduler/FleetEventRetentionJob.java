package com.pixelfleet.event.scheduler;

import com.pixelfleet.event.repository.FleetEventRepository;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * fleet_events 판 보존 정책 — pixel-factory의 {@code FactoryEventRetentionJob}과 같은
 * 이유(BACKLOG의 "이벤트 보존 정책" 항목, 비용 직결)로 같은 방식을 쓴다. 로봇 텔레메트리는
 * factory 사이클보다도 빈도가 잦아(초당 발행) 이 테이블이 더 빨리 쌓인다.
 */
@Component
@ConditionalOnProperty(name = "fleet.events.retention.enabled", havingValue = "true", matchIfMissing = true)
public class FleetEventRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(FleetEventRetentionJob.class);

    private final FleetEventRepository fleetEventRepository;
    private final int retentionDays;

    public FleetEventRetentionJob(
            FleetEventRepository fleetEventRepository,
            @Value("${fleet.events.retention.days:90}") int retentionDays
    ) {
        this.fleetEventRepository = fleetEventRepository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${fleet.events.retention.cron:0 15 3 * * *}")
    @Transactional
    public void purgeOldEvents() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int deleted = fleetEventRepository.deleteByCreatedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Event retention: purged {} fleet_events older than {} ({} day retention).",
                    deleted, cutoff, retentionDays);
        }
    }
}
