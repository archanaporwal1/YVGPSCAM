# Why the APK Was Not Pushed to GitHub & How We Will Fix It

## 1. Root Cause: Why AI Studio Does Not Push `.apk` Files to GitHub
- **AI Studio's "Push to GitHub" is Source-Code Only**: When you click **Push to GitHub** in AI Studio, the platform's GitHub exporter only uploads tracked **source code and resource files** (`.kt`, `.xml`, `.gradle.kts`, images). It automatically strips compiled `.apk` / `.aab` binaries and Gradle build outputs because GitHub's REST Contents API rejects large binary blobs during workspace export.
- **How to Get the APK Directly from AI Studio (No GitHub Required)**:
  - In the top-right corner of AI Studio, open the **Settings (Gear) menu** and click **Generate APK / Download APK** directly onto your computer or phone.

---

## 2. How to Automatically Get the `.apk` Inside Your GitHub Repo
Since AI Studio only pushes source files to GitHub, **GitHub Actions** must build the `.apk` on GitHub's servers after you push and then save the `.apk` directly into your repository and **GitHub Releases**.

Previously, the GitHub Actions workflow failed to publish the APK to your repo tree because:
1. The project uses `compileSdk 36 (minorApiLevel = 1)`, which requires `sdkmanager "platforms;android-36.1" "build-tools;36.0.0"` on GitHub's Ubuntu runner.
2. The workflow only uploaded a temporary workflow artifact instead of **committing `releases/YVGSPCAM-debug.apk` into your repository** and **creating a GitHub Release**.

---

## 3. Planned Changes (`.github/workflows/build-apk.yml`)
1. **Grant `contents: write` Permission**: Allow GitHub Actions to commit the built APK into your repository's `releases/` folder and publish a downloadable **GitHub Release**.
2. **Install Android SDK 36.1 (`platforms;android-36.1`)**: Accept Android SDK licenses and install `platforms;android-36.1` and `build-tools;36.0.0` on the GitHub runner so `gradle assembleDebug` succeeds every time.
3. **Auto-Commit `releases/YVGSPCAM-debug.apk` & Create GitHub Release**:
   - Copy `app/build/outputs/apk/debug/app-debug.apk` to `releases/YVGSPCAM-debug.apk` and push it back to your branch so it appears right in your GitHub file list.
   - Automatically publish a **GitHub Release (`v1.0-latest`)** on the right sidebar of your GitHub repository with `YVGSPCAM-debug.apk` attached for one-click download.
