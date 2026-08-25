package com.mazha0309.miaoassistant.privileged

import android.content.Context
import android.util.Log
import androidx.annotation.Keep

@Keep
class InputInjectorUserService : IInputInjector.Stub {
    private var apkPath: String? = null

    constructor()

    @Keep
    constructor(context: Context) {
        apkPath = context.applicationInfo.sourceDir
    }

    override fun replaceText(text: String, moveCursorLeft: Int): Boolean {
        val snapshot = try {
            if (android.os.Process.myUid() == SHELL_UID) {
                ShellClipboardBridge.swap(text)
            } else {
                apkPath?.let { ClipboardHelperProcess.swap(it, text) }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Unable to preserve the clipboard", error)
            null
        } ?: return false

        var success = false
        try {
            if (!runInput("keyevent", "KEYCODE_MOVE_END")) return false
            Thread.sleep(COMMAND_GAP_MS)
            if (!runInput("keycombination", "KEYCODE_CTRL_LEFT", "KEYCODE_A")) return false
            Thread.sleep(COMMAND_GAP_MS)
            if (!runInput("keyevent", "KEYCODE_DEL")) return false
            Thread.sleep(COMMAND_GAP_MS)
            if (!runInput("keycombination", "KEYCODE_CTRL_LEFT", "KEYCODE_A")) return false
            Thread.sleep(COMMAND_GAP_MS)
            if (!runInput("keyevent", "KEYCODE_DEL")) return false
            Thread.sleep(COMMAND_GAP_MS)
            if (!runInput("keycombination", "KEYCODE_CTRL_LEFT", "KEYCODE_V")) return false

            val safeMove = moveCursorLeft.coerceIn(0, MAX_CURSOR_MOVE)
            if (safeMove > 0) {
                Thread.sleep(COMMAND_GAP_MS)
                val keys = Array(safeMove) { "KEYCODE_DPAD_LEFT" }
                if (!runInput("keyevent", *keys)) return false
            }
            success = true
            return true
        } finally {
            Thread.sleep(CLIPBOARD_RESTORE_DELAY_MS)
            val restored = try {
                if (android.os.Process.myUid() == SHELL_UID) {
                    ShellClipboardBridge.restore(snapshot)
                    true
                } else {
                    apkPath?.let { ClipboardHelperProcess.restore(it, snapshot) } == true
                }
            } catch (error: Exception) {
                Log.w(TAG, "Unable to restore the clipboard", error)
                false
            }
            if (!restored) {
                Log.w(TAG, "Clipboard restoration failed after privileged write; inputSuccess=$success")
            }
        }
    }

    private fun runInput(vararg arguments: String): Boolean = try {
        val process = ProcessBuilder(listOf(INPUT_BINARY, *arguments))
            .redirectErrorStream(true)
            .start()
        if (!ProcessWaiter.waitForExit(process, COMMAND_TIMEOUT_SECONDS)) {
            false
        } else {
            process.inputStream.close()
            process.exitValue() == 0
        }
    } catch (error: Exception) {
        Log.w(TAG, "Unable to inject input command", error)
        false
    }

    override fun destroy() {
        System.exit(0)
    }

    companion object {
        private const val TAG = "MiaoInputInjector"
        private const val INPUT_BINARY = "/system/bin/input"
        private const val COMMAND_GAP_MS = 35L
        private const val CLIPBOARD_RESTORE_DELAY_MS = 80L
        private const val COMMAND_TIMEOUT_SECONDS = 4L
        private const val MAX_CURSOR_MOVE = 64
        private const val SHELL_UID = 2000
    }
}
