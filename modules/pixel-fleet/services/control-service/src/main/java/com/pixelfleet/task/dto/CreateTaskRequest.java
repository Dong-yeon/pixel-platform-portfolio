package com.pixelfleet.task.dto;

import com.pixelfleet.task.domain.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTaskRequest(
        @NotBlank String taskCode,
        @NotBlank String originNode,
        @NotBlank String destinationNode,
        @NotNull TaskPriority priority
) {
}
