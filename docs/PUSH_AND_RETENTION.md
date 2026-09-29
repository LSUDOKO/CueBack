# OneSignal Retention Design

## Principle

CueBack notifications should help the user recover context. They should never feel like generic engagement spam.

## Notification events

### 1. Resume candidate

Triggered when the system has strong evidence that the user has returned to a paused context.

Copy:

> **You’re back in JWT refresh work.**
> Your next action is ready.

Deep link directly to the Warm Start.

### 2. Unresolved-context reminder

Only for contexts with a meaningful unresolved state.

Copy:

> **CueBack saved your place.**
> JWT refresh is still waiting at the expired-token test.

### 3. Daily debrief

One optional digest:

> You paused 4 contexts today.
> 2 are ready to resume.

Tap → context library.

### 4. Return after a long gap

If the system sees a likely return to an old task:

> **Welcome back.**
> Here’s what you were doing when you last left `compiler-notes`.

## Journeys

### Journey A — First value

```text
Install
 ↓
Permission enabled
 ↓
First context captured
 ↓
First successful resume
 ↓
Invite user to enable intelligent reminders
```

### Journey B — Re-entry support

```text
Context paused
 ↓
Return detected
 ↓
Push with warm start deep link
 ↓
User resumes
 ↓
Measure re-entry time
```

### Journey C — Context aging

```text
Unresolved context ages
 ↓
No recent return
 ↓
One useful reminder
 ↓
If ignored, suppress further notifications
```

## Notification safety rules

- Respect quiet hours.
- Provide per-context notification controls.
- Provide global pause.
- Avoid sensitive task details on lock screens by default.
- Never include secrets, file contents, private URLs, or client data.
- Avoid repeated notifications for the same context.

## Metrics

Track:

- notification delivered,
- opened,
- warm start accepted,
- meaningful action after notification,
- notification opt-out,
- notification suppression rate.

The OneSignal category criteria emphasize correct implementation, user value, and creative/resourceful use of messaging. Shipaton's current rules state that a single deployed message is enough for eligibility, while more thoughtful use may receive stronger consideration.
