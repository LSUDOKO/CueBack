# Testing and Quality Plan

## 1. Quality principle

The product promise is trust-sensitive. A wrong warm start is worse than no warm start because it creates confusion.

Therefore, prioritize precision and graceful uncertainty over aggressive automation.

## 2. Unit tests

### Context segmentation

Test:

- app switch,
- idle timeout,
- lock/sleep,
- explicit pause,
- rapid switch noise,
- workspace changes.

### Matching

Test:

- same repository,
- same file,
- same branch,
- same URL,
- different project,
- stale context,
- ambiguous contexts.

### Reconstruction

Assert:

- explicit next action wins,
- unsupported inference is marked unknown,
- evidence is retained,
- correct recovery level selected.

### Billing

Test entitlement states:

- active,
- inactive,
- unknown/network error,
- restored.

## 3. Integration tests

### Desktop → mobile

```text
collector event
 ↓
transport
 ↓
context engine
 ↓
local database
 ↓
notification
 ↓
warm start
```

### RevenueCat

Verify:

- offering loads,
- purchase callback arrives,
- entitlement unlocks,
- restore works,
- cancellation does not crash.

### OneSignal

Verify:

- permission state,
- device registration,
- notification deep link,
- suppression.

## 4. End-to-end scenario

Scenario:

> User edits JWT middleware, runs a failing test, leaves for 45 minutes, returns to the same repository, and resumes.

Expected:

1. session is paused automatically,
2. context capsule is created,
3. blocker is represented accurately,
4. return is detected,
5. warm start is displayed,
6. changed state is shown,
7. user opens exact anchor,
8. first meaningful action is recorded,
9. re-entry time is stored.

## 5. False-positive test

Scenario:

User switches from one repository to a different repository with similar filenames.

Expected:

- no automatic warm start unless confidence threshold is met,
- suggestion can be dismissed.

## 6. Privacy tests

Verify that:

- disabled source produces no events,
- cloud AI OFF sends no task content remotely,
- analytics contains no raw task content,
- delete-all-data removes local records,
- revoked desktop pairing invalidates future events.

## 7. Security tests

- path traversal,
- malformed pairing token,
- expired pairing token,
- replayed pairing request,
- invalid API token,
- oversized event payload,
- injection into AI prompt fields,
- unsafe deep-link schemes.

## 8. Performance targets

Initial product targets:

- local warm-start generation: < 1 second for normal context sizes,
- notification decision: < 2 seconds after eligibility event locally,
- context search: < 300 ms for a few thousand local contexts,
- app cold start: < 2.5 seconds on a modern test device.

These are engineering targets, not externally validated benchmarks.

## 9. Manual release checklist

- clean install,
- onboarding,
- permission denial path,
- pairing,
- automatic capture,
- context history,
- warm start,
- deep links,
- purchase,
- restore,
- notification,
- offline mode,
- data deletion,
- release build.
