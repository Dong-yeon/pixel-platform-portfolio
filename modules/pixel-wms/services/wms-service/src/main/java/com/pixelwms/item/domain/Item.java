package com.pixelwms.item.domain;

import com.pixelplatform.core.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 품목 마스터. 표준CT는 {@link ItemStandardCycleTime}(품번×공정)이 갖는다. */
@Getter
@Entity
@Table(name = "items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Item extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String itemCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 10)
    private String unit;

    /**
     * 낱개(unit) 단위중량(kg) (P23 D4). null이면 파렛트 총중량 검증을 건너뛴다 — 없는
     * 데이터로 억지로 막지 않는다. EMMA 600K 사양서의 "파렛트 총중량 500kg 미만" 상한을
     * 지키는 데 쓴다({@code PalletService}/{@code StockService.receive} 참고).
     */
    @Column(precision = 8, scale = 3)
    private BigDecimal unitWeightKg;

    public Item(String itemCode, String name, String unit) {
        this.itemCode = itemCode;
        this.name = name;
        this.unit = unit;
    }
}
