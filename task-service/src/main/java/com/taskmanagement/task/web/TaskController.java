package com.taskmanagement.task.web;

import com.taskmanagement.task.domain.TaskStatus;
import com.taskmanagement.task.dto.TaskRequest;
import com.taskmanagement.task.dto.TaskResponse;
import com.taskmanagement.task.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/tasks")
@Tag(name = "Tasks")
@SecurityRequirement(name = "bearerAuth")
public class TaskController {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskController.class);

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a task for the authenticated user")
    public Mono<TaskResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TaskRequest request) {
        LOGGER.info("Starting the creation of a new task");
        return taskService.create(userId(jwt), request);
    }

    @GetMapping
    @Operation(summary = "List the authenticated user's tasks (newest first)")
    public Flux<TaskResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        LOGGER.info("Starting to list the current created tasks for the user");
        return taskService.list(userId(jwt), status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one of the authenticated user's tasks")
    public Mono<TaskResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        LOGGER.info("Starting to retrieve a specific task of a user");
        return taskService.get(userId(jwt), id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Replace one of the authenticated user's tasks")
    public Mono<TaskResponse> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                     @Valid @RequestBody TaskRequest request) {
        LOGGER.info("Starting to update a specific task for a user");
        return taskService.update(userId(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete one of the authenticated user's tasks")
    public Mono<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        LOGGER.info("Starting to delete a specific task for a user");
        return taskService.delete(userId(jwt), id);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
