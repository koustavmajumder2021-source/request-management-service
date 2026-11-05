# Request Management Service

A REST service that manages the lifecycle of requests. Every request moves through a fixed
state machine; each change is validated against it and recorded in an audit log.

```
          ┌──────── delete ────────▶ DELETED
          │
 create ─▶ CREATED ── verify ──▶ VERIFIED ── accept ──▶ ACCEPTED ── publish ──▶ PUBLISHED
                                     │                     │
                                     └────── reject ───────┴──────────────────▶ REJECTED
```

- Name and content are mandatory on creation. Names do not have to be unique and cannot be
  changed; every request is identified by a UUID assigned when it is created.
- Only the content (body) can be edited, and only in `CREATED` and `VERIFIED`.
- Deleting and rejecting require a reason.
- Publishing assigns a unique numeric publication number.
- Every state change is stored in an audit log.
- Requests can be browsed with pagination (default page size 10) and filtered by name and state.

Judgement calls not covered by the specification are documented in [ASSUMPTIONS.md](ASSUMPTIONS.md).

## Tech stack

Java 17, Spring Boot 4.1, Spring Data JPA (Hibernate 7), PostgreSQL / H2,
springdoc-openapi, JUnit, AssertJ, Mockito, JaCoCo.

## Running

Requirements: JDK 17 or newer. Maven is not needed; the wrapper downloads it.

```bash
./mvnw spring-boot:run
```

This starts the service on port 8080 with a file-based H2 database (PostgreSQL mode) in `./data/`,
so requests, ids and publication numbers survive restarts. Delete that folder to start from an empty
database. To run against an existing PostgreSQL database instead:

```bash
DB_URL=jdbc:postgresql://localhost:5432/requests DB_USERNAME=requests DB_PASSWORD=requests \
  ./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```

The values above are the profile's defaults; set the variables to point at any PostgreSQL instance.

API documentation: http://localhost:8080/swagger-ui.html (OpenAPI JSON at `/v3/api-docs`).

## Testing

```bash
./mvnw verify
```

Runs unit and integration tests and writes a coverage report to `target/site/jacoco/index.html`.
The build fails if line coverage drops below 90 % (currently ~99 %).

| Layer | Test | Approach |
|---|---|---|
| Model | `RequestLifecycleTest`, `RequestTest`, `ValueObjectsTest` | Plain JUnit, no Spring. All 36 state/action combinations are checked against the diagram. |
| Service | `RequestServiceImplTest` | Mockito mocks of the repository interfaces: verifies orchestration (save before audit, nothing written on failure) and simulates infrastructure failures such as optimistic-lock conflicts. |
| Repository | `PersistenceAdaptersTest` | `@DataJpaTest` against the schema Hibernate generates, including DB constraints. |
| Controller | `RequestApiIntegrationTest` | Full stack through MockMvc: status codes, validation, paging, audit log. |
| Concurrency | `ConcurrencyIntegrationTest` | Parallel publications get unique numbers; racing transitions on one request have exactly one winner; parallel creations with the same name all succeed with distinct ids. |

## API

Base path: `/api/requests`

| Method | Path | Body | Success | Errors |
|---|---|---|---|---|
| `POST` | `/` | `{"name", "content"}` | `201` | `400` |
| `GET` | `/browse?name=&state=&page=&size=` | | `200` page | `400` |
| `GET` | `/{id}` | | `200` | `400`, `404` |
| `PATCH` | `/{id}` | `{"content"}` | `200` | `400`, `404`, `409` |
| `POST` | `/{id}/verify` | | `200` | `400`, `404`, `409` |
| `POST` | `/{id}/accept` | | `200` | `400`, `404`, `409` |
| `POST` | `/{id}/publish` | | `200` | `400`, `404`, `409` |
| `POST` | `/{id}/reject` | `{"reason"}` | `200` | `400`, `404`, `409` |
| `POST` | `/{id}/delete` | `{"reason"}` | `200` | `400`, `404`, `409` |
| `GET` | `/{id}/audit-log` | | `200` | `400`, `404` |

`{id}` is the request's UUID, returned when the request is created (e.g.
`3f2a9c1e-7b4d-4c1a-9e2f-5c8d0e4a6b17`). Names are not unique, so they are used for browsing, not
for addressing a single request.

`400` invalid input (including an id that is not a UUID) · `404` unknown request · `409` action not
allowed in the current state, content not editable, or a concurrent modification · `422` any other business rule violation
(fallback, so a newly added domain error never surfaces as a `500`).

Errors use RFC 9457 problem details:

```json
{
  "type": "about:blank",
  "title": "Invalid state transition",
  "status": 409,
  "detail": "Cannot publish a request in state CREATED",
  "instance": "/api/requests/3f2a9c1e-7b4d-4c1a-9e2f-5c8d0e4a6b17/publish",
  "currentState": "CREATED",
  "action": "PUBLISH"
}
```

Example:

```bash
ID=$(curl -s -X POST localhost:8080/api/requests -H 'Content-Type: application/json' \
     -d '{"name":"Solar panels","content":"Install 20 panels on the roof"}' | jq -r .id)
curl -s -X POST localhost:8080/api/requests/$ID/verify
curl -s -X POST localhost:8080/api/requests/$ID/accept
curl -s -X POST localhost:8080/api/requests/$ID/publish
curl -s localhost:8080/api/requests/$ID
curl -s localhost:8080/api/requests/$ID/audit-log
curl -s 'localhost:8080/api/requests/browse?name=solar&state=PUBLISHED&page=0&size=10'
```

A request resource includes `allowedActions` and `contentEditable`, so clients can drive their UI
without duplicating the state machine.

## Design

```
              HTTP
                │
 ┌──────────────▼──────────────┐
 │ controller + dto            │  REST endpoints, request/response bodies,
 │                             │  exception → HTTP mapping
 └──────────────┬──────────────┘
 ┌──────────────▼──────────────┐       ┌────────────────────────────┐
 │ service                     │──────▶│ repository (interfaces)    │
 │ RequestService (interface)  │       │ RequestRepository          │
 │ impl.RequestServiceImpl     │       │ StateChangeLog             │
 └──────────────┬──────────────┘       └──────────────▲─────────────┘
 ┌──────────────▼──────────────┐                      │
 │ model + exception           │                      │
 │ Request, RequestLifecycle,  │                      │
 │ StateChange, domain errors, │                      │
 │ PublicationNumberGenerator  │                      │
 └──────────────▲──────────────┘                      │
                │ implements                          │ implements
 ┌──────────────┴─────────────────────────────────────┴─────────┐
 │ repository.jpa (JPA entities, Spring Data, DB sequence)      │
 └──────────────────────────────┬───────────────────────────────┘
                                ▼
                 PostgreSQL / H2  (schema generated by Hibernate)
```

```
src/main/java/com/example/requests
├── controller      REST controller and exception handler
├── dto             request and response bodies
├── service         use case interface
│   └── impl        implementation, transaction boundary
├── model           business rules: Request, RequestLifecycle, states, actions,
│                   PublicationNumberGenerator (interface)
├── exception       business errors
├── repository      persistence interfaces used by the service
│   └── jpa         JPA implementations, entities, Spring Data repositories
└── config          bean wiring and configuration properties
```

The domain model (`model` and `exception`) has no framework dependencies: it is plain Java and does
not import Spring, JPA or any other layer of the project. Persistence sits behind interfaces in
`repository/`, implemented by JPA adapters in `repository/jpa/`; the one thing the model itself
needs from outside, a source of publication numbers, is the `PublicationNumberGenerator` interface
in `model/`, implemented by a database sequence in `repository/jpa/`. Only `repository.jpa`,
`controller` and `config` depend on infrastructure.

### Key decisions

**Domain isolated from infrastructure.** `Request` and `RequestLifecycle` are plain Java. The
business rules are unit tested in milliseconds without Spring, and the persistence technology can
change by replacing the `repository.jpa` implementations only. The JPA entity is a separate class so that JPA
requirements (no-arg constructor, mutable fields, `@Version`) don't leak into the domain.

**The state diagram is data.** `RequestLifecycle` is an immutable transition table built once
(`RequestLifecycle.standard()`). There are no state checks scattered through services; adding a
state or arrow is a one-line change. A state-machine library (e.g. Spring Statemachine) would add
more complexity than six transitions justify.

**One endpoint per action.** `POST /{id}/verify`, `/reject`, … map one-to-one to the arrows of the
diagram, keep reason-bearing actions strongly typed, and make illegal transitions an explicit `409`.

**Requests are identified by a UUID assigned in the domain.** `Request.create` gives every request a
random UUID, so it has its identity before it is stored and the persistence layer never has to fill
it in. UUIDs are unique across threads and application instances without any coordination, and
they are not guessable. The publication number therefore remains the only unique *numeric*
identifier, assigned on publication as the specification describes. Names are descriptive and may
repeat, which is why browsing filters by name and can return several requests.

**Publication numbers come from a database sequence.** Values are unique across concurrent
transactions and multiple application instances without application-level locking. A `UNIQUE`
constraint is the safety net. The trade-off is that a rolled-back publication leaves a gap (see
[ASSUMPTIONS.md](ASSUMPTIONS.md)).

**The audit log is written in the same transaction as the state change.** History cannot diverge
from the actual state. A message broker (e.g. Kafka) was deliberately not introduced: nothing
consumes the events, and writing to a broker and a database would create a dual-write consistency
problem. If other services ever need these events, the extension is a transactional outbox.

**Hibernate generates the schema** from the entity mappings (`ddl-auto=update`), which keeps the
project free of migration tooling. The mappings declare the database rules explicitly, so they are
enforced even for writes that bypass the application: valid states, a unique publication number,
a `CHECK` that a request has a publication number if and only if it is `PUBLISHED`, and a foreign
key from audit entries to their request. The one
object Hibernate does not generate, the publication-number sequence, is created by a single
idempotent statement in `schema.sql`. For a production system with long-lived data, versioned
migrations (Flyway or Liquibase) would replace `ddl-auto`, because `update` never drops or renames
columns.

**Validation at two levels.** Bean Validation on DTOs gives precise field errors to clients; the
domain enforces the same rules because it must not rely on its caller.

### Concurrency

- **Racing transitions on the same request** (two publishes, publish vs. reject, edit vs. accept):
  optimistic locking via a `@Version` column. The loser gets `409 Concurrent modification`.
  Conflicts on a single request are rare, so this costs nothing on the normal path, unlike
  pessimistic row locks.
- **Concurrent publications of different requests:** the database sequence guarantees distinct
  numbers.
- **Concurrent creations:** each request gets its own random UUID in the application, so creates
  need no coordination at all, even across application instances.
- The service is stateless, so it can scale horizontally behind a load balancer; all coordination
  happens in the database.

### Configuration

| Property | Default | Purpose |
|---|---|---|
| `app.pagination.default-size` | `10` | Page size when the client sends none |
| `app.pagination.max-size` | `100` | Larger requested sizes are capped |
| `spring.profiles.active` | (none) | `postgres` to use PostgreSQL |
| `springdoc.swagger-ui.enabled` | `true` | Disable API docs UI in production |

### Possible extensions (deliberately not built)

- `ETag` / `If-Match` on the version, so clients cannot overwrite changes made since they read.
- `Idempotency-Key` on `POST` so retried creations do not create duplicates.
- Keyset pagination for deep pages; a `pg_trgm` index for substring search on large data sets.
- Transactional outbox to publish lifecycle events to other services.
- Authentication and per-user audit (`changedBy`).
- UUID v7 (time-ordered) instead of random UUID v4 ids, which keeps index inserts sequential once the
  table is very large. Java 17 has no built-in v7 generator, and at this scale v4 is sufficient.
