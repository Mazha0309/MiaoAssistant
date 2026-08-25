package com.mazha0309.miaoassistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.mazha0309.miaoassistant.config.AppConfig
import com.mazha0309.miaoassistant.config.ConfigRepository
import com.mazha0309.miaoassistant.text.TextProcessor

class GlobalInputAccessibilityService : AccessibilityService() {
    private lateinit var configRepository: ConfigRepository
    private var config = AppConfig()
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var processing = false
    private var lastNodeKey: String? = null
    private var lastAppliedText: String? = null
    private var lastWriteAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        configRepository = ConfigRepository(this)
        config = configRepository.load()
        preferenceListener = configRepository.registerListener { updated -> config = updated }

        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_FOCUSED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 40L
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            packageNames = null
        }
        Log.i(TAG, "Global input service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            -> resetSession()

            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                val source = event.source ?: return
                try {
                    lastNodeKey = nodeKey(source)
                    lastAppliedText = null
                    lastWriteAt = 0L
                } finally {
                    recycle(source)
                }
            }

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> processTextChange(event)
            else -> Unit
        }
    }

    private fun processTextChange(event: AccessibilityEvent) {
        if (processing || !config.enabled) return
        val source = findTarget(event) ?: return
        try {
            val packageName = source.packageName?.toString()
                ?: event.packageName?.toString()
                ?: return
            if (!isPackageAllowed(packageName)) return
            if (!isEligibleTextField(source)) return

            val original = source.text?.toString() ?: return
            if (original.isEmpty() || !TextProcessor.shouldProcess(original, config)) return

            val key = nodeKey(source)
            val now = SystemClock.uptimeMillis()
            if (key == lastNodeKey && original == lastAppliedText && now - lastWriteAt < WRITE_ECHO_WINDOW_MS) {
                return
            }

            val selectionStart = source.textSelectionStart.takeIf { it >= 0 } ?: original.length
            val selectionEnd = source.textSelectionEnd.takeIf { it >= 0 } ?: selectionStart
            val processed = TextProcessor.process(original, config, selectionStart, selectionEnd)
            if (processed.text == original) return

            processing = true
            if (setText(source, processed.text, processed.selectionStart, processed.selectionEnd)) {
                lastNodeKey = key
                lastAppliedText = processed.text
                lastWriteAt = SystemClock.uptimeMillis()
            }
        } catch (error: RuntimeException) {
            Log.w(TAG, "Unable to process the focused input field", error)
            resetSession()
        } finally {
            processing = false
            recycle(source)
        }
    }

    private fun findTarget(event: AccessibilityEvent): AccessibilityNodeInfo? {
        val eventSource = event.source
        if (eventSource != null) {
            if (isEditable(eventSource)) return eventSource
            recycle(eventSource)
        }

        val root = rootInActiveWindow ?: return null
        return try {
            root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        } finally {
            recycle(root)
        }
    }

    private fun isEligibleTextField(node: AccessibilityNodeInfo): Boolean {
        if (!node.isEnabled || !node.isVisibleToUser || !isEditable(node)) return false
        if (node.isPassword || hasPasswordInputType(node.inputType)) return false
        return !node.className?.toString().orEmpty().contains("password", ignoreCase = true)
    }

    private fun isPackageAllowed(packageName: String): Boolean {
        if (packageName == applicationContext.packageName || packageName == SYSTEM_UI_PACKAGE) return false
        return config.isPackageInScope(packageName)
    }

    private fun isEditable(node: AccessibilityNodeInfo): Boolean =
        node.isEditable || node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }

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

    private fun resetSession() {
        lastNodeKey = null
        lastAppliedText = null
        lastWriteAt = 0L
        processing = false
    }

    override fun onInterrupt() = resetSession()

    override fun onDestroy() {
        preferenceListener?.let { listener -> configRepository.unregisterListener(listener) }
        preferenceListener = null
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
    }
}
