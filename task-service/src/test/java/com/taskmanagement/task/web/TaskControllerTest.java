package com.taskmanagement.task.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import com.taskmanagement.task.config.SecurityConfig;
import com.taskmanagement.task.domain.TaskStatus;
import com.taskmanagement.task.dto.TaskRequest;
import com.taskmanagement.task.dto.TaskResponse;
import com.taskmanagement.task.exception.GlobalExceptionHandler;
import com.taskmanagement.task.exception.TaskNotFoundException;
import com.taskmanagement.task.service.TaskService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@WebFluxTest(TaskController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, TaskControllerTest.JwtPropsConfig.class})
class TaskControllerTest {

    @TestConfiguration
    static class JwtPropsConfig {
        @Bean
        com.taskmanagement.task.config.JwtProperties jwtProperties() {
            return new com.taskmanagement.task.config.JwtProperties("http://auth-service",
                    "http://localhost:0/.well-known/jwks.json");
        }
    }

    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDate DUE = LocalDate.of(2030, 1, 15);

    @Autowired
    private WebTestClient client;

    @MockitoBean
    private TaskService taskService;
    @MockitoBean
    private ReactiveJwtDecoder jwtDecoder;

    private WebTestClient authed() {
        return client.mutateWith(mockJwt().jwt(jwt -> jwt.subject(USER_ID.toString())));
    }

    private static TaskResponse sample(UUID id) {
        return new TaskResponse(id, "Title", "Desc", TaskStatus.TODO, DUE, Instant.now(), Instant.now());
    }

    @Test
    void withoutToken_returns401() {
        client.get().uri("/tasks").exchange().expectStatus().isUnauthorized();
        client.post().uri("/tasks").contentType(MediaType.APPLICATION_JSON).bodyValue("{}")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void create_usesUserIdFromToken_andReturns201() {
        UUID id = UUID.randomUUID();
        TaskRequest request = new TaskRequest("Title", "Desc", TaskStatus.TODO, DUE);
        when(taskService.create(USER_ID, request)).thenReturn(Mono.just(sample(id)));

        authed().post().uri("/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo(id.toString())
                .jsonPath("$.userId").doesNotExist();
    }

    @Test
    void create_returns400OnInvalidPayload() {
        authed().post().uri("/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"title\":\"\",\"status\":\"TODO\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.details.length()").isEqualTo(2);
    }

    @Test
    void create_returns400OnUnknownStatus() {
        authed().post().uri("/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"title\":\"t\",\"status\":\"WHATEVER\",\"dueDate\":\"2030-01-15\"}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void list_passesFiltersAndPagination() {
        when(taskService.list(USER_ID, TaskStatus.DONE, 2, 10)).thenReturn(Flux.just(sample(UUID.randomUUID())));

        authed().get().uri("/tasks?status=DONE&page=2&size=10")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1);
    }

    @Test
    void list_rejectsOutOfRangeSize() {
        authed().get().uri("/tasks?size=1000").exchange().expectStatus().isBadRequest();
    }

    @Test
    void get_returns404WhenNotOwnedOrMissing() {
        UUID id = UUID.randomUUID();
        when(taskService.get(eq(USER_ID), eq(id))).thenReturn(Mono.error(new TaskNotFoundException(id)));

        authed().get().uri("/tasks/{id}", id).exchange().expectStatus().isNotFound();
    }

    @Test
    void get_returns400OnMalformedId() {
        authed().get().uri("/tasks/not-a-uuid").exchange().expectStatus().isBadRequest();
    }

    @Test
    void update_returnsUpdatedTask() {
        UUID id = UUID.randomUUID();
        TaskRequest request = new TaskRequest("Title", "Desc", TaskStatus.DONE, DUE);
        when(taskService.update(eq(USER_ID), eq(id), any(TaskRequest.class))).thenReturn(Mono.just(sample(id)));

        authed().put().uri("/tasks/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void delete_returns204() {
        UUID id = UUID.randomUUID();
        when(taskService.delete(USER_ID, id)).thenReturn(Mono.empty());

        authed().delete().uri("/tasks/{id}", id).exchange().expectStatus().isNoContent();
    }
}
