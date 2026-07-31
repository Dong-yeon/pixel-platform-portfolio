package com.pixelfactory.master.domain;

import com.pixelplatform.core.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 차종 — 이 공장이 어떤 차의 부품을 만드는가. 품번의 최상위 묶음이다. */
@Getter
@Entity
@Table(name = "vehicle_models")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VehicleModel extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String modelCode;

    @Column(nullable = false, length = 50)
    private String name;

    /** 양산 중인가 — 단종 차종의 부품도 보수용으로 남아 있으므로 지우지 않고 끈다. */
    @Column(nullable = false)
    private Boolean inProduction;

    public VehicleModel(String modelCode, String name) {
        this.modelCode = modelCode;
        this.name = name;
        this.inProduction = true;
    }
}
