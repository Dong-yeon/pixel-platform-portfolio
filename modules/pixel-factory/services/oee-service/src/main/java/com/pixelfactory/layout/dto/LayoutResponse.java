package com.pixelfactory.layout.dto;

import com.pixelfactory.layout.domain.LayoutBuilding;
import com.pixelfactory.layout.domain.LayoutChargingZone;
import com.pixelfactory.layout.domain.LayoutEdge;
import com.pixelfactory.layout.domain.LayoutElevator;
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
        List<Rack> racks,
        List<Elevator> elevators,
        List<ChargingZone> chargingZones,
        List<Edge> edges
) {

    /**
     * @param floorNo 몇 층의 자리인가. 위층 노드는 아래층과 <b>좌표가 겹치므로</b>
     *                층을 함께 보지 않으면 구분할 수 없다.
     */
    public record Node(String nodeCode, String name, LayoutNodeType nodeType,
                       double posX, double posY, String buildingCode, int floorNo) {

        public static Node from(LayoutNode node) {
            return new Node(node.getNodeCode(), node.getName(), node.getNodeType(),
                    node.getPosX(), node.getPosY(), node.getBuildingCode(), node.getFloorNo());
        }
    }

    /**
     * 두 노드 사이의 연결 (P20) — <b>정적 토폴로지만</b> 담는다. 지금 막혀 있는가 같은
     * 동적 사실은 fleet의 라이브 상태다(설계 근거: {@code docs/p20-layout-routing-design.md} D4).
     */
    public record Edge(String fromNode, String toNode, double baseCost, boolean bidirectional) {

        public static Edge from(LayoutEdge edge) {
            return new Edge(edge.getFromNode(), edge.getToNode(), edge.getBaseCost(), edge.getBidirectional());
        }
    }

    /**
     * 화물 엘리베이터 — <b>물건만</b> 오르내린다. AMR은 자기 층에 머물며 승강장에서 싣고 내린다.
     * 샤프트가 수직으로 관통하므로 층이 달라도 같은 자리에 그린다.
     */
    public record Elevator(String elevatorCode, String buildingCode, String name,
                           double posX, double posY, List<Integer> servesFloors) {

        public static Elevator from(LayoutElevator elevator) {
            List<Integer> floors = java.util.Arrays.stream(elevator.getServesFloors().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Integer::valueOf)
                    .toList();
            return new Elevator(elevator.getElevatorCode(), elevator.getBuildingCode(), elevator.getName(),
                    elevator.getPosX(), elevator.getPosY(), floors);
        }
    }

    /** 충전존 — 충전 베이(DOCK 노드)들을 감싸는 구역. 렉을 비워 둔 자리다. */
    public record ChargingZone(String zoneCode, String buildingCode, int floorNo, String name,
                               double posX, double posY, double width, double height) {

        public static ChargingZone from(LayoutChargingZone zone) {
            return new ChargingZone(zone.getZoneCode(), zone.getBuildingCode(), zone.getFloorNo(),
                    zone.getName(), zone.getPosX(), zone.getPosY(), zone.getWidth(), zone.getHeight());
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
            List<LayoutRack> racks,
            List<LayoutElevator> elevators,
            List<LayoutChargingZone> chargingZones,
            List<LayoutEdge> edges
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
                racks.stream().map(Rack::from).toList(),
                elevators.stream().map(Elevator::from).toList(),
                chargingZones.stream().map(ChargingZone::from).toList(),
                edges.stream().map(Edge::from).toList()
        );
    }
}
