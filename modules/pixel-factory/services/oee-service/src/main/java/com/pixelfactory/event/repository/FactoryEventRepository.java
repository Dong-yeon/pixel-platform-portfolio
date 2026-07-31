package com.pixelfactory.event.repository;

import com.pixelfactory.event.domain.FactoryEvent;
import com.pixelfactory.event.domain.FactoryEventType;
import com.pixelfactory.event.domain.SourceType;
import com.pixelfactory.event.domain.TargetType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FactoryEventRepository extends JpaRepository<FactoryEvent, Long> {

    // 정렬 기준은 적재 시각(createdAt)이 아니라 발생 시각(occurredAt)이다.
    // 밀렸다 한꺼번에 들어온 메시지가 처리 순서대로 붙으면 실제 일어난 순서와 뒤바뀐다.
    List<FactoryEvent> findByOrderByOccurredAtDesc(Pageable pageable);

    /**
     * 타임아웃 이내의 TERMINAL 소스 이벤트 — POP presence(파생 위치) 계산 입력.
     *
     * <p>최신순으로 받아 터미널(sourceId)별 첫 1건을 "그 단말의 현재 상태"로 본다.
     * presence는 저장하지 않고 이 이벤트 스트림에서 파생한다(P12).
     */
    List<FactoryEvent> findBySourceTypeAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(
            SourceType sourceType,
            LocalDateTime since
    );

    List<FactoryEvent> findByWorkOrderIdOrderByOccurredAtDesc(Long workOrderId);

    // ---- OEE 집계용 (idx_factory_events_target_type_time 이 이 형태를 받는다) ----

    /** 조회 구간 안의 이벤트. 상태 구간 조립·사이클 집계에 쓴다. */
    List<FactoryEvent> findByEventTypeAndTargetTypeAndTargetIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
            FactoryEventType eventType,
            TargetType targetType,
            Long targetId,
            LocalDateTime from,
            LocalDateTime to
    );

    /**
     * 조회 시작 <b>이전</b>의 마지막 상태 변경 — 캐리인용.
     *
     * <p>이걸 빼면 구간 시작 시점의 상태를 알 수 없어 첫 이벤트까지가 통째로 비고,
     * 이전부터 이어진 정지가 사라져 A가 부풀려진다.
     */
    Optional<FactoryEvent> findFirstByEventTypeAndTargetTypeAndTargetIdAndOccurredAtLessThanOrderByOccurredAtDesc(
            FactoryEventType eventType,
            TargetType targetType,
            Long targetId,
            LocalDateTime before
    );
}
