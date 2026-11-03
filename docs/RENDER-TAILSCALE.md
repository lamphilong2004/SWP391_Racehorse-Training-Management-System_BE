# Render Free → Tailscale → Leader SQL Server

Leader-authorized deployment candidate based on BE `dev` at `5867f07ad521f2862eb80d5aa85c0bd615826855`. No service, tailnet membership, Windows/SQL setting or real data is changed by preparing this branch. Flow APIs, contracts, schema and seeding are unchanged. Review the PR before any manual deployment.

## Route and startup

```text
Vercel HTTPS → Render HTTPS / JWT / RBAC
  JDBC → 127.0.0.1:11433 (Java TCP bridge, same JVM)
    → SOCKS5 127.0.0.1:1055 (tailscaled userspace)
      → Tailscale ACL tag:racehorse-be → 100.83.34.4:1433
        → PHILONG-100604 / MSSQLSERVER / racehorse_demo
```

The Java bridge uses an explicit `java.net.Proxy.Type.SOCKS` socket. JDBC itself uses an ordinary local TCP connection. No global SOCKS assumption, HTTP reverse proxy, TLS termination, TUN, privileged container, Funnel, subnet route or public SQL listener is involved. TLS bytes pass unchanged through both proxies. Each connection is routed to exactly the approved SQL tailnet IP/port. The listener is hard-coded IPv4 loopback, with at most 12 concurrent forwarded connections; Hikari's existing pool remains five.

The existing Java21 image now includes the official Tailscale `v1.104.1` binaries. Spring Boot `PropertiesLauncher` selects the deployment-only `RenderTailscaleLauncher`; normal `java -jar` local development keeps its existing entrypoint. No business code starts until the launcher:

1. Requires all deployment secrets and the exact strict bridge JDBC URL/profile.
2. Decodes/loads a dedicated public-certificate-only PKCS12, refusing empty, unreadable, invalid, wrong-password or private-key stores.
3. Writes its runtime files to a private temporary directory (0700/files0600). Supplies the truststore through Hikari driver properties, not JDK `cacerts` or global JVM trust roots.
4. Starts `tailscaled --tun=userspace-networking --state=mem:` and a loopback SOCKS5 listener. Runs `tailscale up` with an auth-key **file**, the approved tag, DNS/routes/SSH disabled and a bounded timeout. Child environments contain no application credentials/auth key; child output is discarded. No key is passed as an argument or printed.
5. Deletes the temporary auth-key file after authentication. Opens a SOCKS TCP connection to the SQL IP before starting Spring; this is route readiness, **not** a SQL/TLS/authentication PASS.
6. Starts Spring with the existing staging guards, strict JDBC TLS and Hibernate `validate`. Startup cannot complete against an unavailable or incompatible database.

If daemon or bridge listener exits, the process exits70 for Render to recover. Transient per-connection failures close the affected sockets, and Hikari can establish new connections when the route recovers. `/api/health/ready` remains the SQL readiness signal; process liveness alone does not mean SQL is available. Shutdown closes only this bridge, its daemon and its own private temporary files. In-memory Tailscale state is lost on every restart: use a **reusable, ephemeral, preauthorized key** scoped to `tag:racehorse-be` and inspect/remove stale nodes if needed. Never broaden ACLs as a workaround.

## Exact Render settings

Create/configure the existing approved BE Web Service only after Leader review/approval. Docker runtime, **Free**, reviewed Git SHA, `Dockerfile`, auto deploy **Off**, health path `/api/health`. `render.yaml` is preparation, not an instruction to apply a Blueprint now. Do not use a dev/demo profile or an overridden entrypoint. Render terminates HTTPS; only Spring's HTTP port is publicly exposed by the service.

| Name | Purpose / non-secret example | Where |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Exactly `staging` | Environment |
| `PORT` | Provided by Render, normally `10000` | Render-managed environment |
| `DB_URL` | Exact string below | Environment |
| `DB_USERNAME` | Dedicated least-privilege login `racehorse_render` | Render environment, privately entered |
| `DB_PASSWORD` | Leader-owned SQL password; no shared default | Secret environment value |
| `TS_AUTHKEY` | Reusable ephemeral preauthorized key with `tag:racehorse-be` | Secret environment value |
| `TRUSTSTORE_PASSWORD` | Password used for the existing PKCS12 integrity check | Secret environment value |
| `TRUSTSTORE_BASE64_FILE` | `/etc/secrets/racehorse-truststore.p12.b64` | Environment: file path only |
| `racehorse-truststore.p12.b64` | Base64 encoding of the existing public-CA-only PKCS12; no private keys | **Render Secret File** |
| `JWT_SIGNING_KEY` | Approved Base64 random signing key, ≥32 decoded bytes | Secret environment value |
| `JWT_ACCESS_TOKEN_TTL_SECONDS` | Approved lifetime, e.g. `3600` | Environment |
| `CORS_ALLOWED_ORIGINS` | `https://racehorse-training-management.vercel.app` plus exact approved API Preview origins if used | Environment; no wildcard, slash/path or credentials |
| `STAGING_DOCS_USERNAME` | Separate documentation identity | Private environment value |
| `STAGING_DOCS_PASSWORD` | Separate documentation password, ≥20 characters | Secret environment value |
| `NETWORK_STARTUP_TIMEOUT_SECONDS` | `60` per auth/route stage, accepted range5–120 | Optional environment |

```text
DB_URL=jdbc:sqlserver://127.0.0.1:11433;databaseName=racehorse_demo;encrypt=true;trustServerCertificate=false;hostNameInCertificate=PHILONG-100604;loginTimeout=10
```

Do not append user/password/trust-bypass properties to this URL: the launcher requires the exact reviewed string. `hostNameInCertificate` identifies the SQL certificate, not the relay's loopback IP. Microsoft JDBC supports this property and PKCS12 `trustStoreType`; the Maven test interrogates the actual dependency's properties. **Actual certificate-chain/hostname handshake success or rejection still requires live SQL verification.** No `JAVA_TOOL_OPTIONS` with a password, debug configuration dumps, heap dumps, secret build arguments, private PFX or auth-key files belong in Git/CI/images.

### Existing Windows truststore → Render Secret File

Render Secret Files are text. Keep the existing binary `racehorse-truststore.p12` local; convert it to Base64 without printing its contents. In a local PowerShell window choose the actual existing file and a private, Git-ignored output path:

```powershell
$trustPath = Read-Host 'Absolute path to existing public-CA-only racehorse-truststore.p12'
$encodedPath = Read-Host 'Absolute private local output path ending .p12.b64 (outside Git)'
if (-not (Test-Path -LiteralPath $trustPath -PathType Leaf)) { throw 'Truststore missing' }
if (Test-Path -LiteralPath $encodedPath) { throw 'Refusing to overwrite output' }
[IO.File]::WriteAllText($encodedPath, [Convert]::ToBase64String([IO.File]::ReadAllBytes($trustPath)))
```

Limit that file's Windows access to the owner/required system administrators. In Render Environment → Secret Files, use filename `racehorse-truststore.p12.b64` and privately paste its contents. Enter the password separately as `TRUSTSTORE_PASSWORD`, not in the file name, URL or chat. The launcher decodes it in private runtime storage and binds driver properties `trustStore`, `trustStoreType=PKCS12`, `trustStorePassword`. It rejects private-key entries. Do not upload a PFX or CA private key. `.dockerignore` uses an allowlist and explicitly excludes binary stores/certificates/keys/Base64 files, including files Render places in the build context.

## Leader deployment and live verification

1. Review the exact PR/SHA and Linux CI results. CI uses no real key, SQL database, private truststore or Render account. No deployment is triggered by this PR. If CI fails, keep deployment pending.
2. Confirm laptop awake/connected, MSSQLSERVER and the approved DB available, certificate chain/hostname correct and the existing least-privilege SQL login valid. No service restart, SQL DDL, migration, seed or firewall change is authorized here.
3. Review existing Tailscale ACL/grants: only approved BE-tagged nodes may reach `100.83.34.4:1433`. If Windows Firewall blocks it, Leader checks effective rules and logs on the Tailscale adapter. A fix requires a separate approved rule limited to the tailnet source and TCP1433; **never** open all interfaces/public sources or disable the firewall. This branch does not modify rules.
4. Configure the reviewed Render Free service and the table above. Confirm Free plan and auto deploy Off before manually deploying. Do not create paid resources. Never expose ports1055/11433. No laptop public port forwarding is needed.
5. In the Tailscale admin console verify the Render node appears with `tag:racehorse-be` and the expected userspace client version. No tag/ACL edit is automatic. Logs should show only `NETWORK_CONFIG_VALIDATED`, `NETWORK_TAILSCALE_AUTHENTICATED`, `NETWORK_TCP_ROUTE_READY`, then normal Spring startup. A route-ready marker does not prove TLS/SQL. If startup fails, the launcher gives a fixed failure code; diagnose privately without posting raw secrets/logs.
6. Require Spring `staging` startup and Hibernate schema validation to complete. No automatic schema/seed action exists. Monitor **total** memory/CPU in Render, including cold start and five SQL connections. The image budgets heap192MiB, metaspace128MiB, code32MiB, direct32MiB and Tailscale soft Go limit96MiB. These are not additive hard RSS guarantees; JVM native memory/threads/stacks and Go/gVisor also consume RAM. A daemon-only measurement is not proof the full stack fits512MiB. On OOM/thrashing, stop and request a resource/architecture decision; do not disable security or upgrade automatically.
7. At the actual Render HTTPS URL verify `/api/health`200 (liveness), `/api/health/ready`200 (read-only `SELECT 1`), documentation unauthenticated401, separate Basic docs200. Docs credentials must not authorize `/api/horses`; business access requires a real JWT and approved role. Swagger must not persist authorization. Never put Basic passwords/JWT in screenshots, traces, HAR or shared commands/logs.
8. Before team use, Leader runs negative gates in an approved isolated validation window: wrong/untrusted public CA, wrong certificate hostname, missing/invalid key, stopped/unreachable SQL. Each must fail without a healthy app or secret disclosure. Wrong hostname in `DB_URL` is rejected immediately by the launcher; additionally verify the **actual driver's** certificate mismatch and untrusted-chain rejection using an approved JDBC probe in the same network environment. Configuration validation is not that handshake proof. Restore the correct settings manually; do not alter the SQL certificate or turn off validation.
9. Require a real JWT login → `/api/auth/me` → authorized Horse/Stable Box read. With separately approved disposable staging data, perform Horse create → SQL readback/list/detail → refresh and verify 401/403/logout. No one should infer write PASS from readiness or fixture tests. DB permissions must remain least privilege, without `sysadmin`/`db_owner`.
10. Only after the above, supply the actual HTTPS API origin to the FE owner. Configure an approved **API-mode Preview**, exact CORS origin and Authorization preflight, then run hosted browser E2E. Do not use the role-test Preview or change Vercel Production in this task.

Render Free can sleep/restart; the laptop must remain available. Availability is limited by both systems and key expiry. This is student staging, not a claim of production-grade uptime. Render Free has no interactive service shell; use reviewed diagnostics/metrics and the Tailscale admin console, not a shell-dependent recovery plan.

## Tests, CI and rollback

```text
mvn -B -ntp -Dtest=StagingDeploymentTest,RenderTailscaleTest,LoopbackTcpBridgeTest,FoundationApiTest,AuthenticationApiTest package
docker build -t racehorse-staging:verify .
```

The existing `Staging container` PR workflow builds on Linux/Java21, exercises missing-secret rejection, non-root userspace daemon startup **without joining a tailnet**, Spring failure against an unreachable SQL fixture, and records daemon-only memory. No secret artifacts or image are published. It is not real Tailnet/SQL/Render E2E. Local Maven uses JDK26 with release21; CI is needed to verify the exact Java21 image.

| Gate | Evidence type / current boundary |
|---|---|
| Maven staging, bridge and secret/truststore fixtures | Local socket/PKCS12/security fixtures; see PR test results |
| JWT/RBAC, CORS, health, Swagger isolation | Existing H2/MockMvc fixtures, not SQL evidence |
| Docker build / Java21 container / userspace initialization | GitHub `Staging container` result; NOT VERIFIED if missing or failed |
| Real auth key, tailnet ACL/Windows path, SQL TLS chain/hostname, schema, live readiness, writes/readback | NOT VERIFIED without Leader's runtime secrets and approved live access |
| Full-stack512MiB under load / reconnect / Render sleep-wake | NOT VERIFIED before hosted measurement |

Rollback: keep auto deploy Off and the previous known working Git revision/settings recorded privately. If the candidate fails, stop/suspend its staging service or manually return to the previous approved deployment. Stop sharing its URL. Remove/revoke only its ephemeral node/key under Leader authority when appropriate. Reverting to the old direct-DB image does **not** provide this private route, so it may remain unavailable: do not bypass TLS or open SQL to restore apparent availability. No database reset, restore, migration, instance change or Vercel Production rollback is part of this task.

## Primary references

- [Tailscale userspace networking / explicit SOCKS](https://tailscale.com/docs/concepts/userspace-networking)
- [Official v1.104.1 binary image build paths](https://github.com/tailscale/tailscale/blob/v1.104.1/build_docker.sh)
- [Auth-key file CLI support](https://github.com/tailscale/tailscale/blob/v1.104.1/cmd/tailscale/cli/up.go)
- [Microsoft JDBC TLS/hostname verification](https://learn.microsoft.com/en-us/sql/connect/jdbc/connecting-with-ssl-encryption)
- [Driver properties and PKCS12](https://learn.microsoft.com/en-us/sql/connect/jdbc/setting-the-connection-properties)
- [Boot PropertiesLauncher](https://docs.spring.io/spring-boot/specification/executable-jar/property-launcher.html)
- [Render Docker secret exclusion/runtime permissions](https://render.com/docs/docker-secrets)
- [Render Free limitations](https://render.com/docs/free)
