# CueBack Quickstart

## Prerequisites

- Android Studio
- JDK compatible with the generated Gradle project
- Android SDK
- Git
- Node.js/npm for the desktop companion
- Go for backend development if cloud sync is enabled

## 1. Android setup

Create a debug configuration using `.env.example` values.

Configure:

- RevenueCat public SDK key,
- OneSignal App ID,
- backend URL if needed.

Build:

```bash
cd android
./gradlew test
./gradlew assembleDebug
```

## 2. Desktop setup

```bash
cd desktop
npm ci
npm run dev
```

Grant only the permissions needed for the selected collectors.

## 3. Pair devices

1. Open CueBack on Android.
2. Open desktop companion.
3. Generate pairing code.
4. Confirm the same code on both devices.
5. Select a demo repository.
6. Enable VS Code collector.

## 4. Demo flow

Run the fixture repository:

```text
Open repo
→ edit auth middleware
→ run failing test
→ switch away
→ return
→ Warm Start
→ Resume exact test
```

## 5. RevenueCat test setup

Create:

- monthly product,
- annual product,
- `cueback_pro` entitlement,
- offering containing both products.

Configure a test user or judge promo/free trial according to the current RevenueCat environment.

## 6. OneSignal test setup

Create:

- Android app,
- push permission flow,
- notification deep link to the Warm Start route.

Test:

```text
send notification
→ tap notification
→ Warm Start opens
→ user taps Resume
```

## 7. Production checklist

Before release:

- replace all debug endpoints,
- remove debug fixture access from release builds or protect it behind an internal flag,
- verify billing,
- verify notification permissions,
- verify data deletion,
- verify privacy policy,
- run full test suite,
- generate signed release build.
