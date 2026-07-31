package com.pixelqms.mrb.domain;

import com.pixelplatform.core.common.entity.BaseEntity;
import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelplatform.core.common.exception.ErrorCode;
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
 * MRB(Material Review Board) 심의.
 *
 * <p><b>심의가 열리면 현장이 멈춘다.</b> factory의 설비를 QUALITY_HOLD, 작업지시를 ON_HOLD로
 * 만들고, 판정이 끝나면 푼다. 이 왕복이 "별개 서비스가 계약만으로 연동된다"를 지도 위에서 보여준다.
 */
@Getter
@Entity
@Table(name = "mrb_reviews")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MrbReview extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String mrbNo;

    @Column(nullable = false)
    private Long nonconformanceId;

    @Column(length = 30)
    private String equipmentCode;

    @Column(length = 50)
    private String workOrderNo;

    @Column(length = 50)
    private String lotNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MrbStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MrbDecision decision;

    private Long decidedBy;

    @Column(length = 500)
    private String decisionNote;

    /** factory에 홀드를 실제로 걸었는지 — 판정 시 풀 대상인지 판단한다. */
    @Column(nullable = false)
    private Boolean holdApplied;

    private LocalDateTime decidedAt;

    private LocalDateTime closedAt;

    public MrbReview(String mrbNo, Long nonconformanceId, String equipmentCode,
                     String workOrderNo, String lotNo) {
        this.mrbNo = mrbNo;
        this.nonconformanceId = nonconformanceId;
        this.equipmentCode = equipmentCode;
        this.workOrderNo = workOrderNo;
        this.lotNo = lotNo;
        this.status = MrbStatus.RAISED;
        this.holdApplied = false;
    }

    public void startReview() {
        transitionTo(MrbStatus.UNDER_REVIEW);
    }

    public void decide(MrbDecision decision, Long decidedBy, String decisionNote) {
        transitionTo(MrbStatus.DECIDED);
        this.decision = decision;
        this.decidedBy = decidedBy;
        this.decisionNote = decisionNote;
        this.decidedAt = LocalDateTime.now();
    }

    public void close() {
        transitionTo(MrbStatus.CLOSED);
        this.closedAt = LocalDateTime.now();
    }

    public void markHoldApplied() {
        this.holdApplied = true;
    }

    public void markHoldReleased() {
        this.holdApplied = false;
    }

    /** 심의 중(RAISED·UNDER_REVIEW)이면 현장은 멈춰 있어야 한다. */
    public boolean isOpen() {
        return this.status == MrbStatus.RAISED || this.status == MrbStatus.UNDER_REVIEW;
    }

    private void transitionTo(MrbStatus next) {
        if (!this.status.canTransitionTo(next)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "허용되지 않은 MRB 상태 전이입니다: " + this.status + " -> " + next);
        }
        this.status = next;
    }
}
