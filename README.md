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

- Name and content are mandatory on creation. The name is unique and identifies the request in
  every URL; it cannot be changed.
- Content can be edited only in `CREATED` and `VERIFIED`.
- Deleting and rejecting require a reason.
- Publishing assigns a unique numeric publication number.
- Every state change is stored in an audit log.
- Requests can be browsed with pagination (default page size 10) and filtered by name and state.

Judgement calls not covered by the specification are listed under [Assumptions](#assumptions).

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
| Concurrency | `ConcurrencyIntegrationTest` | Parallel publications get unique numbers; racing transitions on one request, and racing creations with the same name, each have exactly one winner. |

## API

Base path: `/api/requests`

| Method | Path | Body | Success | Errors |
|---|---|---|---|---|
| `POST` | `/` | `{"name", "content"}` | `201` | `400`, `409` |
| `GET` | `/browse?name=&state=&page=&size=` | | `200` page | `400` |
| `PATCH` | `/{name}` | `{"content"}` | `200` | `400`, `404`, `409` |
| `POST` | `/{name}/verify` | | `200` | `404`, `409` |
| `POST` | `/{name}/accept` | | `200` | `404`, `409` |
| `POST` | `/{name}/publish` | | `200` | `404`, `409` |
| `POST` | `/{name}/reject` | `{"reason"}` | `200` | `400`, `404`, `409` |
| `POST` | `/{name}/delete` | `{"reason"}` | `200` | `400`, `404`, `409` |
| `GET` | `/{name}/audit-log` | | `200` | `404` |

Requests are addressed by **name**, URL-encoded in the path (`Solar panels` → `Solar%20panels`).
The numeric `id` is the internal database key; it is still returned in responses.

`400` invalid input · `404` unknown request name · `409` name already in use, action not allowed in
the current state, content not editable, or a concurrent modification · `422` any other business rule violation
(fallback, so a newly added domain error never surfaces as a `500`).

Errors use RFC 9457 problem details:

```json
{
  "type": "about:blank",
  "title": "Invalid state transition",
  "status": 409,
  "detail": "Cannot publish a request in state CREATED",
  "instance": "/api/requests/Solar%20panels/publish",
  "currentState": "CREATED",
  "action": "PUBLISH"
}
```

Example:

```bash
curl -s -X POST localhost:8080/api/requests -H 'Content-Type: application/json' \
     -d '{"name":"Solar panels","content":"Install 20 panels on the roof"}'
curl -s -X POST localhost:8080/api/requests/Solar%20panels/verify
curl -s -X POST localhost:8080/api/requests/Solar%20panels/accept
curl -s -X POST localhost:8080/api/requests/Solar%20panels/publish
curl -s localhost:8080/api/requests/Solar%20panels/audit-log
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
 ┌──────────────▼──────────────┐
 │ service                     │  use cases, transaction boundary
 │ RequestService (interface)  │
 │ impl.RequestServiceImpl     │
 └──────────────┬──────────────┘
 ┌──────────────▼──────────────┐      ┌───────────────────────────┐
 │ model + exception           │      │ repository (interfaces)   │
 │ Request, RequestLifecycle,  │─────▶│ RequestRepository         │
 │ StateChange, domain errors  │      │ StateChangeLog            │
 └─────────────────────────────┘      │ PublicationNumberGenerator│
                                      └─────────────▲─────────────┘
 ┌──────────────────────────────────────────────────┴──────────┐
 │ repository.jpa (JPA entities, Spring Data, DB sequence)     │  implementations
 └─────────────────────────────┬───────────────────────────────┘
                               ▼
                 PostgreSQL / H2  (schema generated by Hibernate)
```

```
src/main/java/com/example/requests
├── controller      REST controller and exception handler
├── dto             request and response bodies
├── service         use case interface
│   └── impl        implementation, transaction boundary
├── model           business rules: Request, RequestLifecycle, states, actions
├── exception       business errors
├── repository      persistence interfaces used by the service and model
│   └── jpa         JPA implementations, entities, Spring Data repositories
└── config          bean wiring and configuration properties
```

`model`, `exception`, the `repository` interfaces and `service` contain no JPA or web code; only
`repository.jpa`, `controller` and `config` depend on infrastructure.

### Key decisions

**Domain isolated from infrastructure.** `Request` and `RequestLifecycle` are plain Java. The
business rules are unit tested in milliseconds without Spring, and the persistence technology can
change by replacing the `repository.jpa` implementations only. The JPA entity is a separate class so that JPA
requirements (no-arg constructor, mutable fields, `@Version`) don't leak into the domain.

**The state diagram is data.** `RequestLifecycle` is an immutable transition table built once
(`RequestLifecycle.standard()`). There are no state checks scattered through services; adding a
state or arrow is a one-line change. A state-machine library (e.g. Spring Statemachine) would add
more complexity than six transitions justify.

**One endpoint per action.** `POST /{name}/verify`, `/reject`, … map one-to-one to the arrows of the
diagram, keep reason-bearing actions strongly typed, and make illegal transitions an explicit `409`.

**Publication numbers come from a database sequence.** Values are unique across concurrent
transactions and multiple application instances without application-level locking. A `UNIQUE`
constraint is the safety net. The trade-off is that a rolled-back publication leaves a gap (see
[Assumptions](#assumptions)).

**The audit log is written in the same transaction as the state change.** History cannot diverge
from the actual state. A message broker (e.g. Kafka) was deliberately not introduced: nothing
consumes the events, and writing to a broker and a database would create a dual-write consistency
problem. If other services ever need these events, the extension is a transactional outbox.

**Hibernate generates the schema** from the entity mappings (`ddl-auto=update`), which keeps the
project free of migration tooling. The mappings declare the database rules explicitly, so they are
enforced even for writes that bypass the application: valid states, a unique publication number,
a `CHECK` that a request has a publication number if and only if it is `PUBLISHED`, unique names
among requests that are not deleted, and a foreign key from audit entries to their request. The one
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
- **Creations with the same name:** the service checks for an active request with that name and
  returns `409 Duplicate name` before saving anything. That check alone has a race window (two
  simultaneous creates can both pass it), so a unique constraint stays in the database as a safety
  net: exactly one insert succeeds and the other also ends as `409 Duplicate name`.
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
   stored, appears in browse results and keeps its audit log. Nothing is physically removed; that also
   keeps the audit log complete. Deletion is exposed as `POST /{name}/delete`, not HTTP `DELETE`,
   because it carries a mandatory body (the reason), and bodies on `DELETE` are poorly supported by
   clients and proxies.
4. **Delete is only allowed from `CREATED`, reject only from `VERIFIED` and `ACCEPTED`**, exactly as
   drawn.

### Data and validation

5. **Name** is required and at most 255 characters. It is stored exactly as sent (not trimmed);
   a whitespace-only name counts as blank.
6. **Name is the public identifier.** All operations on a single request (edit, every transition,
   audit log) address it by name, so the name must be usable in a URL path: it must not contain
   `/`, `\` or `;` and must not be `.` or `..`. Servers treat these specially in paths, so such a
   request could be created but never reached. The numeric `id` remains the internal key. There is
   no endpoint to read a single request; requests are read through `browse`, and every action
   returns the updated request.
7. **Name is unique among requests that are not deleted**, compared exactly (case-sensitive,
   including spaces). Creating a duplicate returns `409 Conflict`. Names of rejected and published
   requests stay reserved; the name of a **deleted** request can be reused. The service checks
   for an active request with the same name before saving. A database unique constraint (on an
   internal `active_name` column that is cleared on deletion) backs this up, so uniqueness also
   holds when two identical requests are created at the same moment.
8. **Addressing by name** targets the current request with that name. If the name only belongs to
   deleted requests, it targets the most recently deleted one, so its audit log stays reachable
   until the name is reused. After reuse, older deleted requests with that name are still stored and
   listed by `GET /api/requests/browse`, but can no longer be addressed by name.
9. **The internal `id` can have gaps.** It is a database identity value; an insert that fails
   (for example the losing side of two simultaneous creates with the same name) still consumes one. It carries no meaning and is not used
   to address requests.
10. **Name is immutable.** Only the content can be edited. A `name` sent to the edit endpoint is
   ignored.
11. **Content** ("body") is required, at most 10,000 characters, and stored as sent. Whitespace is
   not trimmed, since it may be meaningful, but whitespace-only content counts as blank.
12. **Reason** is required (non-blank, at most 1,000 characters, trimmed) for delete and reject. It
   is stored on the request (the reason for its current state) and in the audit entry.
13. **Editing content is not a state change**, so it is not written to the audit log. The audit log
   records state changes, as specified. Content edits are reflected in `updatedAt`.
14. **Validation order:** the request body is validated first, so a missing or blank reason (or
    blank content on edit) returns `400` even when the action would not be allowed in the current
    state. Once the input is valid, an action the state diagram does not allow returns `409`. This
    is the usual order for an HTTP API: a malformed request is rejected before any data is loaded.

### Publication number

15. **A publication number is separate from the technical id.** It is assigned only on publication,
    is unique and increases, and is never changed afterwards.
16. **Gaps are acceptable.** Numbers come from a database sequence. If a publication transaction
    rolls back (e.g. a concurrent modification), its number is not reused. The specification asks
    for a *unique* identifier, not a gap-free one. A gap-free counter would need a single locked
    counter row, forcing every publication to wait in line. Refused publish attempts (wrong state)
    are rejected before a number is drawn, so they do not create gaps.

### Audit log

17. **The audit log records creation and every state transition**, with action, previous state,
    new state, reason and timestamp. It is stored in the same transaction as the change, so it is
    complete by construction. Failed attempts are not recorded.
18. **No actor is recorded.** The specification has no users or authentication; adding a
    `changedBy` column is straightforward once there is an identity.
19. **Timestamps are UTC**, taken from an injected `Clock`.

### Browsing

20. **Name filter** is a case-insensitive "contains" match. SQL wildcard characters in the filter
    (`%`, `_`) are matched literally. A blank filter means "no filter".
21. **State filter** is an exact match on one state, written as in the API (`PUBLISHED`, upper
    case). Unknown or differently cased values return `400`.
22. Filters can be combined (logical AND).
23. **Paging is zero-based.** The default page size is 10 (configurable). Sizes above the configured
    maximum (100) are capped, not rejected. `size < 1` and `page < 0` return `400`. A page beyond
    the end returns an empty list with correct totals, not an error.
24. **Results are ordered by id (creation order).** A stable order is required for paging to be
    consistent; the specification doesn't define one.

### Concurrency

25. **Concurrent changes to the same request** are resolved with optimistic locking. Exactly one
    succeeds; the others receive `409 Concurrent modification` and may retry after reloading.
    Clients cannot send an expected version (`If-Match`) because the specification doesn't
    require it; it's listed under possible extensions.

### Technical

26. **Database:** PostgreSQL is the target. H2 in PostgreSQL compatibility mode is the default for
    running without any setup. It is file-based (`./data/`, git-ignored, so no database is
    submitted) rather than in-memory: an in-memory database would lose the publication-number
    sequence on restart and could then issue the same number twice. Tests use a separate in-memory
    H2 database so they stay fast and isolated. The schema is generated by Hibernate from the same entity
    mappings everywhere, plus `schema.sql` for the publication-number sequence. Testcontainers would allow testing against real PostgreSQL but requires Docker on the
    build machine, which the evaluation environment can't be assumed to have.
27. **No message broker, cache or service split.** No requirement involves asynchronous
    processing, external consumers, or load beyond what one relational database handles. Each would
    add operational cost and new consistency problems (see [Key decisions](#key-decisions)).
28. **No authentication or authorization**, since none is specified.
29. **API documentation** (Swagger UI) is included to make the API easy to evaluate. It is not a GUI
    for the application and can be disabled with `springdoc.swagger-ui.enabled=false`.
30. **API versioning** is not part of the URL. With a single version and no consumers yet, a prefix
    would be speculative; a version can be introduced through the path or a media type when
    needed.
