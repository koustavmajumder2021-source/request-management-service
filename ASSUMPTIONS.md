# Assumptions

Decisions taken where the specification leaves room for interpretation, with the reasoning behind
each.

## Request identity

1. **Names are not unique.** The specification asks for uniqueness only of the publication number,
   and it makes browsing *filterable* by name, which returns a list. Several requests can therefore
   have the same name, in any state: a new request may reuse the name of an existing, deleted,
   rejected or published request.
2. **Every request is identified by a UUID assigned when it is created**, for example
   `3f2a9c1e-7b4d-4c1a-9e2f-5c8d0e4a6b17`. All operations on a single request (read, edit, every
   transition, audit log) use it. A UUID rather than a sequential number keeps the publication
   number the only unique *numeric* identifier, assigned on publication as the specification says.
   UUIDs are generated in the application without any coordination, so they are unique across
   threads and application instances, and they are not guessable. An id that is not a valid UUID
   returns `400`; an unknown one returns `404`.

## Lifecycle

3. **The state diagram is the only source of allowed transitions.** Anything not drawn is refused
   with `409 Conflict`, including repeating a step (e.g. verifying twice) or skipping one (e.g.
   accepting a `CREATED` request).
4. **`PUBLISHED`, `REJECTED` and `DELETED` are terminal.** The diagram has no arrows leaving them.
5. **Delete is a soft delete.** The diagram models `DELETED` as a state, so a deleted request remains
   stored, can still be read by id, appears in browse results and keeps its audit log. Nothing is
   physically removed; that also keeps the audit log complete. Deletion is exposed as
   `POST /{id}/delete`, not HTTP `DELETE`, because it carries a mandatory body (the reason), and
   bodies on `DELETE` are poorly supported by clients and proxies.
6. **Delete is only allowed from `CREATED`, reject only from `VERIFIED` and `ACCEPTED`**, exactly as
   drawn.

## Data and validation

7. **Name** is required and at most 255 characters. It is stored exactly as sent (not trimmed);
   a whitespace-only name counts as blank. Any other characters are allowed, since the name is
   never part of a URL path.
8. **Name is immutable.** Only the content can be edited. A `name` sent to the edit endpoint is
   ignored.
9. **Content** ("body") is required, at most 10,000 characters, and stored as sent. Whitespace is
   not trimmed, since it may be meaningful, but whitespace-only content counts as blank.
10. **Reason** is required (non-blank, at most 1,000 characters, trimmed) for delete and reject. It
    is stored on the request (the reason for its current state) and in the audit entry.
11. **Editing content is not a state change**, so it is not written to the audit log. The audit log
    records state changes, as specified. Content edits are reflected in `updatedAt`.
12. **Validation order:** the request body is validated first, so a missing or blank reason (or
    blank content on edit) returns `400` even when the action would not be allowed in the current
    state. Once the input is valid, an action the state diagram does not allow returns `409`. This
    is the usual order for an HTTP API: a malformed request is rejected before any data is loaded.

## Publication number

13. **A publication number is separate from the request id.** It is assigned only on publication,
    is unique, numeric and increasing, and is never changed afterwards.
14. **Gaps are acceptable.** Numbers come from a database sequence. If a publication transaction
    rolls back (e.g. a concurrent modification), its number is not reused. The specification asks
    for a *unique* identifier, not a gap-free one. A gap-free counter would need a single locked
    counter row, forcing every publication to wait in line. Refused publish attempts (wrong state)
    are rejected before a number is drawn, so they do not create gaps.

## Audit log

15. **The audit log records creation and every state transition**, with action, previous state,
    new state, reason and timestamp. It is stored in the same transaction as the change, so it is
    complete by construction. Failed attempts are not recorded.
16. **No actor is recorded.** The specification has no users or authentication; adding a
    `changedBy` column is straightforward once there is an identity.
17. **Timestamps are UTC**, taken from an injected `Clock`.

## Browsing

18. **Name filter** is a case-insensitive "contains" match. SQL wildcard characters in the filter
    (`%`, `_`) are matched literally. A blank filter means "no filter".
19. **State filter** is an exact match on one state, written as in the API (`PUBLISHED`, upper
    case). Unknown or differently cased values return `400`.
20. Filters can be combined (logical AND).
21. **Paging is zero-based.** The default page size is 10 (configurable). Sizes above the configured
    maximum (100) are capped, not rejected. `size < 1` and `page < 0` return `400`. A page beyond
    the end returns an empty list with correct totals, not an error.
22. **Results are ordered by creation time**, with the id as a tie-breaker. A stable order is
    required for paging to be consistent; the specification doesn't define one. UUIDs carry no
    order, so the creation timestamp is used.

## Concurrency

23. **Concurrent changes to the same request** are resolved with optimistic locking. Exactly one
    succeeds; the others receive `409 Concurrent modification` and may retry after reloading.
    Clients cannot send an expected version (`If-Match`) because the specification doesn't
    require it; it's listed under possible extensions in the README.

## Technical

24. **Database:** PostgreSQL is the target. H2 in PostgreSQL compatibility mode is the default for
    running without any setup. It is file-based (`./data/`, git-ignored, so no database is
    submitted) rather than in-memory: an in-memory database would lose the publication-number
    sequence on restart and could then issue the same number twice. Tests use a separate in-memory
    H2 database so they stay fast and isolated. The schema is generated by Hibernate from the same entity
    mappings everywhere, plus `schema.sql` for the publication-number sequence. Testcontainers would allow testing against real PostgreSQL but requires Docker on the
    build machine, which the evaluation environment can't be assumed to have.
25. **No message broker, cache or service split.** No requirement involves asynchronous
    processing, external consumers, or load beyond what one relational database handles. Each would
    add operational cost and new consistency problems (see [Key decisions](README.md#key-decisions) in the README).
26. **No authentication or authorization**, since none is specified.
27. **API documentation** (Swagger UI) is included to make the API easy to evaluate. It is not a GUI
    for the application and can be disabled with `springdoc.swagger-ui.enabled=false`.
28. **API versioning** is not part of the URL. With a single version and no consumers yet, a prefix
    would be speculative; a version can be introduced through the path or a media type when
    needed.
