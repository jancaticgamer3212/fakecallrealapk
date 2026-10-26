#!/system/bin/sh
# FakeCall Installer - root helper (run by the app through su; can also be run by hand).
#
#   sh installer.sh install /path/to/FakeCall.apk
#   sh installer.sh uninstall
#
# Installs FakeCall as a privileged system app in /system/priv-app (needed for the real call
# screen) or removes it again. It never reboots: a system app is only picked up at boot.

PKG=com.example.fakecall
NAME=FakeCall
DEST_DIR=/system/priv-app/$NAME
DEST_APK=$DEST_DIR/$NAME.apk
RW_DONE=0
SYSDEV=

say() { echo "[installer] $*"; }

ro_remount() {
    [ "$RW_DONE" = 1 ] || return 0
    sync
    mount -o remount,ro /system 2>/dev/null \
        || { [ -n "$SYSDEV" ] && mount -o remount,ro "$SYSDEV" /system 2>/dev/null; }
    RW_DONE=0
}

die() {
    echo "[installer] ERROR: $*"
    ro_remount
    exit 1
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

case "$(id)" in
    uid=0*) ;;
    *) die "not running as root" ;;
esac

# device node behind /system (used as a remount fallback)
while read dev mp rest; do
    [ "$mp" = /system ] && SYSDEV=$dev
done < /proc/mounts

make_writable() {
    mount -o remount,rw /system 2>/dev/null \
        || { [ -n "$SYSDEV" ] && mount -o remount,rw "$SYSDEV" /system 2>/dev/null; } \
        || mount -o remount,rw /dev/block/bootdevice/by-name/system /system 2>/dev/null
    if ! ( : > /system/.fakecall_rw_test ) 2>/dev/null; then
        die "could not make /system writable (remount failed)"
    fi
    rm -f /system/.fakecall_rw_test
    RW_DONE=1
    say "/system is writable"
}

case "$1" in
    install)
        SRC=$2
        [ -n "$SRC" ] && [ -f "$SRC" ] || die "APK not found: $SRC"
        [ "$(fsize "$SRC")" -gt 10000 ] 2>/dev/null || die "APK looks empty or broken: $SRC"
        say "Installing $SRC"

        # a normal (user) install of the same package would shadow the system copy
        pm uninstall "$PKG" >/dev/null 2>&1

        make_writable
        rm -rf "$DEST_DIR"
        mkdir -p "$DEST_DIR" || die "could not create $DEST_DIR"
        cp "$SRC" "$DEST_APK" 2>/dev/null || cat "$SRC" > "$DEST_APK" || die "could not copy the APK"

        # The package scanner is not root: the APK must be world-readable or Android
        # silently skips it at boot.
        chmod 755 "$DEST_DIR"
        chmod 644 "$DEST_APK"
        command -v restorecon >/dev/null 2>&1 && restorecon -R "$DEST_DIR" 2>/dev/null

        if command -v cmp >/dev/null 2>&1; then
            cmp "$SRC" "$DEST_APK" >/dev/null 2>&1 || die "copied APK does not match the source"
        else
            [ "$(fsize "$SRC")" = "$(fsize "$DEST_APK")" ] || die "copied APK has the wrong size"
        fi

        ls -ld "$DEST_DIR"
        ls -l "$DEST_APK"
        ro_remount
        say "/system set back to read-only"
        say "Done. Reboot the phone to finish."
        ;;

    uninstall)
        # a normal install, if there is one (fails harmlessly for a system app)
        pm uninstall "$PKG" >/dev/null 2>&1
        if [ -d "$DEST_DIR" ]; then
            make_writable
            rm -rf "$DEST_DIR" || die "could not delete $DEST_DIR"
            ro_remount
            say "Removed $DEST_DIR"
            say "Done. Reboot the phone to finish."
        else
            say "No system copy found in /system/priv-app."
            say "Done."
        fi
        ;;

    *)
        die "usage: installer.sh install /path/to/FakeCall.apk | installer.sh uninstall"
        ;;
esac
exit 0
