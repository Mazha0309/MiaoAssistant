package com.mazha0309.miaoassistant.privileged

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.util.Log
import com.mazha0309.miaoassistant.BuildConfig
import com.mazha0309.miaoassistant.config.InputWriteMode
import java.util.concurrent.Executors
import rikka.shizuku.Shizuku

/**
 * Writes an already processed value through the explicitly selected privileged channel.
 * Accessibility events still provide the text and focus context; this class only performs
 * select-all/paste when a target app rejects ACTION_SET_TEXT.
 */
class InputWriteController(
    context: Context,
    private val mainHandler: Handler,
) {
    private val apkPath = context.applicationInfo.sourceDir
    private val executor = Executors.newSingleThreadExecutor()
    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context, InputInjectorUserService::class.java),
    )
        .daemon(false)
        .processNameSuffix("input_injector")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)
    private var injector: IInputInjector? = null
    private var bindingUserService = false
    private var activeRequest: WriteRequest? = null
    private var queuedRequest: WriteRequest? = null
    private var closed = false

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            mainHandler.post {
                bindingUserService = false
                injector = service?.let(IInputInjector.Stub::asInterface)
                val request = activeRequest
                if (request == null) {
                    startNextRequest()
                } else if (request.mode == InputWriteMode.SHIZUKU && injector != null) {
                    executePrivileged(request)
                } else if (request.mode == InputWriteMode.SHIZUKU) {
                    finishRequest(request, success = false)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mainHandler.post {
                injector = null
                bindingUserService = false
                activeRequest
                    ?.takeIf { it.mode == InputWriteMode.SHIZUKU }
                    ?.let { finishRequest(it, success = false) }
            }
        }
    }

    fun replaceText(
        mode: InputWriteMode,
        text: String,
        selectionStart: Int,
        shouldContinue: () -> Boolean,
        onResult: (Boolean) -> Unit,
    ): Boolean {
        if (closed || mode == InputWriteMode.ACCESSIBILITY) return false
        if (mode == InputWriteMode.SHIZUKU && !isShizukuAuthorized()) return false

        val request = WriteRequest(
            mode = mode,
            text = text,
            moveCursorLeft = (text.length - selectionStart).coerceIn(0, MAX_CURSOR_MOVE),
            shouldContinue = shouldContinue,
            onResult = onResult,
        )
        queuedRequest?.onResult?.invoke(false)
        queuedRequest = request
        startNextRequest()
        return true
    }

    private fun startNextRequest() {
        if (closed || activeRequest != null) return
        val request = queuedRequest ?: return
        queuedRequest = null
        activeRequest = request

        if (!request.shouldContinue()) {
            finishRequest(request, success = false)
            return
        }

        when (request.mode) {
            InputWriteMode.ACCESSIBILITY -> finishRequest(request, success = false)
            InputWriteMode.ROOT -> executePrivileged(request)
            InputWriteMode.SHIZUKU -> {
                if (injector != null) {
                    executePrivileged(request)
                } else {
                    bindUserService(request)
                }
            }
        }
    }

    private fun bindUserService(request: WriteRequest) {
        if (bindingUserService) return
        if (!isShizukuAuthorized()) {
            finishRequest(request, success = false)
            return
        }
        bindingUserService = true
        runCatching { Shizuku.bindUserService(userServiceArgs, userServiceConnection) }
            .onFailure {
                Log.w(TAG, "Unable to bind Shizuku input service", it)
                bindingUserService = false
                finishRequest(request, success = false)
            }
    }

    private fun executePrivileged(request: WriteRequest) {
        if (activeRequest !== request || !request.shouldContinue()) {
            Log.w(TAG, "Privileged write cancelled because the input context changed")
            finishRequest(request, success = false)
            return
        }
        executor.execute {
            val success = when (request.mode) {
                InputWriteMode.SHIZUKU -> runCatching {
                    injector?.replaceText(request.text, request.moveCursorLeft) == true
                }.getOrDefault(false)

                InputWriteMode.ROOT -> injectAsRoot(request.text, request.moveCursorLeft)
                InputWriteMode.ACCESSIBILITY -> false
            }
            mainHandler.post { finishRequest(request, success) }
        }
    }

    private fun finishRequest(request: WriteRequest, success: Boolean) {
        if (activeRequest !== request) return
        activeRequest = null
        request.onResult(success)
        startNextRequest()
    }

    private fun injectAsRoot(text: String, moveCursorLeft: Int): Boolean {
        val clipboardSnapshot = ClipboardHelperProcess.swap(apkPath, text) ?: run {
            Log.w(TAG, "Root clipboard helper could not preserve the clipboard")
            return false
        }
        var success = false
        try {
            if (!runRootInput("keyevent KEYCODE_MOVE_END")) return false
            Thread.sleep(COMMAND_GAP_MS)
            if (!runRootInput("keycombination KEYCODE_CTRL_LEFT KEYCODE_A")) return false
            Thread.sleep(COMMAND_GAP_MS)
            if (!runRootInput("keyevent KEYCODE_DEL")) return false
            Thread.sleep(COMMAND_GAP_MS)
            // A composing IME may make the first Ctrl+A cover only its composing span. Once
            // that span is deleted, repeat the selection so no stale field prefix survives.
            if (!runRootInput("keycombination KEYCODE_CTRL_LEFT KEYCODE_A")) return false
            Thread.sleep(COMMAND_GAP_MS)
            if (!runRootInput("keyevent KEYCODE_DEL")) return false
            Thread.sleep(COMMAND_GAP_MS)
            if (!runRootInput("keycombination KEYCODE_CTRL_LEFT KEYCODE_V")) return false
            if (moveCursorLeft > 0) {
                Thread.sleep(COMMAND_GAP_MS)
                val keys = List(moveCursorLeft.coerceIn(0, MAX_CURSOR_MOVE)) { "KEYCODE_DPAD_LEFT" }
                if (!runRootInput("keyevent ${keys.joinToString(" ")}")) return false
            }
            success = true
            return true
        } finally {
            Thread.sleep(CLIPBOARD_RESTORE_DELAY_MS)
            if (!ClipboardHelperProcess.restore(apkPath, clipboardSnapshot)) {
                Log.w(TAG, "Root clipboard helper could not restore the clipboard")
                if (!success) Log.w(TAG, "Root write failed before clipboard restoration")
            }
        }
    }

    private fun runRootInput(arguments: String): Boolean = try {
        val process = ProcessBuilder("su", "-c", "$INPUT_BINARY $arguments")
            .redirectErrorStream(true)
            .start()
        if (!ProcessWaiter.waitForExit(process, COMMAND_TIMEOUT_SECONDS)) {
            Log.w(TAG, "Root input command timed out")
            false
        } else {
            process.inputStream.close()
            val success = process.exitValue() == 0
            if (!success) Log.w(TAG, "Root input command failed with exit code ${process.exitValue()}")
            success
        }
    } catch (error: Exception) {
        Log.w(TAG, "Unable to inject root input command", error)
        false
    }

    fun shutdown() {
        closed = true
        queuedRequest?.onResult?.invoke(false)
        queuedRequest = null
        activeRequest?.onResult?.invoke(false)
        activeRequest = null
        if (bindingUserService || injector != null) {
            runCatching { Shizuku.unbindUserService(userServiceArgs, userServiceConnection, true) }
        }
        bindingUserService = false
        injector = null
        executor.shutdownNow()
    }

    private fun isShizukuAuthorized(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    private data class WriteRequest(
        val mode: InputWriteMode,
        val text: String,
        val moveCursorLeft: Int,
        val shouldContinue: () -> Boolean,
        val onResult: (Boolean) -> Unit,
    )

    companion object {
        private const val TAG = "MiaoInputWriter"
        private const val INPUT_BINARY = "/system/bin/input"
        private const val COMMAND_GAP_MS = 35L
        private const val CLIPBOARD_RESTORE_DELAY_MS = 80L
        private const val COMMAND_TIMEOUT_SECONDS = 4L
        private const val MAX_CURSOR_MOVE = 64
    }
}
