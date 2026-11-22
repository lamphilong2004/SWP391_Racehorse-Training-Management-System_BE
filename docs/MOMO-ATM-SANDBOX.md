# MoMo ATM sandbox — Issue #63

DEC-016 addendum: support MoMo **test ATM** (`payWithATM`) alongside the existing payment methods. No real payment endpoint is enabled. This candidate is stacked on BE PR #62 and needs its FE companion.

## Configuration

Set only in the local environment / secret manager:

- `MOMO_PARTNER_CODE`, `MOMO_ACCESS_KEY`, `MOMO_SECRET_KEY`: merchant **test** credentials issued by MoMo.
- `MOMO_ENDPOINT`: defaults to `https://test-payment.momo.vn` (standard HTTPS port only). The live money host is rejected. HTTP loopback is supported only when the sole active Spring profile is `test`; normal demo/runtime and mixed profiles reject it before creating an order or accepting a callback. Invalid URI settings remain unavailable instead of causing a render/API error.
- `MOMO_RETURN_URL`: default `http://localhost:5173/thanh-toan/momo`. Open the FE on the same origin; switching between localhost and 127.0.0.1 loses an origin-scoped login session.
- `MOMO_IPN_URL`: default `http://127.0.0.1:8080/api/payments/momo/ipn`. A local machine cannot receive the provider's remote IPN; use payment-check/query. A public IPN must be HTTPS.

Do not commit keys, browser-return signatures, card details, access tokens or private runtime files. Defaults contain no merchant credentials. Property string output is redacted.

## Contract and settlement

- `POST /api/owner/invoices/{id}/momo-payment` → `payUrl`, `txnRef`, `amount`, `expiresAt`. Own ISSUED invoice only; other owner 404, wrong role 403, already paid 409. ATM range: 10,000–50,000,000 VND.
- `POST /api/payments/momo/return` with `{ "params": { ...callback fields... } }`: authenticated Owner/CM. Validate HMAC, merchant, original request/order and exact VND amount; then query the gateway. Browser resultCode alone cannot pay an invoice.
- `POST /api/payments/momo/ipn`: public, HMAC-verified, exact transaction binding; valid callbacks acknowledge 204. Duplicates do not create another payment or notification.
- Existing Owner/CM `payment-check` dispatches by provider. Official query response is correlated over direct HTTPS; its documented payload has no response signature field. Requests are HMAC-signed.
- Only verified `resultCode=0` becomes SUCCESS/PAID. Pending/unknown responses remain PENDING. Known terminal cancellation/failure codes allow retry. Late verified money on an already settled/cancelled invoice is REFUND_REQUIRED.
- Invoice write lock is acquired by all create/settle/cancel paths. VietQR cannot settle while an online order is open. An uncertain create response retains its reserved MoMo order; checking is safe, making another order is blocked.
- VietQR currently has no separate PENDING/reserved-link state: GET only supplies QR data, and POST records the owner's declaration. Tests cover an existing payable VNPay link, simultaneous VietQR/MoMo submissions and a completed VietQR payment. Merely displaying the QR is not treated as a server reservation; adding that behavior requires a separate business decision.
- MoMo's local `expiresAt` is a display hint, not proof of cancellation. A pending MoMo order stays blocked until a verified terminal result. Inconclusive queries require merchant investigation; no manual fake success fallback.

## Database

`V20261121_01__momo_atm_payment.sql` widens provider/confirmed-via CHECK constraints. Previous migrations and `database/schema.sql` are unchanged. The local installer now uses 10 ordered migrations and a captured `expected-schema-v20261121.json`.

Run the installer only on a **new dedicated test DB** using `docs/TEAM-DATABASE-SETUP.md`. Do not run install against an existing demo. An existing DB upgrade needs the reviewed migration process; changing source is not permission to rebuild a database.

## Verification and live acceptance

Automated tests use explicit H2/HTTP fixtures, not the MoMo service. They cover signature vectors, tampering, merchant/order/request/amount binding, owner scoping, query confirmation, callbacks, cancellation, uncertain requests and concurrent submissions. After integrating BE #62 at `ffa8f19`, all methods reuse its single invoice lock. Additional tests cover concurrent VNPay/MoMo requests and ensure a failed historical MoMo attempt never claims confirmation of a later VietQR payment. SQL Server schema capture and clean install use isolated `racehorse_local_duc_momo_20261121_*` databases, not the user's demo DB.

**Successful sandbox ATM payment and its screenshots remain PENDING.** Long verified the official public merchant test settings from `momo-wallet/payment/nodejs/MoMo.js` on his machine. Load them only into environment variables; do not copy values into source, logs or GitHub. The same public JavaScript settings returned HTTP 403 from this session in both PowerShell and Java before an API result. No successful live payment was verified here.

Long [explicitly authorized Ready for limited review](https://github.com/lamphilong2004/SWP391_Racehorse-Training-Management-System_BE/issues/63#issuecomment-7076465907): create order, redirect to MoMo, cancel/failure, signed return. His [reported dry-run](https://github.com/lamphilong2004/SWP391_Racehorse-Training-Management-System_BE/issues/63#issuecomment-7075910449) on BE `98550a8` / FE `d946301`, copy DB `Racehorse_dec016_dryrun`, reached a real 1006 cancellation with payment FAILED and invoice ISSUED. This is Long's earlier-version evidence, not an independent test of Duc's latest popup. Ready does not close Issue #63 or establish a PAID success. The latest FE uses the shared list/detail payment popup. Existing demo DB/accounts/launcher are unchanged; Long decides merge/migration after reviewing the limited scope. The DEC-016 decision file named in the issue was not present in this checkout; this document records only this issue's MoMo addendum.

Official references (checked 2026-11-21):

- [ATM one-time create and callback signing](https://developers.momo.vn/v3/docs/payment/api/atm/onetime/)
- [Query API and request signing](https://developers.momo.vn/v3/docs/payment/api/payment-api/query/)
- [IPN verification and 204 acknowledgment](https://developers.momo.vn/v3/docs/payment/api/result-handling/notification/)
- [Result codes](https://developers.momo.vn/v3/docs/payment/api/result-handling/resultcode/)
- [Official ATM test instructions](https://developers.momo.vn/v3/docs/payment/onboarding/test-instructions/)
