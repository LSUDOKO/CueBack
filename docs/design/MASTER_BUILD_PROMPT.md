# Master Build Prompt — CueBack

You are the principal engineer and product designer responsible for implementing **CueBack**, a production-quality context-recovery application for RevenueCat Shipaton 2026.

## Product mission

Build:

> **CueBack — The Resume Button for Your Brain**
>
> Automatically detect where a user stopped working, preserve the structured working context, detect when they return, and reconstruct the minimum context needed to continue with an exact next action.

Do not build a generic to-do list. Do not build a generic AI memory app. Do not fake automatic detection.

## Primary release target

Build a polished Android app using Kotlin + Jetpack Compose, plus a lightweight desktop companion that provides the cross-application context signals required for the core automatic workflow.

The Android application is the consumer product and must contain the complete user-facing experience, RevenueCat integration, notifications, settings, privacy controls, and context history.

The desktop companion is responsible for source-aware context collection from explicitly enabled workspaces and applications.

## Non-negotiable product loop

```text
USER WORKS
   ↓
AUTOMATIC DETECTION
   ↓
INTERRUPTION / PAUSE
   ↓
CONTEXT CAPSULE
   ↓
USER RETURNS
   ↓
RETURN MATCHING
   ↓
DELTA MANIFEST
   ↓
ADAPTIVE WARM START
   ↓
EXACT NEXT ACTION
   ↓
MEANINGFUL TASK ACTION
   ↓
RE-ENTRY TIME
```

Every implementation decision must strengthen this loop.

# Phase 0 — Engineering contract

Before writing code:

1. Inspect the repository.
2. Identify existing apps, modules, build systems, and secrets.
3. Preserve working code unless clearly obsolete.
4. Establish a clean module structure.
5. Add `.env.example` files.
6. Add README and developer setup.
7. Add automated formatting/lint/test commands.
8. Never commit credentials.

If the repository is empty, initialize the project according to this specification.

# Phase 1 — Android application

Implement:

- onboarding,
- privacy explanation,
- source permission controls,
- home screen,
- context cards,
- context detail,
- warm start,
- context history,
- search,
- settings,
- delete all data,
- notification settings,
- RevenueCat paywall.

Use:

- Kotlin,
- Jetpack Compose,
- Room,
- ViewModel/state flows,
- repository pattern.

Use Material 3 or an equivalent modern Compose design system.

The UI must be polished and accessibility-aware.

# Phase 2 — Context data model

Implement these core entities:

```text
Context
ContextArtifact
Event
ReentrySession
Settings
SubscriptionState
```

Context fields:

```text
id
taskTitle
goal
currentState
completed
blocker
nextAction
artifacts
spatialAnchor
confidence
createdAt
pausedAt
resumedAt
status
```

Add migration support for future schema changes.

# Phase 3 — Desktop companion

Build a lightweight desktop companion.

Preferred stack:

- Tauri,
- TypeScript frontend,
- Rust/native adapters as needed.

Implement real collectors:

1. active window,
2. selected workspace,
3. Git metadata,
4. filesystem metadata,
5. terminal activity summary where permitted,
6. optional VS Code extension.

Never default to continuous screen capture or continuous audio capture.

The user must explicitly choose monitored workspaces/apps.

## Desktop event model

Emit only structured events such as:

```text
APP_FOCUS_CHANGED
WORKSPACE_OPENED
FILE_OPENED
FILE_CHANGED
GIT_BRANCH_CHANGED
GIT_STATUS_CHANGED
TERMINAL_COMMAND_EXECUTED
EDITOR_CURSOR_MOVED
SESSION_IDLE
SESSION_RESUMED
DEVICE_LOCKED
DEVICE_UNLOCKED
```

Deduplicate noisy events.

# Phase 4 — Automatic session segmentation

Implement an actual boundary detector.

Signals:

- selected-app change,
- workspace change,
- idle period,
- device lock/sleep,
- explicit pause.

Combine multiple signals into a pause score.

When the score exceeds threshold:

1. close the active session,
2. extract structured evidence,
3. generate a Context Capsule,
4. calculate confidence,
5. persist it locally,
6. optionally sync it.

Do not require the user to press “Save” for normal automatic flow.

# Phase 5 — Intent capture fallback

When the engine cannot confidently infer the next action:

show:

> **What were you about to do next?**

Support:

- text,
- speech-to-text,
- skip.

This should take roughly a few seconds and must be optional.

# Phase 6 — Return detection

When tracked activity resumes, compare it against recent contexts.

Use:

- repository/workspace,
- current file,
- branch,
- app,
- URL/domain,
- keyword similarity,
- recentness,
- explicit user selection.

Create a configurable weighted score.

Example initial thresholds:

```text
>= 0.80  automatic warm start
0.60–0.79 suggested warm start
0.40–0.59 context inbox only
< 0.40   ignore
```

Never force a low-confidence recovery.

# Phase 7 — Chrono-Adaptive Warm Start

Implement three recovery levels.

## Level 1 — Micro

For short/low-disruption returns:

```text
Task title
Next action
```

## Level 2 — Recovery Card

Show:

- goal,
- current state,
- blocker,
- next action,
- while-away changes.

## Level 3 — Full reconstruction

Show:

- goal,
- why,
- completed actions,
- current state,
- blocker,
- delta,
- artifacts,
- spatial anchor,
- previous intent notes,
- exact next action.

Always end with one primary Resume action.

# Phase 8 — Delta Manifest

Implement local state comparison.

For Git:

- branch,
- status,
- changed file set,
- commit identity.

For files:

- modified time,
- existence,
- optional hash,
- optional local diff.

For browser:

- title,
- URL,
- domain,
- open/closed status.

Present only meaningful changes.

Example:

```text
WHILE YOU WERE AWAY
✓ No tracked code files changed
✓ Same branch
× JWT docs tab was closed
```

# Phase 9 — Exact artifact launch

Implement safe deep links:

- open file,
- open repository,
- open browser URL,
- open context.

Validate paths against approved workspaces.

Reject path traversal.

# Phase 10 — Re-entry Time

When Warm Start begins, create a `ReentrySession`.

When the first meaningful task action is observed, stop the timer.

Meaningful action examples:

- code edit,
- test command,
- document edit.

Persist:

```text
warmStartAt
firstMeaningfulActionAt
reentrySeconds
```

Display:

> Back in 18 seconds.

Do not frame it as a personal productivity score.

# Phase 11 — RevenueCat

Integrate the current RevenueCat SDK for Android.

Create:

```text
Entitlement: cueback_pro
Products:
  cueback_pro_monthly
  cueback_pro_annual
```

Implement:

- offerings retrieval,
- purchase,
- restore,
- entitlement state,
- loading/error states,
- offline fallback,
- premium feature gates.

Do not fake entitlement state.

Free tier:

- 3 active contexts,
- manual text capture,
- basic warm start,
- local history.

Pro:

- unlimited contexts,
- automatic desktop integrations,
- voice capture,
- adaptive warm starts,
- delta history,
- artifact vault,
- cross-device sync.

Show paywall after the user experiences a real successful recovery.

# Phase 12 — OneSignal

Integrate OneSignal.

Notifications:

1. Resume candidate.
2. Unresolved context reminder.
3. Optional daily context digest.

Every notification must deep-link to a useful state.

Respect:

- quiet hours,
- notification opt-out,
- per-context suppression,
- lock-screen privacy.

Do not send engagement-bait messages.

# Phase 13 — AI/context generation

Use hybrid inference.

### Deterministic first

Generate:

- artifact list,
- timestamps,
- branch,
- test status,
- file anchor,
- change summary.

### Semantic second

Use a lightweight local model or embeddings for matching.

### Cloud third

Provide an optional AI gateway only when the user enables cloud AI.

Use structured JSON output.

Before cloud submission:

- redact likely secrets,
- remove disallowed sources,
- minimize text,
- show user that cloud AI is enabled.

If AI fails, the app must still produce a useful deterministic Warm Start.

# Phase 14 — Privacy controls

Create a real Privacy screen with:

- source list,
- enabled/disabled switches,
- cloud AI toggle,
- cloud sync toggle,
- delete all data,
- export data.

Default:

```text
Cloud AI OFF
Cloud Sync OFF
Browser extension OFF
Other apps OFF
```

# Phase 15 — Backend

Use Go for optional sync/auth APIs.

Endpoints:

```text
POST /v1/pair/start
POST /v1/pair/complete
POST /v1/events/batch
GET  /v1/contexts
GET  /v1/contexts/{id}
POST /v1/contexts/{id}/resume
POST /v1/reentry/{id}/complete
```

The product must work without the backend for core local functionality.

# Phase 16 — Security

Implement:

- TLS,
- secure token storage,
- short-lived pairing tokens,
- schema validation,
- rate limiting,
- path validation,
- redaction,
- structured logs without sensitive task content.

Never log:

- API keys,
- auth headers,
- passwords,
- raw private messages,
- raw task content in analytics.

# Phase 17 — Analytics

Track only useful product/operational events:

```text
context_created
auto_context_created
warm_start_shown
warm_start_accepted
warm_start_rejected
artifact_opened
meaningful_action_detected
reentry_completed
paywall_viewed
trial_started
purchase_completed
notification_opened
```

Never attach task body or sensitive source content to analytics events.

# Phase 18 — Demo fixture mode

Create a developer fixture for one reliable story:

**JWT refresh authentication bug**

Fixture should replay structured events through the same real engine and UI.

Do not build a separate fake UI for the video.

# Phase 19 — Tests

Write:

- unit tests,
- context segmentation tests,
- match scoring tests,
- reconstruction tests,
- delta tests,
- repository tests,
- RevenueCat state tests,
- notification deep-link tests,
- privacy tests,
- security tests,
- end-to-end demo test.

Required E2E assertion:

```text
work
→ interruption
→ auto context
→ return
→ correct context
→ warm start
→ artifact launch
→ meaningful action
→ re-entry time
```

# Phase 20 — UI polish

Use a premium utility aesthetic:

- calm typography,
- clean cards,
- subtle animation,
- accessible controls,
- dark/light theme,
- reduced-motion support.

Do not over-design the dashboard. The Warm Start screen is the product hero.

# Phase 21 — Release preparation

Create:

- release build,
- app icon 1024×1024 source asset,
- store screenshots,
- privacy policy link/placeholder ready for deployment,
- demo account/test instructions if needed,
- judge trial/promo instructions,
- final README,
- final Devpost description.

Verify Shipaton's current requirements immediately before submitting.

# Phase 22 — Final acceptance criteria

The implementation is not complete until all are true:

- [ ] automatic context detection works on the selected demo workspace,
- [ ] context capsules contain real evidence,
- [ ] return detection identifies the demo context,
- [ ] warm start is rendered from production data,
- [ ] exact next action is grounded in evidence or marked as uncertain,
- [ ] delta manifest reflects real changes,
- [ ] artifact launch opens the correct target,
- [ ] Re-entry Time is measured from real activity,
- [ ] RevenueCat purchase/trial works in test mode,
- [ ] premium entitlement gates real features,
- [ ] OneSignal notification deep-link works,
- [ ] privacy controls actually stop collection,
- [ ] delete-all-data actually deletes data,
- [ ] cloud AI is truly optional,
- [ ] offline core flow still works,
- [ ] tests pass,
- [ ] release build succeeds,
- [ ] no secrets are committed.

# Coding standards

- Type-safe models.
- Small modules.
- Clear interfaces.
- No duplicated business logic.
- Avoid magic constants.
- Centralize thresholds/configuration.
- Meaningful error states.
- Structured logging.
- Unit-test core algorithms.
- No TODO placeholders in release paths.
- No fake API responses in production code.

# Final output expected from the coding agent

At the end of implementation, report:

1. files created/changed,
2. architecture summary,
3. features fully implemented,
4. features requiring external credentials/configuration,
5. test commands run and results,
6. release build result,
7. known limitations,
8. exact steps to configure RevenueCat,
9. exact steps to configure OneSignal,
10. exact steps to run the Android + desktop demo.

Never claim a feature is complete unless it is actually implemented and verified.
