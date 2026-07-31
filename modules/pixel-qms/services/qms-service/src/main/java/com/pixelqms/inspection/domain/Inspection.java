package com.pixelqms.inspection.domain;

import com.pixelplatform.core.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 검사.
 *
 * <p>factory의 설비·작업지시를 <b>코드 문자열로만</b> 참조한다(FK 아님, 다른 모듈 DB).
 * 불량이 임계를 넘으면 factory 신호를 받아 공정검사가 자동 생성된다.
 */
@Getter
@Entity
@Table(name = "inspections")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inspection extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String inspectionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InspectionType inspectionType;

    @Column(length = 30)
    private String equipmentCode;

    @Column(length = 50)
    private String workOrderNo;

    @Column(length = 50)
    private String lotNo;

    private Long inspectorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InspectionResult result;

    @Column(nullable = false)
    private Integer inspectedQty;

    @Column(nullable = false)
    private Integer defectQty;

    @Column(length = 500)
    private String note;

    private LocalDateTime completedAt;

    public Inspection(String inspectionNo, InspectionType inspectionType, String equipmentCode,
                      String workOrderNo, String lotNo, Integer inspectedQty, Integer defectQty) {
        this.inspectionNo = inspectionNo;
        this.inspectionType = inspectionType;
        this.equipmentCode = equipmentCode;
        this.workOrderNo = workOrderNo;
        this.lotNo = lotNo;
        this.inspectedQty = inspectedQty == null ? 0 : inspectedQty;
        this.defectQty = defectQty == null ? 0 : defectQty;
        this.result = InspectionResult.PENDING;
    }

    /** 검사 판정. 불합격이면 부적합(NCR) 등록으로 이어진다. */
    public void complete(InspectionResult result, Long inspectorId, Integer inspectedQty,
                         Integer defectQty, String note) {
        this.result = result;
        this.inspectorId = inspectorId;
        if (inspectedQty != null) {
            this.inspectedQty = inspectedQty;
        }
        if (defectQty != null) {
            this.defectQty = defectQty;
        }
        this.note = note;
        this.completedAt = LocalDateTime.now();
    }

    public boolean isPending() {
        return this.result == InspectionResult.PENDING;
    }
}
