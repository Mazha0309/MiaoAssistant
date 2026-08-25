package com.mazha0309.miaoassistant.text

import com.mazha0309.miaoassistant.config.AppConfig
import com.mazha0309.miaoassistant.config.ProcessingMode
import com.mazha0309.miaoassistant.config.ReplacementRule

data class ProcessedText(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val managedSuffixStart: Int? = null,
)

object TextProcessor {
    private val punctuation = setOf('，', ',', '。', '.', '！', '!', '？', '?', '；', ';', '：', ':', '\n')

    /**
     * Moves the suffix appended by the previous real-time rewrite behind newly typed text.
     * This is intentionally tied to the exact previous output, so a user-authored "喵" in
     * arbitrary text is not treated as assistant-managed content.
     */
    fun normalizeTypingAfterManagedSuffix(
        input: String,
        previousAppliedText: String?,
        previousManagedSuffixStart: Int? = null,
        config: AppConfig,
        selectionStart: Int = input.length,
        selectionEnd: Int = selectionStart,
    ): ProcessedText {
        val suffix = config.sentenceSuffix
        val previous = previousAppliedText
        val managedSuffixStart = previousManagedSuffixStart
        if (
            config.processingMode != ProcessingMode.REALTIME ||
            !config.enableSentenceSuffix ||
            suffix.isEmpty() ||
            previous == null ||
            managedSuffixStart == null ||
            managedSuffixStart < 0 ||
            managedSuffixStart + suffix.length != previous.length ||
            !previous.regionMatches(managedSuffixStart, suffix, 0, suffix.length) ||
            input.length <= previous.length ||
            !input.startsWith(previous) ||
            selectionStart < previous.length ||
            selectionEnd < previous.length
        ) {
            return ProcessedText(
                text = input,
                selectionStart = selectionStart.coerceIn(0, input.length),
                selectionEnd = selectionEnd.coerceIn(0, input.length),
            )
        }

        val removalStart = managedSuffixStart
        val removalEnd = previous.length
        fun mapSelection(index: Int): Int = when {
            index <= removalStart -> index
            index <= removalEnd -> removalStart
            else -> index - suffix.length
        }

        return ProcessedText(
            text = input.removeRange(removalStart, removalEnd),
            selectionStart = mapSelection(selectionStart).coerceAtLeast(0),
            selectionEnd = mapSelection(selectionEnd).coerceAtLeast(0),
        )
    }

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

        if (
            config.processingMode == ProcessingMode.REALTIME &&
            config.enableSentenceSuffix &&
            config.sentenceSuffix.isNotEmpty()
        ) {
            mapped = stripManagedSuffixes(mapped, config.sentenceSuffix)
        }

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

        if (
            config.processingMode == ProcessingMode.REALTIME &&
            config.enableSentenceSuffix &&
            config.sentenceSuffix.isNotEmpty() &&
            !hasTerminalPunctuation(mapped.text)
        ) {
            mapped = appendSuffixToTrailingSentence(mapped, config.sentenceSuffix)
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
            managedSuffixStart = mapped.managedSuffixStart,
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

    private fun appendSuffixToTrailingSentence(mapped: MappedText, suffix: String): MappedText {
        val source = mapped.text
        val contentEnd = source.indexOfLast { it != ' ' && it != '\t' && it != '\r' } + 1
        if (contentEnd <= 0) return mapped

        val segmentStart = source
            .substring(0, contentEnd)
            .indexOfLast { it in punctuation }
            .let { if (it < 0) 0 else it + 1 }
        val meaningfulPart = source.substring(segmentStart, contentEnd)
        if (meaningfulPart.isBlank() || meaningfulPart.endsWith(suffix)) return mapped

        val result = source.substring(0, contentEnd) + suffix + source.substring(contentEnd)
        fun mapSelection(index: Int): Int = if (index > contentEnd) index + suffix.length else index
        return MappedText(
            text = result,
            selectionStart = mapSelection(mapped.selectionStart),
            selectionEnd = mapSelection(mapped.selectionEnd),
            managedSuffixStart = contentEnd,
        )
    }

    private fun stripManagedSuffixes(mapped: MappedText, suffix: String): MappedText {
        if (suffix.isEmpty() || mapped.text.isEmpty()) return mapped
        val source = mapped.text
        val removals = mutableListOf<Pair<Int, Int>>()
        var segmentStart = 0
        var index = 0

        while (index <= source.length) {
            val atEnd = index == source.length
            if (!atEnd && source[index] !in punctuation) {
                index += 1
                continue
            }

            val contentEnd = source
                .substring(segmentStart, index)
                .indexOfLast { !it.isWhitespace() }
                .let { if (it < 0) segmentStart else segmentStart + it + 1 }
            if (
                contentEnd - segmentStart >= suffix.length &&
                source.regionMatches(contentEnd - suffix.length, suffix, 0, suffix.length)
            ) {
                removals += (contentEnd - suffix.length) to contentEnd
            }

            if (atEnd) break
            while (index < source.length && source[index] in punctuation) index += 1
            segmentStart = index
        }

        if (removals.isEmpty()) return mapped
        val result = buildString(source.length - removals.sumOf { it.second - it.first }) {
            var copiedUntil = 0
            removals.forEach { (start, end) ->
                append(source, copiedUntil, start)
                copiedUntil = end
            }
            append(source, copiedUntil, source.length)
        }

        fun mapSelection(selection: Int): Int {
            var removed = 0
            removals.forEach { (start, end) ->
                when {
                    selection < start -> return selection - removed
                    selection <= end -> return start - removed
                    else -> removed += end - start
                }
            }
            return selection - removed
        }

        return MappedText(
            text = result,
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
        val managedSuffixStart: Int? = null,
    )
}
