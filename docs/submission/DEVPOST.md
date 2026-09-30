# CueBack: Devpost submission text

**Track:** Next Gen Award (student). Open-source code + demo video.

**Tagline:** Don't save the task. Save your place.

**Links**
- Code: https://github.com/LSUDOKO/CueBack (Apache 2.0)
- Demo video: *(paste the YouTube/Vimeo link after uploading `video/out/cueback-demo.mp4`)*

---

## Inspiration

Interruptions rarely make you forget the goal. They make you forget where you were: what you'd already done, what was broken, and what you were about to try. As a student, that's every evening. You're halfway through a problem set, the group chat explodes, and twenty minutes later you're scrolling back through the page trying to rebuild the thought you had. Research calls this resumption lag. We wanted a phone app that fixes it without asking you to become a better note-taker.

## What it does

CueBack keeps your place in the apps where your real work happens, and hands it back when you return.

- **Notices when you step away.** Pick the apps to watch (a browser, docs, a code app). CueBack sees only which of them is in front and when the screen locks, never what's on screen, and closes a work session when you've really paused.
- **Saves your place in one tap.** Share the page or file to CueBack and write the next step in your own words ("Redo 7b, convert km to m first").
- **Knows when you're back.** Open the same app again and a notification says "You're back in Chrome", with your next step in it.
- **The welcome-back card** leads with your next step, then what you had, the blocker and what happened while you were away. Every fact is labelled with where it came from (Detected, Inferred, You said), so a guess is never presented as a fact.
- **Resume** reopens the exact page or file you saved.
- **Re-entry time.** CueBack measures how long it took you to get back to real work ("Back in 21 seconds"): yours to keep, not a score.
- **Works across apps:** lecture videos, code reviews, research articles, application forms. Everything is searchable in the Library.
- **Cue, the owl,** is the app's guide: it hovers, glances around, reacts to your phone's tilt and to taps, and changes pose with what's happening.

## How we built it

- **Native Android:** Kotlin, Jetpack Compose and Material 3, with a custom dark "Ember" design system and an animated mascot built from our own renders.
- **An on-device context engine in pure Kotlin:** session segmentation from Usage Access events, a pause score (screen lock, switching away, idle time), context capsules with provenance, a return matcher and recovery depth that adapts to how long you were away.
- **Room, DataStore and WorkManager** for storage and background checks, plus a foreground service that recognizes returns within seconds.
- **RevenueCat** for monetization: plans load from the current offering (annual, monthly, lifetime), purchases and restore go through the SDK, and Pro is unlocked only by the `cueback_pro` entitlement. We tested valid, failed and cancelled purchases and restore with RevenueCat's Test Store on a real phone.
- **OneSignal** for push, with privacy-safe tags only (counts and timestamps, never task content).
- **65 automated tests,** including real UI flows under Robolectric, and CI on every push.

## How RevenueCat is used

Free keeps 3 places and watches 1 app. **Pro** watches every app, keeps unlimited places and shows the full reconstruction and timeline. The paywall appears after your first successful return, when the value is obvious, and never blocks the core loop. Entitlement state comes only from RevenueCat's CustomerInfo, so nothing in the app can set Pro locally.

## Privacy

Everything stays on the phone. There is no CueBack server. No screen capture, audio, keystrokes or message content. Pause collection, export everything as JSON or delete everything from Settings.

## Challenges

- Telling a real pause from a quick glance at another app, without reading the screen. We tuned a pause score and a 2-minute silent-reattach window, and verified it on a physical phone.
- Android background limits: we combined a foreground service (fast) with WorkManager (a reliable fallback).
- Being honest about confidence: every line on the welcome-back card says whether you said it, CueBack detected it, or CueBack inferred it.

## What we learned

The next action matters more than a summary. Leading with one sentence, in the user's own words, beats a wall of recovered context.

## What's next

- A daily evening summary of what's still open, and tags and date search in the Library.
- Coding- and study-specific fields.
- A Play Store release.

## Trying it

Build from source (see `docs/SETUP.md`). With no keys, the app runs fully and billing shows "not configured". To try Pro, add a RevenueCat Test Store key and a `cueback_pro` entitlement, as described in `docs/SETUP.md`.

## Assets

- App icon: `docs/submission/icon-1024.png` (1024×1024)
- Screenshots: `docs/submission/screenshot-*.png` (1179×2556, no device frame)
