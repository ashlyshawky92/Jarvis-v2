# J.A.R.V.I.S. — Native Android (Kotlin + Jetpack Compose) — Phase 1

Target Device: HONOR 400 Pro (`DNP-NX9`, Snapdragon 8 Gen 3, 12GB RAM, 512GB)
Runtime Detection: Reads actual Android version and MagicOS version dynamically at runtime.

## Building an APK from an Android Phone (No PC Required)

1. Export this `android-jarvis` folder as a ZIP from the J.A.R.V.I.S. Studio web console.
2. Extract or upload the files into a private GitHub repository using GitHub Mobile or your phone browser (or Termux `git push`).
3. Optional (recommended for Gemini AI Brain): Add a repository secret named `JARVIS_BACKEND_URL` in GitHub Repo -> Settings -> Secrets and variables -> Actions, pointing to your deployed J.A.R.V.I.S. server URL (or Firebase AI Logic + App Check). Never embed raw Gemini API keys in the APK.
4. Open the **Actions** tab on GitHub Mobile/browser -> select **Build JARVIS Android APK** -> tap **Run workflow**.
5. Download the `JARVIS-Phase1-Debug-APK` artifact directly onto your HONOR 400 Pro and install it.
