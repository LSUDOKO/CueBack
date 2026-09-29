# CueBack Privacy Policy

_Effective 30 September 2026_

CueBack helps you pick up work where you left off. It's built to do that with as little data as possible, and to keep that data on your phone.

## What CueBack collects, and where it stays

| Data | Why | Where it stays |
|---|---|---|
| **Which of the apps you selected is in front, and when the screen locks or unlocks.** Collected through Android's Usage Access, only after you grant it and choose apps. | To notice when you pause work and when you return | On your phone |
| **What you type or say to CueBack**: task titles, "what's next?" answers, notes | To show you your place when you come back | On your phone |
| **Links, text and files you share to CueBack** (the link or file reference, not the file's contents) | To reopen your exact spot | On your phone |
| **Re-entry timings**: how long it took you to get back on track | To show "Typically back in 2 min" | On your phone |

CueBack **never** records your screen, audio, keystrokes, messages, notifications from other apps, or the content of any other app. Apps you haven't selected are counted only as "another app"; their names are shown only if you turn that on, and only on your phone.

CueBack has **no server**. There is no CueBack account and no CueBack database outside your phone.

## Services that receive limited data

**RevenueCat** (purchases). If you view plans or buy CueBack Pro, RevenueCat receives a random identifier generated on your phone (for example `cb_3f9a…`) and your purchase information from Google Play. It doesn't receive your name, email or any task content. See [RevenueCat's privacy policy](https://www.revenuecat.com/privacy).

**OneSignal** (push notifications). If notifications are enabled, OneSignal receives a push token, basic device information collected by its SDK, the same random identifier, and these tags only: the number of open contexts, the number with a recorded next step, the time of your last pause, your chosen use case (for example "coding"), and whether you have Pro. It never receives task titles, notes, links or app names. See [OneSignal's privacy policy](https://onesignal.com/privacy_policy).

**Optional cloud AI.** This is off by default. If you turn it on and enter your own endpoint and key, CueBack sends a short summary of the context you ask it to refine: title, goal, blocker, next step, up to five completed items and notes, and link titles or domains. Before sending, it strips tokens and keys it recognizes (for example bearer tokens, AWS, GitHub, Slack and Google keys). File contents are never sent. What happens to that data is governed by the provider you configured. Your API key is stored encrypted with the Android Keystore.

## Your controls

In **Settings** you can:

- **Pause collection**, which stops observing immediately. Manual saves still work.
- **Choose or remove** the apps CueBack watches, or revoke Usage Access in Android settings.
- **Hide details on the lock screen**, so notifications show only "Your next action is ready".
- **Export** everything CueBack stores as a JSON file.
- **Delete all CueBack data** from the phone. This also removes the push tags and logs out of OneSignal.

Uninstalling CueBack removes all of its on-device data.

## Children

CueBack isn't directed at children under 13 and doesn't knowingly collect their data.

## Changes

If this policy changes, the new version will be published at this address with a new effective date.

## Contact

Questions or requests: open an issue at [github.com/LSUDOKO/CueBack/issues](https://github.com/LSUDOKO/CueBack/issues).
