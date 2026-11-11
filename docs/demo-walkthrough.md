# FlowPay demo walkthrough

This is a short, repeatable walkthrough of the local application. It uses synthetic customers and payments. FlowPay does not contact a payment processor or move real money.

## Start the local app

From the repository root in Windows PowerShell, with Docker Desktop's Linux engine running:

```powershell
.\scripts\setup-local.ps1
docker compose up --build --detach --wait
```

Open `http://127.0.0.1:8080/login`. Setup creates an ignored local `.env` if one is not present. No credentials need to be copied into a browser or committed.

## Three-minute walkthrough

### 1. Enter an isolated workspace

Choose **Explore demo workspace**. The API creates or reuses a demo session, synthetic company, passwordless demo operator and four synthetic customers with sample payments. Each browser receives its own company and session. The session cookie is HttpOnly; business reads and writes use the authenticated server identity.

### 2. Show the real dashboard and records

Open **Overview**, then **Payments**. The values and rows come from the API and PostgreSQL, not hard-coded frontend fixtures. Point out that all amounts are BRL and formatted from decimal-string API values.

### 3. Prove idempotent creation

From Overview, choose **Run guided demo**. On the guide:

1. Select a synthetic customer, enter an amount such as `41.27`, add a short description, and choose a deterministic outcome.
2. Choose **Create simulated payment**. The API stores a `PENDING` payment, its initial event and the original creation response together.
3. Choose **Replay exact request**. The client sends the same normalized request with the same `Idempotency-Key`. The API must return the original payment ID and `Idempotency-Replayed: true`; the interface checks these actual response values before showing replay confirmation.
4. Choose **Process payment**. The server applies the configured `APPROVE` or `DECLINE` scenario, commits the final state and a new event, then the guide fetches and displays current payment history.

Explain the distinction: the replay response remains the original `201` creation snapshot; a separate read shows the payment's later current state. The original request and key exist in browser memory only while the guide is active. Reloading interrupts the guide and asks for a fresh run; it does not pretend the old replay is still available.

### 4. Inspect the API contract

Open `http://127.0.0.1:8080/api/docs` for the read-only Swagger UI, or `http://127.0.0.1:8080/api/openapi.yaml` for the canonical OpenAPI document. Swagger's “try it out” execution is disabled. Payment API writes require the normal session and CSRF token.

## What to say while presenting

> “This is a personal Java payment simulation. A retry after a lost response uses the same idempotency key and gets the original creation response, so it does not create a second payment. The API owns the transaction and final status; the UI confirms those actual responses. PostgreSQL locks protect competing state changes.”

Then be explicit: the demo does not call a bank or payment gateway; the outcomes are deterministic simulations. Demo workspaces are synthetic, isolated and expire after 24 hours. Local browser acceptance is documented in [product acceptance](verification/product-acceptance.md) and the guided path in [R18 evidence](verification/2026-11-10-guided-demo.md).

## Optional discussion

- Show `/payments/{id}` and its ordered event timeline.
- Describe how a second process/cancel request sees a final state and returns `409` instead of adding a second final event.
- Compare one idempotency key with a different request body: same key/same request replays; same key/different request conflicts.
- Discuss customer/company and payment/company scoping and why an unknown or foreign object ID is not disclosed.

For further technical detail, see [architecture](architecture.md) and [interview notes](interview-notes.md). Local launch is verified; public hosting is not available or claimed.
