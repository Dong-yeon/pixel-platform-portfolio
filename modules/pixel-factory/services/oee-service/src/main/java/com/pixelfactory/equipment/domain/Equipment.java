package com.pixelfactory.equipment.domain;

import com.pixelplatform.core.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "equipments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Equipment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String equipmentCode;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private Long lineId;

    @Column(nullable = false)
    private Integer idealCycleTimeMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EquipmentStatus status;

    /**
     * 평면도 상의 위치. 대시보드가 지도에 그릴 때 쓴다.
     *
     * <p><b>컬럼명을 명시해야 한다.</b> Hibernate 기본 네이밍은 말미 대문자 뒤에 소문자가
     * 없으면 언더스코어를 넣지 않아 {@code posX} → {@code posx}가 된다. 마이그레이션의
     * {@code pos_x}와 어긋나 스키마 검증이 깨진다(fleet에서 실제로 겪었다).
     */
    @Column(name = "pos_x", nullable = false)
    private Double posX;

    @Column(name = "pos_y", nullable = false)
    private Double posY;

    public Equipment(
            String equipmentCode,
            String name,
            Long lineId,
            Integer idealCycleTimeMs,
            double posX,
            double posY
    ) {
        this.equipmentCode = equipmentCode;
        this.name = name;
        this.lineId = lineId;
        this.idealCycleTimeMs = idealCycleTimeMs;
        this.status = EquipmentStatus.IDLE;
        this.posX = posX;
        this.posY = posY;
    }

    public void changeStatus(EquipmentStatus status) {
        this.status = status;
    }
}
