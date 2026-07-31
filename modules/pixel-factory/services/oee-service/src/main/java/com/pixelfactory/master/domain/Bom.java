package com.pixelfactory.master.domain;

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

/**
 * BOM 한 줄 — "이 품번(parent)에 저 품번(child)이 몇 개 들어간다".
 *
 * <p><b>개정은 행을 고치지 않고 새 rev로 쌓는다</b>(개정이력 방식). 과거 rev로 만든 물건의
 * 구성을 나중에 되짚을 수 있어야 하기 때문이다. 최신 여부는 {@code latestYn}으로 가른다.
 *
 * <p>{@code qtyPer}는 DB가 {@code numeric}이라 <b>반드시 BigDecimal</b>이다. Double로 두면
 * 컴파일은 되고 {@code ddl-auto: validate}가 기동을 막는다(이 리포에서 좌표로 이미 겪었다).
 */
@Getter
@Entity
@Table(name = "boms")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long parentPartId;

    @Column(nullable = false)
    private Long childPartId;

    @Column(nullable = false)
    private Integer revNo;

    @Column(nullable = false)
    private Short seq;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal qtyPer;

    @Column(nullable = false, length = 1)
    private String latestYn;

    public Bom(Long parentPartId, Long childPartId, Integer revNo, Short seq, BigDecimal qtyPer) {
        this.parentPartId = parentPartId;
        this.childPartId = childPartId;
        this.revNo = revNo;
        this.seq = seq;
        this.qtyPer = qtyPer;
        this.latestYn = "Y";
    }

    /** 새 rev가 나오면 이전 rev는 최신이 아니다. */
    public void supersede() {
        this.latestYn = "N";
    }

    public boolean isLatest() {
        return "Y".equals(this.latestYn);
    }
}
