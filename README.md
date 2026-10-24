# FakeCall

An Android app that fakes an incoming phone call at a time you choose, so you have a polite way out of an awkward situation. Pick a caller name and number, pick when it should ring, and the phone wakes up and shows an incoming call.

Built for Android 5.x (minSdk 21). Tested on a Samsung Galaxy A3 (SM-A310F) running stock Android 5.1.1.

## Features

- Choose the caller name and number.
- Choose when the call comes in: immediately, 10 s, 30 s, 1 min, 5 min, 15 min, 30 min, 1 hour, a **custom delay** (hours / minutes / seconds) or a **custom clock time**.
- Wakes the phone and shows over the lock screen, with ringtone and vibration (respects silent / vibrate mode).
- Cancel a scheduled call from the app.
- Two display modes:
  - **Simulated screen** (default, works as a normal install): the app draws its own incoming-call screen with Accept / Decline and an in-call timer.
  - **Real call screen** (experimental, needs root): the app registers with Android's Telecom service and reports an incoming call, so the phone's own call screen, ringtone and call log are used.

No real call is made and nothing is sent to anyone.

## Build

Requires JDK 17 and the Android SDK (platform 34, build-tools 34.0.0). Android Studio works, or from the command line:

```bash
export ANDROID_HOME=~/android-sdk
# the Gradle wrapper jar is not checked in; generate it once with any Gradle 8.x:
gradle wrapper --gradle-version 8.2
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Install

### Normal install (simulated call screen)

```bash
adb install -r app-debug.apk
```

On Android 5 you may need to allow "Unknown sources". The "real call screen" checkbox will untick itself with an error in this mode, which is expected.

### Install as a system app (real call screen, needs root)

Registering as a call provider needs `REGISTER_CALL_PROVIDER`, a permission only system apps can hold. With root you can install the APK as a privileged system app.

#### Easy way: `install_fakecall.sh`

1. Download `FakeCall.apk` from the [Releases](../../releases) page **on the phone** (browser) so it lands in the phone's `Download` folder as `FakeCall.apk`. Or put it on your PC next to the script and push it in step 2.
2. Push the script (and the APK, if you have it on the PC) to the phone and run it as root:

```bash
adb push install_fakecall.sh /data/local/tmp/
adb push FakeCall.apk /data/local/tmp/        # skip if the APK is already on the phone
adb shell su -c "sh /data/local/tmp/install_fakecall.sh"
```

The script finds the APK, removes any normal install of the app, remounts `/system` read-write, copies the APK to `/system/priv-app/FakeCall/` with the right permissions, puts `/system` back to read-only and reboots after 5 seconds. If anything fails it prints an `ERROR:` line and leaves `/system` read-only.

It looks for `FakeCall.apk` in this order: next to the script, `/data/local/tmp`, `/sdcard`, `/sdcard/Download`. If none is found it tries to download it from this repo's release, but only with `curl` or a `wget` that verifies TLS certificates (BusyBox `wget`, which is what Android 5 ships, cannot, so it is never used for https). On a stock Android 5 phone that means the download will not work: get the APK with the browser as in step 1.

Options go after the script name, inside the quotes when using `su -c "..."`:

| Option | What it does |
| --- | --- |
| `noreboot` | install but do not reboot |
| `uninstall` | remove the system app and reboot |
| `download` | ignore local copies and download the APK |
| `https://...apk` | download from this URL instead of the default release |
| `/path/to/file.apk` | install this APK file |

Examples:

```bash
adb shell su -c "sh /data/local/tmp/install_fakecall.sh noreboot"
adb shell su -c "sh /data/local/tmp/install_fakecall.sh uninstall"
adb shell su -c "sh /data/local/tmp/install_fakecall.sh /sdcard/MyBuild.apk"
```

If `su -c` does not work with your root manager, run `adb shell`, then `su`, then `sh /data/local/tmp/install_fakecall.sh`.

#### Manual way

The same thing by hand:

```bash
adb uninstall com.example.fakecall        # remove any normal install first
adb push FakeCall.apk /sdcard/FakeCall.apk
adb shell
su
mount -o remount,rw /system
mkdir -p /system/priv-app/FakeCall
cp /sdcard/FakeCall.apk /system/priv-app/FakeCall/FakeCall.apk
chmod 755 /system/priv-app/FakeCall
chmod 644 /system/priv-app/FakeCall/FakeCall.apk
restorecon -R /system/priv-app/FakeCall   # ignore "not found" if your ROM lacks it
ls -ld /system/priv-app/FakeCall          # expect drwxr-xr-x
ls -l /system/priv-app/FakeCall           # expect -rw-r--r--
mount -o remount,ro /system
reboot
```

If the first `mount` fails, try `mount -o remount,rw /dev/block/bootdevice/by-name/system /system`.

**The permissions matter.** If the APK is left readable only by root (`-rwx------`), the package scanner cannot read it at boot and the app silently never appears.

After the reboot, open the app, tick **Use the phone's real call screen**, and test with **Immediately**. If real mode fails at call time the app shows the error and falls back to the simulated screen.

To remove it: `rm -r /system/priv-app/FakeCall` from a root shell, then reboot. To update, copy the new APK over the old one and reboot.

## Caveats

- The real call screen mode relies on hidden Telecom behaviour of Android 5.x. It is confirmed working on the device above; other ROMs and Android versions may differ.
- Modifying `/system` is done at your own risk.
- A scheduled call is lost if the phone reboots before it fires.
- Targets API 22 on purpose, so it installs on Android 5. Recent Android versions refuse to install apps that old.
