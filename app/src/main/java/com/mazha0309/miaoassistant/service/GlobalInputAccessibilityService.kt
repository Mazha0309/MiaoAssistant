package com.mazha0309.miaoassistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.mazha0309.miaoassistant.config.AppConfig
import com.mazha0309.miaoassistant.config.ConfigRepository
import com.mazha0309.miaoassistant.config.InputWriteMode
import com.mazha0309.miaoassistant.config.ProcessingMode
import com.mazha0309.miaoassistant.privileged.InputWriteController
import com.mazha0309.miaoassistant.text.ProcessedText
import com.mazha0309.miaoassistant.text.TextProcessor

class GlobalInputAccessibilityService : AccessibilityService() {
    private lateinit var configRepository: ConfigRepository
    private lateinit var inputWriteController: InputWriteController
    private var config = AppConfig()
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val processPendingRunnable = Runnable(::processPendingTextChange)
    private var pendingChange: PendingTextChange? = null
    private var nextChangeId = 0L
    private var cachedFocusedNode: AccessibilityNodeInfo? = null
    private var cachedNodePackage: String? = null
    private var processing = false
    private var lastNodeKey: String? = null
    private var lastAppliedText: String? = null
    private var lastAppliedManagedSuffixStart: Int? = null
    private var lastWriteAt = 0L
    private var lastInputPackage: String? = null
    private var lastInputEventAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        configRepository = ConfigRepository(this)
        inputWriteController = InputWriteController(this, mainHandler)
        config = configRepository.load()
        preferenceListener = configRepository.registerListener { updated ->
            val writeModeChanged = updated.inputWriteMode != config.inputWriteMode
            config = updated
            if (!updated.enabled || writeModeChanged) cancelPendingChange()
        }

        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED or
                AccessibilityEvent.TYPE_VIEW_FOCUSED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            // This service performs its own short debounce. Receiving every source event is
            // important for apps that only expose an editable node intermittently.
            notificationTimeout = 0L
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    AccessibilityServiceInfo.FLAG_INPUT_METHOD_EDITOR
                } else {
                    0
                }
            packageNames = null
        }
        Log.i(TAG, "Global input service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        logEventMetadata(event)
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            -> Unit

            AccessibilityEvent.TYPE_VIEW_FOCUSED -> rememberFocusedNode(event)

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> enqueueTextChange(event)

            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                if (config.inputWriteMode == InputWriteMode.ACCESSIBILITY) enqueueTextChange(event)
            }

            else -> Unit
        }
    }

    private fun logEventMetadata(event: AccessibilityEvent) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return
        val packageName = event.packageName?.toString().orEmpty()
        if (!packageName.startsWith("com.tencent.")) return
        val source = event.source
        try {
            Log.d(
                TAG,
                "event=${AccessibilityEvent.eventTypeToString(event.eventType)} " +
                    "package=$packageName eventClass=${event.className} password=${event.isPassword} " +
                    "source=${source?.className} " +
                    "viewId=${source?.viewIdResourceName} editable=${source?.isEditable} " +
                    "enabled=${source?.isEnabled} actions=${source?.actionList?.joinToString { it.id.toString() }} " +
                    "textLength=${source?.text?.length ?: event.text.lastOrNull()?.length ?: -1}",
            )
        } finally {
            if (source != null) recycle(source)
        }
    }

    private fun rememberFocusedNode(event: AccessibilityEvent) {
        val source = event.source ?: return
        try {
            val packageName = source.packageName?.toString() ?: event.packageName?.toString() ?: return
            if (!isPackageAllowed(packageName) || !isEditable(source)) {
                if (packageName != cachedNodePackage) clearCachedNode()
                return
            }
            cancelPendingChange()
            cacheFocusedNode(source, packageName)
            lastNodeKey = nodeKey(source)
            lastAppliedText = null
            lastAppliedManagedSuffixStart = null
            lastWriteAt = 0L
        } finally {
            recycle(source)
        }
    }

    private fun enqueueTextChange(event: AccessibilityEvent) {
        if (!config.enabled) return

        var packageName = event.packageName?.toString()
        var text = event.text.lastOrNull()?.toString()
        var selectionStart = -1
        var selectionEnd = -1
        val source = event.source
        if (source != null) {
            try {
                packageName = source.packageName?.toString() ?: packageName
                source.text?.toString()?.let { text = it }
                selectionStart = source.textSelectionStart
                selectionEnd = source.textSelectionEnd
                if (packageName != null && isEditable(source)) {
                    cacheFocusedNode(source, packageName)
                }
            } finally {
                recycle(source)
            }
        }

        val targetPackage = packageName ?: return
        if (!isPackageAllowed(targetPackage)) return
        lastInputPackage = targetPackage
        lastInputEventAt = SystemClock.uptimeMillis()
        if (selectionStart < 0) {
            selectionStart = if (event.fromIndex >= 0) {
                event.fromIndex + event.addedCount
            } else {
                text?.length ?: -1
            }
        }
        if (selectionEnd < 0) selectionEnd = selectionStart

        val change = PendingTextChange(
            id = ++nextChangeId,
            packageName = targetPackage,
            eventText = text,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
            eventType = event.eventType,
            eventClassName = event.className?.toString(),
            isPassword = event.isPassword,
            attempt = 0,
        )
        pendingChange = change
        mainHandler.removeCallbacks(processPendingRunnable)
        mainHandler.postDelayed(
            processPendingRunnable,
            if (config.processingMode == ProcessingMode.REALTIME) REALTIME_DEBOUNCE_MS else PUNCTUATION_DEBOUNCE_MS,
        )
    }

    private fun processPendingTextChange() {
        val change = pendingChange ?: return
        if (processing || !config.enabled || !isPackageAllowed(change.packageName)) return

        val resolved = findTarget(change.packageName)
        val source = resolved?.node
        if (source == null && config.inputWriteMode == InputWriteMode.ACCESSIBILITY) {
            retry(change, "editable target unavailable")
            return
        }
        if (source == null && !isEligiblePrivilegedEvent(change)) {
            retry(change, "eligible text event unavailable")
            return
        }

        try {
            if (change.isPassword || (source != null && !isEligibleTextField(source))) return

            val nodeText = source?.text?.toString()
            val inputSnapshot = if (
                source == null &&
                change.eventText == null &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ) {
                AccessibilityInputConnectionWriter.readTextSnapshot(this, change.packageName)
            } else {
                null
            }
            val original = when {
                resolved?.refreshed == true && nodeText != null -> nodeText
                inputSnapshot != null -> inputSnapshot.text
                change.eventText != null -> change.eventText
                else -> nodeText
            } ?: run {
                retry(change, "text unavailable")
                return
            }
            if (original.isEmpty()) return

            val key = source?.let(::nodeKey) ?: eventKey(change)
            val now = SystemClock.uptimeMillis()
            if (key == lastNodeKey && original == lastAppliedText && now - lastWriteAt < WRITE_ECHO_WINDOW_MS) {
                return
            }

            val selectionStart = source?.textSelectionStart?.takeIf { it >= 0 }
                ?: inputSnapshot?.selectionStart
                ?: change.selectionStart.takeIf { it >= 0 }
                ?: original.length
            val selectionEnd = source?.textSelectionEnd?.takeIf { it >= 0 }
                ?: inputSnapshot?.selectionEnd
                ?: change.selectionEnd.takeIf { it >= 0 }
                ?: selectionStart
            val normalized = TextProcessor.normalizeTypingAfterManagedSuffix(
                input = original,
                previousAppliedText = lastAppliedText.takeIf { key == lastNodeKey },
                previousManagedSuffixStart = lastAppliedManagedSuffixStart.takeIf { key == lastNodeKey },
                config = config,
                selectionStart = selectionStart,
                selectionEnd = selectionEnd,
            )
            if (!TextProcessor.shouldProcess(normalized.text, config)) return
            val processed = TextProcessor.process(
                normalized.text,
                config,
                normalized.selectionStart,
                normalized.selectionEnd,
            )
            if (processed.text == original) return

            processing = true
            val canUseInputConnection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            when (val mode = config.inputWriteMode) {
                InputWriteMode.ACCESSIBILITY -> {
                    if (source != null && setText(
                            source,
                            processed.text,
                            processed.selectionStart,
                            processed.selectionEnd,
                        )
                    ) {
                        recordSuccessfulWrite(key, processed)
                        pendingChange = null
                    } else if (
                        canUseInputConnection &&
                        AccessibilityInputConnectionWriter.replaceText(
                            service = this,
                            packageName = change.packageName,
                            text = processed.text,
                            selectionStart = processed.selectionStart,
                            selectionEnd = processed.selectionEnd,
                        )
                    ) {
                        recordSuccessfulWrite(key, processed)
                        pendingChange = null
                    } else {
                        retry(change, "accessible text write rejected")
                    }
                }

                InputWriteMode.SHIZUKU,
                InputWriteMode.ROOT,
                -> {
                    if (
                        canUseInputConnection &&
                        AccessibilityInputConnectionWriter.replaceText(
                            service = this,
                            packageName = change.packageName,
                            text = processed.text,
                            selectionStart = processed.selectionStart,
                            selectionEnd = processed.selectionEnd,
                        )
                    ) {
                        recordSuccessfulWrite(key, processed)
                        pendingChange = null
                    } else if (canUseInputConnection) {
                        // Android 13+ must never silently fall back to clipboard paste.
                        retry(change, "accessibility input connection unavailable")
                    } else {
                        val accepted = inputWriteController.replaceText(
                            mode = mode,
                            text = processed.text,
                            selectionStart = processed.selectionStart,
                            shouldContinue = {
                                isWriteContextActive(change.packageName, mode)
                            },
                            onResult = { success ->
                                if (!success) {
                                    clearWriteEcho(key, processed.text)
                                    retryPrivilegedWrite(change, mode)
                                }
                            },
                        )
                        if (accepted) {
                            recordSuccessfulWrite(key, processed)
                            pendingChange = null
                        } else {
                            retry(change, "${mode.storedValue} writer unavailable")
                        }
                    }
                }
            }
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to process the focused input field", error)
            retry(change, error.javaClass.simpleName)
        } finally {
            processing = false
            source?.let(::recycle)
        }
    }

    private fun recordSuccessfulWrite(key: String, processed: ProcessedText) {
        lastNodeKey = key
        lastAppliedText = processed.text
        lastAppliedManagedSuffixStart = processed.managedSuffixStart
        lastWriteAt = SystemClock.uptimeMillis()
    }

    private fun clearWriteEcho(key: String, text: String) {
        if (lastNodeKey == key && lastAppliedText == text) {
            lastAppliedText = null
            lastAppliedManagedSuffixStart = null
            lastWriteAt = 0L
        }
    }

    private fun retryPrivilegedWrite(change: PendingTextChange, mode: InputWriteMode) {
        if (!isWriteContextActive(change.packageName, mode) || pendingChange != null) return
        if (change.attempt >= MAX_RETRIES) {
            Log.w(TAG, "Text rewrite skipped for ${change.packageName}: ${mode.storedValue} writer failed")
            return
        }
        pendingChange = change.copy(attempt = change.attempt + 1)
        mainHandler.removeCallbacks(processPendingRunnable)
        mainHandler.postDelayed(processPendingRunnable, RETRY_DELAY_MS)
    }

    private fun isWriteContextActive(packageName: String, mode: InputWriteMode): Boolean =
        config.enabled &&
            config.inputWriteMode == mode &&
            lastInputPackage == packageName &&
            SystemClock.uptimeMillis() - lastInputEventAt <= PRIVILEGED_CONTEXT_WINDOW_MS &&
            isPackageAllowed(packageName)

    private fun isEligiblePrivilegedEvent(change: PendingTextChange): Boolean {
        if (change.isPassword || change.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return false
        val className = change.eventClassName.orEmpty()
        return className.contains("EditText", ignoreCase = true) ||
            className.contains("TextField", ignoreCase = true) ||
            className.contains("AutoCompleteTextView", ignoreCase = true)
    }

    private fun eventKey(change: PendingTextChange): String =
        "${change.packageName}|event|${change.eventClassName.orEmpty()}"

    private fun findTarget(packageName: String): ResolvedTarget? {
        if (cachedNodePackage == packageName) {
            cachedFocusedNode?.let { cached ->
                val copy = copyNode(cached)
                val refreshed = runCatching { copy.refresh() }.getOrDefault(false)
                if (isEditable(copy) && copy.packageName?.toString() == packageName) {
                    return ResolvedTarget(copy, refreshed)
                }
                recycle(copy)
            }
        }

        val root = rootInActiveWindow ?: return null
        val focused = try {
            root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        } finally {
            recycle(root)
        }
        if (focused == null) return null
        if (!isEditable(focused) || focused.packageName?.toString() != packageName) {
            recycle(focused)
            return null
        }
        cacheFocusedNode(focused, packageName)
        return ResolvedTarget(focused, refreshed = true)
    }

    private fun isEligibleTextField(node: AccessibilityNodeInfo): Boolean {
        // Some custom EditText implementations report isVisibleToUser=false even when their
        // text event is from the active window. The event package and input focus already scope
        // this node, so visibility is not a reliable eligibility gate here.
        if (!node.isEnabled || !isEditable(node)) return false
        if (node.isPassword || hasPasswordInputType(node.inputType)) return false
        return !node.className?.toString().orEmpty().contains("password", ignoreCase = true)
    }

    private fun isPackageAllowed(packageName: String): Boolean {
        if (packageName == applicationContext.packageName || packageName == SYSTEM_UI_PACKAGE) return false
        return config.isPackageInScope(packageName)
    }

    private fun isEditable(node: AccessibilityNodeInfo): Boolean =
        node.isEditable ||
            node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT } ||
            node.className?.toString().orEmpty().contains("EditText", ignoreCase = true)

    private fun hasPasswordInputType(inputType: Int): Boolean {
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputClass) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD

            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    private fun setText(node: AccessibilityNodeInfo, value: String, selectionStart: Int, selectionEnd: Int): Boolean {
        val textArguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, textArguments)) return false

        val selectionArguments = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, selectionStart.coerceIn(0, value.length))
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, selectionEnd.coerceIn(0, value.length))
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArguments)
        return true
    }

    private fun nodeKey(node: AccessibilityNodeInfo): String {
        val bounds = Rect().also(node::getBoundsInScreen)
        return buildString {
            append(node.packageName)
            append('|').append(node.windowId)
            append('|').append(node.viewIdResourceName)
            append('|').append(node.className)
            append('|').append(bounds.flattenToString())
        }
    }

    private fun cacheFocusedNode(node: AccessibilityNodeInfo, packageName: String) {
        clearCachedNode()
        cachedFocusedNode = copyNode(node)
        cachedNodePackage = packageName
    }

    @Suppress("DEPRECATION")
    private fun copyNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo =
        AccessibilityNodeInfo.obtain(node)

    private fun retry(change: PendingTextChange, reason: String) {
        if (pendingChange?.id != change.id) return
        if (change.attempt >= MAX_RETRIES) {
            Log.w(TAG, "Text rewrite skipped for ${change.packageName}: $reason")
            pendingChange = null
            return
        }
        pendingChange = change.copy(attempt = change.attempt + 1)
        mainHandler.removeCallbacks(processPendingRunnable)
        mainHandler.postDelayed(processPendingRunnable, RETRY_DELAY_MS)
    }

    private fun cancelPendingChange() {
        mainHandler.removeCallbacks(processPendingRunnable)
        pendingChange = null
    }

    private fun clearCachedNode() {
        cachedFocusedNode?.let(::recycle)
        cachedFocusedNode = null
        cachedNodePackage = null
    }

    private fun resetSession() {
        cancelPendingChange()
        clearCachedNode()
        lastNodeKey = null
        lastAppliedText = null
        lastAppliedManagedSuffixStart = null
        lastWriteAt = 0L
        lastInputPackage = null
        lastInputEventAt = 0L
        processing = false
    }

    override fun onInterrupt() = resetSession()

    override fun onDestroy() {
        resetSession()
        preferenceListener?.let { listener -> configRepository.unregisterListener(listener) }
        preferenceListener = null
        if (::inputWriteController.isInitialized) inputWriteController.shutdown()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun recycle(node: AccessibilityNodeInfo) {
        node.recycle()
    }

    companion object {
        private const val TAG = "MiaoInputService"
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val WRITE_ECHO_WINDOW_MS = 1_500L
        private const val PRIVILEGED_CONTEXT_WINDOW_MS = 3_000L
        private const val REALTIME_DEBOUNCE_MS = 45L
        private const val PUNCTUATION_DEBOUNCE_MS = 40L
        private const val RETRY_DELAY_MS = 120L
        private const val MAX_RETRIES = 2
    }

    private data class PendingTextChange(
        val id: Long,
        val packageName: String,
        val eventText: String?,
        val selectionStart: Int,
        val selectionEnd: Int,
        val eventType: Int,
        val eventClassName: String?,
        val isPassword: Boolean,
        val attempt: Int,
    )

    private data class ResolvedTarget(
        val node: AccessibilityNodeInfo,
        val refreshed: Boolean,
    )
}
