# Automatic Context Engine

## 1. Objective

The engine must answer five questions reliably:

1. **What was the user doing?**
2. **Where did they stop?**
3. **What had already been accomplished?**
4. **What was likely next?**
5. **When are they probably returning to it?**

## 2. Evidence hierarchy

Use stronger evidence before weaker inference.

### High-confidence evidence

- selected repository/workspace,
- active file,
- branch,
- explicit user note,
- explicit start/resume action,
- test command/result,
- known artifact path.

### Medium-confidence evidence

- application title,
- browser title/domain,
- file sequence,
- recent edits,
- command sequence,
- temporal proximity.

### Lower-confidence evidence

- keyword similarity only,
- generic activity patterns,
- inferred goal without a user note.

## 3. Session segmentation algorithm

Pseudo-code:

```text
onEvent(event):
    append(event)
    updateCurrentActivity(event)

    if shouldPause(event):
        candidate = summarizeCurrentSession()
        confidence = scoreBoundary(candidate)

        if confidence >= FINALIZE_THRESHOLD:
            persistContext(candidate)
        else if confidence >= ASK_THRESHOLD:
            showMinimalPrompt(candidate)
        else:
            keepTransiently
```

`shouldPause` is true when one or more strong signals occur and the aggregate pause score exceeds the configured threshold.

Example pause score:

```text
lock/sleep                     +0.60
switch to untracked app         +0.35
idle > threshold               +0.30
workspace changed              +0.45
explicit pause                 +0.90
```

Combine scores with a cap of 1.0 and decay over time.

## 4. Noise suppression

Ignore or down-weight:

- rapid application switching under 3 seconds,
- repeated identical filesystem events,
- auto-save events without meaningful edits,
- background processes unrelated to selected workspaces,
- browser tabs outside the allowlist.

## 5. State reconstruction

Create a `StateEvidence` object before generating natural language.

```json
{
  "workspace": "demo-auth",
  "branch": "fix/jwt-refresh",
  "last_files": [
    "auth/authMiddleware.go",
    "auth/refresh_test.go"
  ],
  "last_commands": [
    "go test ./auth/..."
  ],
  "test_results": [
    {
      "command": "go test ./auth/...",
      "status": "failed",
      "reason": "401 after token expiry"
    }
  ],
  "browser": [
    {
      "title": "JWT refresh token documentation",
      "url": "https://example.com/docs"
    }
  ]
}
```

## 6. Next-action inference

Order of preference:

1. explicit user next action,
2. explicit task note,
3. unresolved test/error paired with active artifact,
4. unfinished checklist step,
5. inferred next action from event sequence,
6. unknown.

Never invent a next action when the evidence is insufficient.

When unknown, say:

> “CueBack found where you stopped, but it could not confidently infer the next action.”

Then offer one-tap input.

## 7. Context confidence model

Suggested initial formula:

```text
confidence =
    0.25 * source_continuity +
    0.20 * artifact_continuity +
    0.15 * temporal_continuity +
    0.15 * action_sequence_quality +
    0.15 * explicit_intent +
    0.10 * semantic_similarity
```

Clamp to `[0,1]`.

These weights are engineering defaults. They should be calibrated with real user traces during development rather than presented as scientifically established coefficients.

## 8. Return detection

A return candidate is generated when a previously inactive workspace becomes active again.

```text
currentActivity
      ↓
extract identity
      ↓
compare against recent contexts
      ↓
calculate match score
      ↓
if high confidence → open warm start
if medium confidence → suggest warm start
otherwise → no interruption
```

## 9. Chrono-adaptive reconstruction

Instead of hard-coding universal time claims, use configurable bands:

```text
0–5 min      micro warm start
5–30 min     concise recovery
30–120 min   detailed recovery
>120 min     full reconstruction
```

These are product defaults that can be A/B tested.

Add additional signals:

- whether the same task was resumed earlier today,
- number of state changes while away,
- confidence of return match,
- user preference for concise/full recovery.

## 10. Delta generation

### Git-aware delta

Compare:

- HEAD commit,
- current commit,
- branch,
- file status,
- file modification hashes.

### File-aware delta

For approved workspace files:

- compare metadata first,
- hash only if required,
- optionally compute textual diff locally.

### Browser delta

Compare:

- URL,
- title,
- open/closed state,
- domain.

Do not capture full webpage content by default.

## 11. Recovery renderer

The engine returns structured output.

```json
{
  "level": 2,
  "title": "Pick up JWT refresh work",
  "goal": "Fix expired-token refresh handling",
  "current_state": "Refresh interception implemented",
  "completed": [
    "Added refresh interceptor"
  ],
  "blocker": "Expired-token request still returns 401",
  "delta": [
    "No tracked file changes while away"
  ],
  "next_action": "Run auth/refresh_test.go expired-token case",
  "anchor": "auth/refresh_test.go:122-148",
  "confidence": 0.91
}
```

## 12. Automatic + human hybrid

The central design principle is:

```text
Machine-observable state
        +
Human strategic intent
        =
High-quality resumable context
```

Automatic collection should recover observable state. A one-question micro-prompt should recover missing intent.

## 13. Privacy-preserving inference

Do as much inference as possible from:

- metadata,
- file names,
- hashes,
- branch names,
- test summaries,
- application identifiers,
- user-entered text.

Only pass raw content to cloud AI after explicit opt-in and redaction.

## 14. Testing the engine

Create deterministic fixtures:

- coding session interrupted after failing test,
- study session interrupted after solving part of a problem,
- writing session interrupted mid-section,
- context resumed in a different project,
- false-return candidate,
- changed files while away,
- missing artifact,
- AI unavailable.

For each fixture assert:

- correct boundary,
- correct context identity,
- no unsupported next-action invention,
- correct recovery level,
- correct delta.
