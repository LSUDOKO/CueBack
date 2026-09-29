# CueBack Product Specification

## 1. Product thesis

CueBack reduces the cost of returning to interrupted work by preserving working context automatically and presenting only the context necessary to continue.

## 2. Product vocabulary

### Context
A coherent unit of work that can be resumed later.

### Session
A period during which CueBack sees enough activity to associate events with one context.

### Context Capsule
The structured persisted representation of a session's recoverable state.

### Warm Start
The UI presented when CueBack believes the user is returning to a previous context.

### Spatial Anchor
Where the user was in an artifact: file, line range, document section, browser page, design frame, etc.

### Delta Manifest
A comparison of known state at interruption versus known state at return.

### Re-entry Time
The measured time from warm-start display/acceptance to meaningful work resumption.

## 3. Functional requirements

### FR-01 — Onboarding

The app shall let a user:

- create a local identity/session,
- select a primary use case (Coding, Study, Writing, Research, Design, General),
- configure notification permission,
- configure desktop companion pairing,
- choose whether cloud AI is permitted,
- review what data CueBack observes.

Acceptance criteria:

- user understands permissions before enabling them,
- no background collection occurs before the corresponding permission/setting is enabled,
- user can skip desktop pairing and still use the mobile/manual experience.

### FR-02 — Source configuration

Users can allow/deny specific sources:

- application,
- workspace/repository,
- browser domain,
- editor integration,
- file types.

The configuration is stored locally and synced only when cloud sync is enabled.

### FR-03 — Automatic event capture

The desktop companion records structured events, not raw continuous screen/audio recordings.

Minimum event types:

```text
APP_FOCUS_CHANGED
WORKSPACE_OPENED
FILE_OPENED
FILE_CHANGED
GIT_BRANCH_CHANGED
GIT_STATUS_CHANGED
TERMINAL_COMMAND_EXECUTED
BROWSER_PAGE_ACTIVE
EDITOR_CURSOR_MOVED
SESSION_IDLE
SESSION_RESUMED
DEVICE_LOCKED
DEVICE_UNLOCKED
MANUAL_NOTE
```

Each event contains only the minimum fields needed by the engine.

### FR-04 — Automatic session boundary detection

A boundary candidate is created when:

- a tracked application is replaced by an untracked activity,
- no qualifying event occurs for the configured idle period,
- the device sleeps/locks,
- workspace context changes,
- the user manually pauses.

A boundary must be scored before being persisted as a finalized context.

### FR-05 — Context extraction

For a finalized context, CueBack derives:

- title,
- goal,
- current state,
- completed actions,
- unresolved state,
- next action,
- artifacts,
- anchor,
- confidence,
- evidence references.

### FR-06 — Human intent capture

When machine evidence is insufficient, CueBack prompts with a minimal question:

> “What were you about to do next?”

Input options:

- text,
- speech-to-text,
- skip.

The answer becomes `prospective_next_action` and is associated with the context.

### FR-07 — Return detection

CueBack shall evaluate whether a new activity is related to an existing context.

The matching score should combine:

```text
same workspace       +0.25
same active file     +0.20
same branch          +0.10
same application     +0.10
same URL/domain      +0.10
keyword similarity   +0.10
recentness           +0.10
explicit user match  +0.20
```

Weights are implementation defaults, not scientific constants. They must be configurable and tested.

The final score is normalized. Example thresholds:

```text
>= 0.80  auto-open warm start
0.60-0.79 suggest context
0.40-0.59 show in context inbox
< 0.40  no automatic action
```

### FR-08 — Warm-start depth

The recovery depth is chosen based on:

- interruption duration,
- confidence of context match,
- amount of state changed while away,
- user preference,
- whether the user explicitly asked for a full recovery.

#### Level 1 — Micro Warm Start

```text
Task: Fix JWT refresh flow
Next: Run the expired-token test
```

#### Level 2 — Recovery Card

```text
GOAL
Fix refresh-token authentication

YOU HAD
Implemented refresh interception

BLOCKER
Expired-token request returns 401

WHAT CHANGED
No tracked files changed

NEXT
Run auth/refresh_test.go
```

#### Level 3 — Full Reconstruction

```text
Goal
Why this matters
Completed
Open problem
Current state
Decision/context notes
What changed while away
Artifacts
Spatial anchor
Exact next action
[Resume]
```

### FR-09 — Artifact launch

The Warm Start UI can deep-link/open:

- local file,
- repository folder,
- browser URL,
- selected document,
- CueBack context note.

If the source cannot be safely opened, show the path/URL and explain why.

### FR-10 — Delta Manifest

For each tracked artifact, compare:

- existence,
- modified timestamp,
- content hash where permitted,
- git status,
- branch,
- file path,
- title/URL.

Do not upload file contents merely to generate a diff unless the user has explicitly enabled that behavior.

### FR-11 — Context history

Users can inspect:

- current contexts,
- paused contexts,
- completed contexts,
- context evolution,
- past warm starts,
- re-entry time.

### FR-12 — Search

Search fields:

- task title,
- artifact path,
- keyword,
- date,
- tag,
- repository.

Search should work fully offline for local data.

### FR-13 — Notifications

Notifications must be contextual and useful:

- resume candidate,
- unresolved task reminder,
- daily context debrief,
- return cue when the system has evidence of likely re-entry.

No engagement-bait notification copy.

### FR-14 — Subscription

RevenueCat controls premium entitlements.

Free tier default:

- up to 3 active contexts,
- manual text capture,
- basic warm starts,
- local context history.

Pro default:

- unlimited contexts,
- automatic context detection integrations,
- voice capture,
- richer recovery levels,
- context evolution/delta views,
- artifact vault,
- cross-device sync,
- advanced configuration.

Exact pricing is a testable business hypothesis; see `MONETIZATION.md`.

### FR-15 — Offline-first behavior

Core capabilities shall continue without network access:

- create/read contexts,
- local detection,
- local history,
- basic matching,
- basic warm start.

Cloud AI and cloud sync are optional enhancements.

## 4. Task-type templates

### Coding

Fields:

- repository,
- branch,
- active file,
- cursor line,
- test command,
- failure,
- next action.

### Study

Fields:

- course/topic,
- source document,
- section/page,
- concept being solved,
- uncertainty,
- next question/exercise.

### Writing

Fields:

- document,
- section,
- current thesis,
- last edited paragraph,
- unresolved point,
- next paragraph/action.

### Research

Fields:

- research question,
- sources consulted,
- evidence gathered,
- missing evidence,
- next source/query.

### Design

Fields:

- file/project,
- frame/artboard,
- selected object/group,
- current change,
- review issue,
- next edit.

## 5. Error and uncertainty behavior

CueBack should never present an uncertain inference as fact.

Use explicit labels:

- **Detected** — directly observed.
- **Inferred** — generated from evidence.
- **You said** — supplied by the user.
- **Unknown** — not available.

Example:

> Detected: `authMiddleware.go` was the last edited file.
>
> Inferred: You were likely debugging token expiry.
>
> You said: “Next I’ll run the expired-token test.”

## 6. Accessibility

- Dynamic text sizing.
- Strong focus order.
- Screen-reader labels for all icon-only buttons.
- Motion can be reduced.
- Do not make color the sole source of state.
- Voice input is optional.
- Warm Start cards remain understandable without animation.

## 7. Privacy UX

The first-run explanation must explicitly state:

- what is observed,
- what stays local,
- what can be sent to cloud AI if enabled,
- how to pause collection,
- how to delete all data.

## 8. Success metrics

Primary product metric:

**Re-entry Time** and the percentage of resumed contexts where the user performs a meaningful action within the first recovery session.

Secondary metrics:

- context reconstruction acceptance rate,
- false-positive warm-start rate,
- context match confidence,
- notification open rate,
- subscription conversion,
- 7-day and 14-day retained users.

Do not manufacture metrics for the hackathon. Report only actual measured results.
