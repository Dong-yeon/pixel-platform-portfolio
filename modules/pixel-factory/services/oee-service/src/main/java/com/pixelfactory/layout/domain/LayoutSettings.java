package com.pixelfactory.layout.domain;

import com.pixelplatform.core.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
}
