package com.pixelfleet.task.controller;

import com.pixelfleet.common.response.ApiResponse;
import com.pixelfleet.task.dto.CreateTaskRequest;
import com.pixelfleet.task.dto.TaskResponse;
import com.pixelfleet.task.service.TaskService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public ApiResponse<List<TaskResponse>> list() {
        List<TaskResponse> tasks = taskService.findAll().stream()
                .map(TaskResponse::from)
                .toList();
        return ApiResponse.ok(tasks);
    }

    @GetMapping("/{id}")
    public ApiResponse<TaskResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(TaskResponse.from(taskService.getById(id)));
    }

    @PostMapping
    public ApiResponse<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
        TaskResponse created = TaskResponse.from(taskService.create(
                request.taskCode(),
                request.originNode(),
                request.destinationNode(),
                request.priority()));
        return ApiResponse.ok(created);
    }

    /** Manually trigger one dispatch pass (until Phase 2 wires this to a scheduler). */
    @PostMapping("/dispatch")
    public ApiResponse<TaskResponse> dispatch() {
        var assigned = taskService.dispatchOnce();
        return ApiResponse.ok(assigned == null ? null : TaskResponse.from(assigned));
    }
}
