package com.mazha0309.miaoassistant.text

import com.mazha0309.miaoassistant.config.AppConfig
import com.mazha0309.miaoassistant.config.ProcessingMode
import com.mazha0309.miaoassistant.config.ReplacementRule

data class ProcessedText(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
)

object TextProcessor {
    private val punctuation = setOf('，', ',', '。', '.', '！', '!', '？', '?', '；', ';', '：', ':', '\n')

    fun shouldProcess(input: String, config: AppConfig): Boolean {
        if (!config.enabled || input.isEmpty()) return false
        if (config.processingMode == ProcessingMode.REALTIME) return true
        val withoutManagedEmoticon = stripManagedEmoticon(
            MappedText(input, input.length, input.length),
            config.activeEmoticons,
        ).text
        return hasTerminalPunctuation(withoutManagedEmoticon)
    }

    fun process(
        input: String,
        config: AppConfig,
        selectionStart: Int = input.length,
        selectionEnd: Int = selectionStart,
    ): ProcessedText {
        if (input.isEmpty()) return ProcessedText(input, 0, 0)

        var mapped = MappedText(
            text = input,
            selectionStart = selectionStart.coerceIn(0, input.length),
            selectionEnd = selectionEnd.coerceIn(0, input.length),
        )

        if (config.enableRandomEmoticon) {
            mapped = stripManagedEmoticon(mapped, config.activeEmoticons)
        }

        config.rules.forEach { rule ->
            mapped = replaceLiteral(mapped, rule)
        }

        val completedSentence = hasTerminalPunctuation(mapped.text)
        if (completedSentence && config.enableSentenceSuffix && config.sentenceSuffix.isNotEmpty()) {
            mapped = appendSuffixAtPunctuation(mapped, config.sentenceSuffix)
        }

        if (completedSentence && config.enableRandomEmoticon) {
            val emoticons = config.activeEmoticons.filter(String::isNotBlank)
            if (emoticons.isNotEmpty()) {
                val index = (mapped.text.hashCode() and Int.MAX_VALUE) % emoticons.size
                mapped = mapped.copy(text = mapped.text + " " + emoticons[index])
            }
        }

        return ProcessedText(
            text = mapped.text,
            selectionStart = mapped.selectionStart.coerceIn(0, mapped.text.length),
            selectionEnd = mapped.selectionEnd.coerceIn(0, mapped.text.length),
        )
    }

    fun hasTerminalPunctuation(text: String): Boolean {
        val withoutTrailingHorizontalSpace = text.trimEnd(' ', '\t', '\r')
        return withoutTrailingHorizontalSpace.lastOrNull() in punctuation
    }

    private fun replaceLiteral(mapped: MappedText, rule: ReplacementRule): MappedText {
        if (rule.from == rule.to || !mapped.text.contains(rule.from)) return mapped

        val source = mapped.text
        val matches = buildList {
            var searchFrom = 0
            while (searchFrom <= source.length - rule.from.length) {
                val match = source.indexOf(rule.from, searchFrom)
                if (match < 0) break
                add(match)
                searchFrom = match + rule.from.length
            }
        }
        if (matches.isEmpty()) return mapped

        fun mapSelection(index: Int): Int {
            var delta = 0
            matches.forEach { matchStart ->
                val matchEnd = matchStart + rule.from.length
                when {
                    index < matchStart -> return index + delta
                    index == matchStart -> return matchStart + delta
                    index <= matchEnd -> return matchStart + delta + rule.to.length
                }
                delta += rule.to.length - rule.from.length
            }
            return index + delta
        }

        return MappedText(
            text = source.replace(rule.from, rule.to),
            selectionStart = mapSelection(mapped.selectionStart),
            selectionEnd = mapSelection(mapped.selectionEnd),
        )
    }

    private fun appendSuffixAtPunctuation(mapped: MappedText, suffix: String): MappedText {
        val source = mapped.text
        val result = StringBuilder(source.length + suffix.length * 2)
        val insertionPositions = mutableListOf<Int>()
        var segmentStart = 0
        var index = 0

        while (index < source.length) {
            if (source[index] !in punctuation) {
                index += 1
                continue
            }

            val boundaryStart = index
            while (index < source.length && source[index] in punctuation) index += 1

            val segment = source.substring(segmentStart, boundaryStart)
            val trimmedLength = segment.indexOfLast { !it.isWhitespace() } + 1
            val meaningfulPart = segment.substring(0, trimmedLength)
            result.append(meaningfulPart)
            if (meaningfulPart.isNotBlank() && !meaningfulPart.endsWith(suffix)) {
                insertionPositions += segmentStart + trimmedLength
                result.append(suffix)
            }
            result.append(segment.substring(trimmedLength))
            result.append(source, boundaryStart, index)
            segmentStart = index
        }
        result.append(source, segmentStart, source.length)

        fun mapSelection(original: Int): Int = original + insertionPositions.count { it < original } * suffix.length

        return MappedText(
            text = result.toString(),
            selectionStart = mapSelection(mapped.selectionStart),
            selectionEnd = mapSelection(mapped.selectionEnd),
        )
    }

    private fun stripManagedEmoticon(mapped: MappedText, emoticons: List<String>): MappedText {
        if (mapped.text.isEmpty()) return mapped
        val contentEnd = mapped.text.indexOfLast { it != ' ' && it != '\t' && it != '\r' } + 1
        if (contentEnd <= 0) return mapped
        val content = mapped.text.substring(0, contentEnd)
        val emoticon = emoticons
            .asSequence()
            .filter(String::isNotBlank)
            .sortedByDescending(String::length)
            .firstOrNull { candidate ->
                if (!content.endsWith(candidate)) return@firstOrNull false
                val start = content.length - candidate.length
                start == 0 || content[start - 1].isWhitespace()
            }
            ?: return mapped

        var removalStart = content.length - emoticon.length
        if (removalStart > 0 && mapped.text[removalStart - 1] == ' ') removalStart -= 1
        val removalEnd = contentEnd
        val removedLength = removalEnd - removalStart

        fun mapSelection(index: Int): Int = when {
            index <= removalStart -> index
            index <= removalEnd -> removalStart
            else -> index - removedLength
        }

        return MappedText(
            text = mapped.text.removeRange(removalStart, removalEnd),
            selectionStart = mapSelection(mapped.selectionStart),
            selectionEnd = mapSelection(mapped.selectionEnd),
        )
    }

    private data class MappedText(
        val text: String,
        val selectionStart: Int,
        val selectionEnd: Int,
    )
}
