package com.pixelfactory.layout.dto;

import com.pixelfactory.layout.domain.LayoutNode;
import com.pixelfactory.layout.domain.LayoutNodeType;
import com.pixelfactory.layout.domain.LayoutSettings;
import java.util.List;

/**
 * 공장 평면도. <b>좌표의 단일 진실 공급원</b>이다.
 *
 * <p>대시보드는 이 응답만으로 지도를 그리고(하드코딩된 좌표 없음), fleet은 노드 좌표를
 * 받아 캐시한다. 설비 좌표는 여기 넣지 않고 {@code EquipmentResponse}에 실어 보낸다 —
 * 설비는 이미 실시간 채널로 흐르므로, 위치를 바꿔도 같은 경로로 함께 갱신된다.
 *
 * @param terminals POP 단말. P12에서 채운다 — 지금은 빈 목록이며, 소비 측이 미리
 *                  이 필드를 다뤄 두면 나중에 서버만 바뀌면 된다.
 */
public record LayoutResponse(
        double width,
        double height,
        double upperAisleY,
        double lowerAisleY,
        List<Node> nodes,
        List<Terminal> terminals
) {

    public record Node(String nodeCode, String name, LayoutNodeType nodeType, double posX, double posY) {

        public static Node from(LayoutNode node) {
            return new Node(node.getNodeCode(), node.getName(), node.getNodeType(),
                    node.getPosX(), node.getPosY());
        }
    }

    /** POP 단말 (P12). */
    public record Terminal(String terminalCode, String name, double posX, double posY) {
    }

    public static LayoutResponse of(LayoutSettings settings, List<LayoutNode> nodes) {
        return new LayoutResponse(
                settings.getWidth(),
                settings.getHeight(),
                settings.getUpperAisleY(),
                settings.getLowerAisleY(),
                nodes.stream().map(Node::from).toList(),
                List.of()
        );
    }
}
