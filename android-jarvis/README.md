# J.A.R.V.I.S. — Native Android (Kotlin + Jetpack Compose) — Phase 1.1

Target Device: HONOR 400 Pro (`DNP-NX9`, Snapdragon 8 Gen 3, 12GB RAM, 512GB)  
Target SDK Compatibility: `compileSdk = 35`, `targetSdk = 35`, `minSdk = 28` (AGP `8.7.3` + Kotlin `2.1.0` compatible; runtime Android & MagicOS versions detected dynamically via `DeviceRuntimeInfo`).

---

## 1. Phase 1.1 Upgrade Changelog

- **Memory System (`MemoryRepository.kt`, `MemoryModels.kt`)**:
  - Replaced fire-and-forget writes with awaited DataStore `Result` persistence.
  - Added key normalization, categories (`MemoryCategory`), timestamps (`createdAtMillis`, `updatedAtMillis`), search, relevant-prompt filtering (`getRelevantMemoriesForPrompt`), and `memoryEnabled` enforcement on reads & writes.
  - Added corruption protection: invalid JSON is never silently overwritten and can be explicitly backed up and recovered.
- **Speech Recognition (`SpeechToTextEngine.kt`)**:
  - Explicit states: `IDLE`, `INITIALIZING`, `LISTENING`, `PROCESSING`, `ERROR`.
  - Uses `SpeechRecognizer.createOnDeviceSpeechRecognizer` on Android 12+ (API 31+) when `isOnDeviceRecognitionAvailable` is true and offline is preferred, with honest error reporting when offline models are absent.
  - Added atomic session ID guards to prevent stale callbacks from corrupting newer sessions, plus `onStop` lifecycle cleanup.
- **Text-to-Speech (`TextToSpeechEngine.kt`)**:
  - Added `TtsProvider` abstraction, installed voice discovery (`InstalledVoiceOption`), utterance ID tracking, and guaranteed state recovery callbacks on TTS failures so the UI never remains stuck in `SPEAKING`.
- **Gemini Backend (`GeminiBackendAiProvider.kt`)**:
  - Enforced HTTPS URL validation (`BackendUrlValidator`, allowing HTTP only for `localhost`/`127.0.0.1`/`10.0.2.2` debug).
  - Added exponential backoff retries (max 3 attempts) for transient `5xx`/`429`/`IOException` errors, OkHttp `Call.cancel()` on coroutine cancellation, duplicated turn removal, and local allowlist verification.
- **Action Registry & Local Command Router (`ActionRegistry.kt`, `LocalCommandRouter.kt`)**:
  - Upgraded to suspendable `executeAction` with 4 Phase 1 actions (`get_device_status`, `save_memory_note`, `retrieve_memory`, `delete_memory_note`), mandatory confirmation enforcement inside `ActionRegistry`, and pending token idempotency.
  - Added `LocalCommandRouter` for offline & zero-latency Egyptian Arabic / English battery, connectivity, memory, and ambiguity handling.
- **Persistent Conversation History (`ConversationDatabase.kt`)**:
  - Added `SQLiteOpenHelper` store (`jarvis_conversations.db`, `DATABASE_VERSION = 1`, WAL mode, parameterized queries, `onUpgrade` migration hook), strictly separated from `MemoryRepository`.
- **Wake Word Preparation (`WakeWordEngine.kt`)**:
  - Added clean `WakeWordEngine` contract documenting `FOREGROUND_SERVICE_MICROPHONE` and HONOR MagicOS battery restrictions without any fake detector.
- **Automated Tests**:
  - Added JVM unit tests: `MemoryRepositoryTest`, `ActionRegistryTest`, `LocalCommandRouterTest`, `JarvisSessionCoordinatorTest`.

---

## 2. Backend API Contract

The Android app never stores Gemini API keys. It communicates over HTTPS with your trusted backend:

### `GET /api/jarvis/health`
Response (`200 OK`):
```json
{
  "status": "ok",
  "geminiConfigured": true,
  "defaultModel": "gemini-4-hydrogen",
  "phase": "Phase 1 — Foundation"
}
```

### `POST /api/jarvis/chat`
Request:
```json
{
  "message": "يا جارفيس، صباح الخير",
  "model": "gemini-4-hydrogen",
  "history": [{ "role": "user", "text": "..." }],
  "memories": { "الاسم": "أشلي" }
}
```
Response (`200 OK`):
```json
{
  "reply": "صباح النور يا فندم، تحت أمرك.",
  "plannedAction": {
    "actionId": "get_device_status",
    "parameters": {},
    "requiresConfirmation": false
  },
  "modelUsed": "gemini-4-hydrogen"
}
```

---

## 3. Configuring Gemini Backend Credentials Securely

1. Deploy the companion backend (`server.ts`) to Cloud Run / AI Studio / your HTTPS host with `GEMINI_API_KEY` stored in server environment secrets—**never** in the Android codebase or APK.
2. In your GitHub repo **Settings > Secrets and variables > Actions**, add `JARVIS_BACKEND_URL` (e.g., `https://your-service.run.app`), or enter the HTTPS URL inside the app's **Settings > AI Brain**.

---

## 4. Building an APK from a Smartphone (GitHub Actions)

1. Download `jarvis-android-phase1.zip` from the web studio and upload the extracted files to a private GitHub repository (including `.github/workflows/build-apk.yml` and `.github/workflows/release-apk.yml`).
2. Open **Actions** on GitHub Mobile / mobile browser -> select **Build JARVIS Debug APK & Run Unit Tests** -> tap **Run workflow**.
3. Once finished, download **JARVIS-Phase1.1-Debug-APK** and install it on your HONOR 400 Pro.
4. *(Optional Release APK)*: Add GitHub Secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`, then run **Build JARVIS Signed Release APK (Optional)**.

---

## 5. Remaining Limitations

- This AI Studio web container does not include JDK/Gradle/Android SDK, so `gradle testDebugUnitTest` and `gradle assembleDebug` must execute in GitHub Actions (or Android Studio/Termux).
- `gradle-wrapper.jar` is not pre-generated because `gradle` is not installed in the web container; GitHub Actions uses `gradle/actions/setup-gradle@v4` directly (`gradle testDebugUnitTest` / `gradle assembleDebug`).
- Always-on wake word (`"JARVIS"`), phone calls, SMS, app launching, notification reading, and reminders remain intentionally unimplemented until Phases 2–5.
