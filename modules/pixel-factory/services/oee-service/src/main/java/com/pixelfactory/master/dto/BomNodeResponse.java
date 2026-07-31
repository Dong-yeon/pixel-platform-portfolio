package com.pixelfactory.master.dto;

import com.pixelfactory.master.domain.PartType;
import java.math.BigDecimal;
import java.util.List;

/**
 * BOM 트리 한 노드.
 *
 * <p>트리는 <b>서버가 조립한다</b>. 화면이 평면 목록을 받아 붙이면 부모 판정을 문자열 prefix로
 * 하게 되는데(실 운영 MES에서 그렇게 하다 편집 중 상태가 꼬였다), 여기서는 중첩 구조를 그대로 준다.
 *
 * @param qtyPer 상위 1개를 만들 때 들어가는 수량. 최상위(루트)는 null.
 * @param level  루트가 0.
 */
public record BomNodeResponse(
        String partCode,
        String name,
        PartType partType,
        String unit,
        BigDecimal qtyPer,
        int level,
        List<BomNodeResponse> children
) {
}
