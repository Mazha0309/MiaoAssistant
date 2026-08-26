#!/system/bin/sh

PACKAGE="@@APPLICATION_ID@@"
SERVICE="$PACKAGE/$PACKAGE.keepalive.KeepAliveService"
ACCESSIBILITY="$PACKAGE/$PACKAGE.service.GlobalInputAccessibilityService"
ACCESSIBILITY_SHORT="$PACKAGE/.service.GlobalInputAccessibilityService"
STATE_DIR="/data/adb/miaoassistant"
MARKER="$STATE_DIR/root-keepalive.enabled"
PID_FILE="$STATE_DIR/root-keepalive.pid"
SCRIPT_PATH="/data/adb/service.d/miaoassistant-keepalive.sh"
PREFERENCES="/data/user/0/$PACKAGE/shared_prefs/miao_assistant_config.xml"

[ -f "$MARKER" ] || exit 0

if [ -r "$PID_FILE" ]; then
    old_pid="$(cat "$PID_FILE" 2>/dev/null)"
    case "$old_pid" in
        ''|*[!0-9]*) ;;
        *)
            if kill -0 "$old_pid" 2>/dev/null && \
                tr '\000' ' ' < "/proc/$old_pid/cmdline" 2>/dev/null | grep -q "$SCRIPT_PATH"; then
                exit 0
            fi
            ;;
    esac
fi

echo "$$" > "$PID_FILE"
chmod 0600 "$PID_FILE"

cleanup() {
    rm -f "$PID_FILE"
    rmdir "$STATE_DIR" 2>/dev/null
}
trap cleanup 0 TERM INT

while [ "$(getprop sys.boot_completed)" != "1" ]; do
    [ -f "$MARKER" ] || exit 0
    sleep 5
done

missing_package_checks=0
configuration_seen=0
while [ -f "$MARKER" ]; do
    if pm path "$PACKAGE" >/dev/null 2>&1; then
        missing_package_checks=0
    else
        missing_package_checks=$((missing_package_checks + 1))
        if [ "$missing_package_checks" -ge 10 ]; then
            rm -f "$MARKER" "$SCRIPT_PATH"
            rmdir "$STATE_DIR" 2>/dev/null
            exit 0
        fi
        sleep 30
        continue
    fi

    if [ -r "$PREFERENCES" ]; then
        configuration_seen=1
        if ! grep -q '<boolean name="keep_alive" value="true"' "$PREFERENCES" || \
            ! grep -q '<boolean name="root_keep_alive" value="true"' "$PREFERENCES"; then
            rm -f "$MARKER" "$SCRIPT_PATH"
            rmdir "$STATE_DIR" 2>/dev/null
            exit 0
        fi
    elif [ "$configuration_seen" -eq 1 ]; then
        # App data was cleared while the package remained installed.
        rm -f "$MARKER" "$SCRIPT_PATH"
        rmdir "$STATE_DIR" 2>/dev/null
        exit 0
    else
        # Credential-encrypted app data may still be locked shortly after boot.
        sleep 30
        continue
    fi

    enabled_services="$(settings --user 0 get secure enabled_accessibility_services 2>/dev/null)"
    [ "$enabled_services" = "null" ] && enabled_services=""
    case ":$enabled_services:" in
        *":$ACCESSIBILITY:"*|*":$ACCESSIBILITY_SHORT:"*) ;;
        *)
            if [ -n "$enabled_services" ]; then
                enabled_services="$enabled_services:$ACCESSIBILITY"
            else
                enabled_services="$ACCESSIBILITY"
            fi
            settings --user 0 put secure enabled_accessibility_services "$enabled_services"
            settings --user 0 put secure accessibility_enabled 1
            ;;
    esac

    if ! dumpsys activity services "$SERVICE" 2>/dev/null | grep -q "ServiceRecord"; then
        am start-foreground-service --user 0 \
            -a "$PACKAGE.action.START_KEEP_ALIVE" \
            -n "$SERVICE" >/dev/null 2>&1
    fi

    sleep 30
done
