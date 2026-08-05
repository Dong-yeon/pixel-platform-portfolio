package com.pixelfactory.layout.domain;

import com.pixelplatform.core.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 평면도 자체의 치수 — <b>행이 하나뿐이다</b>(id = 1, DB 제약으로 고정).
 *
 * <p>평면도는 "여러 개 중 하나"가 아니라 이 공장의 속성이라 컬럼으로 갖는 게 맞다.
 * 설정 파일에 두지 않고 DB에 둔 이유: 노드·설비 좌표와 <b>같은 곳</b>에 있어야
 * robot-sim 대조 테스트가 한 파일만 보면 되고, 값을 바꿀 때 두 군데를 안 건드린다.
 */
@Getter
@Entity
@Table(name = "layout_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LayoutSettings extends BaseEntity {

    /** 싱글톤 행의 id. */
    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Column(nullable = false)
    private Double width;

    @Column(nullable = false)
    private Double height;

    /** 상단 가로 통로 y — LINE-1(A열) 담당. */
    @Column(name = "upper_aisle_y", nullable = false)
    private Double upperAisleY;

    /** 하단 가로 통로 y — LINE-2(B열) 담당. */
    @Column(name = "lower_aisle_y", nullable = false)
    private Double lowerAisleY;

    /**
     * 평면도 버전 (P20) — 물리적 배치(좌표·건물)가 바뀌는 마이그레이션에서만 올린다.
     * 그래프 표현 방식만 바뀌는 변경(P20-1 자체가 그 예)은 버전을 올리지 않는다.
     * 과거 시점 평면도 재생 같은 소비 기능은 아직 없다 — 지금은 기록만 한다.
     */
    @Column(name = "layout_version", nullable = false)
    private Integer layoutVersion;

    /** 이 버전이 유효해진 시각. */
    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom;
}
