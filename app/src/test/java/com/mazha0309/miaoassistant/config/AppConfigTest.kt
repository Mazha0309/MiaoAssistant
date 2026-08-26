package com.mazha0309.miaoassistant.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConfigTest {
    @Test
    fun rootKeepAliveIsOptIn() {
        assertFalse(AppConfig().rootKeepAliveEnabled)
    }

    @Test
    fun defaultsIncludeRequestedReplacementRules() {
        assertEquals(
            listOf(
                ReplacementRule("我", "本喵"),
                ReplacementRule("你", "主人"),
            ),
            AppConfig().rules,
        )
    }

    @Test
    fun excludeSelectedModeAllowsEverythingExceptSelection() {
        val config = AppConfig(
            appScopeMode = AppScopeMode.EXCLUDE_SELECTED,
            scopedPackages = setOf("example.blocked"),
        )

        assertFalse(config.isPackageInScope("example.blocked"))
        assertTrue(config.isPackageInScope("example.allowed"))
    }

    @Test
    fun includeSelectedModeAllowsOnlySelection() {
        val config = AppConfig(
            appScopeMode = AppScopeMode.INCLUDE_SELECTED,
            scopedPackages = setOf("example.allowed"),
        )

        assertTrue(config.isPackageInScope("example.allowed"))
        assertFalse(config.isPackageInScope("example.blocked"))
    }
}
