package com.pixelfleet.sim.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * robot-sim이 아는 렉 코드가 factory 마스터(layout_racks)와 어긋나면 <b>빌드를 깨뜨린다</b>
 * (P21) — {@link NodeMapLayoutConsistencyTest}와 같은 이유·같은 방식이다.
 *
 * <p>여기서는 좌표까지는 대조하지 않는다. {@link RackMap}은 <b>코드 집합만</b> 안다 — 랙
 * 피더가 렉으로 가는 좌표는 관제 서버가 계산해 GOTO에 실어 보내고(design doc D2·D3),
 * robot-sim은 "이 목적지가 렉인가"만 판정해 취출 타이머를 돈다(Simulator 참고). 코드가
 * 하나라도 어긋나면 그 렉으로 가는 랙 피더 주문이 조용히 "일반 노드"로 취급돼 취출 대기
 * 없이 즉시 완료된다 — 그래서 코드 집합의 일치는 반드시 지켜야 한다.
 */
class RackMapLayoutConsistencyTest {

    private static final Path MIGRATION = Path.of(
            "..", "..", "pixel-factory", "services", "oee-service",
            "src", "main", "resources", "db", "migration", "V12__elevator_and_charging_zone.sql");

    /** ('WH-1F-R01', 'WH', 1,  7.0,  4.0, 'V', 4, 5, 200, now(), now()) */
    private static final Pattern RACK_ROW = Pattern.compile(
            "\\('([A-Z0-9-]+)',\\s*'[A-Z]+',\\s*([0-9]+)");

    private static Set<String> masterRackCodes;

    private final RackMap rackMap = new RackMap();

    @BeforeAll
    static void parseMaster() throws IOException {
        if (!Files.exists(MIGRATION)) {
            fail("서버 평면도 마이그레이션을 찾을 수 없다: " + MIGRATION.toAbsolutePath()
                    + "\n파일이 이동·개명됐다면 이 테스트의 경로를 고칠 것. 건너뛰면 렉 불일치를 놓친다.");
        }

        String sql = Files.readString(MIGRATION);
        // layout_racks INSERT문 이후만 본다 — 같은 (code, building, floor) 모양의 행이
        // layout_charging_zones(CZ-1F 등)에도 있어서, 파일 전체에 적용하면 충전존 코드까지
        // 렉으로 잘못 집힌다.
        int racksStart = sql.indexOf("insert into layout_racks");
        if (racksStart < 0) {
            fail("V12에서 'insert into layout_racks' 구문을 찾지 못했다 — 마이그레이션 형식이 바뀌었나?");
        }
        String racksSection = sql.substring(racksStart);

        masterRackCodes = new HashSet<>();
        Matcher rows = RACK_ROW.matcher(racksSection);
        while (rows.find()) {
            masterRackCodes.add(rows.group(1));
        }
    }

    @Test
    @DisplayName("마이그레이션 파싱 자체가 성공했는지 — 정규식이 헛돌면 아래 검사들이 모두 공허하게 통과한다")
    void masterParsedSomething() {
        assertThat(masterRackCodes).as("마이그레이션에서 파싱된 렉 코드").isNotEmpty();
    }

    @Test
    @DisplayName("서버 마스터의 모든 렉이 RackMap에 있어야 한다")
    void everyMasterRackKnown() {
        assertThat(rackMap.knownRackCodes())
                .as("robot-sim이 아는 렉은 서버 마스터를 전부 포함해야 한다")
                .containsAll(masterRackCodes);
    }

    @Test
    @DisplayName("RackMap에만 있는 유령 렉이 없어야 한다")
    void noExtraRacksInSimulator() {
        assertThat(rackMap.knownRackCodes())
                .as("robot-sim이 아는 렉은 서버 마스터의 부분집합이어야 한다")
                .isSubsetOf(masterRackCodes);
    }
}
