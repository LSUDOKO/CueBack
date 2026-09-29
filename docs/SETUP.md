# Setup

This guide covers building CueBack and connecting the two optional services: RevenueCat for billing and OneSignal for push. The app builds and runs without either one. Billing and push then show a clear "not configured" state.

## 1. Build

Requirements: JDK 21, the Android SDK, and an Android 9+ device or emulator.

```bash
git clone https://github.com/LSUDOKO/CueBack.git
cd CueBack
cp local.properties.example local.properties
```

Set `sdk.dir` in `local.properties`, then:

```bash
./gradlew installDebug             # build and install on a connected device
./gradlew :app:testDebugUnitTest   # run the test suite
```

`local.properties` is git-ignored. Keys go there and are never committed.

## 2. Keys

| Property | Where to find it | Used for |
|---|---|---|
| `REVENUECAT_API_KEY` | RevenueCat → Project → API keys → **Public app-specific key** | Plans, purchases, restore, entitlements |
| `ONESIGNAL_APP_ID` | OneSignal → Settings → Keys & IDs → **App ID** | Push delivery, tags, outcomes |

Both are public client identifiers. Never put a RevenueCat **secret** key or a OneSignal **REST API** key in the app.

### Test Store keys (`test_…`)

RevenueCat's Test Store simulates purchases without Google Play, which makes it ideal for development. The key works only in **debug** builds. A release build that finds a `test_` key drops it and builds with billing disabled, with a Gradle warning. That's deliberate: RevenueCat's SDK crashes with a Test Store key in a non-debuggable build.

For release, use the Google Play public key (`goog_…`).

## 3. RevenueCat

1. Create a project (or an app inside an existing project) for CueBack.
2. **Products:** create monthly, yearly and, optionally, lifetime products. For release, create matching subscriptions and an in-app product in Google Play Console. Product IDs can be anything, because the app reads whatever packages the current offering contains.
3. **Entitlement:** create an entitlement with the identifier **`cueback_pro`** and attach all the products. The app unlocks Pro only when this exact entitlement is active.
4. **Offering:** make sure an offering containing the packages is marked **Current**.

Check it from the paywall: plans load with prices, a purchase switches the screen to "You have CueBack Pro", and Settings → Subscription shows the plan.

> If a purchase succeeds but Pro doesn't unlock, the entitlement identifier doesn't match. You can see which entitlements a user holds on the customer page in the RevenueCat dashboard.

## 4. OneSignal and Firebase

Android push is delivered through Firebase Cloud Messaging (FCM), so OneSignal needs a Firebase project.

1. In the [Firebase console](https://console.firebase.google.com), create or reuse a project.
2. Go to **Project settings → Service accounts → Generate new private key** and download the JSON file.
3. In OneSignal, go to **Settings → Push & In-App → Google Android (FCM)**, upload the JSON file and save.
4. Put the OneSignal **App ID** in `local.properties`.

Check it by opening the app with notifications allowed. The device should appear under **Audience → Subscriptions** as *Subscribed*. A status of `INVALID_FCM_SENDER_ID` means step 3 hasn't been done.

CueBack sends only privacy-safe tags: counts, timestamps, use case and Pro state. Task content is never sent. See [PUSH_AND_RETENTION.md](PUSH_AND_RETENTION.md).

## 5. Testing on a physical phone

1. On the phone, open **Settings → About phone** and tap **Build number** seven times.
2. Go to **Developer options** and switch on **USB debugging**.
3. Connect by USB and accept the "Allow USB debugging?" prompt.
4. Run `adb devices`. The phone should be listed as `device`.

Useful commands:

```bash
./gradlew installDebug
adb shell appops set com.cueback.app GET_USAGE_STATS allow                  # grant usage access
adb shell pm grant com.cueback.app android.permission.POST_NOTIFICATIONS
```

Detection timings to keep in mind when testing by hand (from `EngineConfig`):

| Rule | Value |
|---|---|
| A work session counts after | 60 s in a watched app |
| Locking the screen counts as a pause after | 30 s |
| Switching to another app counts as a pause after | 5 min |
| Returns sooner than this reattach silently | 2 min |
| A return counts after this long back in the app | 15 s |

Quiet hours (22:00–07:00 by default) hold back notifications, so turn them off in CueBack's Settings when testing at night.
