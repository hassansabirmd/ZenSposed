# ZenSposed

A hardcore Android focus / digital-detox app for **rooted** devices with **LSPosed**.  
During a session it locks you into a full-screen focus UI, blocks Home / Recents / notification shade (configurable), and only allows whitelisted apps (plus calls, SMS, and system share / file-picker flows).

**Inspired by Zen Space on OnePlus phones** — ZenSposed brings a similar “enter focus and stay there” idea to any rooted device with LSPosed, with stronger system-level enforcement.

**Package:** `com.hassan.zensposed` · **Min SDK:** 31 · **Target SDK:** 35 · **Version:** 1.0

---

# ⚠️ DISCLAIMER — READ THIS FIRST

## **NO WARRANTY IS GUARANTEED.**

## **USE AT YOUR OWN RISK.**

### **This app is vibe-coded. There is no promise that it is complete, secure, stable, or safe for every device. Root + LSPosed + system hooks can break updates, soft-brick behavior, or lock you out of normal phone use if something goes wrong. You are solely responsible for how you install and use it.**

### **Tested only on Google Pixel 8 Pro.** Other OEMs (Samsung, Xiaomi, OnePlus, etc.) may behave differently or not work at all.

By installing or forking this project you accept that the authors owe you nothing if your device, data, or workflow is harmed.

---

## Who this is for

- People who want a **serious** focus lock (not a soft “blocklist” you can dismiss).
- Users already comfortable with **KernelSU / Magisk (Zygisk)**, **LSPosed**, and soft reboots.
- Developers exploring root + system_server hooks for lock / parental / detox style apps.
- Anyone willing to keep an **emergency exit** (long password or QR) and accept the risks above.

**Not intended for:** non-rooted phones, Play Protect–only devices, users who need App Store–friendly “normal mode,” or anyone who cannot recover from a stuck session / soft reboot.

---

## What the app does

| Feature | Description |
|--------|-------------|
| Focus sessions | Timed or open-ended (“no limit”) sessions with a full-screen focus UI |
| Profiles | Saved presets (duration + own whitelist) |
| Deep Zen | Home quick-start with wheel timer + quick duration chips |
| Whitelist | Extra apps on the focus screen; dialer / messages / contacts / calculator always allowed |
| System flows | Share sheets & document UI allowed so e.g. Files → WhatsApp still works |
| Panel lock | Optional block of notification / QS shade via `StatusBarManagerService` |
| Home / Recents | Optional `DISABLE_HOME` / `DISABLE_RECENT` during session |
| Quick toggles | Wi‑Fi, mobile data, hotspot, torch, DND (optional) |
| Emergency exit | Long password **or** QR scan (one method active) |
| Themes | Focus backgrounds (Ocean, Forest, Sunset, Night, Desert, **OLED Black**) + app light/dark/system |
| Stats | Local session history (Room) |
| Boot resume | Restores an active session after reboot when possible |

There is **no** “normal / non-root mode.” The app refuses to run as a full product without root + loaded LSPosed hooks.

---

## Requirements

1. **Android 12+** (API 31+).
2. **Root:** Magisk or KernelSU with working `su` (libsu).
3. **Zygisk** (Magisk) or equivalent LSPosed host on KernelSU.
4. **LSPosed** (official / Zygisk variant) with this app enabled as a module.
5. **Soft reboot** (or reboot) after enabling the module so `system_server` hooks load.
6. Recommended: grant the runtime / special permissions the onboarding screen asks for (accessibility, notifications, battery exemption, etc.).

### Verified device

| Device | Status |
|--------|--------|
| **Google Pixel 8 Pro** | Tested — primary development device |
| Other devices | Untested — contributions and reports welcome |

---

## How to make it work (setup)

1. Flash / install a working root stack (KernelSU or Magisk + Zygisk).
2. Install **LSPosed** and open the LSPosed Manager.
3. Build or install the ZenSposed APK (`./gradlew :app:assembleDebug` or a release build).
4. In LSPosed → **Modules** → enable **ZenSposed**.
5. Open the module scope and ensure at least:
   - **System Framework** (`android`) — **required**
   - Optionally: SystemUI, Settings, Package Installer, Permission Controller (see [LSPosed hooks](#lsposed-hooks))
6. **Soft reboot** (LSPosed) or full reboot.
7. Open ZenSposed → complete onboarding → confirm privilege check (root + LSPosed alive file).
8. Enable **Accessibility** for ZenSposed (secondary watchdog).
9. Set an **emergency password** (20+ chars) or **exit QR** before relying on a long session.
10. Start a short test session and verify: whitelist apps open, Home/Recents behave as configured, panel lock works, exit method works.

If the app says hooks are missing: module not scoped to `android`, or you skipped the soft reboot.

---

## Permissions (and why)

| Permission | Purpose |
|------------|---------|
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | Keep `FocusService` alive for the active session |
| `POST_NOTIFICATIONS` | Ongoing session notification |
| `SYSTEM_ALERT_WINDOW` | Overlay / bring focus UI forward when needed |
| `RECEIVE_BOOT_COMPLETED` | Resume session after reboot |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Reduce OEM killing the session service |
| `WAKE_LOCK` | Keep timing / AOD-related work reliable |
| `VIBRATE` | Feedback where used |
| `QUERY_ALL_PACKAGES` | Build whitelist picker and resolve default dialer/SMS/etc. |
| `PACKAGE_USAGE_STATS` | Optional usage-related helpers (declared for enforcement tooling) |
| `CAMERA` | Scan exit QR / register custom exit QR |
| `FLASHLIGHT` | Torch quick toggle |
| `ACCESS_NOTIFICATION_POLICY` | Do Not Disturb toggle |
| `ACCESS_WIFI_STATE` / `ACCESS_NETWORK_STATE` | Reflect Wi‑Fi / data state on the focus screen |
| Accessibility service | Bounce non-whitelisted apps back to the focus screen |
| Device admin (optional path) | Extra uninstall / tamper resistance while locked |
| Root (`su`) | Bridge file, keep-alive, radio toggles, clear recents, force-launch focus |
| LSPosed module | Real panel / Home / Recents / startActivity / uninstall guards |

---

## LSPosed hooks

Entry: `com.hassan.zensposed.xposed.FocusXposedEntry` (`assets/xposed_init`).

**Recommended scope** (`res/values/arrays.xml`):

| Package | Role |
|---------|------|
| `android` (System Framework) | **Required** — `system_server` hooks |
| `com.android.systemui` | SystemUI-related scope |
| `com.android.settings` | Settings / Wi‑Fi panel paths |
| `com.android.packageinstaller` / `com.google.android.packageinstaller` | Block uninstall UX while locked |
| `com.android.permissioncontroller` | Permission / installer related flows |

### What is hooked (high level)

| Target | Behavior during an active session |
|--------|-----------------------------------|
| `StatusBarManagerService` (`disable` / `disable2`) | Lock notification shade / QS; optional Home & Recents disable via flags |
| Broadcast `SET_PANEL_LOCKED` + bridge file | App tells `system_server` to apply / clear status-bar flags |
| `ActivityManagerService` activity starts | Cancel starts of non-whitelisted packages (share / dialer / system UI exceptions) |
| `forceStopPackage` | Block force-stop of ZenSposed while locked |
| `PackageManagerService.deletePackageVersioned` | Block uninstall of ZenSposed while locked |
| Settings / package installer packages | Extra guards against removing the app mid-session |

Live state is shared via a world-readable bridge file written with root:

`/data/local/tmp/zensposed/state.prop`

Hooks prove they loaded by writing:

`/data/system/zensposed_xposed_alive`

---

## Build

```bash
git clone <your-repo-url>
cd ZenSposed
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Release (local — requires keystore env vars):

```bash
export ZENSPOSED_STORE_FILE=/path/to/zensposed-release.jks
export ZENSPOSED_STORE_PASSWORD=...
export ZENSPOSED_KEY_ALIAS=zensposed
export ZENSPOSED_KEY_PASSWORD=...
./gradlew :app:assembleRelease
```

### CI / signed GitHub Releases

- **CI** (`.github/workflows/ci.yml`) builds a debug APK on every push/PR to `main`.
- **Release** (`.github/workflows/release.yml`) builds a **signed** release APK when you push a tag like `v1.0.0`, or via **Actions → Release signed APK → Run workflow**.

Required repository secrets (Settings → Secrets and variables → Actions):

| Secret | Description |
|--------|-------------|
| `SIGNING_KEYSTORE_BASE64` | Base64 of the release `.jks` / PKCS12 keystore |
| `SIGNING_STORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_ALIAS` | Key alias (`zensposed`) |
| `SIGNING_KEY_PASSWORD` | Key password |

Never commit the keystore or passwords. Keep a private backup of `.signing/` if you generated one locally.

Notes:

- JDK **17** required.
- `local.properties` with `sdk.dir=...` is created by Android Studio (gitignored).
- Xposed API is `compileOnly` — do not ship it inside the APK.
- Release minify is currently **off**; ProGuard keep rules for `xposed.**` already exist if you enable minify later.

---

## Project structure

```
app/src/main/java/com/hassan/zensposed/
├── MainActivity.kt              # App chrome (home, settings, profiles, stats)
├── ZenSposedApp.kt              # Application + repositories
├── accessibility/               # FocusAccessibilityService (watchdog)
├── admin/                       # Device admin receiver
├── core/                        # Constants, AppResolver, PrivilegeRequirements, QR helpers
├── data/
│   ├── db/                      # Room sessions / stats
│   ├── model/                   # NatureTheme, Profile, …
│   ├── prefs/                   # SecurePrefs (password / QR secrets)
│   └── settings/                # DataStore settings
├── focus/                       # FocusService, FocusController, FocusState, DND/Torch
├── lock/                        # LockEnforcer (bridge + status-bar broadcast)
├── receiver/                    # Boot / user-present
├── root/                        # RootManager (su commands)
├── ui/                          # Compose screens (home, focus, settings, …)
└── xposed/                      # FocusXposedEntry, BridgeState
```

### Main classes / responsibilities

| Class | Role |
|-------|------|
| `FocusController` / `FocusState` | Start/stop session + in-memory / persisted UI state |
| `FocusService` | Foreground session loop, top-app enforcement, timer |
| `FocusActivity` + `FocusScreen` | Full-screen focus UI, toggles, whitelist grid, exit |
| `LockEnforcer` | Writes bridge, broadcasts panel flags, keep-alive |
| `RootManager` | `su` helpers (Wi‑Fi, clear recents, bridge I/O, LSPosed probes) |
| `FocusXposedEntry` | All LSPosed hooks |
| `BridgeState` | Read bridge from `system_server` process |
| `FocusAccessibilityService` | Secondary reassert if a non-allowed app appears |
| `SettingsRepository` | Themes, whitelist, panel/home/recents flags, appearance |
| `AppResolver` | Default dialer/SMS/contacts/calculator + always-allowed system UI |
| `PrivilegeRequirements` | Gate: root + LSPosed framework + alive hooks |
| `SecurePrefs` | Emergency password / QR payload storage |
| `MainViewModel` | Settings / profiles / installed apps for Compose UI |

---

## Suggesting edits & contributing

Suggestions and improvements are **very welcome**.

1. **Issues** — open a GitHub Issue with device model, Android version, Magisk/KernelSU + LSPosed versions, and steps to reproduce.
2. **Ideas** — feature requests and UX notes are fine even without a patch.
3. **Pull requests**
   - Fork the repo
   - Create a branch (`feat/...`, `fix/...`)
   - Keep changes focused; match existing Kotlin / Compose style
   - Test on a real rooted device if the change touches root, accessibility, or Xposed
   - Describe what you changed and how you tested
4. **Code of collaboration** — be respectful; this is a hobby / experimental project, not a commercial SLA.

### Forking & reuse

You are free to **fork** this repository or reuse ideas / code in your own projects under the [MIT License](LICENSE).

**Please add a citation**, for example:

> Based on / inspired by **ZenSposed** by Hassan (`com.hassan.zensposed`).

---

## Privacy

- No analytics SDK is bundled for telemetry in this codebase.
- Session stats and settings stay on-device (Room + DataStore + encrypted prefs for secrets).
- Root commands and the bridge file are local to the device.
- Review the code before installing if that matters to you — you have full source.

---

## Known limitations

- Pixel-first; other OEMs may need hook or dump-format tweaks.
- Hard locks can feel “too strong” — always configure exit **before** long sessions.
- Soft reboot required after every module / APK update that changes Xposed code.
- Clearing recents / Wi‑Fi panel rely on suppress windows + system APIs that vary by Android version.

---

## License

[MIT](LICENSE) — provided **as is**, with **no warranty**. See the disclaimer at the top of this README.
