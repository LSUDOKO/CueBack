# Security policy

## Reporting a vulnerability

Please **don't open a public issue** for security problems. Instead, report them privately through [GitHub security advisories](https://github.com/LSUDOKO/CueBack/security/advisories/new).

Include what you found, how to reproduce it, and the affected version (Settings → bottom of the screen). You'll get a reply within a few days.

## Scope

Things we especially want to hear about:

- Any way CueBack could collect data from apps the user didn't select, or before Usage Access is granted
- Task content reaching RevenueCat, OneSignal or a cloud AI endpoint when it shouldn't
- Bypassing the artifact scheme allowlist to launch unexpected intents
- Unlocking Pro without a valid RevenueCat entitlement

## Design notes

- There is no CueBack server; all task data stays on the device.
- Only public client identifiers (RevenueCat public SDK key, OneSignal App ID) ship in the app. They're read from `local.properties` at build time and never committed.
- The optional cloud AI key is encrypted with an Android Keystore AES-GCM key, and text is redacted before it's sent.

See [docs/PRIVACY_SECURITY.md](docs/PRIVACY_SECURITY.md) for the full design.
