# DEC-012 — Club Manager sets the initial password

- **Status:** APPROVED by the Project Leader, 2026-10-28.
- **Supersedes in part:** DEC-008 §6 clause "no role ever sees or sets a password" and "MF-17-created users have a NULL hash and set their password via reset". Every other DEC-008 rule (self-registration, approval with exactly one role, BCrypt, JWT only for ACTIVE users, neutral responses, rate limits) is unchanged.
- **Reason:** there is no production email service, so a staff account created by the Club Manager could only be activated through the development-only reset outbox on the server machine. Team testing and the review need accounts that work immediately.

## Decision

1. When creating a staff or owner account (`POST /api/admin/users`), the Club Manager enters a password and its confirmation. The form requires it; the API still accepts no password (old reset path) for compatibility.
2. When editing an account (`PUT /api/admin/users/{id}`), the Club Manager may enter a new password for a user who forgot theirs. Leaving it empty keeps the user's current password.
3. Password policy is the existing one: 8–64 characters, at least one letter and one digit, at most 72 UTF-8 bytes, not equal to the email. Stored as BCrypt; never returned by the API or written to logs (`UserInput.toString` is redacted).
4. Each manager-set password is audited as `USER_PASSWORD_SET_BY_MANAGER` (no password content).
5. The user logs in with email + that password and should change it in *Tài khoản → Đổi mật khẩu* (`PUT /api/account/password`, requires the current password). A forced change on first login is **not** implemented.

## Known limitation

The Club Manager knows the initial password until the user changes it. A real email activation link remains the preferred production design.
