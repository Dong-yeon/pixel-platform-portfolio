package com.pixelfactory.event.scheduler;

import com.pixelfactory.event.repository.FactoryEventRepository;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code docs/BACKLOG.md}에 오래 남아 있던 항목 — "이벤트 보존 정책. factory_events가
 * 계속 쌓인다. 배포 환경 비용 직결." 실측 당시 이미 72,862건이었다(P8 검증, 설비 8대
 * 몇 시간만 돌려서).
 *
 * <p><b>왜 삭제이지 아카이브가 아닌가.</b> 이 이벤트 스트림은 OEE 계산의 원본이지만,
 * OEE는 항상 <b>최근 구간</b>(시프트·일 단위)만 조회한다({@code OeeService}). 몇 달 지난
 * 원본 이벤트를 다시 재집계할 화면이나 배치가 없다 — 필요해지면(예: 연간 리포트) 그때
 * S3 등으로 내보내는 잡을 별도로 만들면 되고, 지금 만들면 쓰이지 않는 코드다.
 *
 * <p><b>기본 90일.</b> 시프트·일·월 단위 OEE 조회를 넉넉히 덮으면서도, 데모/실측용으로는
 * 사실상 무제한이다. 운영 전환 시 비용을 보고 좁히면 된다.
 */
@Component
@ConditionalOnProperty(name = "factory.events.retention.enabled", havingValue = "true", matchIfMissing = true)
public class FactoryEventRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(FactoryEventRetentionJob.class);

    private final FactoryEventRepository factoryEventRepository;
    private final int retentionDays;

    public FactoryEventRetentionJob(
            FactoryEventRepository factoryEventRepository,
            @Value("${factory.events.retention.days:90}") int retentionDays
    ) {
        this.factoryEventRepository = factoryEventRepository;
        this.retentionDays = retentionDays;
    }

    /**
     * 매일 새벽 3시(서버 시간대) — 트래픽이 가장 적을 시간대를 고정 시각으로 잡는다.
     * fixedDelay가 아니라 cron인 이유: 재기동 시점에 따라 삭제 시점이 흘러가지 않게.
     */
    @Scheduled(cron = "${factory.events.retention.cron:0 0 3 * * *}")
    @Transactional
    public void purgeOldEvents() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int deleted = factoryEventRepository.deleteByCreatedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Event retention: purged {} factory_events older than {} ({} day retention).",
                    deleted, cutoff, retentionDays);
        }
    }
}
