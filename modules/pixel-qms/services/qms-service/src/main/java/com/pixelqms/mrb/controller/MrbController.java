package com.pixelqms.mrb.controller;

import com.pixelqms.auth.CurrentUserProvider;
import com.pixelqms.mrb.dto.MrbCreateRequest;
import com.pixelqms.mrb.dto.MrbDecisionRequest;
import com.pixelqms.mrb.dto.MrbResponse;
import com.pixelqms.mrb.service.MrbService;
import com.pixelplatform.core.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MRB 심의. 개시·판정은 품질 담당(INSPECTOR/ADMIN)만 — 현장을 멈추고 다시 돌리는 행위다.
 */
@RestController
@RequestMapping("/api/mrb")
public class MrbController {

    private final MrbService mrbService;
    private final CurrentUserProvider currentUserProvider;

    public MrbController(MrbService mrbService, CurrentUserProvider currentUserProvider) {
        this.mrbService = mrbService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public ApiResponse<List<MrbResponse>> getReviews() {
        return ApiResponse.ok(mrbService.getReviews());
    }

    /** 지도의 품질관리실 배지 — 열려 있는 심의 건수. */
    @GetMapping("/open")
    public ApiResponse<Map<String, Object>> getOpen() {
        return ApiResponse.ok(Map.of(
                "count", mrbService.countOpen(),
                "reviews", mrbService.getOpenReviews()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('INSPECTOR', 'ADMIN')")
    public ApiResponse<MrbResponse> raise(@Valid @RequestBody MrbCreateRequest request) {
        return ApiResponse.ok(mrbService.raise(request));
    }

    @PostMapping("/{id}/start-review")
    @PreAuthorize("hasAnyRole('INSPECTOR', 'ADMIN')")
    public ApiResponse<MrbResponse> startReview(@PathVariable Long id) {
        return ApiResponse.ok(mrbService.startReview(id));
    }

    @PostMapping("/{id}/decide")
    @PreAuthorize("hasAnyRole('INSPECTOR', 'ADMIN')")
    public ApiResponse<MrbResponse> decide(@PathVariable Long id, @Valid @RequestBody MrbDecisionRequest request) {
        return ApiResponse.ok(mrbService.decide(id, request, currentUserProvider.currentUserIdOrNull()));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('INSPECTOR', 'ADMIN')")
    public ApiResponse<MrbResponse> close(@PathVariable Long id) {
        return ApiResponse.ok(mrbService.close(id));
    }
}
