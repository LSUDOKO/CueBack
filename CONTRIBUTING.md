# Contributing

Thanks for your interest in CueBack.

## Development setup

Follow [docs/SETUP.md](docs/SETUP.md). You don't need any keys to build, run or test.

## Before opening a pull request

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Both must pass; CI runs the same checks plus a release build.

## Guidelines

- **Keep the engine pure.** `core/engine` and `core/model` must not depend on Android. That's what keeps them fast to test.
- **Add a test with every behavior change.** Engine rules go in the engine tests, and user-visible flows in `UiFlowTest`.
- **Never present a guess as a fact.** Anything shown to the user carries a provenance label (`Detected`, `Inferred`, `You said`, `Unknown`).
- **Privacy is a feature.** New data sources need to be opt-in, visible in Settings, and included in export and delete.
- **Tune thresholds only in `EngineConfig`,** and explain the reasoning in the pull request.
- Keep commits to a one-line summary of what changed.

## Reporting bugs

Open an issue with the bug report template. Include your Android version, the phone model, and the steps to reproduce.
