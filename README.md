# Reactive Task Management System

Reactive microservices built with **Java 21**, **Spring Boot 3.5** and **Spring WebFlux**.
Authenticated users register, log in and manage their own tasks through a secure REST API.

| Service        | Port | Responsibility                                        | Database  |
|----------------|------|-------------------------------------------------------|-----------|
| `auth-service` | 8081 | Registration, login, JWT (RS256) issuing, public JWKS | `auth_db` |
| `task-service` | 8082 | Task CRUD, scoped to the user identified by the JWT   | `task_db` |

```
            register / login                      Bearer <JWT>
  client ---------------------> auth-service      client ------------> task-service
                                  |  signs (private key)                 |  verifies (public key)
                                  v                                      v
                               auth_db        <-- JWKS (public keys) --  task_db
                         (one PostgreSQL container, two databases)
```

**Stack:** Spring WebFlux, Spring Security (OAuth2 resource server), Spring Data R2DBC, PostgreSQL 16,
springdoc-openapi, JUnit 5 / Mockito / Reactor `StepVerifier`, Karate 1.4, Docker Compose.

## Quick start

Prerequisites: **Docker with Compose**. (Java 21 and Maven 3.9+ are only needed to run the tests or the
services outside Docker.)

```bash
docker compose up --build
```

The first build downloads all Maven dependencies and takes a few minutes; later builds are cached.
Startup is health-check ordered: `postgres` -> `auth-service` -> `task-service`.

| What                  | URL / address                                  |
|-----------------------|------------------------------------------------|
| auth-service API      | <http://localhost:8081>                        |
| auth-service Swagger  | <http://localhost:8081/swagger-ui.html>        |
| task-service API      | <http://localhost:8082>                        |
| task-service Swagger  | <http://localhost:8082/swagger-ui.html>        |
| PostgreSQL (host)     | `localhost:5433` (user `postgres` / `postgres`) |

PostgreSQL is published on host port **5433**, not 5432, so it does not clash with a Postgres you may
already run locally. Containers talk to each other on the internal network and are unaffected.

Stop everything with `docker compose down`; add `-v` to also delete the database volume.

### Running without Docker for the services

Requires Java 21, Maven 3.9+ and a PostgreSQL initialised with `docker/postgres/init.sql`
(simplest: `docker compose up -d postgres`, which listens on 5433).

```bash
mvn clean package

export AUTH_DB_URL=r2dbc:postgresql://localhost:5433/auth_db
export TASK_DB_URL=r2dbc:postgresql://localhost:5433/task_db

java -jar auth-service/target/auth-service-1.0.0.jar     # terminal 1
java -jar task-service/target/task-service-1.0.0.jar     # terminal 2
```

### Configuration

Every setting has a local-development default and can be overridden with environment variables.

| Variable           | Service | Default                                          | Purpose                                         |
|--------------------|---------|--------------------------------------------------|-------------------------------------------------|
| `SERVER_PORT`      | both    | `8081` (auth) / `8082` (task)                    | HTTP port                                       |
| `AUTH_DB_URL`      | auth    | `r2dbc:postgresql://localhost:5432/auth_db`      | R2DBC URL                                       |
| `AUTH_DB_USER`     | auth    | `auth_user`                                      | Database user                                   |
| `AUTH_DB_PASSWORD` | auth    | `auth_pass`                                      | Database password                               |
| `TASK_DB_URL`      | task    | `r2dbc:postgresql://localhost:5432/task_db`      | R2DBC URL                                       |
| `TASK_DB_USER`     | task    | `task_user`                                      | Database user                                   |
| `TASK_DB_PASSWORD` | task    | `task_pass`                                      | Database password                               |
| `JWT_ISSUER`       | both    | `http://auth-service`                            | `iss` claim; **must be identical in both**      |
| `JWT_EXPIRATION`   | auth    | `1h`                                             | Token lifetime                                  |
| `JWT_PRIVATE_KEY`  | auth    | empty (ephemeral key)                            | PEM, PKCS#8 RSA private key                     |
| `JWT_PUBLIC_KEY`   | auth    | empty (ephemeral key)                            | PEM, X.509 RSA public key                       |
| `AUTH_JWKS_URI`    | task    | `http://localhost:8081/.well-known/jwks.json`    | Where task-service fetches the public keys     |

## Testing

```bash
# Unit tests - no infrastructure needed
mvn test

# Integration tests (Karate) - require the system to be running
docker compose up -d --build
mvn verify -Pintegration -pl karate-tests
# other hosts: -Dauth.url=http://host:8081 -Dtask.url=http://host:8082
```

The Karate module is skipped unless the `integration` profile is active, so a plain `mvn test` or
`mvn package` never needs the system to be up.

**Unit tests (32)** - JUnit 5, Mockito, `StepVerifier`, `WebTestClient`:

- `auth-service` (13): `AuthService` (hashing, e-mail normalisation, duplicate e-mail, duplicate-key
  race, wrong password, unknown user), `JwtService` (RS256 signature verifiable with the public key,
  claims, expiry) and `AuthController` (status codes, validation, JWKS exposes no private material).
- `task-service` (19): `TaskService` (owner taken from the token, every operation scoped by user,
  another user's task reported as not found) and `TaskController` (401 without token, user id taken
  from the JWT subject, validation, pagination limits, malformed ids).

**Integration tests (21 Karate scenarios)** in `karate-tests/src/test/resources/features`:

- `auth.feature` - register, login, RS256 header and claims, case-insensitive e-mail, duplicate e-mail
  (409), validation (400), wrong password / unknown user (401), JWKS.
- `tasks.feature` - create/read/update/delete lifecycle, newest-first ordering, status filter,
  pagination, payload validation, malformed id, page-size limit.
- `isolation.feature` - no token, garbage token and tampered token (401); user B cannot read, update,
  delete or list user A's tasks (404); the owner cannot be forged through the request body.

Every scenario registers fresh users with random e-mails, so the suite can be re-run against the same
database without cleanup.

## API

Interactive OpenAPI documentation is served by each service (links in the table above). In the
task-service Swagger UI click **Authorize** and paste the token returned by `/auth/login`.
The raw OpenAPI document is at `/v3/api-docs`.

### auth-service

| Method | Path                     | Description                         | Success | Errors   |
|--------|--------------------------|-------------------------------------|---------|----------|
| POST   | `/auth/register`         | Create a user                       | 201     | 400, 409 |
| POST   | `/auth/login`            | Exchange credentials for a JWT      | 200     | 400, 401 |
| GET    | `/.well-known/jwks.json` | Public keys used to verify the JWTs | 200     |          |

Register: `{ "email": "...", "password": "..." }` - valid e-mail, password 8-72 characters.
Login response: `{ "accessToken": "...", "tokenType": "Bearer", "expiresIn": 3600 }`.

The token is an RS256 JWT with claims `iss`, `sub` (user id), `email`, `iat` and `exp`.

### task-service (all endpoints require `Authorization: Bearer <token>`)

| Method | Path          | Description                                                   | Success | Errors        |
|--------|---------------|---------------------------------------------------------------|---------|---------------|
| POST   | `/tasks`      | Create a task                                                 | 201     | 400, 401      |
| GET    | `/tasks`      | List own tasks, newest first. Query: `status`, `page`, `size` | 200     | 400, 401      |
| GET    | `/tasks/{id}` | Get one task                                                  | 200     | 400, 401, 404 |
| PUT    | `/tasks/{id}` | Replace a task                                                | 200     | 400, 401, 404 |
| DELETE | `/tasks/{id}` | Delete a task                                                 | 204     | 401, 404      |

Task fields: `title` (required, max 150), `description` (optional, max 2000),
`status` (`TODO` | `IN_PROGRESS` | `DONE`, required), `dueDate` (`yyyy-MM-dd`, required).
Responses also include `id`, `createdAt` and `updatedAt`; the owner id is never exposed.
`page` defaults to 0 and `size` to 20 (allowed range 1-100).

Errors from both services share one shape:
`{ "timestamp", "status", "error", "message", "details": [] }`.

### Example

```bash
curl -X POST localhost:8081/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"jane@example.com","password":"S3cret-pass!"}'

TOKEN=$(curl -s -X POST localhost:8081/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"jane@example.com","password":"S3cret-pass!"}' | jq -r .accessToken)

curl -X POST localhost:8082/tasks -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Write README","description":"Document it","status":"TODO","dueDate":"2030-01-15"}'

curl "localhost:8082/tasks?status=TODO&page=0&size=10" -H "Authorization: Bearer $TOKEN"
```

## Troubleshooting

- **`port is already allocated`** - another process uses 8081, 8082 or 5433. Stop it, or change the host
  side of the mapping in `docker-compose.yml`.
- **Karate tests fail with connection refused** - the system is not up yet. Check `docker compose ps`
  until the three services are `healthy`.
- **`401` from task-service right after restarting auth-service** - the ephemeral signing key changed;
  log in again to get a new token.
- **Start from a clean database** - `docker compose down -v`. The init script only runs on an empty volume.

## Project layout

```
.
├── pom.xml                       parent POM (Spring Boot 3.5, Java 21)
├── auth-service/                 controller -> service -> repository, Dockerfile
├── task-service/                 controller -> service -> repository, Dockerfile
├── karate-tests/                 Karate integration tests (profile: integration)
├── docker/postgres/              Dockerfile + init.sql (databases and roles)
├── docker-compose.yml
├── .dockerignore
└── .gitignore
```
