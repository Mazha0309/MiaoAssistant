package com.mazha0309.miaoassistant.privileged

import android.util.Base64
import java.nio.charset.StandardCharsets

internal object ClipboardHelperProcess {
    fun swap(apkPath: String, text: String): String? {
        val encodedText = Base64.encodeToString(text.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        if (encodedText.length > MAX_ARGUMENT_LENGTH) return null
        return run(apkPath, "swap", encodedText)?.takeUnless { it == "OK" }
    }

    fun restore(apkPath: String, snapshot: String): Boolean =
        run(apkPath, "restore", snapshot) == "OK"

    private fun run(apkPath: String, action: String, payload: String): String? {
        if (!SAFE_PATH.matches(apkPath) || !SAFE_PAYLOAD.matches(payload)) return null
        val command = "CLASSPATH=$apkPath $APP_PROCESS /system/bin $HELPER_CLASS $action $payload"
        return try {
            // Running the binder bridge as Android's system UID lets it preserve rich clips
            // (including URI-backed clips) without ClipboardService clearing them for shell.
            val process = ProcessBuilder("su", SYSTEM_UID, "-c", command).start()
            if (!ProcessWaiter.waitForExit(process, TIMEOUT_SECONDS)) {
                process.inputStream.close()
                process.errorStream.close()
                return null
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.errorStream.close()
            if (process.exitValue() != 0) return null
            output.lineSequence()
                .lastOrNull { it.startsWith(RESULT_PREFIX) }
                ?.removePrefix(RESULT_PREFIX)
        } catch (_: Exception) {
            null
        }
    }

    private val SAFE_PATH = Regex("^[A-Za-z0-9_./+=~\\-]+$")
    private val SAFE_PAYLOAD = Regex("^[A-Za-z0-9+/=~]+$")
    private const val APP_PROCESS = "/system/bin/app_process"
    private const val HELPER_CLASS = "com.mazha0309.miaoassistant.privileged.ClipboardShell"
    private const val RESULT_PREFIX = "MIAO_CLIP:"
    private const val SYSTEM_UID = "1000"
    private const val TIMEOUT_SECONDS = 5L
    private const val MAX_ARGUMENT_LENGTH = 48 * 1024
}
