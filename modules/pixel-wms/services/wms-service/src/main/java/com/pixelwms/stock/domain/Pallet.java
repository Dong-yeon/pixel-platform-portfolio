package com.pixelwms.stock.domain;

import com.pixelplatform.core.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 로봇이 실제로 옮기는 물리 단위 (P23).
 *
 * <p>EMMA 600K는 품목을 옮기지 않는다 — 1100×1100mm 팔레트를 통째로 잭업해서 옮긴다
 * (근거: AMR 사양요구서 §"设计输入"). {@code pltCode}는 팔레트 바닥면 QR코드에 대응한다.
 * 파렛트당 재고 행은 하나뿐이다(파렛트당 품목 하나) — {@link Stock}의 {@code uq_stock_pallet}
 * 제약이 강제한다(설계 근거: docs/p23-pallet-unit-design.md D2).
 */
@Getter
@Entity
@Table(name = "pallets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pallet extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String pltCode;

    @Column(nullable = false)
    private Long locationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PalletStatus status;

    /** 총중량(kg). 단위중량이 없는 품목을 실으면 null로 남는다(D4 — 검증 생략). */
    @Column(precision = 6, scale = 2)
    private BigDecimal weightKg;

    public Pallet(String pltCode, Long locationId, BigDecimal weightKg) {
        this.pltCode = pltCode;
        this.locationId = locationId;
        this.status = PalletStatus.LOADED;
        this.weightKg = weightKg;
    }

    /** fleet에 운송 작업을 넘겼다 — 아직 물리적으로는 로케이션에 있다(완료 통지 전까지). */
    public void markInTransit() {
        this.status = PalletStatus.IN_TRANSIT;
    }

    /** 출고 완료 — 재고를 다 내렸다. 이 파렛트 레코드의 수명은 여기서 끝난다(회수·재사용은 범위 밖). */
    public void markRetired() {
        this.status = PalletStatus.RETIRED;
    }
}
