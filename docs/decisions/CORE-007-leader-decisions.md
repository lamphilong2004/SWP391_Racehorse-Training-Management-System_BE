# CORE-007 Leader decisions

Recorded from the Leader's instruction dated 2026-10-16. No DEC identifier was assigned in that instruction.

## Current identity and authorization

- A verified JWT establishes user identity only.
- Every protected request resolves the user's current account status and role assignments from the database.
- Database roles, not JWT role claims, determine Spring Security authorities, `/api/auth/me`, access decisions, and audit actor context.
- Role revocation takes effect on the next request after its database transaction commits; removing one role does not remove any other assigned roles.
- An inactive account is denied access to protected endpoints and `/api/auth/me` using the shared API error contract.

## Logout audit action

- The approved audit action identifier for `POST /api/auth/logout` is `USER_LOGGED_OUT`.
- Record the authenticated actor through the existing `dbo.audit_log` convention and return `204` only after audit persistence succeeds.
- Logout remains stateless: do not add token blacklisting or another revocation mechanism.
- `USER_LOGGED_OUT` is a new Leader decision for CORE-007. It was not found in, and is not attributed to, CORE-051 Q-15.
