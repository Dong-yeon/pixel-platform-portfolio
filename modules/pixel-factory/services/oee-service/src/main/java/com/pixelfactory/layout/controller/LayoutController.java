package com.pixelfactory.layout.controller;

import com.pixelfactory.layout.dto.LayoutResponse;
import com.pixelfactory.layout.service.LayoutService;
import com.pixelplatform.core.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공장 평면도 조회 — 좌표의 단일 진실 공급원.
 *
 * <p><b>인증이 필요하다</b>(SecurityConfig, {@code anyRequest().authenticated()}). fleet이
 * 기동 시 이걸 읽는데, 예전엔 서비스 간 인증(M2M)이 없어 부득이 permitAll로 열어 뒀었다
 * (평면도가 민감정보는 아니라는 게 근거였다 — 설비 위치·하역 좌표뿐). P16 WP2부터 fleet도
 * QMS→factory·WMS→fleet과 같은 {@code ServiceTokenProvider} 패턴으로 서비스 토큰을 실어
 * 보내므로 이제 닫혀 있다. 게이트웨이는 여전히 안 거친다(프라이빗 네트워크 안에서 모듈
 * 간 직접 호출 — QMS/WMS와 동일한 관례).
 */
@RestController
@RequestMapping("/api/layout")
public class LayoutController {

    private final LayoutService layoutService;

    public LayoutController(LayoutService layoutService) {
        this.layoutService = layoutService;
    }

    @GetMapping
    public ApiResponse<LayoutResponse> get() {
        return ApiResponse.ok(layoutService.get());
    }
}
