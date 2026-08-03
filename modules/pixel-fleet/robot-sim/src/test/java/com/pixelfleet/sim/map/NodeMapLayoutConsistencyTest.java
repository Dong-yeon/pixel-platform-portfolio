package com.pixelfleet.sim.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * robot-sim의 좌표가 서버 마스터와 어긋나면 <b>빌드를 깨뜨린다.</b>
 *
 * <p><b>왜 robot-sim은 서버에서 좌표를 받지 않는가.</b> 시뮬레이터는 물리 세계를 흉내내는
 * 쪽이다. 실제 설비·로봇은 서버가 알려주는 대로 자기 위치를 바꾸지 않는다. 그래서 여기는
 * 자기 좌표를 그대로 갖고, 대신 서버 마스터와 <b>같은지 검사</b>한다 — 런타임 의존을 만들지
 * 않으면서 조용한 불일치를 막는 방법이다.
 *
 * <p>대조 기준은 pixel-factory의 <b>최신</b> 평면도 마이그레이션 SQL이다. DB 시드가 곧 마스터의
 * 정의이고, 실행 중인 서버 없이 읽을 수 있다. 평면도를 다시 그리는 마이그레이션을 추가하면
 * (V7 → V9처럼) <b>이 경로도 함께 옮겨야 한다</b> — 안 그러면 옛 시드와 대조하며 조용히 통과한다.
 *
 * <p>파일을 못 찾으면 <b>실패</b>시킨다(건너뛰지 않는다). 경로가 바뀌었는데 테스트가 조용히
 * 통과하면 이 검사가 있으나 마나이기 때문이다.
 */
class NodeMapLayoutConsistencyTest {

    /** robot-sim 모듈 디렉터리 기준 상대 경로(Gradle 테스트의 작업 디렉터리). */
    private static final Path MIGRATION = Path.of(
            "..", "..", "pixel-factory", "services", "oee-service",
            "src", "main", "resources", "db", "migration", "V12__elevator_and_charging_zone.sql");

    /** ('WH-DOCK-1', '1번 충전 도크', 'DOCK', 4, 6, now(), now()) */
    private static final Pattern NODE_ROW = Pattern.compile(
            "\\('([A-Z0-9-]+)',\\s*'[^']*',\\s*'([A-Z_]+)',\\s*([0-9.]+),\\s*([0-9.]+)");

    /** values (1, 44, 24, 8.5, 15.5, now(), now()) */
    private static final Pattern SETTINGS_ROW = Pattern.compile(
            "values\\s*\\(1,\\s*([0-9.]+),\\s*([0-9.]+),\\s*([0-9.]+),\\s*([0-9.]+)");

    private static Map<String, double[]> masterNodes;
    private static double masterWidth;
    private static double masterHeight;
    private static double masterUpperAisleY;
    private static double masterLowerAisleY;

    private final NodeMap nodeMap = new NodeMap();

    @BeforeAll
    static void parseMaster() throws IOException {
        if (!Files.exists(MIGRATION)) {
            fail("서버 평면도 마이그레이션을 찾을 수 없다: " + MIGRATION.toAbsolutePath()
                    + "\n파일이 이동·개명됐다면 이 테스트의 경로를 고칠 것. "
                    + "건너뛰면 좌표 불일치를 놓친다.");
        }

        String sql = Files.readString(MIGRATION);

        masterNodes = new HashMap<>();
        Matcher nodes = NODE_ROW.matcher(sql);
        while (nodes.find()) {
            masterNodes.put(nodes.group(1),
                    new double[]{Double.parseDouble(nodes.group(3)), Double.parseDouble(nodes.group(4))});
        }

        Matcher settings = SETTINGS_ROW.matcher(sql);
        if (!settings.find()) {
            fail("layout_settings 시드를 파싱하지 못했다 — 마이그레이션 형식이 바뀌었나? " + MIGRATION);
        }
        masterWidth = Double.parseDouble(settings.group(1));
        masterHeight = Double.parseDouble(settings.group(2));
        masterUpperAisleY = Double.parseDouble(settings.group(3));
        masterLowerAisleY = Double.parseDouble(settings.group(4));
    }

    @Test
    @DisplayName("마이그레이션 파싱 자체가 성공했는지 — 정규식이 헛돌면 이 테스트 전체가 무의미하다")
    void masterParsedSomething() {
        // 노드 수를 박아 두지 않는 이유: 설비를 늘리면 노드도 늘어난다. 다만 0건이면
        // 정규식이 안 맞은 것이므로, 그때는 아래 좌표 검사들이 모두 공허하게 통과한다.
        assertThat(masterNodes).as("마이그레이션에서 파싱된 노드").isNotEmpty();
        assertThat(masterWidth).isPositive();
        assertThat(masterHeight).isPositive();
    }

    @Test
    @DisplayName("서버 마스터의 모든 노드가 NodeMap에 같은 좌표로 있어야 한다")
    void everyMasterNodeMatches() {
        for (Map.Entry<String, double[]> entry : masterNodes.entrySet()) {
            String code = entry.getKey();
            double[] expected = entry.getValue();
            double[] actual = nodeMap.resolve(code);

            assertThat(actual)
                    .as("노드 %s 좌표 (서버 마스터=%s, robot-sim=%s). "
                            + "어긋나면 배차 거리 비교와 화면 표시가 조용히 틀어진다.",
                            code, java.util.Arrays.toString(expected), java.util.Arrays.toString(actual))
                    .containsExactly(expected[0], expected[1]);
        }
    }

    @Test
    @DisplayName("NodeMap에만 있는 유령 노드가 없어야 한다")
    void noExtraNodesInSimulator() {
        assertThat(nodeMap.knownNodeCodes())
                .as("robot-sim이 아는 노드는 서버 마스터의 부분집합이어야 한다")
                .isSubsetOf(masterNodes.keySet());
    }

    @Test
    @DisplayName("평면도 크기가 서버 마스터와 같아야 한다 — 미지 노드 해시 폴백이 이 값으로 좌표를 만든다")
    void mapExtentMatches() {
        assertThat(NodeMap.MAX_X).isEqualTo(masterWidth);
        assertThat(NodeMap.MAX_Y).isEqualTo(masterHeight);
    }

    @Test
    @DisplayName("통로 y가 서버 마스터와 같아야 한다 — 다르면 그린 선과 실제 주행이 갈린다")
    void aisleYMatches() {
        assertThat(NodeMap.UPPER_AISLE_Y).isEqualTo(masterUpperAisleY);
        assertThat(NodeMap.LOWER_AISLE_Y).isEqualTo(masterLowerAisleY);
    }
}
