package com.pixelfleet.sim.battery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.pixelfleet.sim.config.SimProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 배터리 20~24% 사각지대 회귀 테스트 — <b>실제로 함대 전체가 멈췄던</b> 장애다.
 *
 * <p>배차 최소 배터리(control-service {@code NearestBatteryAwareAssignmentPolicy
 * .MIN_BATTERY_PERCENT})와 충전 시작 기준(robot-sim {@code sim.low-battery-threshold})이
 * <b>서로 다른 두 서비스에</b> 흩어져 있다. 배터리는 MOVING일 때만 닳고 IDLE에서는 그대로라,
 * 이 두 값 사이에 갇힌 로봇은 배차받기엔 낮고 충전 가기엔 높고 스스로 빠져나올 방법이 없다 —
 * 로봇이 하나씩 이 구간에 빠지며 실제로 6대 전부가 멈췄다.
 *
 * <p>불변식: <b>충전 복귀 기준 &gt; 배차 최소 배터리</b>.
 * {@link com.pixelfleet.sim.map.NodeMapLayoutConsistencyTest}와 같은 이유로 런타임
 * 의존(서비스 간 호출)을 만들지 않고 <b>소스를 직접 읽어</b> 대조한다 —
 * 두 서비스가 각자 배포되므로 실행 중인 상대 서비스가 없어도 이 검사는 성립해야 한다.
 *
 * <p>기준 파일을 못 찾으면 <b>실패</b>시킨다(건너뛰지 않는다) — 경로가 바뀌었는데 조용히
 * 통과하면 이 검사가 있으나 마나다.
 */
class BatteryDeadZoneInvariantTest {

    /** robot-sim 모듈 디렉터리 기준 상대 경로(Gradle 테스트의 작업 디렉터리). */
    private static final Path ASSIGNMENT_POLICY = Path.of(
            "..", "services", "control-service",
            "src", "main", "java", "com", "pixelfleet", "task", "dispatch",
            "NearestBatteryAwareAssignmentPolicy.java");

    /** {@code static final int MIN_BATTERY_PERCENT = 25;} */
    private static final Pattern MIN_BATTERY_FIELD = Pattern.compile(
            "MIN_BATTERY_PERCENT\\s*=\\s*(\\d+)");

    private static int dispatchMinBatteryPercent;

    @BeforeAll
    static void parseDispatchThreshold() throws IOException {
        if (!Files.exists(ASSIGNMENT_POLICY)) {
            fail("control-service의 배차 정책 파일을 찾을 수 없다: " + ASSIGNMENT_POLICY.toAbsolutePath()
                    + "\n파일이 이동·개명됐다면 이 테스트의 경로를 고칠 것. "
                    + "건너뛰면 배터리 사각지대 재발을 놓친다.");
        }

        String source = Files.readString(ASSIGNMENT_POLICY);
        Matcher matcher = MIN_BATTERY_FIELD.matcher(source);
        if (!matcher.find()) {
            fail("MIN_BATTERY_PERCENT 필드를 파싱하지 못했다 — 필드명이나 선언 형식이 바뀌었나? "
                    + ASSIGNMENT_POLICY);
        }
        dispatchMinBatteryPercent = Integer.parseInt(matcher.group(1));
    }

    @Test
    @DisplayName("파싱 자체가 성공했는지 — 정규식이 헛돌면 아래 검사가 전부 공허하게 통과한다")
    void dispatchThresholdParsedSomething() {
        assertThat(dispatchMinBatteryPercent).isBetween(1, 99);
    }

    @Test
    @DisplayName("불변식: SimProperties 기본 충전 복귀 기준 > control-service 배차 최소 배터리")
    void defaultLowBatteryThresholdExceedsDispatchMinimum() {
        int chargeReturnThreshold = new SimProperties().getLowBatteryThreshold();

        assertThat(chargeReturnThreshold)
                .as("충전 복귀 기준(%d)이 배차 최소 배터리(%d)보다 높아야 사각지대가 생기지 않는다. "
                        + "낮거나 같으면 20~24%%처럼 배차도 안 되고 충전도 안 가는 구간이 생겨 "
                        + "IDLE 로봇이 스스로 못 빠져나온다(실제로 함대 6대 전부가 이렇게 멈췄다).",
                        chargeReturnThreshold, dispatchMinBatteryPercent)
                .isGreaterThan(dispatchMinBatteryPercent);
    }

    @Test
    @DisplayName("불변식: application.yml에 실제 배포되는 값도 같은 불변식을 지켜야 한다")
    void configuredLowBatteryThresholdExceedsDispatchMinimum() throws IOException {
        Path applicationYml = Path.of("src", "main", "resources", "application.yml");
        if (!Files.exists(applicationYml)) {
            fail("robot-sim application.yml을 찾을 수 없다: " + applicationYml.toAbsolutePath());
        }
        String yml = Files.readString(applicationYml);
        Matcher matcher = Pattern.compile("low-battery-threshold:\\s*(\\d+)").matcher(yml);
        if (!matcher.find()) {
            fail("application.yml에서 low-battery-threshold를 파싱하지 못했다 — 키 이름이 바뀌었나?");
        }
        int configuredThreshold = Integer.parseInt(matcher.group(1));

        assertThat(configuredThreshold)
                .as("기본값 테스트만으론 부족하다 — application.yml이 기본값을 낮게 덮어써도 "
                        + "사각지대가 재현된다. 실제 배포값을 직접 대조해야 한다.")
                .isGreaterThan(dispatchMinBatteryPercent);
    }
}
