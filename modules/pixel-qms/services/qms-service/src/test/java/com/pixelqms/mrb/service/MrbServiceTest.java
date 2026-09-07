package com.pixelqms.mrb.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pixelplatform.core.common.exception.BusinessException;
import com.pixelqms.factory.FactoryQualityClient;
import com.pixelqms.mrb.domain.MrbDecision;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * MRB 심의(MrbService) 회귀 테스트 — factory와의 REST 왕복(홀드/해제)이 컴포저블
 * 아키텍처의 핵심이라 "factory가 없어도/실패해도 QMS는 계속 돈다"와 상태머신 불변식을
 * 집중적으로 본다. {@link FactoryQualityClient}와 리포지토리는 mock, {@link MrbReview}는
 * 실제 도메인 객체를 써서 진짜 상태 전이를 태운다({@code OrderServiceRegressionTest}와
 * 같은 방침 — I/O 경계만 자르고 오케스트레이션·상태머신은 실제 코드로 돈다).
 */
class MrbServiceTest {

    private MrbReviewRepository mrbRepository;
    private NonconformanceRepository ncrRepository;
    private FactoryQualityClient factoryQualityClient;
    private NotificationSender notificationSender;
    private MrbService mrbService;

    @BeforeEach
    void setUp() {
        mrbRepository = mock(MrbReviewRepository.class);
        ncrRepository = mock(NonconformanceRepository.class);
        factoryQualityClient = mock(FactoryQualityClient.class);
        notificationSender = mock(NotificationSender.class);
        NotificationProperties notificationProperties = new NotificationProperties(); // 기본값 그대로

        when(mrbRepository.save(any(MrbReview.class))).thenAnswer(inv -> inv.getArgument(0));

        mrbService = new MrbService(
                mrbRepository, ncrRepository, factoryQualityClient, notificationSender, notificationProperties);
    }

    private static Nonconformance ncr(String ncrNo) {
        return new Nonconformance(ncrNo, 1L, 1L, "CNC-01", "WO-1", "LOT-1", 5, "치수 불량");
    }

    /**
     * <b>MrbService.raise() 88~95행의 핵심 방어 로직.</b> factory에 홀드 요청이
     * 실패(꺼져 있음/네트워크 오류 등으로 {@code hold()}가 false)해도 MRB 심의 생성
     * 자체는 절대 막히면 안 된다 — "factory가 없다고 심의를 못 여는 것은 말이 안 된다"는
     * 설계 의도의 회귀 가드. 홀드 미반영 상태(holdApplied=false)로 남을 뿐, 저장과
     * 품질팀 통지는 정상적으로 진행돼야 한다.
     */
    @Test
    void raise는_factory_홀드_반영에_실패해도_MRB_심의_생성을_막지_않는다() {
        Nonconformance ncr = ncr("NCR-2026-001");
        when(ncrRepository.findById(10L)).thenReturn(Optional.of(ncr));
        when(mrbRepository.existsByMrbNo("MRB-2026-001")).thenReturn(false);
        when(factoryQualityClient.hold(anyString(), anyString(), anyString(), anyString())).thenReturn(false);

        MrbResponse response = mrbService.raise(new MrbCreateRequest(10L));

        assertThat(response.mrbNo()).isEqualTo("MRB-2026-001");
        assertThat(response.holdApplied()).isFalse(); // 반영은 안 됐지만
        assertThat(response.status()).isEqualTo(MrbStatus.RAISED); // 심의 자체는 정상 생성.
        verify(notificationSender).send(anyString(), anyString(), anyString(), eq("MRB-2026-001"));
    }

    /** 존재하지 않는 부적합(NCR)을 참조하면 심의를 열 수 없다. */
    @Test
    void raise는_존재하지_않는_부적합을_참조하면_거절된다() {
        when(ncrRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mrbService.raise(new MrbCreateRequest(999L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("부적합을 찾을 수 없습니다");

        verify(factoryQualityClient, never()).hold(any(), any(), any(), any());
    }

    /**
     * 같은 NCR로 이미 심의가 열려 있으면(mrbNo 중복) 새로 열 수 없다 — 한 부적합에
     * 심의가 중복으로 쌓이면 안 된다. 이 검사가 factory 홀드 요청보다 먼저 일어나므로
     * 중복 요청 시 현장에 불필요한 홀드가 다시 걸리지도 않아야 한다.
     */
    @Test
    void raise는_동일_NCR로_이미_심의가_열려있으면_거절되고_factory에_홀드를_요청하지_않는다() {
        Nonconformance ncr = ncr("NCR-2026-002");
        when(ncrRepository.findById(20L)).thenReturn(Optional.of(ncr));
        when(mrbRepository.existsByMrbNo("MRB-2026-002")).thenReturn(true);

        assertThatThrownBy(() -> mrbService.raise(new MrbCreateRequest(20L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 심의가 열려");

        verify(mrbRepository, never()).save(any());
        verify(factoryQualityClient, never()).hold(any(), any(), any(), any());
    }

    /**
     * 판정(decide)은 결과와 무관하게 현장을 다시 돌리는 것이 목적이다(MrbService 111행
     * 주석). factory에 홀드가 실제로 반영돼 있던 심의만 해제를 요청해야 하고 —
     * factory가 꺼져 있어 애초에 반영이 안 됐던 심의는 해제할 것이 없으니 요청 자체를
     * 보내면 안 된다(불필요한 API 호출/오해의 소지).
     */
    @Test
    void decide는_홀드가_반영됐던_심의만_판정_결과와_무관하게_factory_해제를_요청한다() {
        // 케이스 A: 홀드가 실제로 반영됐던 심의 — 폐기(SCRAP) 판정이어도 해제를 요청해야 한다.
        MrbReview heldMrb = new MrbReview("MRB-HELD", 1L, "CNC-01", "WO-1", "LOT-1");
        heldMrb.markHoldApplied();
        heldMrb.startReview();
        ReflectionTestUtils.setField(heldMrb, "id", 1L);
        when(mrbRepository.findById(1L)).thenReturn(Optional.of(heldMrb));
        when(factoryQualityClient.release(anyString(), anyString(), anyString(), anyString())).thenReturn(true);

        mrbService.decide(1L, new MrbDecisionRequest(MrbDecision.SCRAP, "폐기 처리"), 42L);

        verify(factoryQualityClient).release("CNC-01", "WO-1", "SCRAP", "MRB-HELD");
        assertThat(heldMrb.getHoldApplied()).isFalse(); // 해제 반영 확인 후 플래그도 내려간다.

        // 케이스 B: factory가 꺼져 있어 애초에 홀드가 반영되지 않았던 심의 — 해제 요청 자체가 없어야 한다.
        MrbReview unheldMrb = new MrbReview("MRB-UNHELD", 2L, "CNC-02", "WO-2", "LOT-2");
        unheldMrb.startReview();
        ReflectionTestUtils.setField(unheldMrb, "id", 2L);
        when(mrbRepository.findById(2L)).thenReturn(Optional.of(unheldMrb));

        mrbService.decide(2L, new MrbDecisionRequest(MrbDecision.USE_AS_IS, "특채"), 42L);

        verify(factoryQualityClient, never()).release(eq("CNC-02"), any(), any(), any());
    }

    /**
     * MrbStatus 상태머신(RAISED→UNDER_REVIEW→DECIDED→CLOSED, 되돌아가는 전이 없음)의
     * 회귀 가드. 이미 DECIDED로 끝난 심의를 다시 판정하려 하면(중복 클릭·재시도 등)
     * 조용히 성공하는 게 아니라 명시적으로 거절돼야 한다 — 그래야 판정 이력이 뒤집히지
     * 않는다("심의는 기록이라 취소가 아니라 판정으로 끝낸다", MrbStatus 주석).
     */
    @Test
    void decide는_이미_판정된_MRB를_다시_판정하면_거절된다() {
        MrbReview mrb = new MrbReview("MRB-DUP", 3L, "CNC-03", "WO-3", "LOT-3");
        mrb.startReview();
        ReflectionTestUtils.setField(mrb, "id", 3L);
        when(mrbRepository.findById(3L)).thenReturn(Optional.of(mrb));

        mrbService.decide(3L, new MrbDecisionRequest(MrbDecision.REWORK, "1차 판정"), 1L);
        assertThat(mrb.getStatus()).isEqualTo(MrbStatus.DECIDED);

        assertThatThrownBy(() -> mrbService.decide(3L, new MrbDecisionRequest(MrbDecision.SCRAP, "재판정 시도"), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("허용되지 않은 MRB 상태 전이");

        assertThat(mrb.getDecision()).isEqualTo(MrbDecision.REWORK); // 첫 판정 그대로 — 뒤집히지 않는다.
    }
}
