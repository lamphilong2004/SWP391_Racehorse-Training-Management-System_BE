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

## Registration request schema approval (new approval)

- **Approved by Leader:** 2026-10-16, in the CORE-007 implementation instruction. This is a new approval of the proposal from commit `a51632af40995b2d817cc88c54ab8a055e71a757`; the proposal was explicitly unapproved when originally committed.
- The approved 14-column SQL Server definition is recorded in `database/migrations/V20261016_01__create_registration_request.sql`; the original review artifact remains unchanged at `docs/proposals/CORE-007-registration-request-proposal.sql`.
- Status is limited to `PENDING_APPROVAL`, `APPROVED`, or `REJECTED`. Pending requests require a password hash and no decision fields. Approved requests require reviewer/time, `approved_role_id`, and `created_user_id`, with a NULL password hash. Rejected requests require reviewer/time and NULL `approved_role_id`, `created_user_id`, and password hash.
- All timestamps are UTC `DATETIME2(3)`. `created_at` defaults to `SYSUTCDATETIME()`; the application writes `updated_at` on modification. `email_normalized` is application-written and uses `Latin1_General_100_BIN2`.
- Only pending rows are subject to the unique normalized-email index; decided rows remain as history. Login queries use parameterized normalized email and `PENDING_APPROVAL`, and never compare passwords in SQL.
- Email uses the shared trim → Unicode NFC → lowercase (`Locale.ROOT`) normalizer for both registration lookup and `app_user` lookup. New users and seed records store normalized email. Existing `app_user` schema/collation is unchanged; legacy rows must be inspected and any normalization remediation proposed separately, never silently bulk-updated.
- Login always evaluates `app_user` first. A found account never falls through to a pending request, regardless of password or account status. A pending credential can never issue a JWT.
- CORE-047 must create the account and role, update the registration request, and clear its password hash in one transaction. CORE-007 implements only pending-login lookup and does not implement approval.
- No token revocation, blacklist, or other speculative revocation mechanism is approved by this decision.
