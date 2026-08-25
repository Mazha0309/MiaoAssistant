package com.mazha0309.miaoassistant.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TagFaces
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazha0309.miaoassistant.R
import com.mazha0309.miaoassistant.config.AppConfig
import com.mazha0309.miaoassistant.config.ConfigRepository
import com.mazha0309.miaoassistant.config.ProcessingMode
import com.mazha0309.miaoassistant.text.TextProcessor
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun RewritePage(
    config: AppConfig,
    bottomInnerPadding: Dp,
    onConfigChange: (AppConfig) -> Unit,
    onInvalidRules: (Int) -> Unit,
) {
    var editor by remember { mutableStateOf<RewriteEditor?>(null) }
    val previewSample = stringResource(R.string.preview_sample)
    var previewInput by rememberSaveable(previewSample) { mutableStateOf(previewSample) }

    MiaoPage(
        title = R.string.rewrite_page_title,
        bottomInnerPadding = bottomInnerPadding,
        blurEnabled = config.blurEnabled,
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                OverlayDropdownPreference(
                    items = listOf(
                        stringResource(R.string.mode_punctuation),
                        stringResource(R.string.mode_realtime),
                    ),
                    selectedIndex = if (config.processingMode == ProcessingMode.PUNCTUATION) 0 else 1,
                    title = stringResource(R.string.processing_mode),
                    summary = stringResource(R.string.processing_mode_summary),
                    startAction = { PreferenceIcon(Icons.Rounded.Settings) },
                    onSelectedIndexChange = { index ->
                        onConfigChange(
                            config.copy(
                                processingMode = if (index == 0) {
                                    ProcessingMode.PUNCTUATION
                                } else {
                                    ProcessingMode.REALTIME
                                },
                            ),
                        )
                    },
                )
                SwitchPreference(
                    checked = config.enableSentenceSuffix,
                    onCheckedChange = { onConfigChange(config.copy(enableSentenceSuffix = it)) },
                    title = stringResource(R.string.sentence_suffix),
                    summary = stringResource(R.string.sentence_suffix_summary),
                    startAction = { PreferenceIcon(Icons.AutoMirrored.Rounded.Notes) },
                )
                ArrowPreference(
                    title = stringResource(R.string.suffix_content),
                    summary = stringResource(R.string.suffix_content_summary, config.sentenceSuffix),
                    startAction = { PreferenceIcon(Icons.Rounded.Edit) },
                    enabled = config.enableSentenceSuffix,
                    onClick = { editor = RewriteEditor.SUFFIX },
                )
                SwitchPreference(
                    checked = config.enableRandomEmoticon,
                    onCheckedChange = { onConfigChange(config.copy(enableRandomEmoticon = it)) },
                    title = stringResource(R.string.random_emoticon),
                    summary = stringResource(R.string.random_emoticon_summary),
                    startAction = { PreferenceIcon(Icons.Rounded.TagFaces) },
                )
                ArrowPreference(
                    title = stringResource(R.string.custom_emoticons),
                    summary = if (config.customEmoticons.isEmpty()) {
                        stringResource(R.string.custom_emoticons_builtin)
                    } else {
                        stringResource(R.string.custom_emoticons_count, config.customEmoticons.size)
                    },
                    startAction = { PreferenceIcon(Icons.Rounded.TagFaces) },
                    enabled = config.enableRandomEmoticon,
                    onClick = { editor = RewriteEditor.EMOTICONS },
                )
            }

            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.replacement_rules),
                    summary = if (config.rules.isEmpty()) {
                        stringResource(R.string.replacement_rules_empty)
                    } else {
                        stringResource(R.string.replacement_rules_count, config.rules.size)
                    },
                    startAction = { PreferenceIcon(Icons.Rounded.SwapHoriz) },
                    onClick = { editor = RewriteEditor.RULES },
                )
            }

            Spacer(Modifier.height(12.dp))
            PreviewCard(
                input = previewInput,
                onInputChange = { previewInput = it },
                config = config,
            )
        }
    }

    EditorDialog(
        show = editor == RewriteEditor.SUFFIX,
        title = stringResource(R.string.edit_suffix_title),
        summary = stringResource(R.string.edit_suffix_hint),
        initialValue = config.sentenceSuffix,
        minLines = 1,
        onDismiss = { editor = null },
        onSave = { value ->
            onConfigChange(config.copy(sentenceSuffix = value.trim().ifEmpty { "喵" }))
            editor = null
        },
    )
    EditorDialog(
        show = editor == RewriteEditor.RULES,
        title = stringResource(R.string.edit_rules_title),
        summary = stringResource(R.string.rule_editor_hint),
        initialValue = config.rules.joinToString("\n") { it.serialize() },
        minLines = 7,
        onDismiss = { editor = null },
        onSave = { value ->
            val (rules, invalidCount) = ConfigRepository.parseRules(value)
            onConfigChange(config.copy(rules = rules))
            if (invalidCount > 0) onInvalidRules(invalidCount)
            editor = null
        },
    )
    EditorDialog(
        show = editor == RewriteEditor.EMOTICONS,
        title = stringResource(R.string.edit_emoticons_title),
        summary = stringResource(R.string.edit_emoticons_hint),
        initialValue = config.customEmoticons.joinToString("\n"),
        minLines = 6,
        onDismiss = { editor = null },
        onSave = { value ->
            onConfigChange(config.copy(customEmoticons = ConfigRepository.parseNonBlankLines(value)))
            editor = null
        },
    )
}

@Composable
private fun PreviewCard(
    input: String,
    onInputChange: (String) -> Unit,
    config: AppConfig,
) {
    val result = remember(input, config) {
        TextProcessor.process(input, config.copy(enabled = true)).text
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
    ) {
        Text(
            text = stringResource(R.string.section_preview),
            fontSize = 17.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        TextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.preview_input),
            minLines = 3,
            maxLines = 6,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.preview_result),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(6.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(14.dp),
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Text(
                text = result.ifEmpty { "—" },
                fontSize = 15.sp,
                lineHeight = 22.sp,
            )
        }
    }
}

private enum class RewriteEditor {
    SUFFIX,
    RULES,
    EMOTICONS,
}
