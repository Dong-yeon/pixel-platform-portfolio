package com.pixelfleet.sim.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 창고동 1층 렉 860기가 전부 커넥터(스파인)·아이슬에서 ≥1.75 떨어져 있는지 자동 검증한다
 * (P32 — 이전까지 이 규칙은 마이그레이션 주석에만 있던 수기 관례였다, 설계 근거:
 * docs/p32-warehouse-realistic-relayout-design.md 3절 "커넥터 클리어런스(≥1.75) 검증").
 * 864가 아니라 860인 이유: V23(D9)이 충전존과 겹치던 4기(밴드12 열37~40)를 지웠다 —
 * 그 4기는 V22 텍스트엔 남아 있지만 DB엔 없으므로 파싱 후 걷어낸다.
 *
 * <p>1.75 = 로봇 반지름(0.95) + 렉 반폭(0.8, {@code orientation='VD'} 발자국 1.6의 절반) —
 * V12 주석이 원래 밝힌 산식과 같다. 두 방향을 각각 검사한다:
 * <ul>
 *   <li>세로(y) — 렉이 자기 밴드의 아이슬(가로 통로, y = 4.0 + (밴드-1)×5.7)에서 충분히
 *       떨어졌는가. 안 그러면 그 통로를 지나는 AGV와 겹친다.
 *   <li>가로(x) — 렉이 좌·우 스파인(x=2 / x=52, 로봇이 수직으로 오가는 길)에서 충분히
 *       떨어졌는가.
 * </ul>
 *
 * <p>좌표는 {@link RackMap}이 안 갖고 있으므로(코드만 안다, P21 D2) factory V22 마이그레이션
 * SQL을 직접 파싱한다 — {@link NodeMapLayoutConsistencyTest}·{@link RackMapLayoutConsistencyTest}
 * 와 같은 방식이다.
 */
class RackClearanceTest {

    private static final Path MIGRATION = Path.of(
            "..", "..", "pixel-factory", "services", "oee-service",
            "src", "main", "resources", "db", "migration", "V22__warehouse_band_relayout.sql");

    private static final double MIN_CLEARANCE = 1.75;
    /** 부동소수 오차 허용(예: 9.7-7.95는 수학적으로 1.75지만 double로는 1.7499999999999991) —
     * 실제 설계 여유가 아니라 표현 오차이므로, 유닛 축척(미터급)에 비해 무의미하게 작다. */
    private static final double EPSILON = 1e-6;
    private static final double LEFT_SPINE_X = 2.0;
    private static final double RIGHT_SPINE_X = 52.0;
    private static final double BAND1_AISLE_Y = 4.0;
    private static final double BAND_PITCH = 5.7;

    /** ('WH-1F-B01-R01', 'WH', 1, 3.75, 2.25, 'VD', 1, 3, 45, now(), now()) */
    private static final Pattern RACK_ROW = Pattern.compile(
            "\\('WH-1F-B(\\d{2})-R(\\d{2})',\\s*'WH',\\s*1,\\s*([0-9.]+),\\s*([0-9.]+),\\s*'VD'");

    /** V23(D9)이 지운 4기 — 충전존(CZ-1F)과 좌표상 겹쳐서 제외됐다. */
    private static final int EXCLUDED_BAND = 12;
    private static final java.util.Set<Integer> EXCLUDED_COLS = java.util.Set.of(37, 38, 39, 40);

    private record RackPoint(int band, double x, double y) {
    }

    private static List<RackPoint> racks;

    @BeforeAll
    static void parseRacks() throws IOException {
        if (!Files.exists(MIGRATION)) {
            fail("서버 평면도 마이그레이션을 찾을 수 없다: " + MIGRATION.toAbsolutePath());
        }
        String sql = Files.readString(MIGRATION);
        int racksStart = sql.indexOf("insert into layout_racks");
        if (racksStart < 0) {
            fail(MIGRATION.getFileName() + "에서 'insert into layout_racks' 구문을 찾지 못했다");
        }

        racks = new ArrayList<>();
        Matcher rows = RACK_ROW.matcher(sql.substring(racksStart));
        while (rows.find()) {
            int band = Integer.parseInt(rows.group(1));
            int col = Integer.parseInt(rows.group(2));
            if (band == EXCLUDED_BAND && EXCLUDED_COLS.contains(col)) {
                continue; // V23이 DELETE했다 — V22 텍스트엔 남아 있지만 DB엔 없다.
            }
            racks.add(new RackPoint(band,
                    Double.parseDouble(rows.group(3)),
                    Double.parseDouble(rows.group(4))));
        }
    }

    @Test
    @DisplayName("마이그레이션 파싱 자체가 성공했는지 — 860기가 다 잡혔는지")
    void parsedAllRacks() {
        assertThat(racks).as("V22에서 파싱된 밴드 렉(V23 D9 제외분 반영)").hasSize(860);
    }

    @Test
    @DisplayName("모든 렉이 자기 밴드 아이슬(y)에서 ≥1.75 떨어져 있어야 한다")
    void clearsOwnBandAisle() {
        for (RackPoint r : racks) {
            double aisleY = BAND1_AISLE_Y + (r.band() - 1) * BAND_PITCH;
            double clearance = Math.abs(r.y() - aisleY);
            assertThat(clearance)
                    .as("밴드%02d 렉(%s, %s) — 아이슬 y=%s과의 거리", r.band(), r.x(), r.y(), aisleY)
                    .isGreaterThanOrEqualTo(MIN_CLEARANCE - EPSILON);
        }
    }

    @Test
    @DisplayName("모든 렉이 좌·우 스파인(x)에서 ≥1.75 떨어져 있어야 한다")
    void clearsBothSpines() {
        for (RackPoint r : racks) {
            assertThat(r.x() - LEFT_SPINE_X)
                    .as("렉(%s, %s) — 좌측 스파인(x=%s)과의 거리", r.x(), r.y(), LEFT_SPINE_X)
                    .isGreaterThanOrEqualTo(MIN_CLEARANCE - EPSILON);
            assertThat(RIGHT_SPINE_X - r.x())
                    .as("렉(%s, %s) — 우측 스파인(x=%s)과의 거리", r.x(), r.y(), RIGHT_SPINE_X)
                    .isGreaterThanOrEqualTo(MIN_CLEARANCE - EPSILON);
        }
    }
}
