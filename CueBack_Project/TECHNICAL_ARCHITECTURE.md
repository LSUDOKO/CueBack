# CueBack Technical Architecture

## 1. Architecture goals

The system must be:

- local-first,
- privacy-preserving,
- observable,
- deterministic where possible,
- AI-assisted rather than AI-dependent,
- resilient to offline operation,
- suitable for a real production release.

## 2. High-level architecture

```text
┌──────────────────────────────────────────────┐
│                  USER                        │
└──────────────────────┬───────────────────────┘
                       │
              ┌────────▼────────┐
              │ Android App     │
              │ Compose         │
              │ - Warm Start    │
              │ - History       │
              │ - Settings      │
              │ - RevenueCat    │
              │ - OneSignal     │
              └────────┬────────┘
                       │ secure local sync / WebSocket
              ┌────────▼────────┐
              │ Desktop Agent   │
              │ - active window │
              │ - workspace     │
              │ - Git           │
              │ - browser       │
              │ - editor        │
              └────────┬────────┘
                       │
              ┌────────▼────────┐
              │ Context Engine  │
              │ - segmentation  │
              │ - extraction    │
              │ - matching      │
              │ - reconstruction│
              │ - confidence    │
              └────────┬────────┘
                       │
                ┌──────▼──────┐
                │ Local Store  │
                │ SQLite/Room │
                └──────┬──────┘
                       │ optional opt-in sync
                 ┌─────▼─────┐
                 │ Go Backend│
                 │ Auth/Sync │
                 └───────────┘
```

## 3. Android app modules

```text
app/
  ui/
    onboarding/
    home/
    context/
    warmstart/
    history/
    paywall/
    settings/
  data/
    db/
    repository/
    network/
    billing/
    notifications/
  domain/
    model/
    usecase/
    matching/
  services/
    pairing/
    sync/
```

Use repository interfaces so local and cloud data sources are interchangeable.

## 4. Desktop agent modules

```text
desktop/
  src/
    collectors/
      activeWindow/
      git/
      filesystem/
      terminal/
      browser/
    session/
    privacy/
    transport/
    storage/
    pairing/
```

### Collector rules

Collectors should:

- run only after user permission,
- emit structured metadata,
- be independently enabled/disabled,
- fail closed if permissions disappear,
- avoid storing raw screenshots/audio by default.

## 5. Event schema

Example JSON:

```json
{
  "id": "evt_01J...",
  "timestamp": "2026-09-28T19:12:15Z",
  "type": "FILE_CHANGED",
  "source": "vscode",
  "context": {
    "workspace_id": "ws_demo",
    "repository": "demo-auth",
    "branch": "fix/jwt-refresh",
    "file": "auth/authMiddleware.go"
  },
  "payload": {
    "line_start": 84,
    "line_end": 101,
    "change_kind": "edit"
  },
  "privacy": {
    "contains_raw_content": false
  }
}
```

## 6. Context schema

```json
{
  "id": "ctx_01J...",
  "task_title": "Fix JWT refresh authentication",
  "epistemic_goal": "Make expired-token requests refresh correctly",
  "attentive_state": {
    "summary": "Refresh interceptor implemented; expired-token case remains failing"
  },
  "prospective_next_action": "Run expired-token integration test",
  "blocker": "401 response after token expiry",
  "spatial_anchor": {
    "type": "file",
    "path": "auth/refresh_test.go",
    "line_start": 122,
    "line_end": 148
  },
  "artifacts": [
    "auth/authMiddleware.go",
    "auth/refresh_test.go"
  ],
  "confidence": 0.91,
  "created_at": "2026-09-28T18:52:00Z",
  "paused_at": "2026-09-28T19:12:15Z"
}
```

## 7. Database model

Tables/entities:

### users

- id
- created_at
- last_seen_at
- locale

### contexts

- id
- user_id
- task_title
- goal
- current_state
- next_action
- blocker
- confidence
- created_at
- paused_at
- resumed_at
- status

### context_artifacts

- id
- context_id
- type
- locator
- title
- hash
- last_seen_at

### events

- id
- context_id nullable
- timestamp
- type
- source
- structured_payload

### reentry_sessions

- id
- context_id
- warm_start_at
- resumed_at
- first_meaningful_action_at
- reentry_seconds

### settings

- user_id
- source_permissions
- idle_threshold
- notification_preferences
- ai_mode
- cloud_sync_enabled

### subscriptions

- user_id
- revenuecat_app_user_id
- entitlement
- status

## 8. Context engine pipeline

```text
Raw structured events
       ↓
Normalize
       ↓
Remove duplicate/noisy events
       ↓
Detect session boundary
       ↓
Extract evidence
       ↓
Build state object
       ↓
Generate title/summary/next action
       ↓
Calculate confidence
       ↓
Persist Context Capsule
       ↓
Wait for return signal
       ↓
Match against existing contexts
       ↓
Build Delta Manifest
       ↓
Select recovery depth
       ↓
Render Warm Start
```

## 9. AI architecture

### Tier 0 — deterministic

Use rules and structured data for:

- session boundaries,
- artifact lists,
- git branch/status,
- timestamps,
- file anchors,
- change detection.

### Tier 1 — local NLP

Use lightweight local models or embeddings only for:

- title generation,
- semantic matching,
- concise summarization.

### Tier 2 — cloud LLM (opt-in)

Use an OpenAI-compatible provider or other configured service for:

- complex reconstruction,
- long-context synthesis,
- ambiguous next-action inference.

Inputs must be redacted/filtered according to privacy settings.

## 10. Pairing and sync

Use a short-lived pairing code or QR token.

Recommended flow:

1. Android creates pairing request.
2. Desktop displays challenge.
3. User confirms the same code on both devices.
4. Both derive a session key.
5. Structured events are sent over authenticated local transport.
6. Optional cloud sync uses end-to-end encrypted payloads if implemented.

Do not embed static shared secrets in the application.

## 11. API outline

### POST /v1/pair/start

Creates pairing challenge.

### POST /v1/pair/complete

Completes pairing.

### POST /v1/events/batch

Accepts structured events.

### POST /v1/contexts/rebuild

Rebuilds a context from recent evidence. Cloud AI only if permitted.

### GET /v1/contexts

Returns contexts for the authenticated user.

### GET /v1/contexts/{id}

Returns one context and artifact metadata.

### POST /v1/contexts/{id}/resume

Creates a re-entry session.

### POST /v1/reentry/{id}/complete

Marks first meaningful action.

## 12. Security controls

- HTTPS in production.
- Signed authentication tokens.
- Short-lived pairing credentials.
- Encrypted local database where supported.
- Secure OS storage for refresh tokens/API credentials.
- Never log raw event payloads containing user content.
- Redact secrets before telemetry/AI.
- Rate-limit public APIs.
- Validate all deep-link locators.
- Restrict file launch to user-approved paths.

## 13. Failure modes

### Desktop unavailable

Mobile app continues with manual contexts and notification-based recovery.

### AI unavailable

Fall back to deterministic context card from structured evidence.

### Sync unavailable

Queue locally and retry with exponential backoff.

### Return match uncertain

Show a suggestion rather than automatically opening a warm start.

### Artifact moved

Display last-known path and attempt workspace-relative resolution.

## 14. Production observability

Collect privacy-safe operational metrics:

- event processing latency,
- context generation failures,
- sync latency,
- push delivery failures,
- billing errors,
- crash-free sessions.

Do not record task contents as analytics.
