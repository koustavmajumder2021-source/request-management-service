# Assumptions

Decisions taken where the specification leaves room for interpretation, with the reasoning behind
each.

## Lifecycle

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

## Data and validation

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

## Publication number

11. **A publication number is separate from the technical id.** It is assigned only on publication,
    is unique and increases, and is never changed afterwards.
12. **Gaps are acceptable.** Numbers come from a database sequence. If a publication transaction
    rolls back (e.g. a concurrent modification), its number is not reused. The specification asks
    for a *unique* identifier, not a gap-free one. A gap-free counter would need a single locked
    counter row, forcing every publication to wait in line. Refused publish attempts (wrong state)
    are rejected before a number is drawn, so they do not create gaps.

## Audit log

13. **The audit log records creation and every state transition**, with action, previous state,
    new state, reason and timestamp. It is stored in the same transaction as the change, so it is
    complete by construction. Failed attempts are not recorded.
14. **No actor is recorded.** The specification has no users or authentication; adding a
    `changedBy` column is straightforward once there is an identity.
15. **Timestamps are UTC**, taken from an injected `Clock`.

## Browsing

16. **Name filter** is a case-insensitive "contains" match. SQL wildcard characters in the filter
    (`%`, `_`) are matched literally. A blank filter means "no filter".
17. **State filter** is an exact match on one state. Unknown values return `400`.
18. Filters can be combined (logical AND).
19. **Paging is zero-based.** The default page size is 10 (configurable). Sizes above the configured
    maximum (100) are capped, not rejected. `size < 1` and `page < 0` return `400`. A page beyond
    the end returns an empty list with correct totals, not an error.
20. **Results are ordered by id (creation order).** A stable order is required for paging to be
    consistent; the specification doesn't define one.

## Concurrency

21. **Concurrent changes to the same request** are resolved with optimistic locking. Exactly one
    succeeds; the others receive `409 Concurrent modification` and may retry after reloading.
    Clients cannot send an expected version (`If-Match`) because the specification doesn't
    require it; it's listed as an extension in the README.

## Technical

22. **Database:** PostgreSQL is the target. H2 in PostgreSQL compatibility mode is the default for
    running and testing without any setup. The schema is managed by Flyway and is identical for
    both. Testcontainers would allow testing against real PostgreSQL but requires Docker on the
    build machine, which the evaluation environment can't be assumed to have.
23. **No message broker, cache or service split.** No requirement involves asynchronous
    processing, external consumers, or load beyond what one relational database handles. Each would
    add operational cost and new consistency problems (see README, "Key decisions").
24. **No authentication or authorization**, since none is specified.
25. **API documentation** (Swagger UI) is included to make the API easy to evaluate. It is not a GUI
    for the application and can be disabled with `springdoc.swagger-ui.enabled=false`.
26. **API versioning** is not part of the URL. With a single version and no consumers yet, a prefix
    would be speculative; a version can be introduced through the path or a media type when
    needed.
