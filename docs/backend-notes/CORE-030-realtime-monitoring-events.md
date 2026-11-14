# CORE-030 — Realtime metrics, monitoring alerts and event delivery

## Scope and source of truth

Primary implementation: `src/main/java/com/racehorse/training/realtime/Core030Service.java`.

This note describes observed behavior and does not change Java source, message schemas or database writes.

## Metric intake

Manual metric submissions require the `WORK_RIDER` role and the assigned rider for the target session. Metrics are accepted only while the session is `IN_PROGRESS` or `PAUSED`.

Submission occurs inside a `SERIALIZABLE` transaction. The service locks the session, validates metric values, and resolves an effective critical heart-rate threshold from any applicable horse-specific override (falling back to the global value). It then persists a metric sample and calculates `NORMAL`, `WARNING`, or `CRITICAL`.

## Critical alert creation

A critical alert is created when a heart-rate reading crosses into the critical region from a noncritical prior reading, or when no prior applicable reading exists. Repeated readings still in the critical region do not create additional alerts until a later crossing occurs.

A warning event suggests reducing intensity. A critical alert can be acknowledged by the authorized trainer or veterinarian, subject to role-specific actions. The trainer can act to reduce intensity, pause or stop where the state and validation gates permit. A medically relevant stop can create an incident.

## Delivery after commit

The `send(destination, payload)` helper registers `afterCommit` publication when Spring transaction synchronization is active. Events are therefore not published for database transactions that subsequently roll back. Outside a synchronization scope the helper publishes immediately.

Observed topic families include:

- `/topic/sessions/{sessionId}/metrics`
- `/topic/sessions/{sessionId}/alerts`
- `/topic/sessions/{sessionId}/state`

The event names and topic paths here are implementation observations, not a newly introduced API contract.

## Maintenance notes

Keep the transaction and event-delivery relationship in mind when changing monitoring workflows: client-visible events should not claim changes that later roll back. This document does not imply that messaging or database tests have been rerun.
