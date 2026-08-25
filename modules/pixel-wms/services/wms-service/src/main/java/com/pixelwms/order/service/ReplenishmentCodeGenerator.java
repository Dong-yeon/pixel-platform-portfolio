package com.pixelwms.order.service;

import com.pixelwms.order.repository.ReplenishmentOrderRepository;
import org.springframework.stereotype.Component;

/**
 * 보충 지시 코드 발급 — DB 시퀀스(V8, {@code replenishment_order_seq}) 기반.
 *
 * <p>{@code PalletCodeGenerator}(P23)·fleet {@code OrderCodeGenerator}(P19)와 같은 패턴 —
 * 이 지시는 외부 호출자가 아니라 시스템(안전재고 감지)이 스스로 만들므로 채번도 스스로 한다.
 */
@Component
public class ReplenishmentCodeGenerator {

    private final ReplenishmentOrderRepository repository;

    public ReplenishmentCodeGenerator(ReplenishmentOrderRepository repository) {
        this.repository = repository;
    }

    public String next() {
        return "RPL-" + String.format("%08d", repository.nextOrderSeq());
    }
}
