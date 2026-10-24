#!/system/bin/sh
# FakeCall - install as a privileged system app (needs root).
#
#   adb push install_fakecall.sh /data/local/tmp/
#   adb shell su -c "sh /data/local/tmp/install_fakecall.sh"
#
# The APK is taken from the first place it is found:
#   next to this script, /data/local/tmp, /sdcard, /sdcard/Download
# and if it is not there it is downloaded from GitHub. That needs curl, or a wget
# that verifies TLS certificates (BusyBox wget does not, so it is never used for
# https). Otherwise download the APK in the phone's browser and run the script again.
#
# Options (put them after the script name):
#   noreboot            don't reboot at the end
#   uninstall           remove the system app again
#   download            always download the latest APK, ignore local copies
#   https://...apk      download from this URL instead of the default
#   /path/to/some.apk   use this APK file

PKG=com.example.fakecall
NAME=FakeCall
DEST_DIR=/system/priv-app/$NAME
DEST_APK=$DEST_DIR/$NAME.apk
APK_URL=https://github.com/jancaticgamer3212/fakecallrealapk/releases/download/v1/FakeCall.apk
DL_APK=/data/local/tmp/FakeCall.apk

MODE=install
REBOOT=1
FORCE_DOWNLOAD=0
APK_ARG=
RW_DONE=0
SELF=$0

say() { echo "[FakeCall] $*"; }

ro_remount() {
    [ "$RW_DONE" = 1 ] || return 0
    sync
    mount -o remount,ro /system 2>/dev/null \
        || { [ -n "$SYSDEV" ] && mount -o remount,ro "$SYSDEV" /system 2>/dev/null; }
    RW_DONE=0
}

die() {
    echo "[FakeCall] ERROR: $*" >&2
    ro_remount
    exit 1
}

for a in "$@"; do
    case "$a" in
        noreboot) REBOOT=0 ;;
        uninstall) MODE=uninstall ;;
        download) FORCE_DOWNLOAD=1 ;;
        http://*|https://*) APK_URL=$a; FORCE_DOWNLOAD=1 ;;
        *.apk) APK_ARG=$a ;;
        *) die "unknown option: $a" ;;
    esac
done

# ---- need root: re-run ourselves through su if we are not root yet ----
case "$(id)" in
    uid=0*) ;;
    *)
        say "not root, trying su..."
        exec su -c "sh '$SELF' $*"
        ;;
esac

# ---- find the device node behind /system (used as a remount fallback) ----
SYSDEV=
while read dev mp rest; do
    [ "$mp" = /system ] && SYSDEV=$dev
done < /proc/mounts

remount_rw() {
    mount -o remount,rw /system 2>/dev/null && return 0
    [ -n "$SYSDEV" ] && mount -o remount,rw "$SYSDEV" /system 2>/dev/null && return 0
    mount -o remount,rw /dev/block/bootdevice/by-name/system /system 2>/dev/null && return 0
    return 1
}

make_writable() {
    remount_rw
    if ! ( : > /system/.fakecall_rw_test ) 2>/dev/null; then
        die "could not make /system writable (remount failed)"
    fi
    rm -f /system/.fakecall_rw_test
    RW_DONE=1
}

finish() {
    if [ "$REBOOT" = 1 ]; then
        say "Rebooting in 5 seconds (Ctrl-C to cancel)..."
        sleep 5
        reboot
    else
        say "Done. Reboot the phone to finish."
    fi
}

# file size in bytes, whichever tools this Android version has
fsize() {
    s=$(wc -c < "$1" 2>/dev/null) && [ -n "$s" ] && { echo $s; return; }
    s=$(stat -c %s "$1" 2>/dev/null) && [ -n "$s" ] && { echo $s; return; }
    set -- $(ls -l "$1")
    case "$2" in
        ""|*[!0-9]*) echo "$4" ;;   # no link-count column (Android 5 toolbox)
        *) echo "$5" ;;             # with link-count column
    esac
}

# Downloads $APK_URL to $DL_APK with whatever downloader the phone has.
# TLS certificates are always verified. Returns 0 on success.
download_apk() {
    say "Downloading $APK_URL"
    for tool in "curl" "wget" "busybox wget" "toybox wget"; do
        set -- $tool
        command -v "$1" >/dev/null 2>&1 || continue
        # BusyBox/toybox wget cannot check TLS certificates. Never use a downloader
        # like that for an https URL - the file is going into /system.
        if [ "$tool" != curl ]; then
            case "$APK_URL" in
                https://*)
                    if ! $tool --help 2>&1 | grep -q ca-certificate; then
                        say "skipping '$tool': it cannot verify TLS certificates"
                        continue
                    fi
                    ;;
            esac
        fi
        rm -f "$DL_APK"
        if [ "$tool" = curl ]; then
            curl -fL -o "$DL_APK" "$APK_URL"
        else
            "$@" -O "$DL_APK" "$APK_URL"
        fi
        # a real APK is tens of KB; an error page or empty file is not
        if [ -f "$DL_APK" ] && [ "$(fsize "$DL_APK")" -gt 10000 ] 2>/dev/null; then
            say "Downloaded with: $tool"
            return 0
        fi
        say "$tool could not download it"
    done
    rm -f "$DL_APK"
    return 1
}

# ---------------------------- uninstall ----------------------------
if [ "$MODE" = uninstall ]; then
    [ -d "$DEST_DIR" ] || die "$DEST_DIR does not exist - nothing to remove"
    make_writable
    rm -rf "$DEST_DIR" || die "could not delete $DEST_DIR"
    ro_remount
    say "Removed $DEST_DIR."
    finish
    exit 0
fi

# ----------------------------- install -----------------------------
SCRIPT_DIR=${SELF%/*}
[ "$SCRIPT_DIR" = "$SELF" ] && SCRIPT_DIR=.

SRC=
if [ -n "$APK_ARG" ]; then
    [ -f "$APK_ARG" ] || die "$APK_ARG not found"
    SRC=$APK_ARG
elif [ "$FORCE_DOWNLOAD" = 1 ]; then
    download_apk || die "download failed (see above)"
    SRC=$DL_APK
else
    for c in "$SCRIPT_DIR/FakeCall.apk" /data/local/tmp/FakeCall.apk /sdcard/FakeCall.apk \
             /sdcard/Download/FakeCall.apk /storage/emulated/0/FakeCall.apk; do
        if [ -f "$c" ]; then
            SRC=$c
            break
        fi
    done
    if [ -z "$SRC" ]; then
        say "FakeCall.apk not found locally"
        if download_apk; then
            SRC=$DL_APK
        else
            say "This phone has no downloader that can safely fetch it (needs curl or a wget that checks certificates)."
            say "Open this link in the phone's browser, let it download, then run the script again:"
            say "  $APK_URL"
            die "no APK available"
        fi
    fi
fi
say "Using APK: $SRC"

# a normal (user) install of the same package would shadow the system copy
pm uninstall "$PKG" >/dev/null 2>&1

make_writable
say "/system is writable"

rm -rf "$DEST_DIR"
mkdir -p "$DEST_DIR" || die "could not create $DEST_DIR"
cp "$SRC" "$DEST_APK" 2>/dev/null || cat "$SRC" > "$DEST_APK" || die "could not copy the APK"

# The package scanner is not root: the APK must be world-readable or Android
# silently skips it at boot.
chmod 755 "$DEST_DIR"
chmod 644 "$DEST_APK"
command -v restorecon >/dev/null 2>&1 && restorecon -R "$DEST_DIR" 2>/dev/null

# verify the copy
if command -v cmp >/dev/null 2>&1; then
    cmp "$SRC" "$DEST_APK" >/dev/null 2>&1 || die "copied APK does not match the source"
else
    [ "$(fsize "$SRC")" = "$(fsize "$DEST_APK")" ] || die "copied APK has the wrong size"
fi

say "Installed:"
ls -ld "$DEST_DIR"
ls -l "$DEST_APK"

ro_remount
say "/system set back to read-only"
finish
