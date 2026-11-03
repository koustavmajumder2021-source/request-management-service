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

- Name and content are mandatory on creation.
- Content can be edited only in `CREATED` and `VERIFIED`.
- Deleting and rejecting require a reason.
- Publishing assigns a unique numeric publication number.
- Every state change is stored in an audit log.
- Requests can be listed with pagination (default page size 10) and filtered by name and state.

Judgement calls not covered by the specification are listed in [ASSUMPTIONS.md](ASSUMPTIONS.md).

## Tech stack

Java 17, Spring Boot 4.1, Spring Data JPA (Hibernate 7), Flyway, PostgreSQL / H2,
springdoc-openapi, JUnit 5, AssertJ, JaCoCo.

## Running

Requirements: JDK 17 or newer. Maven is not needed; the wrapper downloads it.

```bash
./mvnw spring-boot:run
```

This starts the service on port 8080 with an in-memory H2 database (PostgreSQL mode).
To run against PostgreSQL instead:

```bash
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres
```

Connection settings can be overridden with `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`.

API documentation: http://localhost:8080/swagger-ui.html (OpenAPI JSON at `/v3/api-docs`).

## Testing

```bash
./mvnw verify
```

Runs unit and integration tests and writes a coverage report to `target/site/jacoco/index.html`.
The build fails if line coverage drops below 90 % (currently ~99 %).

| Layer | Test | Approach |
|---|---|---|
| Domain | `RequestLifecycleTest`, `RequestTest`, `ValueObjectsTest` | Plain JUnit, no Spring. All 36 state/action combinations are checked against the diagram. |
| Application | `RequestServiceTest` | In-memory implementations of the ports instead of mocks. |
| Persistence | `PersistenceAdaptersTest` | `@DataJpaTest` against the real Flyway schema, including DB constraints. |
| API | `RequestApiIntegrationTest` | Full stack through MockMvc: status codes, validation, paging, history. |
| Concurrency | `ConcurrencyIntegrationTest` | Parallel publications get unique numbers; racing transitions on one request have exactly one winner. |

## API

Base path: `/api/requests`

| Method | Path | Body | Success | Errors |
|---|---|---|---|---|
| `POST` | `/` | `{"name", "content"}` | `201` + `Location` | `400` |
| `GET` | `/{id}` | | `200` | `404` |
| `GET` | `/?name=&state=&page=&size=` | | `200` page | `400` |
| `PATCH` | `/{id}` | `{"content"}` | `200` | `400`, `404`, `409` |
| `POST` | `/{id}/verify` | | `200` | `404`, `409` |
| `POST` | `/{id}/accept` | | `200` | `404`, `409` |
| `POST` | `/{id}/publish` | | `200` | `404`, `409` |
| `POST` | `/{id}/reject` | `{"reason"}` | `200` | `400`, `404`, `409` |
| `POST` | `/{id}/delete` | `{"reason"}` | `200` | `400`, `404`, `409` |
| `GET` | `/{id}/history` | | `200` | `404` |

`400` invalid input · `404` unknown request · `409` action not allowed in the current state,
content not editable, or a concurrent modification.

Errors use RFC 9457 problem details:

```json
{
  "type": "about:blank",
  "title": "Invalid state transition",
  "status": 409,
  "detail": "Cannot publish a request in state CREATED",
  "instance": "/api/requests/1/publish",
  "currentState": "CREATED",
  "action": "PUBLISH"
}
```

Example:

```bash
curl -s -X POST localhost:8080/api/requests -H 'Content-Type: application/json' \
     -d '{"name":"Solar panels","content":"Install 20 panels on the roof"}'
curl -s -X POST localhost:8080/api/requests/1/verify
curl -s -X POST localhost:8080/api/requests/1/accept
curl -s -X POST localhost:8080/api/requests/1/publish
curl -s localhost:8080/api/requests/1/history
curl -s 'localhost:8080/api/requests?name=solar&state=PUBLISHED&page=0&size=10'
```

A request resource includes `allowedActions` and `contentEditable`, so clients can drive their UI
without duplicating the state machine.

## Design

```
            HTTP
              │
 ┌────────────▼─────────────┐
 │ web (controller, DTOs,   │  inbound adapter
 │ exception → HTTP mapping)│
 └────────────┬─────────────┘
 ┌────────────▼─────────────┐
 │ application              │  use cases, transaction boundary
 │ RequestService           │
 └────────────┬─────────────┘
 ┌────────────▼─────────────┐      ┌───────────────────────────┐
 │ domain                   │      │ domain.port               │
 │ Request, RequestLifecycle│─────▶│ RequestRepository         │
 │ StateChange, exceptions  │      │ StateChangeLog            │
 └──────────────────────────┘      │ PublicationNumberGenerator│
                                   └─────────────▲─────────────┘
 ┌───────────────────────────────────────────────┴─────────────┐
 │ persistence (JPA entities, Spring Data, DB sequence)        │  outbound adapters
 └─────────────────────────────┬───────────────────────────────┘
                               ▼
                 PostgreSQL / H2  (schema owned by Flyway)
```

```
src/main/java/com/example/requests
├── domain                  business rules, no framework dependencies
│   ├── exception
│   └── port                interfaces implemented by infrastructure
├── application             use cases and transactions
└── infrastructure
    ├── config              wiring of domain objects
    ├── persistence         JPA adapters, Flyway-managed schema
    └── web                 REST controller, DTOs, error handling
```

### Key decisions

**Domain isolated from infrastructure.** `Request` and `RequestLifecycle` are plain Java. The
business rules are unit tested in milliseconds without Spring, and the persistence technology can
change by replacing the adapters only. The JPA entity is a separate class so that JPA
requirements (no-arg constructor, mutable fields, `@Version`) don't leak into the domain.

**The state diagram is data.** `RequestLifecycle` is an immutable transition table built once
(`RequestLifecycle.standard()`). There are no state checks scattered through services; adding a
state or arrow is a one-line change. A state-machine library (e.g. Spring Statemachine) would add
more complexity than six transitions justify.

**One endpoint per action.** `POST /{id}/verify`, `/reject`, … map one-to-one to the arrows of the
diagram, keep reason-bearing actions strongly typed, and make illegal transitions an explicit `409`.

**Publication numbers come from a database sequence.** Values are unique across concurrent
transactions and multiple application instances without application-level locking. A `UNIQUE`
constraint is the safety net. The trade-off is that a rolled-back publication leaves a gap (see
ASSUMPTIONS.md).

**The audit log is written in the same transaction as the state change.** History cannot diverge
from the actual state. A message broker (e.g. Kafka) was deliberately not introduced: nothing
consumes the events, and writing to a broker and a database would create a dual-write consistency
problem. If other services ever need these events, the extension is a transactional outbox.

**Flyway owns the schema.** Hibernate runs with `ddl-auto=validate`, so the application refuses to
start if entities and schema drift apart. Database `CHECK` constraints enforce the most important
invariants (valid states, publication number present if and only if `PUBLISHED`) even for writes
that bypass the application.

**Validation at two levels.** Bean Validation on DTOs gives precise field errors to clients; the
domain enforces the same rules because it must not rely on its caller.

### Concurrency

- **Racing transitions on the same request** (two publishes, publish vs. reject, edit vs. accept):
  optimistic locking via a `@Version` column. The loser gets `409 Concurrent modification`.
  Conflicts on a single request are rare, so this costs nothing on the normal path, unlike
  pessimistic row locks.
- **Concurrent publications of different requests:** the database sequence guarantees distinct
  numbers.
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
