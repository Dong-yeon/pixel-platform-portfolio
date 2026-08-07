package com.pixelfleet.order.service;

import com.pixelfleet.order.repository.FleetOrderRepository;
import org.springframework.stereotype.Component;

/**
 * fleet 자체 주문 코드 발급 — DB 시퀀스(V9, {@code fleet_order_code_seq}) 기반.
 *
 * <p>타임스탬프나 UUID 대신 시퀀스를 쓴 이유: 동시 생성에도 충돌이 안 나고(DB가 직렬화),
 * 사람이 읽고 순서를 가늠할 수 있다(포트폴리오 데모라 "명확함 &gt; 영리함"). WMS가 보내는
 * 자기 전표 번호는 이 값과 별개로 {@code externalId}에 그대로 실린다.
 */
@Component
public class OrderCodeGenerator {

    private final FleetOrderRepository fleetOrderRepository;

    public OrderCodeGenerator(FleetOrderRepository fleetOrderRepository) {
        this.fleetOrderRepository = fleetOrderRepository;
    }

    public String next() {
        return "FO-" + String.format("%08d", fleetOrderRepository.nextOrderCodeSeq());
    }
}
