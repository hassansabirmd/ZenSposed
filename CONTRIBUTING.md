# Contributing

Thanks for taking an interest in ZenSposed.

## Before you start

- Read the **Disclaimer** in [README.md](README.md). This project uses root and LSPosed system hooks.
- Prefer testing on a real rooted device (development target: **Pixel 8 Pro**).

## How to contribute

1. Fork the repository.
2. Create a focused branch (`fix/wifi-icon`, `feat/oem-hooks`, …).
3. Make your changes; keep PRs small when possible.
4. Build with `./gradlew :app:assembleDebug`.
5. Open a Pull Request describing **what**, **why**, and **how you tested**.

## Issues

Include:

- Device model & Android version  
- Magisk or KernelSU version  
- LSPosed version  
- ZenSposed version / commit  
- Steps to reproduce  
- Logs if relevant (`adb logcat` filtered for `ZenSposed` / `Xposed`)

## Code style

- Kotlin + Jetpack Compose, match existing naming and package layout.
- Do not commit `local.properties`, keystores, or personal paths.
- Xposed changes usually need a soft reboot after install.

## License & attribution

By contributing you agree your changes are licensed under the project [MIT License](LICENSE).  
If you reuse this work elsewhere, please keep a citation to ZenSposed (see README).
