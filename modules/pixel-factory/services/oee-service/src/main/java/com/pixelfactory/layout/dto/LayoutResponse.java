package com.pixelfactory.layout.dto;

import com.pixelfactory.layout.domain.LayoutBuilding;
import com.pixelfactory.layout.domain.LayoutFloor;
import com.pixelfactory.layout.domain.LayoutNode;
import com.pixelfactory.layout.domain.LayoutNodeType;
import com.pixelfactory.layout.domain.LayoutRack;
import com.pixelfactory.layout.domain.LayoutSettings;
import com.pixelfactory.terminal.domain.PopTerminal;
import java.util.List;
import java.util.Map;

/**
 * 공장 평면도. <b>좌표의 단일 진실 공급원</b>이다.
 *
 * <p>대시보드는 이 응답만으로 지도를 그리고(하드코딩된 좌표 없음), fleet은 노드 좌표를
 * 받아 캐시한다. 설비 좌표는 여기 넣지 않고 {@code EquipmentResponse}에 실어 보낸다 —
 * 설비는 이미 실시간 채널로 흐르므로, 위치를 바꿔도 같은 경로로 함께 갱신된다.
 *
 * <p><b>소속은 좌표로 판정한다.</b> 어떤 설비·노드·단말이 어느 건물인지는 별도 필드로 주지
 * 않는다 — 건물은 사각형이고 대상은 점이니, 포함 관계가 곧 소속이다. 필드를 더하면 좌표와
 * 어긋날 수 있는 두 번째 진실이 생긴다.
 *
 * @param upperAisleY 상단 통로 y. <b>계속 내려보낸다</b> — fleet의 통로 일치 검사와
 *                    robot-sim 정합 테스트가 이 값에 의존한다(빼면 두 안전장치가 조용해진다).
 */
public record LayoutResponse(
        double width,
        double height,
        double upperAisleY,
        double lowerAisleY,
        List<Building> buildings,
        List<Node> nodes,
        List<Terminal> terminals,
        List<Rack> racks
) {

    public record Node(String nodeCode, String name, LayoutNodeType nodeType, double posX, double posY) {

        public static Node from(LayoutNode node) {
            return new Node(node.getNodeCode(), node.getName(), node.getNodeType(),
                    node.getPosX(), node.getPosY());
        }
    }

    /** POP 단말 (P12). */
    public record Terminal(String terminalCode, String name, double posX, double posY) {

        public static Terminal from(PopTerminal terminal) {
            return new Terminal(terminal.getTerminalCode(), terminal.getName(),
                    terminal.getPosX(), terminal.getPosY());
        }
    }

    /** 건물 — 생산동 / 창고동(3층) / 품질동. */
    public record Building(
            String buildingCode,
            String name,
            double posX,
            double posY,
            double width,
            double height,
            int floorCount,
            List<Floor> floors
    ) {
    }

    public record Floor(int floorNo, String name) {

        public static Floor from(LayoutFloor floor) {
            return new Floor(floor.getFloorNo(), floor.getName());
        }
    }

    /**
     * 렉(선반). {@code capacityQty}는 만재 수량이고 실제 적재 수량은 WMS가 갖는다 —
     * 소비 측이 {@code rackCode}와 WMS {@code locationCode}를 맞춰 적재율을 낸다.
     */
    public record Rack(
            String rackCode,
            String buildingCode,
            int floorNo,
            double posX,
            double posY,
            String orientation,
            int columnsCount,
            int levelsCount,
            int capacityQty
    ) {

        public static Rack from(LayoutRack rack) {
            return new Rack(rack.getRackCode(), rack.getBuildingCode(), rack.getFloorNo(),
                    rack.getPosX(), rack.getPosY(), rack.getOrientation(),
                    rack.getColumnsCount(), rack.getLevelsCount(), rack.getCapacityQty());
        }
    }

    public static LayoutResponse of(
            LayoutSettings settings,
            List<LayoutBuilding> buildings,
            Map<Long, List<LayoutFloor>> floorsByBuilding,
            List<LayoutNode> nodes,
            List<PopTerminal> terminals,
            List<LayoutRack> racks
    ) {
        return new LayoutResponse(
                settings.getWidth(),
                settings.getHeight(),
                settings.getUpperAisleY(),
                settings.getLowerAisleY(),
                buildings.stream()
                        .map(building -> new Building(
                                building.getBuildingCode(),
                                building.getName(),
                                building.getPosX(),
                                building.getPosY(),
                                building.getWidth(),
                                building.getHeight(),
                                building.getFloorCount(),
                                floorsByBuilding.getOrDefault(building.getId(), List.of())
                                        .stream().map(Floor::from).toList()))
                        .toList(),
                nodes.stream().map(Node::from).toList(),
                terminals.stream().map(Terminal::from).toList(),
                racks.stream().map(Rack::from).toList()
        );
    }
}
