package com.pixelfactory.oee.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pixelfactory.oee.domain.OeeInput;
import com.pixelfactory.oee.domain.OeeResult;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 계산기는 순수 함수라 DB 없이 검증한다.
 *
 * <p>기준 케이스는 로드맵(docs/pixel-platform-roadmap.md P9)의 손계산 값이다.
 */
class OeeCalculatorTest {

    private final OeeCalculator calculator = new OeeCalculator();

    @Test
    @DisplayName("손계산 케이스: 계획 450분 / 실가동 403분 / 표준CT 1분 / 생산 373 / 불량 12")
    void matchesHandCalculation() {
        OeeResult result = calculator.calculate(new OeeInput(
                Duration.ofMinutes(450),
                Duration.ofMinutes(403),
                Duration.ofMinutes(1).toMillis(),
                373,
                12
        ));

        // A = 403/450, P = (1분 × 373)/403분, Q = 361/373
        assertThat(result.availability()).isCloseTo(0.896, within(0.0005));
        assertThat(result.performance()).isCloseTo(0.926, within(0.0005));
        assertThat(result.quality()).isCloseTo(0.968, within(0.0005));
        assertThat(result.oee()).isCloseTo(0.802, within(0.0005));
        assertThat(result.performanceAnomaly()).isFalse();
    }

    @Test
    @DisplayName("P > 1.0 이면 값을 자르지 않고 플래그를 세운다 — 표준CT가 틀렸다는 신호를 숨기면 안 된다")
    void doesNotClampPerformanceAboveOne() {
        // 표준CT 2분인데 실가동 100분에 60개를 냈다 → 필요 시간 120분 > 실가동 100분
        OeeResult result = calculator.calculate(new OeeInput(
                Duration.ofMinutes(120),
                Duration.ofMinutes(100),
                Duration.ofMinutes(2).toMillis(),
                60,
                0
        ));

        assertThat(result.performance()).isCloseTo(1.2, within(0.0001));
        assertThat(result.performance()).isGreaterThan(1.0);
        assertThat(result.performanceAnomaly()).isTrue();
    }

    @Test
    @DisplayName("계획가동시간이 0이면 평가 대상이 아니다 — NaN/Infinity를 흘려보내지 않는다")
    void zeroPlannedTimeIsNotApplicable() {
        OeeResult result = calculator.calculate(new OeeInput(
                Duration.ZERO, Duration.ZERO, 30_000, 0, 0));

        assertThat(result.availability()).isZero();
        assertThat(result.oee()).isZero();
        assertThat(Double.isNaN(result.oee())).isFalse();
        assertThat(Double.isInfinite(result.oee())).isFalse();
    }

    @Test
    @DisplayName("실가동 0 · 생산 0 이어도 NaN이 나오지 않는다")
    void zeroOperatingAndProducedStaysFinite() {
        OeeResult result = calculator.calculate(new OeeInput(
                Duration.ofMinutes(480), Duration.ZERO, 30_000, 0, 0));

        assertThat(result.availability()).isZero();
        assertThat(result.performance()).isZero();
        assertThat(result.quality()).isZero();
        assertThat(Double.isNaN(result.oee())).isFalse();
    }

    @Test
    @DisplayName("불량수가 총생산수를 넘으면 입력에서 막는다")
    void rejectsDefectsExceedingProduced() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new OeeInput(Duration.ofMinutes(100), Duration.ofMinutes(90), 30_000, 10, 11));
    }

    private static org.assertj.core.data.Offset<Double> within(double offset) {
        return org.assertj.core.data.Offset.offset(offset);
    }
}
