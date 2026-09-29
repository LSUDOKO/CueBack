# CueBack — The Resume Button for Your Brain

## One-line pitch

**CueBack automatically remembers where your work stopped, reconstructs the useful context, and brings you back to the exact next action when you return.**

## Core promise

> **Don’t save the task. Save your place.**

CueBack is a context-recovery product rather than a conventional to-do list. A task manager records *what* needs to be done; CueBack records the working state required to continue the task after an interruption.

### Product loop

```text
WORK → INTERRUPTION → AUTOMATIC STATE CAPTURE → TIME PASSES → RETURN DETECTION
     → CONTEXT RECONSTRUCTION → EXACT NEXT ACTION → FIRST MEANINGFUL ACTION
     → RE-ENTRY TIME MEASUREMENT → CONTEXT EVOLUTION
```

## Problem

Interruptions do not necessarily erase the goal. They can destroy the temporary working context around the goal: what has already been completed, what remains unresolved, which artifact was active, why a decision was made, and what the next action was supposed to be.

Research on interruption and task resumption describes resumption lag and the need to reconstruct suspended-task context. Recent systems research such as **TaCoS: Generated Context Summaries for Task Resumption** explores automatically generated context summaries for this problem. The analysis supplied for CueBack also emphasizes a key product insight: observable machine state and human strategic intent are complementary, not interchangeable.

## Product definition

**CueBack preserves working context before an interruption and reconstructs the smallest useful amount of that context when the user returns.**

The system should automatically infer:

1. when a work session has ended or paused,
2. which task/context was active,
3. what observable state existed at the stopping point,
4. what changed while the user was away,
5. when the user is likely returning to that task,
6. how much context is needed for a useful warm start.

Manual capture remains available as a fallback, not as the core workflow.

## Product boundaries

CueBack is **not**:

- a generic task manager,
- a focus blocker,
- a continuous surveillance recorder,
- a general-purpose AI memory product,
- a screenshot-to-reminder utility.

CueBack is a **context recovery / cognitive continuity system**.

## Primary user

Start with a narrow user profile for the hackathon:

- developers,
- students doing long-form technical work,
- writers/researchers/designers who frequently switch contexts.

The first demo should focus on **developer + technical study workflows** because they provide highly visible state signals such as files, branches, terminal commands, active documents, browser tabs, and exact next actions.

## Core experience

### 1. Automatic Context Detection

Users choose what CueBack is allowed to observe:

- selected desktop workspaces,
- selected applications,
- selected browser domains or tabs,
- selected projects/repositories.

CueBack observes only those sources and creates a local event stream.

### 2. Automatic Session Segmentation

A session is considered paused when multiple signals agree, for example:

- active app changes from a tracked work app,
- no tracked work activity for a configurable period,
- machine lock/sleep,
- tracked workspace becomes inactive,
- explicit pause.

The segmentation engine should not rely on a single heuristic.

### 3. Context Capsule

Every saved context contains:

- task title,
- goal,
- current state,
- completed work,
- unresolved issue/blocker,
- likely next action,
- active artifacts,
- spatial anchor,
- timestamp,
- confidence score,
- source evidence.

### 4. Return Detection

When the user comes back, CueBack computes whether the new activity is related to a previous context.

Signals include:

- same repository/workspace,
- same file/document,
- same application,
- same URL/domain,
- same task keywords,
- same branch,
- recent context similarity,
- explicit user selection.

### 5. Chrono-Adaptive Warm Start

The longer and more disruptive the interruption, the more reconstruction CueBack provides.

Example behavior:

- **Short interruption:** one-line next action.
- **Medium interruption:** recovery card with current state + next action + delta.
- **Long interruption:** full context reconstruction with goal, reasoning, artifact trail, changes, and launchpad.

The duration thresholds are product hypotheses and should be configurable/experimentable, not presented as universal scientific constants.

### 6. Delta Manifest

On return, CueBack compares the saved state with the current state and displays:

> **What changed while you were away?**

Example:

- `authMiddleware.go` changed by another process.
- Git branch unchanged.
- JWT docs tab closed.
- No new test results found.

### 7. Exact Re-entry

The most important output is not a summary. It is an actionable re-entry point:

> **Next:** run the expired-token test in `auth/refresh_test.go`.

A warm start should always end with one obvious action.

### 8. Re-entry Time

CueBack measures time from warm-start presentation/acceptance to the first meaningful tracked activity.

The metric is:

```text
Re-entry Time = timestamp(first meaningful task action)
                  - timestamp(warm start opened)
```

Examples of meaningful actions:

- first code edit,
- first terminal command in the selected workspace,
- first document edit,
- first study note edit,
- first design action in a tracked design tool.

The user should own the metric; it is not presented as a score of personal worth or productivity.

## Product modules

### Mobile app

Primary responsibilities:

- onboarding,
- permissions and privacy controls,
- active contexts,
- context history,
- warm-start cards,
- notification settings,
- subscription/paywall,
- account/settings.

### Desktop companion

Primary responsibilities:

- active-window detection,
- selected workspace monitoring,
- git/workspace metadata,
- file/change metadata,
- optional editor integration,
- optional browser extension integration,
- local event stream,
- secure sync to the mobile app.

### Backend

Only required for features that cannot remain entirely local:

- authentication,
- RevenueCat customer mapping if needed,
- opt-in cloud backup/sync,
- optional AI summarization provider gateway,
- telemetry/analytics with consent.

The privacy-first default is local-first operation.

## Suggested hackathon stack

### Android

- Kotlin
- Jetpack Compose
- AndroidX
- Room
- WorkManager
- UsageStatsManager where appropriate and permitted
- OneSignal SDK
- RevenueCat SDK

### Desktop companion

- Tauri + TypeScript for a lightweight desktop shell
- Rust/native adapters where OS-level active-window access is needed
- SQLite/JSON event buffer
- Git CLI/libgit2 integration
- VS Code extension as the first deep integration
- Browser extension as an optional integration

### Backend

- Go
- REST/WebSocket API
- PostgreSQL for cloud sync metadata
- Redis only if later required

### AI/context extraction

Use a hybrid engine:

- deterministic extractors first,
- structured summarizer second,
- optional LLM refinement third.

Do not make the core product dependent on an expensive model call.

## Shipaton 2026 alignment

The official Shipaton requirements state that a qualifying project must be a working app on iOS, iPadOS, macOS, or Android and use RevenueCat for at least one purchase; the first public version must be released during the Aug 1–Sep 30, 2026 submission period. The Devpost listing also requires a public demo video of no more than two minutes, a published store URL for standard categories, an icon, a screenshot, and a free trial or promo code that lets judges test premium functionality. See the official rules before submission because dates and requirements are controlled by the sponsor.

Relevant categories to design for:

- **Grand Prize:** real release, traction, growth experiments, and concrete results.
- **RevenueCat Design Award:** polished interaction, animation, and product craft.
- **Next Gen Award:** student-focused path with video + public open-source repository.
- **HAMM Award:** strong RevenueCat monetization strategy and fit.
- **Keep Them Coming Back (OneSignal):** useful re-engagement messaging/Journeys.
- **Funnel Vision (Stripe):** optional web-to-app funnel using RevenueCat Funnels + Stripe.
- **Ship Kotlin Everywhere:** only pursue if an iOS + Android Compose/KMP implementation can be polished enough to be genuinely functional.
- **Productivity — Christopher Lawley:** potentially relevant only through a strong, polished reusable-content/artifact organization workflow; do not claim CueBack is a direct match to the exact brief unless the product actually implements that workflow.

## Submission guardrails

Never fake:

- automatic detection,
- context reconstruction,
- RevenueCat purchase state,
- notification delivery,
- analytics numbers,
- user growth,
- store listing status.

The demo should use the actual application with deterministic test fixtures where external state cannot be reliably demonstrated.

## Research and source notes

See `RESEARCH.md` for the research basis, source links, market positioning, and claims that should be phrased cautiously.

## Documentation map

- `PRODUCT_SPEC.md` — complete functional product requirements.
- `TECHNICAL_ARCHITECTURE.md` — system architecture and data flow.
- `AUTOMATIC_CONTEXT_ENGINE.md` — detection, inference, confidence, and reconstruction algorithms.
- `UX_UI_SPEC.md` — screen-by-screen UX and interaction specification.
- `MONETIZATION.md` — RevenueCat products, entitlements, paywall logic, and pricing hypotheses.
- `ONESIGNAL_RETENTION.md` — notification events and Journeys.
- `PRIVACY_SECURITY.md` — data model, consent, local-first design, secrets, deletion.
- `IMPLEMENTATION_PLAN.md` — build sequence, milestones, and acceptance gates.
- `TESTING.md` — unit, integration, E2E, privacy, and release checks.
- `HACKATHON_SUBMISSION.md` — Devpost-ready positioning, demo script, assets, and submission checklist.
- `MASTER_BUILD_PROMPT.md` — one prompt for an AI coding agent to build the project.
- `ENVIRONMENT.md` — environment variables and service configuration.
