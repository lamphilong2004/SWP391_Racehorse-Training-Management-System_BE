# SWP391 Racehorse Training & Management System — Backend

Spring Boot REST API for the Racehorse Training & Management System (Flows 1–3 + shared infrastructure).

## Stack

- Java (compiled for **Java 21 LTS**; runs on JDK 21 or newer)
- Spring Boot **4.1.1** (Spring MVC, Validation, Data JPA)
- Microsoft SQL Server (approved schema DB-01, 51 tables)
- springdoc-openapi 3.1.1 (Swagger UI)

## Run locally

1. Start SQL Server (service `MSSQLSERVER`) and enable TCP/IP on port 1433.
2. Create the dev database and apply the approved schema (verified by checksum):

   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts/db/setup-dev-db.ps1 -UseWindowsAuth
   ```

   or with a SQL login: set `DB_USERNAME` / `DB_PASSWORD` first and omit `-UseWindowsAuth`.
3. Set the connection environment variables (never commit them):

   | Variable | Default |
   |---|---|
   | `DB_URL` | `jdbc:sqlserver://localhost:1433;databaseName=racehorse_dev;encrypt=true;trustServerCertificate=true` |
   | `DB_USERNAME` | (empty) |
   | `DB_PASSWORD` | (empty) |
   | `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` |

4. Run: `mvn spring-boot:run` (profile `dev` by default).
5. Check: `GET http://localhost:8080/api/health` → `{"success":true,"data":{"status":"UP"}}`.
   Swagger UI: `http://localhost:8080/swagger-ui.html`.

## Conventions

- Every response uses the envelope `{ "success": bool, "data": ..., "error": { "code", "message", "details": [] } }`.
- Error codes: `VALIDATION_ERROR` (400), `UNAUTHORIZED` (401), `FORBIDDEN` (403), `NOT_FOUND` (404),
  `METHOD_NOT_ALLOWED` (405), `CONFLICT` / `BUSINESS_RULE_VIOLATION` (409), `TOO_MANY_REQUESTS` (429), `INTERNAL_ERROR` (500).
- `database/schema.sql` is a byte-identical copy of the canonical `project-docs/database/schema.sql`
  (checksum in `database/schema.sql.sha256`). **Never edit it**; Hibernate runs with `ddl-auto=validate`.
- API contracts live in `docs/api/` (CORE-003 … CORE-006).

## Tests

`mvn verify` — runs on the `test` profile with an in-memory H2 database; no SQL Server required.

## CORE-019 Integration Boundary

- Medical restriction endpoints derive availability from `medical_restriction`; `AVAILABLE` is never persisted. The active-row unique index in DB-01 remains the final duplicate-create guard.
- `MedicalRestrictionService.exerciseAllowed` exposes the `LIMITED`/`LOCKED` exercise gate for training scheduling and start workflows. This backend snapshot has no training controller/service implementation to wire into those call sites; that consumer integration remains pending.
- `CurrentActorProvider` is the CORE-007-compatible boundary. The CORE-007 provider/security implementation is not part of this branch; until a provider is wired, restriction endpoints fail closed with `401 UNAUTHORIZED`.
- H2 tests exercise persistence and a unique-key concurrency analogue. SQL Server integration and filtered-index behavior against a real SQL Server instance have not been run by these tests.

## Work tracking

Implementation follows `project-docs/planning/Racehorse_Core_Flow_Execution.xlsx` (one branch per CORE task:
`feat/CORE-XXX-short-description`).
