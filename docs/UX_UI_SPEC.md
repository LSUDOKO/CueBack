# CueBack UX/UI Specification

## Design direction

**Feel:** calm, premium, intelligent, extremely focused.

CueBack should feel like a utility that quietly restores continuity—not another productivity dashboard filled with charts.

## Core visual language

- dark/light system theme support,
- generous spacing,
- strong typography hierarchy,
- cards with subtle elevation,
- minimal iconography,
- a single primary action on each recovery surface,
- restrained animation,
- immediate feedback.

## Information architecture

```text
Home
 ├── Active contexts
 ├── Suggested resume
 ├── Recent contexts
 └── Re-entry metrics

Context
 ├── State
 ├── Artifacts
 ├── Timeline
 └── Settings

Warm Start
 ├── Micro view
 ├── Recovery card
 └── Full reconstruction

Library
 ├── Search
 ├── Filters
 └── Context history

Settings
 ├── Privacy
 ├── Sources
 ├── Notifications
 ├── AI
 ├── Subscription
 └── Delete data
```

## Screen 1 — Home

Header:

> Good evening
> Pick up where you left off.

Primary card:

> **JWT refresh authentication**
> Last active 37m ago
> Next: Run the expired-token test
> [Resume]

Below:

> Active contexts

Each card shows:

- task,
- last active time,
- unresolved state,
- confidence if relevant,
- quick Resume.

## Screen 2 — Detection permission

Explain exactly what is being observed.

Example:

> CueBack can watch selected work apps and projects to detect when a task pauses and when you return. It stores structured work context by default—not continuous screen recordings.

Controls:

- Select apps
- Select workspaces
- Browser domains
- Pause collection
- Learn more

## Screen 3 — Warm Start

### Hero

> **Welcome back.**

> You were fixing JWT refresh authentication.

Then:

```text
YOU HAD
Refresh interceptor implemented

BLOCKER
Expired-token request returns 401

WHILE YOU WERE AWAY
No tracked files changed

NEXT
Run the expired-token test
```

Primary CTA:

> **Resume where I left off**

Secondary:

> Show full context

Tertiary:

> Not this task

## Screen 4 — Full reconstruction

Sections:

1. Goal
2. Why
3. Current state
4. Completed
5. Blocker
6. What changed while away
7. Artifacts
8. Last known location
9. Next action

The final section is always the most prominent.

## Screen 5 — Re-entry result

After meaningful activity:

> **Back in 18 seconds**
>
> CueBack measured the time from your warm start to your first meaningful task action.

Do not present this as a competitive score.

## Screen 6 — Context history

Searchable history with filters:

- Today
- Yesterday
- This week
- Coding
- Study
- Unresolved

Each result shows the last known next action.

## Screen 7 — Context detail

Show:

- context timeline,
- state changes,
- artifacts,
- previous warm starts,
- re-entry sessions.

## Screen 8 — Paywall

Trigger only after value has been demonstrated.

Example:

> **Never reload your work from scratch again.**

Pro includes:

- unlimited contexts,
- automatic recovery integrations,
- richer warm starts,
- voice capture,
- cross-device sync,
- artifact history.

CTA:

> Start free trial

Secondary:

> Continue free

## Screen 9 — Notification actions

Every notification should deep-link directly to a warm start or context.

Example:

> **CueBack**
> You’re back in `demo-auth`.
> Your next action is ready.

Buttons:

- Resume
- Dismiss

## Animation specification

Warm Start transition:

1. blurred context background,
2. task title fades in,
3. state sections appear sequentially,
4. next action slides into focus,
5. Resume CTA becomes active.

Keep total animation under 500–700 ms and provide reduced-motion behavior.

## Empty states

### No context yet

> You’re all set.
> CueBack will quietly remember where you stop.

### No confident match

> CueBack found a few recent contexts, but none is a confident match.

### Desktop offline

> Desktop companion is offline. Manual contexts still work.

## Copy principles

Prefer:

> “You were here.”

Over:

> “AI generated activity summary.”

Prefer:

> “Next action”

Over:

> “Recommended productivity optimization.”

The UI should feel human and direct.
