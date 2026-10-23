package com.taskmanagement.task.service;

import com.taskmanagement.task.domain.Task;
import com.taskmanagement.task.domain.TaskStatus;
import com.taskmanagement.task.dto.TaskRequest;
import com.taskmanagement.task.dto.TaskResponse;
import com.taskmanagement.task.exception.TaskNotFoundException;
import com.taskmanagement.task.repository.TaskRepository;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * All operations receive the authenticated user's id and never trust any
 * user identifier coming from the request payload.
 */
@Service
public class TaskService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository repository;

    public TaskService(TaskRepository repository) {
        this.repository = repository;
    }

    public Mono<TaskResponse> create(UUID userId, TaskRequest request) {
        LOGGER.info("Starting to create a new task for the user");
        Task task = Task.create(userId, request.title(), request.description(), request.status(),
                request.dueDate());
        Mono<TaskResponse> savedTask = repository.save(task).map(TaskResponse::from);
        LOGGER.info("Successfully created a new task for the user");
        return savedTask;
    }

    public Flux<TaskResponse> list(UUID userId, TaskStatus status, int page, int size) {
        LOGGER.info("Starting to retrieve a list of tasks with status: {} with page: {} and size: {}", status, page, size);
        PageRequest pageable = PageRequest.of(page, size);
        Flux<Task> tasks = status == null
                ? repository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                : repository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status, pageable);
        Flux<TaskResponse> taskList = tasks.map(TaskResponse::from);
        LOGGER.info("Successfully listed all tasks for the user");
        return taskList;
    }

    public Mono<TaskResponse> get(UUID userId, UUID taskId) {
        LOGGER.info("Starting to retrieve a task for the user");
        Mono<TaskResponse> foundTask = findOwned(userId, taskId).map(TaskResponse::from);
        LOGGER.info("Successfully retrieved a task for the user");
        return foundTask;
    }

    public Mono<TaskResponse> update(UUID userId, UUID taskId, TaskRequest request) {
        LOGGER.info("Starting to update a task for the user");
        Mono<TaskResponse> updatedTask = findOwned(userId, taskId)
                .doOnNext(task -> task.update(request.title(), request.description(), request.status(),
                        request.dueDate()))
                .flatMap(repository::save)
                .map(TaskResponse::from);
        LOGGER.info("Successfully updated a task for the user");
        return updatedTask;
    }

    public Mono<Void> delete(UUID userId, UUID taskId) {
        LOGGER.info("Starting to delete a task for the user");
        Mono<Void> deletedTask = repository.deleteByIdAndUserId(taskId, userId)
                .filter(deleted -> deleted > 0)
                .switchIfEmpty(Mono.defer(() -> {
                    LOGGER.warn("An error occurred during the deletion of a task");
                    return Mono.error(new TaskNotFoundException(taskId));
                }))
                .then();
        LOGGER.info("Successfully delete a task for the user");
        return deletedTask;
    }

    private Mono<Task> findOwned(UUID userId, UUID taskId) {
        return repository.findByIdAndUserId(taskId, userId)
                .switchIfEmpty(Mono.error(new TaskNotFoundException(taskId)));
    }
}
