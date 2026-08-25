package com.mazha0309.miaoassistant.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplacementRuleTest {
    @Test
    fun parsesSupportedSeparatorsAndEmptyReplacement() {
        assertEquals(ReplacementRule("我", "本喵"), ReplacementRule.parse("我=本喵"))
        assertEquals(ReplacementRule("你", "主人"), ReplacementRule.parse("你＝主人"))
        assertEquals(ReplacementRule("删除", ""), ReplacementRule.parse("删除→"))
    }

    @Test
    fun rejectsMissingSourceOrSeparator() {
        assertNull(ReplacementRule.parse(""))
        assertNull(ReplacementRule.parse("没有分隔符"))
        assertNull(ReplacementRule.parse("=替换"))
    }

    @Test
    fun reportsInvalidNonBlankLines() {
        val (rules, invalid) = ConfigRepository.parseRules("我=本喵\n无效\n\n你→主人")
        assertEquals(2, rules.size)
        assertEquals(1, invalid)
    }

    @Test
    fun seedsEditableDefaultsWithoutOverwritingExistingSources() {
        val existing = listOf(
            ReplacementRule("我", "咱"),
            ReplacementRule("测试", "成功"),
        )

        assertEquals(
            listOf(
                ReplacementRule("你", "主人"),
                ReplacementRule("我", "咱"),
                ReplacementRule("测试", "成功"),
            ),
            ConfigRepository.seedDefaultRules(existing),
        )
    }
}
