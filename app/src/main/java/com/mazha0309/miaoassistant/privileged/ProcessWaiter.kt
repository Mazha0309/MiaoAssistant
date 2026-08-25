package com.mazha0309.miaoassistant.privileged

internal object ProcessWaiter {
    fun waitForExit(process: Process, timeoutSeconds: Long): Boolean {
        val deadline = System.nanoTime() + timeoutSeconds * NANOS_PER_SECOND
        while (System.nanoTime() < deadline) {
            try {
                process.exitValue()
                return true
            } catch (_: IllegalThreadStateException) {
                Thread.sleep(POLL_INTERVAL_MS)
            }
        }
        process.destroy()
        return false
    }

    private const val POLL_INTERVAL_MS = 20L
    private const val NANOS_PER_SECOND = 1_000_000_000L
}
