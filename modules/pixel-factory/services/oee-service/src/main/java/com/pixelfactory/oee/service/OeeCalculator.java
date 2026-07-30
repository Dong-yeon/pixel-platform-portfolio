package com.pixelfactory.oee.service;

import com.pixelfactory.oee.domain.OeeInput;
import com.pixelfactory.oee.domain.OeeResult;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * OEE = Availability × Performance × Quality.
 *
 * <pre>
 *   A = 실가동시간 / 계획가동시간
 *   P = (표준CT × 총생산수) / 실가동시간
 *   Q = 양품수 / 총생산수
 * </pre>
 *
 * <p>순수 함수다 — DB도 시계도 보지 않는다. 그래서 손계산 케이스를 그대로 테스트할 수 있다.
 */
@Component
public class OeeCalculator {

    public OeeResult calculate(OeeInput input) {
        Duration planned = input.plannedTime();
        Duration operating = input.operatingTime();

        // 계획가동시간이 0이면 A의 분모가 없다 = 평가할 대상이 아니다(시프트 밖 등).
        // 0으로 나눠 NaN/Infinity 를 흘려보내면 화면 끝까지 따라간다.
        if (planned.isZero()) {
            return OeeResult.notApplicable(planned, operating);
        }

        double availability = (double) operating.getSeconds() / planned.getSeconds();

        // 실가동이 0이면 P는 정의되지 않는다(분모 0). 아무것도 안 돌았으니 0으로 본다.
        // 이때 생산수가 0이 아니면 데이터가 모순이지만, 여기서 판단하지 않고 0을 준다.
        double performance = operating.isZero()
                ? 0.0
                : (double) (input.idealCycleTimeMs() * input.producedQty()) / operating.toMillis();

        // 생산이 없으면 Q도 정의되지 않는다. 불량률 0%가 아니라 "평가 불가"라 0으로 둔다.
        double quality = input.producedQty() == 0
                ? 0.0
                : (double) input.goodQty() / input.producedQty();

        // P > 1.0 은 **자르지 않는다.** 표준CT가 실제보다 크게 잡혀 있다는 신호이고,
        // 1.0으로 클램프하면 그 신호가 사라져 잘못된 마스터가 영원히 방치된다.
        boolean performanceAnomaly = performance > 1.0;

        return new OeeResult(
                availability,
                performance,
                quality,
                availability * performance * quality,
                performanceAnomaly,
                planned,
                operating,
                input.producedQty(),
                input.defectQty()
        );
    }
}
