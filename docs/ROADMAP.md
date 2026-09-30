# CueBack roadmap (Android-only release)

> **Status, 30 Sep 2026:** about 85% complete. Product code for every phase is done and tested (65 tests; pause and return detection verified on a physical Android 15 phone). Remaining work is account configuration and store release. See the [README](../README.md#project-status).

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
app/src/main/java/com/cueback/app
  core/engine      pure Kotlin: segmentation, capsule builder, confidence, matcher, depth, delta, re-entry
  core/model       domain models + provenance labels (Detected / Inferred / You said / Unknown)
  data/db          Room entities, DAOs, migrations
  data/repo        repositories (contexts, events, re-entry, settings)
  platform         UsageStats collector, tracked-app catalog, artifact launcher
  billing          RevenueCat wrapper -> EntitlementState flow
  notify           OneSignal wrapper, local resume notifications, quiet hours, suppression
  detect           Live detection foreground service + WorkManager fallback
  ui/*             Compose screens (onboarding, home, warm start, detail, library, capture, paywall, settings)
```

Manual DI (`AppContainer`) — no Hilt, to keep the build small.

## Phases (each ends with tests passing + one-line commit + push)

| # | Phase | Done when | Status |
|---|---|---|---|
| 0 | Toolchain + scaffold: Gradle/AGP 9, version catalog, CI workflow, `local.properties.example`, README | `./gradlew assembleDebug test` green | ✅ |
| 1 | Context engine (TDD): events, noise filter, pause scoring, capsule builder, confidence, return matcher, depth selector, delta builder, re-entry tracker | engine unit tests green | ✅ |
| 2 | Data layer: Room schema + exported migrations, repositories, DataStore settings | DAO/repo tests (Robolectric) green | ✅ |
| 3 | Core UI: design system (light/dark, reduced motion), onboarding + privacy explainer, Home, Capture, Context detail, Library + search | screens render, navigation works on emulator | ✅ |
| 4 | Warm Start (micro / recovery card / full), artifact launcher (scheme allowlist), Re-entry measurement + "Back in Ns" | E2E: context → warm start → artifact → meaningful action → re-entry stored | ✅ |
| 5 | Automatic detection: Usage Access flow, tracked-app picker, share target, live detection service, return detection → resume notification deep link | real app switch on emulator creates a capsule and a return triggers warm start | ✅ verified on device |
| 6 | RevenueCat: offerings, custom paywall, purchase, restore, entitlement gates, "not configured" state | gate tests green; paywall reachable after first successful resume | ✅ code · ⚠️ dashboard entitlement |
| 7 | OneSignal: init, permission, login/tags, outcomes, click → deep link, quiet hours, per-context mute | deep-link parser tests green | ✅ code · ⚠️ FCM setup |
| 8 | Privacy & settings: source toggles, pause collection, JSON export, delete all data, optional cloud AI (OpenAI-compatible, redaction, off by default) | privacy tests: disabled source → no events; delete → empty DB | ✅ |
| 9 | Demo fixture (debug only, same engine), polish, icon, R8 release build, store/judge docs | release build succeeds; final acceptance checklist reviewed | 🟡 release signing and store listing left |

## Tiering (RevenueCat entitlement `cueback_pro`)

Free: 3 active contexts, 1 tracked app with automatic detection, text capture, micro + recovery-card warm starts, local history.
Pro: unlimited contexts and tracked apps, voice capture, full reconstruction + delta timeline, smart resume reminders.

JSON export and delete-all are available to everyone: data portability is never paywalled.

## Requires your accounts (keys go in `local.properties`, never committed)

- `REVENUECAT_API_KEY`: RevenueCat public Android SDK key. Entitlement `cueback_pro`; product IDs are free-form because the app reads the current offering's packages.
- `ONESIGNAL_APP_ID` — OneSignal app with FCM configured.

Without keys the app runs; billing and push show an explicit "not configured" state rather than fake data.

## Remaining before store release

- [ ] RevenueCat: create the `cueback_pro` entitlement and attach the products (the current project grants a different entitlement)
- [ ] RevenueCat: Google Play products and the `goog_` public key for release builds
- [ ] OneSignal: connect Firebase (FCM), then verify a push reaches a device and opens the welcome-back card
- [ ] Upload keystore and a signed release build (release currently uses the debug signing config)
- [ ] Play Console listing: screenshots, data-safety form, privacy policy URL ([PRIVACY_POLICY.md](../PRIVACY_POLICY.md))
