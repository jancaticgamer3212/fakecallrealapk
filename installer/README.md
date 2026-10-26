# Fake Call Installer

A small Android app that installs (and removes) the bundled `FakeCall.apk` for you, so you don't need `adb`.

**Download:** `FakeCallInstaller.apk` is on the [latest release](https://github.com/jancaticgamer3212/fakecallrealapk/releases/latest) ([direct link](https://github.com/jancaticgamer3212/fakecallrealapk/releases/latest/download/FakeCallInstaller.apk)). Install it on the phone like any APK (Android may ask you to allow "Unknown sources").

| | With root | Without root |
| --- | --- | --- |
| Install | **Install as system app** (copies FakeCall to `/system/priv-app`, then asks to reboot) | **Install normally** (system package installer) |
| Uninstall | **Uninstall system app** (removes it from `/system/priv-app`, then asks to reboot) | **Uninstall** (system uninstaller) |
| Call screen | The phone's real call screen (experimental), or the simulated one | Simulated call screen only |

The status line at the top shows whether FakeCall is installed and whether it is a system app.

## How the root install works

The app unpacks `installer.sh` and `FakeCall.apk` from its assets and runs
`su -c "sh installer.sh install FakeCall.apk"`. The script removes any normal install, remounts `/system`
read-write, copies the APK to `/system/priv-app/FakeCall/` with the right permissions (755 / 644, otherwise
Android silently ignores it at boot), checks the copy, and puts `/system` back to read-only. It never reboots on
its own; the app asks you. The script's output is shown in the log at the bottom of the screen.

Your root manager (SuperSU, Magisk, ...) may ask whether to allow "Fake Call Installer". Allow it.

## Notes

- Installing normally needs "Unknown sources" turned on (Android shows a button to the setting if it is off).
- If FakeCall is a system app, "Install normally" and "Uninstall" refuse and tell you to use the system app
  buttons, because a system app cannot be removed without root.
- The root install removes any normal install of FakeCall first, which clears its settings.
- To bundle a different FakeCall build, replace `app/src/main/assets/FakeCall.apk` and rebuild.

## Build

JDK 17 and the Android SDK (platform 34, build-tools 34.0.0):

```bash
export ANDROID_HOME=~/android-sdk
gradle wrapper --gradle-version 8.2     # once; the wrapper jar is not checked in
./gradlew assembleDebug
```

The APK is `app/build/outputs/apk/debug/app-debug.apk`.
