package com.taskmanagement.task.dto;

import com.taskmanagement.task.domain.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Payload for both create and full update (PUT).
 */
public record TaskRequest(
        @NotBlank @Size(max = 150) String title,
        @Size(max = 2000) String description,
        @NotNull TaskStatus status,
        @NotNull LocalDate dueDate) {
}
