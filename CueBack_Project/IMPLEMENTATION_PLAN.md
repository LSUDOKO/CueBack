# Implementation Plan

## 1. Shipping strategy

Build the real product loop first:

```text
Detect → Save → Return → Reconstruct → Resume → Measure
```

Every added feature must strengthen this loop.

## 2. Release architecture

### Phase 1 — Core Android app

Implement:

- onboarding,
- contexts,
- local database,
- manual context creation,
- warm start UI,
- context history,
- Re-entry Time.

### Phase 2 — Desktop detection

Implement:

- pairing,
- active-window collector,
- selected workspace collector,
- Git metadata,
- file metadata,
- session segmentation.

### Phase 3 — Automatic reconstruction

Implement:

- context engine,
- confidence scoring,
- next-action extraction,
- return matching,
- Delta Manifest.

### Phase 4 — Notifications + RevenueCat

Implement:

- OneSignal,
- notification deep links,
- RevenueCat products,
- entitlement gating,
- free trial,
- restore purchases.

### Phase 5 — AI enhancement

Implement:

- local semantic matching,
- optional cloud summarizer,
- structured outputs,
- redaction.

### Phase 6 — polish

Implement:

- animations,
- accessibility,
- empty/error states,
- analytics,
- crash reporting,
- store assets.

## 3. Minimum real demo

The demo must work without fake screens.

Demo environment:

- one Android device/emulator,
- one desktop workspace,
- one Git repository,
- VS Code,
- terminal,
- optional browser extension.

Demo task:

> Fix a JWT refresh-token bug.

Event trace:

```text
Open repo
 ↓
Open authMiddleware.go
 ↓
Edit refresh handling
 ↓
Run test
 ↓
Test fails
 ↓
Switch away
 ↓
CueBack creates context
 ↓
Wait/simulate time passage
 ↓
Return to repo
 ↓
CueBack recognizes repo
 ↓
Warm Start appears
 ↓
User opens exact test anchor
 ↓
User edits/runs test
 ↓
Re-entry Time recorded
```

## 4. Deterministic demo mode

A real app can include a developer-only fixture mode that replays an actual structured event trace.

Rules:

- fixture data must use the same production rendering and engine paths,
- demo mode must be clearly separated from user data,
- do not fabricate RevenueCat purchase state; use a real test product or judge promo code.

## 5. Definition of done

### Product

- [ ] User can create context manually.
- [ ] Desktop can automatically capture context events.
- [ ] Context is finalized after interruption.
- [ ] Return detection works.
- [ ] Warm Start displays real state.
- [ ] Delta Manifest works for Git/file metadata.
- [ ] User can launch an artifact.
- [ ] Re-entry Time is measured.
- [ ] Data can be deleted.

### Billing

- [ ] RevenueCat SDK initialized.
- [ ] Products configured.
- [ ] Entitlement checked.
- [ ] Purchase works in test environment.
- [ ] Restore works.
- [ ] Premium features gate correctly.
- [ ] Trial or judge promo works.

### Notifications

- [ ] OneSignal SDK initialized.
- [ ] Permission flow works.
- [ ] Resume notification deep-links correctly.
- [ ] Quiet hours work.
- [ ] Suppression works.

### Security

- [ ] Pairing tokens expire.
- [ ] Local secrets use secure storage.
- [ ] Cloud AI is opt-in.
- [ ] Raw task contents are absent from analytics.
- [ ] Delete-all-data path works.

### Release

- [ ] Release build succeeds.
- [ ] App opens after clean install.
- [ ] No broken links.
- [ ] No test/debug text in production.
- [ ] Store assets generated.
- [ ] Demo video recorded.

## 6. Suggested repository structure

```text
cueback/
├── android/
│   ├── app/
│   ├── build.gradle.kts
│   └── README.md
├── desktop/
│   ├── src-tauri/
│   ├── src/
│   └── README.md
├── server/
│   ├── cmd/
│   ├── internal/
│   └── migrations/
├── browser-extension/
├── docs/
├── fixtures/
├── scripts/
├── .github/
│   └── workflows/
├── LICENSE
└── README.md
```

## 7. Build commands

Android example:

```bash
./gradlew test
./gradlew assembleRelease
```

Go backend example:

```bash
go test ./...
go run ./cmd/server
```

Desktop example:

```bash
npm ci
npm run build
npm run tauri build
```

Use the actual project scripts once generated; do not leave placeholder commands in the final release README.
