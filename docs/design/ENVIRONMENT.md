# Environment and Configuration

## Android

```env
REVENUECAT_PUBLIC_SDK_KEY_ANDROID=
ONESIGNAL_APP_ID=
API_BASE_URL=

# Optional cloud AI
AI_PROVIDER_MODE=local
AI_BASE_URL=
AI_MODEL=
AI_API_KEY=

# Optional telemetry
ANALYTICS_ENABLED=false
```

Never place secret server keys in the Android client.

## Backend

```env
APP_ENV=development
PORT=8080
DATABASE_URL=
JWT_SECRET=
CORS_ORIGINS=

# RevenueCat server integration, if used
REVENUECAT_SECRET_KEY=

# Optional AI gateway
AI_PROVIDER_MODE=disabled
AI_BASE_URL=
AI_API_KEY=
AI_MODEL=
```

## Desktop

```env
CUEBACK_API_URL=
CUEBACK_PAIRING_ENDPOINT=
LOG_LEVEL=info
```

## Build-time rules

- Use `.env.example` files in the repository.
- Never commit secrets.
- Use CI secret storage.
- Separate debug and release configurations.
- Do not ship test API keys in release builds.
