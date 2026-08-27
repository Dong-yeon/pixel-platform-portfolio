package com.pixelfleet.sim.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
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
 * <p>여기서는 좌표까지는 대조하지 않는다. {@link RackMap}은 <b>코드 집합만</b> 안다 — AGV
 * (옛 이름: 랙 피더)가 렉으로 가는 좌표는 관제 서버가 계산해 GOTO에 실어 보내고(design doc
 * D2·D3), robot-sim은 "이 목적지가 렉인가"만 판정해 취출 타이머를 돈다(Simulator 참고). 코드가
 * 하나라도 어긋나면 그 렉으로 가는 AGV 주문이 조용히 "일반 노드"로 취급돼 취출 대기
 * 없이 즉시 완료된다 — 그래서 코드 집합의 일치는 반드시 지켜야 한다.
 *
 * <p><b>마스터가 여러 마이그레이션에 걸쳐 있다.</b> V12가 원본 27기, V19(P28)가 추가
 * 27기, V21(P30)이 4번째 베이 18기, V22(P32)가 창고동 <b>1층만</b> 전면 재발번(864기,
 * {@code WH-1F-B01-R01}~ 형태)했다. V12/V19/V21에 있던 옛 1층 코드({@code WH-1F-R\d+})는
 * V22가 대체했으므로 이 테스트에서 제외한다 — 파일 텍스트 자체는 그대로 남아 있지만
 * (과거 마이그레이션은 안 고친다, P20 이후 관행) DB에는 더 이상 없는 행이다. 2·3층 코드
 * (WH-2F-R*, WH-3F-R*)는 D5(무변경)라 V12/V19/V21에서 그대로 유효하다.
 */
class RackMapLayoutConsistencyTest {

    private static final List<Path> MIGRATIONS = List.of(
            Path.of("..", "..", "pixel-factory", "services", "oee-service",
                    "src", "main", "resources", "db", "migration", "V12__elevator_and_charging_zone.sql"),
            Path.of("..", "..", "pixel-factory", "services", "oee-service",
                    "src", "main", "resources", "db", "migration", "V19__more_warehouse_racks.sql"),
            Path.of("..", "..", "pixel-factory", "services", "oee-service",
                    "src", "main", "resources", "db", "migration", "V21__warehouse_fourth_bay.sql"),
            Path.of("..", "..", "pixel-factory", "services", "oee-service",
                    "src", "main", "resources", "db", "migration", "V22__warehouse_band_relayout.sql"));

    /** ('WH-1F-R01', 'WH', 1,  7.0,  4.0, 'V', 4, 5, 200, now(), now()) */
    private static final Pattern RACK_ROW = Pattern.compile(
            "\\('([A-Z0-9-]+)',\\s*'[A-Z]+',\\s*([0-9]+)");

    /** V22가 대체한 옛 창고동 1층 코드 — V12/V19/V21 텍스트엔 남아 있지만 DB엔 없다. */
    private static final Pattern RETIRED_1F_CODE = Pattern.compile("^WH-1F-R\\d+$");

    private static Set<String> masterRackCodes;

    private final RackMap rackMap = new RackMap();

    @BeforeAll
    static void parseMaster() throws IOException {
        masterRackCodes = new HashSet<>();
        for (Path migration : MIGRATIONS) {
            if (!Files.exists(migration)) {
                fail("서버 평면도 마이그레이션을 찾을 수 없다: " + migration.toAbsolutePath()
                        + "\n파일이 이동·개명됐다면 이 테스트의 경로를 고칠 것. 건너뛰면 렉 불일치를 놓친다.");
            }

            String sql = Files.readString(migration);
            // layout_racks INSERT문 이후만 본다 — 같은 (code, building, floor) 모양의 행이
            // layout_charging_zones(CZ-1F 등)에도 있어서, 파일 전체에 적용하면 충전존 코드까지
            // 렉으로 잘못 집힌다.
            int racksStart = sql.indexOf("insert into layout_racks");
            if (racksStart < 0) {
                fail(migration.getFileName() + "에서 'insert into layout_racks' 구문을 찾지 못했다"
                        + " — 마이그레이션 형식이 바뀌었나?");
            }
            String racksSection = sql.substring(racksStart);

            Matcher rows = RACK_ROW.matcher(racksSection);
            while (rows.find()) {
                masterRackCodes.add(rows.group(1));
            }
        }
        // V22가 지운 옛 1층 코드를 걷어낸다 — 안 그러면 "V22에서 이미 delete된 행"이 여전히
        // 기대 집합에 남아, RackMap과의 대조가 실제 DB 상태와 어긋난 채로 통과해 버린다.
        masterRackCodes.removeIf(code -> RETIRED_1F_CODE.matcher(code).matches());
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
