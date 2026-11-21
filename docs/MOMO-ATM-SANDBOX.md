# MoMo ATM sandbox — Issue #63

DEC-016 addendum: support MoMo **test ATM** (`payWithATM`) alongside the existing payment methods. No real payment endpoint is enabled. This candidate is stacked on BE PR #62 and needs its FE companion.

## Configuration

Set only in the local environment / secret manager:

- `MOMO_PARTNER_CODE`, `MOMO_ACCESS_KEY`, `MOMO_SECRET_KEY`: merchant **test** credentials issued by MoMo.
- `MOMO_ENDPOINT`: defaults to `https://test-payment.momo.vn`. The live money host is rejected. HTTP loopback is supported for explicit automated test fixtures only.
- `MOMO_RETURN_URL`: default `http://127.0.0.1:5173/thanh-toan/momo`.
- `MOMO_IPN_URL`: default `http://127.0.0.1:8080/api/payments/momo/ipn`. A local machine cannot receive the provider's remote IPN; use payment-check/query. A public IPN must be HTTPS.

Do not commit keys, browser-return signatures, card details, access tokens or private runtime files. Defaults contain no merchant credentials. Property string output is redacted.

## Contract and settlement

- `POST /api/owner/invoices/{id}/momo-payment` → `payUrl`, `txnRef`, `amount`, `expiresAt`. Own ISSUED invoice only; other owner 404, wrong role 403, already paid 409. ATM range: 10,000–50,000,000 VND.
- `POST /api/payments/momo/return` with `{ "params": { ...callback fields... } }`: authenticated Owner/CM. Validate HMAC, merchant, original request/order and exact VND amount; then query the gateway. Browser resultCode alone cannot pay an invoice.
- `POST /api/payments/momo/ipn`: public, HMAC-verified, exact transaction binding; valid callbacks acknowledge 204. Duplicates do not create another payment or notification.
- Existing Owner/CM `payment-check` dispatches by provider. Official query response is correlated over direct HTTPS; its documented payload has no response signature field. Requests are HMAC-signed.
- Only verified `resultCode=0` becomes SUCCESS/PAID. Pending/unknown responses remain PENDING. Known terminal cancellation/failure codes allow retry. Late verified money on an already settled/cancelled invoice is REFUND_REQUIRED.
- Invoice write lock is acquired by all create/settle/cancel paths. VietQR cannot settle while an online order is open. An uncertain create response retains its reserved MoMo order; checking is safe, making another order is blocked.
- MoMo's local `expiresAt` is a display hint, not proof of cancellation. A pending MoMo order stays blocked until a verified terminal result. Inconclusive queries require merchant investigation; no manual fake success fallback.

## Database

`V20261121_01__momo_atm_payment.sql` widens provider/confirmed-via CHECK constraints. Previous migrations and `database/schema.sql` are unchanged. The local installer now uses 10 ordered migrations and a captured `expected-schema-v20261121.json`.

Run the installer only on a **new dedicated test DB** using `docs/TEAM-DATABASE-SETUP.md`. Do not run install against an existing demo. An existing DB upgrade needs the reviewed migration process; changing source is not permission to rebuild a database.

## Verification and live acceptance

Automated tests use explicit H2/HTTP fixtures, not the MoMo service. They cover signature vectors, tampering, merchant/order/request/amount binding, owner scoping, query confirmation, callbacks, cancellation, uncertain requests and concurrent submissions. SQL Server schema capture and clean install use isolated `racehorse_local_duc_momo_20261121_*` databases, not the user's demo DB.

**Live sandbox ATM payment and real-flow screenshots are PENDING: no merchant test keys have been supplied.** Do not mark Issue #63 complete until a real test payment, return/query confirmation and history have been checked with issued test credentials. The existing DEC-016 decision file named in the issue was not present in this checkout; this document records only this issue's MoMo addendum.

Official references (checked 2026-11-21):

- [ATM one-time create and callback signing](https://developers.momo.vn/v3/docs/payment/api/atm/onetime/)
- [Query API and request signing](https://developers.momo.vn/v3/docs/payment/api/payment-api/query/)
- [IPN verification and 204 acknowledgment](https://developers.momo.vn/v3/docs/payment/api/result-handling/notification/)
- [Result codes](https://developers.momo.vn/v3/docs/payment/api/result-handling/resultcode/)
- [Official ATM test instructions](https://developers.momo.vn/v3/docs/payment/onboarding/test-instructions/)
