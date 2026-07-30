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
 * <p><b>이 엔드포인트는 인증 없이 열려 있다</b>(SecurityConfig). 두 가지 이유다.
 * <ol>
 *   <li>평면도는 민감정보가 아니다 — 설비 위치와 하역 지점 좌표뿐이고, 생산 실적이나
 *       사용자 정보는 들어 있지 않다.</li>
 *   <li><b>fleet이 기동 시 이걸 읽어야 한다.</b> 서비스 간 인증(M2M)이 아직 없어서
 *       토큰을 받아올 방법이 없다. 인증을 걸면 fleet은 좌표를 못 받고 폴백만 쓴다.</li>
 * </ol>
 *
 * <p>M2M 인증이 생기면 이 엔드포인트도 닫고 fleet이 서비스 토큰으로 게이트웨이를 경유하게
 * 바꾼다(백로그).
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
