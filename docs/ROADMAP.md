# CueBack — Build Plan (Android-only release)

Shipaton 2026 requires a working mobile app with RevenueCat. The desktop companion and Go backend
described in the original docs are **out of scope** for this release; every signal the engine uses
comes from the Android device itself. The product still works fully offline.

## Android signal sources (real, no fakes)

| Source | API | Evidence it produces |
|---|---|---|
| App usage | `UsageStatsManager.queryEvents` (user grants Usage Access) | tracked-app focus, untracked-app switch, durations |
| Screen / lock | `SCREEN_INTERACTIVE`, `SCREEN_NON_INTERACTIVE`, `KEYGUARD_SHOWN/HIDDEN` usage events | device lock / unlock boundaries |
| Share sheet | `ACTION_SEND` intent filter | URL / text / file artifacts attached to the live context |
| User intent | "What were you about to do next?" prompt (text, voice via `RecognizerIntent`) | explicit next action |
| Explicit | Pause / Resume buttons, quick-settings style notification action | explicit boundaries |

Only apps the user explicitly selects are tracked. Nothing is captured before Usage Access is granted
and a tracked app is chosen.

## Architecture

```text
android/app
  core/engine      pure Kotlin: segmentation, capsule builder, confidence, matcher, depth, delta, re-entry
  core/model       domain models + provenance labels (Detected / Inferred / You said / Unknown)
  data/db          Room entities, DAOs, migrations
  data/repo        repositories (contexts, events, re-entry, settings)
  data/usage       UsageStats collector, tracked-app catalog
  billing          RevenueCat wrapper -> EntitlementState flow
  notify           OneSignal wrapper, local resume notifications, quiet hours, suppression
  detect           Live detection foreground service + WorkManager fallback
  ui/*             Compose screens (onboarding, home, warm start, detail, library, capture, paywall, settings)
```

Manual DI (`AppContainer`) — no Hilt, to keep the build small.

## Phases (each ends with tests passing + one-line commit + push)

| # | Phase | Done when |
|---|---|---|
| 0 | Toolchain + scaffold: Gradle/AGP 9, version catalog, CI workflow, `local.properties.example`, README | `./gradlew assembleDebug test` green |
| 1 | Context engine (TDD): events, noise filter, pause scoring, capsule builder, confidence, return matcher, depth selector, delta builder, re-entry tracker | engine unit tests green |
| 2 | Data layer: Room schema + exported migrations, repositories, DataStore settings | DAO/repo tests (Robolectric) green |
| 3 | Core UI: design system (light/dark, reduced motion), onboarding + privacy explainer, Home, Capture, Context detail, Library + search | screens render, navigation works on emulator |
| 4 | Warm Start (micro / recovery card / full), artifact launcher (scheme allowlist), Re-entry measurement + "Back in Ns" | E2E: context → warm start → artifact → meaningful action → re-entry stored |
| 5 | Automatic detection: Usage Access flow, tracked-app picker, share target, live detection service, return detection → resume notification deep link | real app switch on emulator creates a capsule and a return triggers warm start |
| 6 | RevenueCat: offerings, custom paywall, purchase, restore, entitlement gates, "not configured" state | gate tests green; paywall reachable after first successful resume |
| 7 | OneSignal: init, permission, login/tags, outcomes, click → deep link, quiet hours, per-context mute | deep-link parser tests green |
| 8 | Privacy & settings: source toggles, pause collection, JSON export, delete all data, optional cloud AI (OpenAI-compatible, redaction, off by default) | privacy tests: disabled source → no events; delete → empty DB |
| 9 | Demo fixture (debug only, same engine), polish, icon, R8 release build, store/judge docs | release build succeeds; final acceptance checklist reviewed |

## Tiering (RevenueCat entitlement `cueback_pro`)

Free: 3 active contexts, 1 tracked app with automatic detection, text capture, micro + recovery-card warm starts, local history.
Pro: unlimited contexts and tracked apps, voice capture, full reconstruction + delta timeline, smart resume reminders, JSON export.

## Requires your accounts (keys go in `android/local.properties`, never committed)

- `REVENUECAT_API_KEY` — RevenueCat public Android SDK key, products `cueback_pro_monthly`, `cueback_pro_annual`, entitlement `cueback_pro`.
- `ONESIGNAL_APP_ID` — OneSignal app with FCM configured.

Without keys the app runs; billing and push show an explicit "not configured" state rather than fake data.
