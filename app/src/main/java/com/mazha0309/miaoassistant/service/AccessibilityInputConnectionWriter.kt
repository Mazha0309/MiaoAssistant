package com.mazha0309.miaoassistant.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.text.InputType
import androidx.annotation.RequiresApi

internal data class InputTextSnapshot(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
)

/**
 * Replaces text through the editor's real InputConnection on Android 13 and newer.
 * Unlike the privileged compatibility path for older Android versions, this never
 * reads from or writes to the clipboard.
 */
internal object AccessibilityInputConnectionWriter {
    fun readTextSnapshot(
        service: AccessibilityService,
        packageName: String,
    ): InputTextSnapshot? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return Api33.readTextSnapshot(service, packageName)
    }

    fun replaceText(
        service: AccessibilityService,
        packageName: String,
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return Api33.replaceText(
            service = service,
            packageName = packageName,
            text = text,
            selectionStart = selectionStart,
            selectionEnd = selectionEnd,
        )
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private object Api33 {
        fun readTextSnapshot(
            service: AccessibilityService,
            packageName: String,
        ): InputTextSnapshot? = runCatching {
            val inputMethod = service.inputMethod ?: return null
            if (!inputMethod.currentInputStarted) return null
            val editorInfo = inputMethod.currentInputEditorInfo ?: return null
            if (editorInfo.packageName != packageName || isPasswordInputType(editorInfo.inputType)) return null
            val connection = inputMethod.currentInputConnection ?: return null
            val surrounding = connection.getSurroundingText(
                MAX_SURROUNDING_CHARS,
                MAX_SURROUNDING_CHARS,
                0,
            ) ?: return null
            val text = surrounding.text.toString()
            val selectionStart = surrounding.selectionStart
            val selectionEnd = surrounding.selectionEnd
            if (
                surrounding.offset != 0 ||
                selectionStart !in 0..text.length ||
                selectionEnd !in 0..text.length ||
                selectionStart >= MAX_SURROUNDING_CHARS ||
                text.length - selectionEnd >= MAX_SURROUNDING_CHARS
            ) {
                return null
            }
            InputTextSnapshot(text, selectionStart, selectionEnd)
        }.getOrNull()

        fun replaceText(
            service: AccessibilityService,
            packageName: String,
            text: String,
            selectionStart: Int,
            selectionEnd: Int,
        ): Boolean = runCatching {
            val inputMethod = service.inputMethod ?: return false
            if (!inputMethod.currentInputStarted) return false
            val editorInfo = inputMethod.currentInputEditorInfo ?: return false
            if (editorInfo.packageName != packageName || isPasswordInputType(editorInfo.inputType)) return false
            val connection = inputMethod.currentInputConnection ?: return false

            // commitText replaces a composing span before it considers the selection. Clear
            // that span first, then select all so custom IMEs cannot leave an old prefix.
            connection.commitText("", 1, null)
            connection.performContextMenuAction(android.R.id.selectAll)
            connection.commitText(text, 1, null)

            val safeStart = selectionStart.coerceIn(0, text.length)
            val safeEnd = selectionEnd.coerceIn(0, text.length)
            if (safeStart != text.length || safeEnd != text.length) {
                connection.setSelection(safeStart, safeEnd)
            }
            true
        }.getOrDefault(false)

        private fun isPasswordInputType(inputType: Int): Boolean {
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

        private const val MAX_SURROUNDING_CHARS = 16 * 1024
    }
}
