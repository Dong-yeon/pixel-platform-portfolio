package com.pixelwms.stock.service;

import com.pixelwms.stock.repository.PalletRepository;
import org.springframework.stereotype.Component;

/**
 * WMS 자체 파렛트 코드 발급 — DB 시퀀스(V7, {@code pallet_code_seq}) 기반.
 *
 * <p>fleet의 {@code OrderCodeGenerator}(V9, {@code fleet_order_code_seq})와 같은 패턴 —
 * 타임스탬프·UUID 대신 시퀀스를 써서 동시 생성 충돌을 DB가 직렬화로 막고, 사람이 읽고
 * 순서를 가늠할 수 있게 한다. 실물이라면 바코드 프린터가 QR을 찍어내는 자리이지만,
 * 포트폴리오에는 그 장비가 없으므로 서버가 대신 채번한다.
 */
@Component
public class PalletCodeGenerator {

    private final PalletRepository palletRepository;

    public PalletCodeGenerator(PalletRepository palletRepository) {
        this.palletRepository = palletRepository;
    }

    public String next() {
        return "PLT-" + String.format("%08d", palletRepository.nextPalletCodeSeq());
    }
}
