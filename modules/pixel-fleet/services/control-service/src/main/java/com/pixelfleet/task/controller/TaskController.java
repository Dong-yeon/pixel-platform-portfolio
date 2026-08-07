package com.pixelfleet.task.controller;

import com.pixelfleet.order.service.OrderCodeGenerator;
import com.pixelfleet.order.service.OrderService;
import com.pixelfleet.order.service.OrderService.StepSpec;
import com.pixelfleet.task.dto.CreateTaskRequest;
import com.pixelfleet.task.dto.TaskResponse;
import com.pixelplatform.core.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <b>호환 어댑터</b> — 예전 작업(출발→도착) REST를 새 주문 엔진 위에 얹는다.
 *
 * <p>P19-1은 엔진 교체 단계라 소비자(WMS·대시보드)가 무변경으로 돌아야 한다.
 * create는 "싣고 → 내리는" 2스텝 주문으로 변환된다. order_code는 fleet이 자체 발급하고
 * (P19 나머지 작업 — {@link OrderCodeGenerator}), WMS가 보낸 taskCode는 external_id
 * 자리에 실린다. 완료/실패 통지는 external_id를 우선하므로(notificationCode()) WMS
 * 쪽 계약은 그대로 유지된다. P19-2에서 M4형 주문 API로 옮기면 이 컨트롤러는 삭제.
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final OrderService orderService;
    private final OrderCodeGenerator orderCodeGenerator;

    public TaskController(OrderService orderService, OrderCodeGenerator orderCodeGenerator) {
        this.orderService = orderService;
        this.orderCodeGenerator = orderCodeGenerator;
    }

    @GetMapping
    public ApiResponse<List<TaskResponse>> list() {
        List<TaskResponse> tasks = orderService.findRecent().stream()
                .map(TaskResponse::from)
                .toList();
        return ApiResponse.ok(tasks);
    }

    @GetMapping("/{id}")
    public ApiResponse<TaskResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(TaskResponse.from(orderService.getById(id)));
    }

    @PostMapping
    public ApiResponse<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
        TaskResponse created = TaskResponse.from(orderService.create(
                orderCodeGenerator.next(),
                request.taskCode(),   // WMS의 전표 번호 — external_id로만 남는다(통지 열쇠)
                List.of(StepSpec.load(request.originNode()), StepSpec.unload(request.destinationNode())),
                request.priorityValue(),
                true));
        return ApiResponse.ok(created);
    }

    /**
     * Manually trigger one assignment. Dispatch also runs automatically on a fixed delay
     * (see DispatchScheduler / {@code dispatch.*}); this endpoint stays for demos and for
     * when {@code dispatch.enabled=false}.
     */
    @PostMapping("/dispatch")
    public ApiResponse<TaskResponse> dispatch() {
        var assigned = orderService.dispatchOnce();
        return ApiResponse.ok(assigned == null ? null : TaskResponse.from(assigned));
    }
}
