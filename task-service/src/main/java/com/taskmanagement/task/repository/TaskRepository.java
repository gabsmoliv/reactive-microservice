package com.taskmanagement.task.repository;

import com.taskmanagement.task.domain.Task;
import com.taskmanagement.task.domain.TaskStatus;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Every query is scoped by {@code userId}: ownership is enforced at the data
 * access level, so a task of another user is indistinguishable from a missing one.
 */
public interface TaskRepository extends ReactiveCrudRepository<Task, UUID> {

    Flux<Task> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Flux<Task> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, TaskStatus status, Pageable pageable);

    Mono<Task> findByIdAndUserId(UUID id, UUID userId);

    Mono<Long> deleteByIdAndUserId(UUID id, UUID userId);
}
