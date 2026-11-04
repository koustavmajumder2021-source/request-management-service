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

Judgement calls not covered by the specification are listed under [Assumptions](#assumptions).

## Tech stack

Java 17, Spring Boot 4.1, Spring Data JPA (Hibernate 7), Flyway, PostgreSQL / H2,
springdoc-openapi, JUnit, AssertJ, Mockito, JaCoCo.

## Running

Requirements: JDK 17 or newer. Maven is not needed; the wrapper downloads it.

```bash
./mvnw spring-boot:run
```

This starts the service on port 8080 with a file-based H2 database (PostgreSQL mode) in `./data/`,
so requests, ids and publication numbers survive restarts. Delete that folder to start from an empty
database. To run against PostgreSQL instead:

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
| Application | `RequestServiceTest` | Mockito mocks of the ports: verifies orchestration (save before audit, nothing written on failure) and simulates infrastructure failures such as optimistic-lock conflicts. |
| Persistence | `PersistenceAdaptersTest` | `@DataJpaTest` against the real Flyway schema, including DB constraints. |
| API | `RequestApiIntegrationTest` | Full stack through MockMvc: status codes, validation, paging, history. |
| Concurrency | `ConcurrencyIntegrationTest` | Parallel publications get unique numbers; racing transitions on one request have exactly one winner. |

## API

Base path: `/api/requests`

| Method | Path | Body | Success | Errors |
|---|---|---|---|---|
| `POST` | `/` | `{"name", "content"}` | `201` | `400` |
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
content not editable, or a concurrent modification · `422` any other business rule violation
(fallback, so a newly added domain error never surfaces as a `500`).

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
[Assumptions](#assumptions)).

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
- UUID v7 request ids instead of auto-increment. They are time-ordered (index-friendly), not
  enumerable through the API, and can be generated in the domain. The specification sets no
  requirement on the id, so the simpler sequential id was kept. The publication number stays
  numeric either way, as required.

## Assumptions

Decisions taken where the specification leaves room for interpretation, with the reasoning behind
each.

### Lifecycle

1. **The state diagram is the only source of allowed transitions.** Anything not drawn is refused
   with `409 Conflict`, including repeating a step (e.g. verifying twice) or skipping one (e.g.
   accepting a `CREATED` request).
2. **`PUBLISHED`, `REJECTED` and `DELETED` are terminal.** The diagram has no arrows leaving them.
3. **Delete is a soft delete.** The diagram models `DELETED` as a state, so a deleted request remains
   readable, appears in listings and keeps its history. Nothing is physically removed; that also
   keeps the audit log complete. Deletion is exposed as `POST /{id}/delete`, not HTTP `DELETE`,
   because it carries a mandatory body (the reason), and bodies on `DELETE` are poorly supported by
   clients and proxies.
4. **Delete is only allowed from `CREATED`, reject only from `VERIFIED` and `ACCEPTED`**, exactly as
   drawn.

### Data and validation

5. **Name** is required, trimmed, and at most 255 characters. Names are **not unique**: the
   specification doesn't ask for it, and two requests can legitimately share a name.
6. **Name is immutable.** The specification only allows editing the body. A `name` sent to the edit
   endpoint is ignored.
7. **Content** ("body") is required, at most 10,000 characters, and stored as sent. Whitespace is
   not trimmed, since it may be meaningful, but whitespace-only content counts as blank.
8. **Reason** is required (non-blank, at most 1,000 characters, trimmed) for delete and reject. It
   is stored on the request (the reason for its current state) and in the audit entry.
9. **Editing content is not a state change**, so it is not written to the audit log. The audit log
   records state changes, as specified. Content edits are reflected in `updatedAt`.
10. **Validation order:** an action that isn't allowed in the current state returns `409` even when
    the reason is also missing. Whether the action is possible at all is the more fundamental error.

### Publication number

11. **A publication number is separate from the technical id.** It is assigned only on publication,
    is unique and increases, and is never changed afterwards.
12. **Gaps are acceptable.** Numbers come from a database sequence. If a publication transaction
    rolls back (e.g. a concurrent modification), its number is not reused. The specification asks
    for a *unique* identifier, not a gap-free one. A gap-free counter would need a single locked
    counter row, forcing every publication to wait in line. Refused publish attempts (wrong state)
    are rejected before a number is drawn, so they do not create gaps.

### Audit log

13. **The audit log records creation and every state transition**, with action, previous state,
    new state, reason and timestamp. It is stored in the same transaction as the change, so it is
    complete by construction. Failed attempts are not recorded.
14. **No actor is recorded.** The specification has no users or authentication; adding a
    `changedBy` column is straightforward once there is an identity.
15. **Timestamps are UTC**, taken from an injected `Clock`.

### Browsing

16. **Name filter** is a case-insensitive "contains" match. SQL wildcard characters in the filter
    (`%`, `_`) are matched literally. A blank filter means "no filter".
17. **State filter** is an exact match on one state. Unknown values return `400`.
18. Filters can be combined (logical AND).
19. **Paging is zero-based.** The default page size is 10 (configurable). Sizes above the configured
    maximum (100) are capped, not rejected. `size < 1` and `page < 0` return `400`. A page beyond
    the end returns an empty list with correct totals, not an error.
20. **Results are ordered by id (creation order).** A stable order is required for paging to be
    consistent; the specification doesn't define one.

### Concurrency

21. **Concurrent changes to the same request** are resolved with optimistic locking. Exactly one
    succeeds; the others receive `409 Concurrent modification` and may retry after reloading.
    Clients cannot send an expected version (`If-Match`) because the specification doesn't
    require it; it's listed under possible extensions.

### Technical

22. **Database:** PostgreSQL is the target. H2 in PostgreSQL compatibility mode is the default for
    running without any setup. It is file-based (`./data/`, git-ignored, so no database is
    submitted) rather than in-memory: an in-memory database would lose the publication-number
    sequence on restart and could then issue the same number twice. Tests use a separate in-memory
    H2 database so they stay fast and isolated. The schema is managed by Flyway and is identical
    everywhere. Testcontainers would allow testing against real PostgreSQL but requires Docker on the
    build machine, which the evaluation environment can't be assumed to have.
23. **No message broker, cache or service split.** No requirement involves asynchronous
    processing, external consumers, or load beyond what one relational database handles. Each would
    add operational cost and new consistency problems (see [Key decisions](#key-decisions)).
24. **No authentication or authorization**, since none is specified.
25. **API documentation** (Swagger UI) is included to make the API easy to evaluate. It is not a GUI
    for the application and can be disabled with `springdoc.swagger-ui.enabled=false`.
26. **API versioning** is not part of the URL. With a single version and no consumers yet, a prefix
    would be speculative; a version can be introduced through the path or a media type when
    needed.
