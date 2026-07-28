package com.pixelplatform.core.user.domain;

/**
 * 플랫폼 전역 사용자 역할. 모듈이 {@link User}를 공유하므로 역할도 한 곳에서 정의한다.
 * 모듈마다 실제로 쓰는 역할은 다르며, 각 모듈의 UserDataInitializer가 필요한 것만 시드한다.
 */
public enum UserRole {
    /** 전 모듈 관리자 */
    ADMIN,
    /** 현장 작업자 (공통) */
    OPERATOR,
    /** 품질 검사자 — pixel-factory */
    INSPECTOR,
    /** 배차 담당자 — pixel-fleet */
    DISPATCHER
}
