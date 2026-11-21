# OpenShorts Android

Native Android client for an OpenShorts server, built with Kotlin and Jetpack Compose.

## Included
- Dark Material 3 mobile interface
- Android video picker and upload flow
- OpenShorts two-step upload API integration
- Vertical clip-generation job submission and status/log polling
- Server URL, optional bearer token and Gemini BYOK configuration
- EncryptedSharedPreferences for saved connection credentials
- GitHub Actions debug APK build

## Requirements
- Android 8.0+ (API 26)
- A reachable OpenShorts server. AI video processing runs on that server, not on the phone.
- Self-hosted deployments need Gemini BYOK or an OpenAI-compatible local LLM configured on the server.
- Use HTTPS for internet-facing deployments. HTTP is enabled for local-network testing.

## Build
Open in Android Studio with JDK 17, sync Gradle, then run the app. Or use Gradle 8.9:
```bash
gradle :app:assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions publishes it as an artifact.

## Server setup
Deploy OpenShorts using its official Docker Compose instructions: https://github.com/mutonby/openshorts
Then enter the server URL in the app. The app calls:
- `POST /api/uploads`
- `PUT /api/uploads/{upload_id}`
- `POST /api/process`
- `GET /api/status/{job_id}`

The app sends the required ownership acknowledgement before processing. Only process content you own or have permission to use.
