# CORE-028 - Work Rider assignment and history

## Implementation reference

- `Core028Controller.java`
- `Core028Service.java`

## Assignment query

GET /api/work-rider/assignments requires the WORK_RIDER role.

The query is scoped to the authenticated rider's user ID.
The caller cannot select another rider through a request parameter.

Without an explicit status filter, the service returns
SCHEDULED, IN_PROGRESS and PAUSED sessions.

Assignments are ordered by scheduled start time and session ID.

## History query

GET /api/work-rider/history returns COMPLETED and INTERRUPTED sessions
belonging to the authenticated rider.

Pagination defaults to page 0 and size 20.

History ordering uses actual end time with scheduled start
as the fallback, followed by session ID in descending order.

The response contains items, page, size, totalItems and totalPages.

## Verification checklist

- Confirm that another rider's assignments are not returned.
- Confirm active assignments and terminal history remain separate.
- Confirm that unsupported status filters are rejected.
- Confirm pagination metadata agrees with the matching records.

These are verification scenarios, not newly executed test results.
