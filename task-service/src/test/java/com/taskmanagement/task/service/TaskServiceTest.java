package com.taskmanagement.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.taskmanagement.task.domain.Task;
import com.taskmanagement.task.domain.TaskStatus;
import com.taskmanagement.task.dto.TaskRequest;
import com.taskmanagement.task.exception.TaskNotFoundException;
import com.taskmanagement.task.repository.TaskRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID OTHER_USER = UUID.randomUUID();
    private static final LocalDate DUE = LocalDate.of(2030, 1, 15);

    @Mock
    private TaskRepository repository;
    @InjectMocks
    private TaskService service;

    private static Task existingTask(UUID id, UUID owner) {
        Task task = Task.create(owner, "Old title", "Old description", TaskStatus.TODO, DUE);
        task.setId(id);
        return task;
    }

    @Test
    void create_assignsAuthenticatedUserAsOwner() {
        when(repository.save(any(Task.class))).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return Mono.just(t);
        });

        StepVerifier.create(service.create(OWNER, new TaskRequest("Write docs", "desc", TaskStatus.TODO, DUE)))
                .assertNext(response -> {
                    assertThat(response.id()).isNotNull();
                    assertThat(response.title()).isEqualTo("Write docs");
                    assertThat(response.status()).isEqualTo(TaskStatus.TODO);
                    assertThat(response.dueDate()).isEqualTo(DUE);
                })
                .verifyComplete();

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(OWNER);
    }

    @Test
    void list_withoutStatus_queriesOnlyOwnTasks() {
        Task task = existingTask(UUID.randomUUID(), OWNER);
        when(repository.findByUserIdOrderByCreatedAtDesc(OWNER, PageRequest.of(1, 5))).thenReturn(Flux.just(task));

        StepVerifier.create(service.list(OWNER, null, 1, 5))
                .assertNext(r -> assertThat(r.title()).isEqualTo("Old title"))
                .verifyComplete();

        verify(repository, never()).findAll();
    }

    @Test
    void list_withStatus_filtersByStatusAndOwner() {
        when(repository.findByUserIdAndStatusOrderByCreatedAtDesc(OWNER, TaskStatus.DONE, PageRequest.of(0, 20)))
                .thenReturn(Flux.empty());

        StepVerifier.create(service.list(OWNER, TaskStatus.DONE, 0, 20))
                .verifyComplete();
    }

    @Test
    void get_returnsOwnTask() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndUserId(id, OWNER)).thenReturn(Mono.just(existingTask(id, OWNER)));

        StepVerifier.create(service.get(OWNER, id))
                .assertNext(r -> assertThat(r.id()).isEqualTo(id))
                .verifyComplete();
    }

    @Test
    void get_ofAnotherUsersTask_isReportedAsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndUserId(id, OTHER_USER)).thenReturn(Mono.empty());

        StepVerifier.create(service.get(OTHER_USER, id))
                .expectError(TaskNotFoundException.class)
                .verify();
    }

    @Test
    void update_replacesFieldsAndKeepsOwner() {
        UUID id = UUID.randomUUID();
        Task task = existingTask(id, OWNER);
        when(repository.findByIdAndUserId(id, OWNER)).thenReturn(Mono.just(task));
        when(repository.save(any(Task.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        LocalDate newDue = DUE.plusDays(7);
        StepVerifier.create(service.update(OWNER, id, new TaskRequest("New", "New desc", TaskStatus.DONE, newDue)))
                .assertNext(r -> {
                    assertThat(r.title()).isEqualTo("New");
                    assertThat(r.description()).isEqualTo("New desc");
                    assertThat(r.status()).isEqualTo(TaskStatus.DONE);
                    assertThat(r.dueDate()).isEqualTo(newDue);
                })
                .verifyComplete();

        assertThat(task.getUserId()).isEqualTo(OWNER);
    }

    @Test
    void update_ofAnotherUsersTask_failsWithoutSaving() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndUserId(id, OTHER_USER)).thenReturn(Mono.empty());

        StepVerifier.create(service.update(OTHER_USER, id, new TaskRequest("x", null, TaskStatus.TODO, DUE)))
                .expectError(TaskNotFoundException.class)
                .verify();

        verify(repository, never()).save(any());
    }

    @Test
    void delete_removesOwnTask() {
        UUID id = UUID.randomUUID();
        when(repository.deleteByIdAndUserId(id, OWNER)).thenReturn(Mono.just(1L));

        StepVerifier.create(service.delete(OWNER, id)).verifyComplete();
    }

    @Test
    void delete_ofAnotherUsersTask_isReportedAsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.deleteByIdAndUserId(eq(id), eq(OTHER_USER))).thenReturn(Mono.just(0L));

        StepVerifier.create(service.delete(OTHER_USER, id))
                .expectError(TaskNotFoundException.class)
                .verify();
    }
}
