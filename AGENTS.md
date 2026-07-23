# AGENTS.md

## Cursor Cloud specific instructions

ZenSposed is a **single-module Android app** (Gradle/Kotlin, `:app`). There is no
server/backend and no automated test suite. Standard build/run commands live in
`README.md` and `CONTRIBUTING.md`; the notes below only cover non-obvious cloud caveats.

### Toolchain (already installed in the VM snapshot)
- JDK **17** (Temurin) is the system default `java` (required — the project fails on JDK 21+).
- Android SDK lives at `~/android-sdk` (platform `android-35`, `build-tools;35.0.0`,
  `platform-tools`, `emulator`, `system-images;android-35;google_apis;x86_64`).
- `local.properties` (`sdk.dir=$HOME/android-sdk`) is gitignored and re-created by the
  update script — do not commit it.

### Build / lint
- Build debug APK: `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
  This is the only check that runs in CI (`.github/workflows/ci.yml`).
- Lint: `./gradlew :app:lintDebug`. The lint toolchain works, but the build currently
  **fails on a pre-existing code error** (`UnsafeOptInUsageError` in
  `app/src/main/java/com/hassan/zensposed/ui/focus/QrScanner.kt:91`). This is a source
  issue, not an environment problem, and lint is not part of CI.

### Running the app
- The cloud VM has **no KVM** (`/dev/kvm` absent), so the Android emulator only runs in
  slow software mode. Boot takes ~8 minutes. Start headless:
  `emulator -avd zen35 -no-window -no-audio -no-snapshot -no-boot-anim -gpu swiftshader_indirect -accel off`
  (AVD `zen35` already exists). Wait on `adb shell getprop sys.boot_completed` returning `1`.
- Because the emulator is CPU-starved, the **Pixel Launcher repeatedly shows an ANR dialog**.
  This is the launcher, not ZenSposed. Dismiss with `adb shell input tap 135 580` (the
  "Wait" button) and re-foreground the app with
  `adb shell am start -n com.hassan.zensposed/.MainActivity`. Prefer driving the app via
  `adb` + `adb exec-out screencap -p` rather than desktop GUI automation.
- **Full functionality cannot be tested here.** The app gates everything behind an
  onboarding screen that requires root (Magisk/KernelSU) + LSPosed hooks in `system_server`,
  which are impossible on a stock emulator. On the emulator you can only exercise the
  onboarding/permission-gate UI (e.g. `adb shell pm grant com.hassan.zensposed
  android.permission.POST_NOTIFICATIONS` flips that row's checkmark). Real focus-session
  behavior needs a rooted physical device (see `README.md`).
