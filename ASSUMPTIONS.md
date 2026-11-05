# Assumptions

Decisions taken where the specification leaves room for interpretation, with the reasoning behind
each.

## Lifecycle

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

## Data and validation

5. **Name** is required and at most 255 characters. It is stored exactly as sent (not trimmed);
   a whitespace-only name counts as blank.
6. **Name is the public identifier.** All operations on a single request (edit, every transition,
   audit log) address it by name, so the name must be usable in a URL path: it must not contain
   `/`, `\` or `;` and must not be `.` or `..`. Servers treat these specially in paths, so such a
   request could be created but never reached. The numeric `id` remains the internal key. There is
   no endpoint to read a single request; requests are read through `browse`, and every action
   returns the updated request.
7. **Name is unique among requests that are not deleted**, compared exactly (case-sensitive,
   including spaces). Creating a duplicate returns `409 Conflict`. The service checks
   for an active request with the same name before saving. A database unique constraint (on an
   internal `active_name` column that is cleared on deletion) backs this up, so uniqueness also
   holds when two identical requests are created at the same moment.
8. **Reusing the name of a deleted request is allowed; of a rejected one, it is not.**
   - If a request is **deleted**, a new request **can** be created with the same name.
   - If a request is **rejected**, its name stays taken: creating another request with that name
     returns `409 Conflict`. The same applies to **published** requests.

   Deletion withdraws a request that should not have existed (it is only possible straight after
   creation, before verification), so its name is released. A rejection is a decision on a request
   that went through review; keeping its name reserved means the name always refers to that decided
   request and its audit log, and a rejected request cannot be resubmitted unnoticed under the same
   name. Published requests are final for the same reason.
9. **Addressing by name** targets the current request with that name. If the name only belongs to
   deleted requests, it targets the most recently deleted one, so its audit log stays reachable
   until the name is reused. After reuse, older deleted requests with that name are still stored and
   listed by `GET /api/requests/browse`, but can no longer be addressed by name.
10. **The internal `id` can have gaps.** It is a database identity value; an insert that fails
   (for example the losing side of two simultaneous creates with the same name) still consumes one. It carries no meaning and is not used
   to address requests.
11. **Name is immutable.** Only the content can be edited. A `name` sent to the edit endpoint is
   ignored.
12. **Content** ("body") is required, at most 10,000 characters, and stored as sent. Whitespace is
   not trimmed, since it may be meaningful, but whitespace-only content counts as blank.
13. **Reason** is required (non-blank, at most 1,000 characters, trimmed) for delete and reject. It
   is stored on the request (the reason for its current state) and in the audit entry.
14. **Editing content is not a state change**, so it is not written to the audit log. The audit log
   records state changes, as specified. Content edits are reflected in `updatedAt`.
15. **Validation order:** the request body is validated first, so a missing or blank reason (or
    blank content on edit) returns `400` even when the action would not be allowed in the current
    state. Once the input is valid, an action the state diagram does not allow returns `409`. This
    is the usual order for an HTTP API: a malformed request is rejected before any data is loaded.

## Publication number

16. **A publication number is separate from the technical id.** It is assigned only on publication,
    is unique and increases, and is never changed afterwards.
17. **Gaps are acceptable.** Numbers come from a database sequence. If a publication transaction
    rolls back (e.g. a concurrent modification), its number is not reused. The specification asks
    for a *unique* identifier, not a gap-free one. A gap-free counter would need a single locked
    counter row, forcing every publication to wait in line. Refused publish attempts (wrong state)
    are rejected before a number is drawn, so they do not create gaps.

## Audit log

18. **The audit log records creation and every state transition**, with action, previous state,
    new state, reason and timestamp. It is stored in the same transaction as the change, so it is
    complete by construction. Failed attempts are not recorded.
19. **No actor is recorded.** The specification has no users or authentication; adding a
    `changedBy` column is straightforward once there is an identity.
20. **Timestamps are UTC**, taken from an injected `Clock`.

## Browsing

21. **Name filter** is a case-insensitive "contains" match. SQL wildcard characters in the filter
    (`%`, `_`) are matched literally. A blank filter means "no filter".
22. **State filter** is an exact match on one state, written as in the API (`PUBLISHED`, upper
    case). Unknown or differently cased values return `400`.
23. Filters can be combined (logical AND).
24. **Paging is zero-based.** The default page size is 10 (configurable). Sizes above the configured
    maximum (100) are capped, not rejected. `size < 1` and `page < 0` return `400`. A page beyond
    the end returns an empty list with correct totals, not an error.
25. **Results are ordered by id (creation order).** A stable order is required for paging to be
    consistent; the specification doesn't define one.

## Concurrency

26. **Concurrent changes to the same request** are resolved with optimistic locking. Exactly one
    succeeds; the others receive `409 Concurrent modification` and may retry after reloading.
    Clients cannot send an expected version (`If-Match`) because the specification doesn't
    require it; it's listed under possible extensions in the README.

## Technical

27. **Database:** PostgreSQL is the target. H2 in PostgreSQL compatibility mode is the default for
    running without any setup. It is file-based (`./data/`, git-ignored, so no database is
    submitted) rather than in-memory: an in-memory database would lose the publication-number
    sequence on restart and could then issue the same number twice. Tests use a separate in-memory
    H2 database so they stay fast and isolated. The schema is generated by Hibernate from the same entity
    mappings everywhere, plus `schema.sql` for the publication-number sequence. Testcontainers would allow testing against real PostgreSQL but requires Docker on the
    build machine, which the evaluation environment can't be assumed to have.
28. **No message broker, cache or service split.** No requirement involves asynchronous
    processing, external consumers, or load beyond what one relational database handles. Each would
    add operational cost and new consistency problems (see [Key decisions](README.md#key-decisions) in the README).
29. **No authentication or authorization**, since none is specified.
30. **API documentation** (Swagger UI) is included to make the API easy to evaluate. It is not a GUI
    for the application and can be disabled with `springdoc.swagger-ui.enabled=false`.
31. **API versioning** is not part of the URL. With a single version and no consumers yet, a prefix
    would be speculative; a version can be introduced through the path or a media type when
    needed.
