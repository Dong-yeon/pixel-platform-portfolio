package com.pixelfleet.task.controller;

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
 * create는 "싣고 → 내리는" 2스텝 주문으로 변환되며, taskCode가 order_code와
 * external_id를 겸한다(완료 통지 계약 유지). P19-2에서 M4형 주문 API로 옮기면 삭제.
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final OrderService orderService;

    public TaskController(OrderService orderService) {
        this.orderService = orderService;
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
                request.taskCode(),
                request.taskCode(),   // 외부에서 낸 코드가 곧 통지 열쇠다
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
