# Jeevani AI

A mobile-first multilingual personal healthcare, report-management, AI-insights and emergency-assistance prototype.

## What is included

- Secure registration/login using an HTTP-only signed session cookie
- SQLite database with user-specific access control
- Medical report upload for PDF/images
- PDF text extraction with `pypdf`
- Optional image OCR with `pytesseract` when Tesseract is installed
- Automatic report classification
- Optional Gemini AI report analysis with consent and structured output
- Deterministic local analysis fallback when Gemini is not enabled or unavailable
- AI findings, precautions and priority labels
- Multilingual language preference (English, Telugu, Hindi, Tamil, Kannada, Malayalam)
- Browser text-to-speech for localized voice output
- Emergency contact management
- Persisted SOS alerts with device geolocation, registered-contact SMS drafts/call links, and nearby-hospital lookup
- Interactive OpenStreetMap hospital/clinic map using Leaflet and the public Overpass API
- Installable Progressive Web App (PWA) for supported mobile and desktop browsers
- Responsive dashboard and mobile navigation
- Dockerfile and docker-compose

## Run on Windows

1. Install Python 3.11+.
2. Open PowerShell in this folder.
3. Create a virtual environment:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
```

4. Create your local environment file:

```powershell
Copy-Item .env.example .env
```

Set `GEMINI_API_KEY` in `.env`. The key stays server-side and `.env` is excluded from Git and Docker build contexts. Report text is sent to Gemini only when a user checks the consent box during upload. Without consent, the app uses local analysis.

5. Install packages:

```powershell
pip install -r requirements.txt
```

6. Start the server:

```powershell
uvicorn backend.app:app --reload
```

7. Open http://127.0.0.1:8000

## Install as an app

Jeevani AI is a Progressive Web App. Open it in a supported browser and choose **Install app** or **Add to Home Screen** from the browser menu. Installation is available on `localhost` for development and on an HTTPS origin when deployed. The service worker caches the static application shell only; sign-in, reports, chat, SOS, maps, and other API-dependent features still require a network connection. User-specific API responses and uploaded medical files are not cached.

## Native Android app

A native Android client is available in `android/`. It connects directly to the same FastAPI backend and includes account registration/login, profile editing, report upload and review, the health assistant, emergency-contact management, SOS history, user-initiated SMS/calls, and nearby healthcare searches.

Build a debug APK from PowerShell:

```powershell
cd android
.\gradlew.bat assembleDebug
```

The APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`. Android Studio can also open the `android/` folder as a project. The debug build defaults to `http://10.0.2.2:8000`; the app checks its configured URL and then tries the emulator defaults against `/api/health` during login, registration, and startup. On a physical phone, enter a reachable HTTPS backend address; automatic discovery cannot determine an arbitrary PC's Wi-Fi address, and Android cleartext HTTP is enabled only for the emulator development hosts.

Install the debug APK on a connected device with `adb install -r app/build/outputs/apk/debug/app-debug.apk` from the `android/` directory, or copy the APK to the device and open it. This debug-signed build is for development/testing; publishing requires a release build signed with your own protected signing key.

### Build a signed demo APK

Use the existing application ID `com.jeevani.ai`; changing it creates a different Android app and prevents installing an update over the current one. Choose a backend that demo phones can reach over HTTPS. Do not use a developer machine's localhost or emulator address for a phone demo.

1. From the `android/` directory, create a signing key once and store it somewhere safe:

   ```powershell
   keytool -genkey -v -keystore release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias jeevani
   ```

2. Create `android/keystore.properties` locally (it is git-ignored) with the values for that key:

   ```properties
   storeFile=release-key.jks
   storePassword=YOUR_STORE_PASSWORD
   keyAlias=jeevani
   keyPassword=YOUR_KEY_PASSWORD
   ```

   Back up the keystore and its passwords securely. Never commit or share them.

3. Build the release APK, providing the reachable HTTPS API root (no trailing slash):

   ```powershell
   .\gradlew.bat assembleRelease "-PjeevaniApiBaseUrl=https://your-api.example.com"
   ```

   The signed APK is `app/build/outputs/apk/release/app-release.apk`. If `keystore.properties` is absent, Gradle builds an unsigned release APK that cannot be installed as a normal shared demo.

4. Verify the APK signature before sharing:

   ```powershell
   $sdk = $env:ANDROID_SDK_ROOT
   if (-not $sdk) { $sdk = $env:ANDROID_HOME }
   & "$sdk\build-tools\35.0.0\apksigner.bat" verify --verbose app\build\outputs\apk\release\app-release.apk
   ```

The release app accepts HTTPS only. Test the signed APK on the target phones and verify backend login and core flows before distribution. Increase `versionCode` in `app/build.gradle` for each update and sign updates with the same key.

## Run on Linux/macOS

```bash
python3 -m venv .venv
source .venv/bin/activate
cp .env.example .env
# Add your Gemini API key to .env
pip install -r requirements.txt
uvicorn backend.app:app --reload
```

## Demo behavior and production replacements

Gemini analysis is optional and depends on API-key availability and provider quotas. The deterministic local summary is used when consent is not given or the Gemini request fails. Neither analysis mode is a medical diagnosis system. The hospitals map uses OpenStreetMap tiles and Overpass data without an API key. A map search sends its center coordinates to public Overpass services; if one is unavailable, the frontend tries alternate public instances. These shared services may rate-limit requests, and map data can be incomplete or outdated. The health assistant also depends on a reachable Gemini service and retries configured models for transient connection failures. SOS events and supplied coordinates are saved in the local database. SMS links open a prefilled draft on the user's device; the user must review and send it. Call links initiate a call only after the user taps them. No message or call is sent automatically, and this app does not contact emergency services. Do not rely on the map for emergency routing; contact local emergency services directly. For production, use validated healthcare workflows and appropriate privacy/compliance controls.

Image OCR requires the Tesseract executable. If Tesseract is not installed, image files are still stored and classified from their filename, while PDF text extraction continues to work. The browser's built-in SpeechSynthesis API provides voice output without a server API key.

## API health check

GET `/api/health`

## Security notes

- Change `JEEVANI_SECRET` before deployment.
- Use HTTPS in production and set the session cookie `secure=True`.
- Store uploaded medical documents in encrypted, access-controlled object storage in production.
- Do not rely on OpenStreetMap healthcare listings for real emergency routing.
- Have clinicians/security/privacy experts review the AI workflow before real-world medical use.
