package com.pixelqms.ncr.domain;

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

/** 부적합(NCR) — 검사에서 발견된 문제. 심의가 필요하면 MRB로 올라간다. */
@Getter
@Entity
@Table(name = "nonconformances")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Nonconformance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String ncrNo;

    private Long inspectionId;

    private Long defectTypeId;

    @Column(length = 30)
    private String equipmentCode;

    @Column(length = 50)
    private String workOrderNo;

    @Column(length = 50)
    private String lotNo;

    @Column(nullable = false)
    private Integer defectQty;

    @Column(length = 500)
    private String description;

    public Nonconformance(String ncrNo, Long inspectionId, Long defectTypeId, String equipmentCode,
                          String workOrderNo, String lotNo, Integer defectQty, String description) {
        this.ncrNo = ncrNo;
        this.inspectionId = inspectionId;
        this.defectTypeId = defectTypeId;
        this.equipmentCode = equipmentCode;
        this.workOrderNo = workOrderNo;
        this.lotNo = lotNo;
        this.defectQty = defectQty;
        this.description = description;
    }
}
