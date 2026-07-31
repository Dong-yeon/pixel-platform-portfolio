package com.pixelfactory.master.domain;

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

/**
 * 품번.
 *
 * <p>{@code partCode}에 unique가 걸려 있다 — 마스터가 1:1이 아니면 BOM 트리를 조립할 때
 * 노드가 곱해져 같은 자재가 화면에 여러 번 뜬다(실 운영 MES에서 겪은 사고). 원천에서 막는다.
 *
 * <p>WMS의 {@code items}와 같은 세계를 가리키며 <b>코드로 정합</b>한다(FK 아님 — DB per module).
 */
@Getter
@Entity
@Table(name = "parts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Part extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String partCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PartType partType;

    @Column(nullable = false, length = 10)
    private String unit;

    /** 차종. 여러 차종에 공용으로 쓰는 부품은 null이다. */
    private Long modelId;

    public Part(String partCode, String name, PartType partType, String unit, Long modelId) {
        this.partCode = partCode;
        this.name = name;
        this.partType = partType;
        this.unit = unit;
        this.modelId = modelId;
    }
}
