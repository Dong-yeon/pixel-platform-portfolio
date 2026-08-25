package com.pixelwms.stock.repository;

import com.pixelwms.stock.domain.Pallet;
import com.pixelwms.stock.domain.PalletStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PalletRepository extends JpaRepository<Pallet, Long> {

    Optional<Pallet> findByPltCode(String pltCode);

    List<Pallet> findByLocationIdAndStatus(Long locationId, PalletStatus status);

    /** 도너 파렛트 탐색(P26 D5) — 로케이션 전체에서 그 상태인 것들. */
    List<Pallet> findByStatus(PalletStatus status);

    /** 슬롯 점유 카운트(D7) — RETIRED가 아니면(LOADED든 IN_TRANSIT든) 자리를 차지한다. */
    long countByLocationIdAndStatusNot(Long locationId, PalletStatus status);

    /** 신규 파렛트 코드 채번용 시퀀스(V7). PalletCodeGenerator가 포맷을 입힌다. */
    @Query(value = "select nextval('pallet_code_seq')", nativeQuery = true)
    long nextPalletCodeSeq();
}
