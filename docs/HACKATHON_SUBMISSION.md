# Shipaton 2026 Submission Package

## Project name

**CueBack**

## Tagline

**The Resume Button for Your Brain**

## One-line description

CueBack automatically remembers where you stopped working and reconstructs the context you need to continue when you return.

## Short description

> Interruptions force us to reload our working context: what we finished, what failed, where we were, and what we planned to do next. CueBack quietly captures structured context from the work sources you choose, detects when a task pauses, recognizes when you return, and opens a warm start with the exact next action. It is not a to-do list. It is a resume button for interrupted work.

## Full project description

### The problem

When we get interrupted, we often remember the task but lose the temporary state around it. We reopen the editor, document, or browser tab and spend time reconstructing what we were doing.

### The solution

CueBack creates a Context Capsule from structured work signals such as the selected workspace, active file, Git branch, recent change, test result, browser context, and optional user intent. When the user leaves, it preserves that state. When they return, CueBack detects the likely task, compares what changed while they were away, and shows the smallest useful recovery view.

### Why it is different

A reminder tells you **what** to do.
A focus blocker tries to stop interruptions.
A memory tool stores what happened.

CueBack answers:

> **Where was I when I stopped?**

### Automatic workflow

```text
Work
 ↓
CueBack detects task state
 ↓
Interruption
 ↓
Context Capsule saved automatically
 ↓
Time away
 ↓
Return detected
 ↓
Delta generated
 ↓
Adaptive Warm Start
 ↓
Exact next action
 ↓
Meaningful work resumes
```

## 2-minute demo script

### 0:00–0:10 — Hook

Show a developer working in VS Code.

Voiceover:

> “We don’t usually forget the task. We forget where we were inside the task.”

### 0:10–0:25 — Work

Open a demo repo.

Show:

- `authMiddleware.go`,
- failing expired-token test,
- terminal output.

### 0:25–0:35 — Interruption

Switch away.

Desktop agent captures the structured context.

Show a small status indicator:

> Context saved automatically.

### 0:35–0:50 — Return

Return to the same repository.

CueBack recognizes the context.

### 0:50–1:15 — Warm Start

Show:

> You were fixing JWT refresh authentication.
>
> Completed: refresh interceptor
>
> Blocker: expired-token request returns 401
>
> While away: no tracked file changes
>
> **Next: run the expired-token test**

### 1:15–1:30 — Exact anchor

Tap Resume.

Open the exact file/line.

### 1:30–1:42 — Re-entry metric

Show:

> Back in 18 seconds.

### 1:42–1:54 — Monetization

Show the real RevenueCat paywall.

> “Unlimited contexts and automatic recovery are Pro.”

### 1:54–2:00 — Closing

> “CueBack. Don’t save the task. Save your place.”

## Submission asset checklist

- [ ] Public store listing if entering standard store categories.
- [ ] RevenueCat configured.
- [ ] Trial or judge promo.
- [ ] ≤2-minute public demo video.
- [ ] 1024×1024 icon.
- [ ] Required screenshot(s).
- [ ] Text description.
- [ ] Source repository if submitting for Next Gen.
- [ ] Open-source license if submitting for Next Gen.
- [ ] #Shipaton public build posts if pursuing BuildInPublic.

## Category alignment notes

### Grand Prize

Focus the story on:

- real release,
- real users,
- real revenue,
- growth experiments,
- retention learning.

### Design Award

Focus on:

- Warm Start interaction,
- adaptive information density,
- motion,
- accessibility,
- calm, polished UI.

### Next Gen

Provide:

- public source repo,
- open-source license,
- high-quality video,
- working core functionality,
- clear RevenueCat integration.

### HAMM

Show:

- real paywall,
- real products,
- real trial/promo,
- entitlement gating,
- monetization logic that maps directly to recurring automation value.

### OneSignal

Show:

- context-aware push,
- deep-linked warm start,
- suppression/quiet hours,
- measurable post-notification resume action.

### Funnel Vision

Only include if actually implemented:

- public landing page,
- RevenueCat Funnels,
- Stripe checkout,
- conversion tracking,
- deep link into app.

## Devpost copy principles

Be factual. Do not claim:

- “first ever,”
- “no competitors,”
- “scientifically proven to save X%,”
- unsupported traction numbers.

Use actual observed data from the product and analytics.
