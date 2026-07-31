package com.pixelqms.mrb.service;

import com.pixelqms.factory.FactoryQualityClient;
import com.pixelqms.mrb.domain.MrbReview;
import com.pixelqms.mrb.domain.MrbStatus;
import com.pixelqms.mrb.dto.MrbCreateRequest;
import com.pixelqms.mrb.dto.MrbDecisionRequest;
import com.pixelqms.mrb.dto.MrbResponse;
import com.pixelqms.mrb.repository.MrbReviewRepository;
import com.pixelqms.ncr.domain.Nonconformance;
import com.pixelqms.ncr.repository.NonconformanceRepository;
import com.pixelqms.notification.NotificationProperties;
import com.pixelqms.notification.NotificationSender;
import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelplatform.core.common.exception.ErrorCode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MRB 심의 — <b>컴포저블 아키텍처가 화면에 드러나는 지점</b>.
 *
 * <p>심의를 열면 factory의 설비가 QUALITY_HOLD로 바뀌어 지도가 주황이 되고, 판정하면 풀려
 * 색이 돌아온다. 별개 서비스·별개 DB가 REST 계약만으로 연동된다.
 *
 * <p>factory가 꺼져 있어도 심의는 진행된다 — 홀드는 "요청했지만 반영 안 됨"으로 남는다.
 */
@Service
@Transactional(readOnly = true)
public class MrbService {

    private static final Logger log = LoggerFactory.getLogger(MrbService.class);
    private static final List<MrbStatus> OPEN_STATUSES = List.of(MrbStatus.RAISED, MrbStatus.UNDER_REVIEW);

    private final MrbReviewRepository mrbRepository;
    private final NonconformanceRepository ncrRepository;
    private final FactoryQualityClient factoryQualityClient;
    private final NotificationSender notificationSender;
    private final NotificationProperties notificationProperties;

    public MrbService(
            MrbReviewRepository mrbRepository,
            NonconformanceRepository ncrRepository,
            FactoryQualityClient factoryQualityClient,
            NotificationSender notificationSender,
            NotificationProperties notificationProperties
    ) {
        this.mrbRepository = mrbRepository;
        this.ncrRepository = ncrRepository;
        this.factoryQualityClient = factoryQualityClient;
        this.notificationSender = notificationSender;
        this.notificationProperties = notificationProperties;
    }

    public List<MrbResponse> getReviews() {
        return mrbRepository.findByOrderByIdDesc().stream().map(MrbResponse::from).toList();
    }

    /** 지도의 "품질관리실" 대기 건수 배지. */
    public long countOpen() {
        return mrbRepository.countByStatusIn(OPEN_STATUSES);
    }

    public List<MrbResponse> getOpenReviews() {
        return mrbRepository.findByStatusInOrderByIdDesc(OPEN_STATUSES).stream().map(MrbResponse::from).toList();
    }

    /**
     * 심의 개시 — 현장을 멈추고 품질팀에 메일을 보낸다.
     */
    @Transactional
    public MrbResponse raise(MrbCreateRequest request) {
        Nonconformance ncr = ncrRepository.findById(request.nonconformanceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "부적합을 찾을 수 없습니다. id=" + request.nonconformanceId()));

        String mrbNo = "MRB-" + ncr.getNcrNo().replace("NCR-", "");
        if (mrbRepository.existsByMrbNo(mrbNo)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "이미 심의가 열려 있습니다: " + mrbNo);
        }

        MrbReview mrb = mrbRepository.save(new MrbReview(
                mrbNo, ncr.getId(), ncr.getEquipmentCode(), ncr.getWorkOrderNo(), ncr.getLotNo()));

        // 심의가 열렸으니 현장을 멈춘다. factory가 없으면 반영되지 않은 채로 남는다.
        boolean held = factoryQualityClient.hold(
                ncr.getEquipmentCode(), ncr.getWorkOrderNo(),
                "MRB 심의 중: " + mrbNo, mrbNo);
        if (held) {
            mrb.markHoldApplied();
        } else {
            log.warn("factory에 홀드를 반영하지 못했습니다: {} (심의는 계속 진행)", mrbNo);
        }

        notifyQualityTeam(mrb, ncr);
        return MrbResponse.from(mrb);
    }

    @Transactional
    public MrbResponse startReview(Long id) {
        MrbReview mrb = requireMrb(id);
        mrb.startReview();
        return MrbResponse.from(mrb);
    }

    /**
     * 판정 — 현장을 다시 돌린다.
     *
     * <p>판정 내용과 무관하게 홀드는 푼다. 폐기·반품이어도 그 설비를 계속 세워 둘 이유는 없다
     * (후속 조치는 작업지시 쪽 이야기다).
     */
    @Transactional
    public MrbResponse decide(Long id, MrbDecisionRequest request, Long decidedBy) {
        MrbReview mrb = requireMrb(id);
        mrb.decide(request.decision(), decidedBy, request.decisionNote());

        if (Boolean.TRUE.equals(mrb.getHoldApplied())) {
            boolean released = factoryQualityClient.release(
                    mrb.getEquipmentCode(), mrb.getWorkOrderNo(),
                    request.decision().name(), mrb.getMrbNo());
            if (released) {
                mrb.markHoldReleased();
            } else {
                log.warn("factory 홀드 해제를 반영하지 못했습니다: {}", mrb.getMrbNo());
            }
        }

        return MrbResponse.from(mrb);
    }

    @Transactional
    public MrbResponse close(Long id) {
        MrbReview mrb = requireMrb(id);
        mrb.close();
        return MrbResponse.from(mrb);
    }

    private MrbReview requireMrb(Long id) {
        return mrbRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "심의를 찾을 수 없습니다. id=" + id));
    }

    /** MRB 등록 시 자동 발송 — 실제 SMTP가 아니라 발송함에 쌓인다(대시보드가 메일 카드로 보여준다). */
    private void notifyQualityTeam(MrbReview mrb, Nonconformance ncr) {
        String subject = "[MRB] " + (ncr.getLotNo() != null ? ncr.getLotNo() : ncr.getNcrNo()) + " 부적합 심의 요청";
        String body = """
                설비: %s
                작업지시: %s
                로트: %s
                불량 수량: %d EA
                부적합: %s
                심의번호: %s

                심의가 열려 해당 설비를 품질 홀드했습니다. 판정 후 자동으로 해제됩니다.
                """.formatted(
                nullToDash(ncr.getEquipmentCode()),
                nullToDash(ncr.getWorkOrderNo()),
                nullToDash(ncr.getLotNo()),
                ncr.getDefectQty(),
                ncr.getNcrNo(),
                mrb.getMrbNo());

        notificationSender.send(notificationProperties.getQualityTeamAddress(), subject, body, mrb.getMrbNo());
    }

    private String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
