package com.mazha0309.miaoassistant.keepalive

import android.content.Context
import android.util.Log
import com.mazha0309.miaoassistant.privileged.ProcessWaiter

internal object RootKeepAliveController {
    fun enable(context: Context): Boolean {
        val packageName = context.packageName
        if (!PACKAGE_NAME_PATTERN.matches(packageName)) return false

        val script = runCatching {
            context.assets.open(WATCHDOG_ASSET).bufferedReader().use { reader ->
                reader.readText().replace(APPLICATION_ID_TOKEN, packageName)
            }
        }.getOrElse { error ->
            Log.e(TAG, "Unable to read the root keep-alive watchdog", error)
            return false
        }

        if (!runRootCommand(INSTALL_COMMAND, script.toByteArray(Charsets.UTF_8))) return false
        if (!runRootCommand(START_COMMAND)) {
            disable()
            return false
        }

        Thread.sleep(STARTUP_CHECK_DELAY_MS)
        if (runRootCommand(VERIFY_COMMAND)) return true

        Log.w(TAG, "Root keep-alive watchdog did not remain running")
        disable()
        return false
    }

    fun disable(): Boolean = runRootCommand(DISABLE_COMMAND)

    private fun runRootCommand(command: String, stdin: ByteArray? = null): Boolean = try {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        process.outputStream.use { output ->
            if (stdin != null) output.write(stdin)
        }
        if (!ProcessWaiter.waitForExit(process, ROOT_COMMAND_TIMEOUT_SECONDS)) {
            Log.w(TAG, "Root keep-alive command timed out")
            false
        } else {
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val success = process.exitValue() == 0
            if (!success) {
                Log.w(TAG, "Root keep-alive command failed (${process.exitValue()}): ${output.take(300)}")
            }
            success
        }
    } catch (error: Exception) {
        Log.w(TAG, "Unable to run root keep-alive command", error)
        false
    }

    private const val TAG = "MiaoRootKeepAlive"
    private const val WATCHDOG_ASSET = "root-keepalive-watchdog.sh"
    private const val APPLICATION_ID_TOKEN = "@@APPLICATION_ID@@"
    private const val STATE_DIR = "/data/adb/miaoassistant"
    private const val MARKER_PATH = "$STATE_DIR/root-keepalive.enabled"
    private const val PID_PATH = "$STATE_DIR/root-keepalive.pid"
    private const val SCRIPT_PATH = "/data/adb/service.d/miaoassistant-keepalive.sh"
    private const val ROOT_COMMAND_TIMEOUT_SECONDS = 30L
    private const val STARTUP_CHECK_DELAY_MS = 350L

    private val PACKAGE_NAME_PATTERN = Regex("[A-Za-z0-9_.]+")

    private val INSTALL_COMMAND = """
        umask 077
        mkdir -p /data/adb/service.d $STATE_DIR || exit 1
        cat > $SCRIPT_PATH || exit 1
        chmod 0700 $SCRIPT_PATH || exit 1
        : > $MARKER_PATH || exit 1
        chmod 0600 $MARKER_PATH || exit 1
    """.trimIndent()

    private val START_COMMAND = """
        if [ -r $PID_PATH ]; then
            watchdog_pid="${'$'}(cat $PID_PATH 2>/dev/null)"
            case "${'$'}watchdog_pid" in
                ''|*[!0-9]*) ;;
                *)
                    if tr '\000' ' ' < "/proc/${'$'}watchdog_pid/cmdline" 2>/dev/null | grep -q "$SCRIPT_PATH"; then
                        kill "${'$'}watchdog_pid" 2>/dev/null
                    fi
                    ;;
            esac
        fi
        rm -f $PID_PATH
        nohup /system/bin/sh $SCRIPT_PATH </dev/null >/dev/null 2>&1 &
    """.trimIndent()

    private val VERIFY_COMMAND = """
        watchdog_pid="${'$'}(cat $PID_PATH 2>/dev/null)"
        case "${'$'}watchdog_pid" in
            ''|*[!0-9]*) exit 1 ;;
        esac
        kill -0 "${'$'}watchdog_pid" 2>/dev/null || exit 1
        tr '\000' ' ' < "/proc/${'$'}watchdog_pid/cmdline" 2>/dev/null | grep -q "$SCRIPT_PATH"
    """.trimIndent()

    private val DISABLE_COMMAND = """
        rm -f $MARKER_PATH
        if [ -r $PID_PATH ]; then
            watchdog_pid="${'$'}(cat $PID_PATH 2>/dev/null)"
            case "${'$'}watchdog_pid" in
                ''|*[!0-9]*) ;;
                *)
                    if tr '\000' ' ' < "/proc/${'$'}watchdog_pid/cmdline" 2>/dev/null | grep -q "$SCRIPT_PATH"; then
                        kill "${'$'}watchdog_pid" 2>/dev/null
                    fi
                    ;;
            esac
        fi
        rm -f $PID_PATH $SCRIPT_PATH
        rmdir $STATE_DIR 2>/dev/null || true
    """.trimIndent()
}
