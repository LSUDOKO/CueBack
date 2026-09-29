# Privacy and Security

## Privacy philosophy

CueBack should know *enough to restore context* without becoming a surveillance product.

## Default data policy

### Stored locally by default

- context capsules,
- task metadata,
- file paths for approved workspaces,
- git metadata,
- timestamps,
- notification preferences,
- local re-entry metrics.

### Not captured by default

- continuous screenshots,
- continuous microphone audio,
- webcam feeds,
- keystroke-by-keystroke logs,
- passwords,
- private messages,
- arbitrary browser history.

## Source-level permissions

Every source is individually controllable.

```text
VS Code             ON
Selected repository ON
Browser extension   OFF
Terminal            ON
Other applications  OFF
Cloud AI            OFF
Cloud sync          OFF
```

## Secret detection

Before cloud AI or analytics:

- detect common tokens/keys,
- redact environment secrets,
- redact Authorization headers,
- redact common API-key patterns,
- reject obviously sensitive payloads.

## AI privacy modes

### Local only

No task content leaves the device.

### Cloud summary

Only user-approved context is sent to the configured provider.

### Cloud sync

Only if explicitly enabled.

## Data deletion

One action:

> Delete all CueBack data

Deletes local contexts, events, and settings and invalidates device pairing.

Cloud data must have an equivalent delete path if cloud sync is enabled.

## Data export

Provide JSON export for user-owned context history.

## Authentication

Recommended:

- passkey/OAuth where available,
- short-lived access token,
- refresh token stored in OS secure storage,
- device binding for desktop pairing.

## Threat model

### Threat: malicious local app tries to steal pairing token

Mitigation:

- short expiry,
- one-time use,
- explicit user confirmation,
- TLS,
- device-level authentication.

### Threat: sensitive context reaches AI provider accidentally

Mitigation:

- local-first mode,
- explicit cloud opt-in,
- redaction pipeline,
- provider adapter with allowlist,
- visible cloud-processing status.

### Threat: malicious path traversal via artifact launcher

Mitigation:

- canonicalize paths,
- require approved workspace root,
- reject `..` escapes,
- validate schemes for URLs.

### Threat: analytics leaks task data

Mitigation:

- event schemas contain IDs and aggregate metrics only,
- never send context body, file content, raw command, or private URL in analytics.

## Transparency copy

Use plain language:

> CueBack observes only sources you enable. Its default job is to preserve structured context such as the app, workspace, file, task state, and next action. Continuous screen or microphone recording is not required for the core experience.
