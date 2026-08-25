package com.mazha0309.miaoassistant.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.mazha0309.miaoassistant.config.AppConfig
import com.mazha0309.miaoassistant.config.ProcessingMode
import com.mazha0309.miaoassistant.config.ReplacementRule

class TextProcessorTest {
    @Test
    fun ordinarySpacesDoNotCreateSentenceSuffixes() {
        val config = plainConfig(enableSentenceSuffix = true)
        assertEquals("hello world喵.", TextProcessor.process("hello world.", config).text)
    }

    @Test
    fun appendsSuffixBeforeEachPunctuationGroup() {
        val config = plainConfig(enableSentenceSuffix = true)
        assertEquals("你好喵，世界喵！", TextProcessor.process("你好，世界！", config).text)
    }

    @Test
    fun sentenceSuffixIsIdempotent() {
        val config = plainConfig(enableSentenceSuffix = true)
        val once = TextProcessor.process("你好。", config).text
        val twice = TextProcessor.process(once, config).text
        assertEquals("你好喵。", once)
        assertEquals(once, twice)
    }

    @Test
    fun managedEmoticonIsStableAndCaretStaysBeforeIt() {
        val config = plainConfig(
            enableRandomEmoticon = true,
            customEmoticons = listOf("(A)", "(B)"),
        )
        val once = TextProcessor.process("Hi!", config)
        val twice = TextProcessor.process(once.text, config, once.selectionStart, once.selectionEnd)
        assertEquals(once.text, twice.text)
        assertEquals(3, once.selectionStart)
        assertTrue(once.text.startsWith("Hi! ("))
    }

    @Test
    fun mapsSelectionAcrossReplacementAndSuffix() {
        val config = plainConfig(
            enableSentenceSuffix = true,
            rules = listOf(ReplacementRule("我", "本喵")),
        )
        val result = TextProcessor.process("我很好。", config, 4, 4)
        assertEquals("本喵很好喵。", result.text)
        assertEquals(result.text.length, result.selectionStart)
        assertEquals(result.selectionStart, result.selectionEnd)
    }

    @Test
    fun appliesReplacementRulesInConfiguredOrder() {
        val config = plainConfig(
            rules = listOf(
                ReplacementRule("我", "你"),
                ReplacementRule("你", "主人"),
            ),
        )
        assertEquals("主人好", TextProcessor.process("我好", config).text)
    }

    @Test
    fun punctuationModeOnlyTriggersForCompletedSentence() {
        val config = plainConfig(
            processingMode = ProcessingMode.PUNCTUATION,
            enableRandomEmoticon = true,
            customEmoticons = listOf("=^.^="),
        )
        assertFalse(TextProcessor.shouldProcess("你好", config))
        assertTrue(TextProcessor.shouldProcess("你好。", config))
        assertTrue(TextProcessor.shouldProcess("你好。 =^.^=", config))
    }

    @Test
    fun realtimeModeTriggersWithoutPunctuation() {
        val config = plainConfig(processingMode = ProcessingMode.REALTIME)
        assertTrue(TextProcessor.shouldProcess("正在输入", config))
    }

    @Test
    fun realtimeModeAppliesRulesAndSuffixWithoutPunctuation() {
        val config = plainConfig(
            processingMode = ProcessingMode.REALTIME,
            enableSentenceSuffix = true,
            rules = listOf(ReplacementRule("我", "本喵")),
        )
        val result = TextProcessor.process("我服了", config)
        assertEquals("本喵服了喵", result.text)
        assertEquals(result.text.length - 1, result.selectionStart)
    }

    @Test
    fun realtimeSuffixStaysStableWhileTypingBeforeIt() {
        val config = plainConfig(
            processingMode = ProcessingMode.REALTIME,
            enableSentenceSuffix = true,
            rules = listOf(ReplacementRule("我", "本喵")),
        )
        val first = TextProcessor.process("我", config)
        val withNextCharacter = first.text.substring(0, first.selectionStart) +
            "服" + first.text.substring(first.selectionStart)
        val second = TextProcessor.process(
            withNextCharacter,
            config,
            first.selectionStart + 1,
            first.selectionStart + 1,
        )
        assertEquals("本喵服喵", second.text)
        assertEquals(second.text.length - 1, second.selectionStart)
    }

    @Test
    fun realtimeSuffixMovesBehindTextTypedAfterPreviousOutput() {
        val config = plainConfig(
            processingMode = ProcessingMode.REALTIME,
            enableSentenceSuffix = true,
            rules = listOf(ReplacementRule("我", "本喵")),
        )
        val previous = TextProcessor.process("我服", config)
        assertEquals("本喵服喵", previous.text)

        val rawInput = previous.text + "了"
        val normalized = TextProcessor.normalizeTypingAfterManagedSuffix(
            input = rawInput,
            previousAppliedText = previous.text,
            config = config,
            selectionStart = rawInput.length,
            selectionEnd = rawInput.length,
        )
        val result = TextProcessor.process(
            normalized.text,
            config,
            normalized.selectionStart,
            normalized.selectionEnd,
        )

        assertEquals("本喵服了喵", result.text)
        assertEquals(result.text.length - 1, result.selectionStart)
        assertEquals(result.selectionStart, result.selectionEnd)
    }

    @Test
    fun arbitrarySuffixInsideTextIsNotRemovedWithoutMatchingPreviousOutput() {
        val config = plainConfig(
            processingMode = ProcessingMode.REALTIME,
            enableSentenceSuffix = true,
        )
        val input = "前喵后"
        val normalized = TextProcessor.normalizeTypingAfterManagedSuffix(
            input = input,
            previousAppliedText = "其他喵",
            config = config,
        )

        assertEquals(input, normalized.text)
        assertEquals(input.length, normalized.selectionStart)
    }

    private fun plainConfig(
        processingMode: ProcessingMode = ProcessingMode.PUNCTUATION,
        enableSentenceSuffix: Boolean = false,
        enableRandomEmoticon: Boolean = false,
        customEmoticons: List<String> = emptyList(),
        rules: List<ReplacementRule> = emptyList(),
    ) = AppConfig(
        processingMode = processingMode,
        enableSentenceSuffix = enableSentenceSuffix,
        enableRandomEmoticon = enableRandomEmoticon,
        customEmoticons = customEmoticons,
        rules = rules,
    )
}
