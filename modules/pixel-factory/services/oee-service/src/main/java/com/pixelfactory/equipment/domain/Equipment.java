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

    public Equipment(String equipmentCode, String name, Long lineId, Integer idealCycleTimeMs) {
        this.equipmentCode = equipmentCode;
        this.name = name;
        this.lineId = lineId;
        this.idealCycleTimeMs = idealCycleTimeMs;
        this.status = EquipmentStatus.IDLE;
    }

    public void changeStatus(EquipmentStatus status) {
        this.status = status;
    }
}
