package com.pixelfactory.oee.service;

/**
 * 표준 사이클타임 공급자.
 *
 * <p><b>표준CT는 원래 품번(과 공정) 단위다</b> — 같은 설비도 무엇을 깎느냐에 따라 표준이
 * 다르다. 지금은 {@code equipments.ideal_cycle_time_ms} 설비 고정값을 쓰지만(D6),
 * 시그니처에 {@code itemId}를 미리 받아 둔다. P13에서 품목 마스터가 생기면 <b>구현만
 * 갈아끼우면</b> 되고 계산기는 손대지 않는다.
 */
public interface IdealCycleTimeProvider {

    /**
     * @param itemId 품번. 아직 품목 마스터가 없어 {@code null}이 들어온다 —
     *               현재 구현은 무시하고 설비 고정값을 준다.
     */
    long idealCycleTimeMs(Long equipmentId, Long itemId);
}
