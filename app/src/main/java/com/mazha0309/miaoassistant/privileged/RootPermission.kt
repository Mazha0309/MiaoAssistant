package com.mazha0309.miaoassistant.privileged

internal object RootPermission {
    fun request(): Boolean = try {
        val process = ProcessBuilder("su", "-c", "id -u")
            .redirectErrorStream(true)
            .start()
        if (!ProcessWaiter.waitForExit(process, PERMISSION_TIMEOUT_SECONDS)) {
            false
        } else {
            process.exitValue() == 0 && process.inputStream.bufferedReader().use { it.readText().trim() } == "0"
        }
    } catch (_: Exception) {
        false
    }

    private const val PERMISSION_TIMEOUT_SECONDS = 30L
}
