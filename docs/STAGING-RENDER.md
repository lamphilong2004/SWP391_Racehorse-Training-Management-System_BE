# Render staging

Deployment preparation for BE dev `9dc594d`; separate from Production and local-first developer databases. This patch maps to CORE-001 deployment/CORS/Swagger and CORE-007 security configuration under the Leader's direct deployment authorization. It does not complete either full CORE or modify DB-01, migrations or seeding.

## Build and deploy

```sh
docker build -t racehorse-staging .
```

The multi-stage image compiles on Java21 and runs only `StagingDeploymentTest`. It runs as non-root UID10001, defaults to profile `staging`, binds `0.0.0.0:${PORT:10000}`, and performs no schema creation, seed or migration. `.dockerignore` allows only pom/src and excludes local secret/certificate files. The public-repository `Staging container` PR check builds this image on a standard free GitHub runner and checks its Java version, runtime user/profile and failure without configuration. It does not publish an image or deploy a service.

After review and an approved reachable SQL staging database exist, create the Render Docker web service from the reviewed **dev commit**, Free plan, Dockerfile `./Dockerfile`, health path `/api/health/ready`, auto-deploy **Off**. `render.yaml` describes these settings but has not been applied. Do not create a service before credentials/network access are ready. A first deploy is an explicit hosting action; no paid resource is authorized by this file.

Configure through Render Environment, never build arguments, source or FE variables:

Variable | Value
---|---
SPRING_PROFILES_ACTIVE | `staging` only; cannot combine dev/test
DB_URL | `jdbc:sqlserver://<approved-host>:1433;databaseName=<staging-db>;encrypt=true;trustServerCertificate=false`; hostname must match certificate
DB_USERNAME / DB_PASSWORD | Dedicated least-privilege staging SQL login, privately supplied
JWT_SIGNING_KEY | Privately generated Base64 random key, at least32 decoded bytes
JWT_ACCESS_TOKEN_TTL_SECONDS | Explicit approved positive lifetime, e.g.3600
CORS_ALLOWED_ORIGINS | Exact FE HTTPS Preview origin(s), comma-separated; no wildcard/path
STAGING_DOCS_USERNAME / STAGING_DOCS_PASSWORD | Separate documentation credentials; password at least20 characters; never use a business account password

`PORT` is provided by Render. For a managed SQL endpoint with a public CA, use the Java trust roots and strict hostname validation. For a private CA, supply a dedicated public-CA truststore through a host secret file and the driver's trustStore/trustStorePassword properties; do not bake private keys, truststores or passwords into the image. Do not use Long's laptop SQL address, open laptop1433 or disable validation.

## HTTP boundaries

- `/api/health` retains existing public process liveness. It does not query SQL.
- `/api/health/ready` is staging-only; runs read-only `SELECT 1`, returns200 UP or503 with a generic unavailable error. It reveals no host, schema, credentials or exception. It does not prove business writes, canonical schema or authorization correctness.
- `/swagger-ui/index.html`, `/swagger-ui.html`, `/v3/api-docs` and child paths require separate HTTP Basic documentation credentials over Render HTTPS. A browser prompts for them, then Swagger's Authorize action takes a **real business JWT** for API calls. Swagger does not persist that token. Do not share screenshots/traces containing credentials or tokens.
- Documentation credentials apply only to documentation paths. The canonical JWT/RBAC chain remains responsible for every business API; Basic credentials cannot replace a business JWT. Staging does not enable dev/test public Swagger policy.
- No broad proxy/header trust or CORS wildcard is enabled. Incorrect browser origins must be rejected.

## Hosted gate and rollback

After a manual staging deploy, verify readiness200, unauthenticated docs401 and protected docs200, CORS OPTIONS allowing exactly the selected FE origin/Authorization, real login and `/me`, SQL-backed Horse read/write/readback, role denial and logout. Then build FE Preview with `RACEHORSE_STAGING_MODE=api`, `VITE_API_BASE_URL=https://<actual-render-origin>` and no role-test/sample flag. Run hosted Login/Create/List/Detail/Refresh before any Production proposal.

Without an approved SQL endpoint, stage remains **BLOCKED**, not online. On deployment failure retain the previous service revision; use Render's manual rollback/previous commit. No reset/restore/migration is performed automatically. Deleting resources or changing DB data requires separate approval.

## SQL choices for Leader approval

1. **Recommended initial pilot: Azure SQL Database free offer.** $0 database usage within100,000 vCore-seconds/month and32GB data, selecting auto-pause at free limit instead of paid overage. Azure account/subscription eligibility is required; Students Starter is excluded. Configure the canonical collation on a new empty DB, apply unchanged approved scripts only after approval, and verify compatibility. This is SQL Server-compatible Azure SQL, not proof that DB-01 has passed on that service. Restrict its firewall to Render outbound ranges and the approved installation IP; never enable all Azure services. A managed public SQL endpoint is still network-restricted and TLS-protected. Periodic readiness queries keep consuming free compute while BE runs; stop staging when not testing and watch quota. Cold start/quota exhaustion can block demo availability.
2. **Full engine fallback: dedicated Linux x86-64 VM with SQL Server Express.** Example budget: Hetzner CX23 €5.49/month excluding IPv4/VAT under its15June2026 list, plus IPv4/backups as selected. Confirm actual availability, region,4GB RAM specification and final price before ordering. Install approved SQL Server version/schema/collation, a valid hostname/certificate, restricted SQL login and firewall only for Render outbound ranges. Higher operations burden: patching, backup and certificate renewal. No laptop SQL exposure; no VM purchase/provisioning is authorized yet.

References checked19October2026: [Render Docker](https://render.com/docs/docker), [health checks](https://render.com/docs/health-checks), [Blueprint](https://render.com/docs/blueprint-spec), [outbound ranges](https://render.com/docs/outbound-ip-addresses), [Azure free offer](https://learn.microsoft.com/en-us/azure/azure-sql/database/free-offer?view=azuresql), [student eligibility](https://learn.microsoft.com/en-us/azure/azure-sql/database/free-offer-faq?view=azuresql), [VM price](https://docs.hetzner.com/general/infrastructure-and-availability/price-adjustment/), [public CI costs](https://docs.github.com/en/billing/concepts/product-billing/github-actions).
