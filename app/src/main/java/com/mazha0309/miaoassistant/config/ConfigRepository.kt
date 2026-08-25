package com.mazha0309.miaoassistant.config

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class ConfigRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AppConfig = AppConfig(
        enabled = preferences.getBoolean(KEY_ENABLED, true),
        processingMode = ProcessingMode.fromStored(preferences.getString(KEY_PROCESSING_MODE, null)),
        enableSentenceSuffix = preferences.getBoolean(KEY_ENABLE_SUFFIX, true),
        sentenceSuffix = preferences.getString(KEY_SUFFIX, "喵").orEmpty().ifEmpty { "喵" },
        enableRandomEmoticon = preferences.getBoolean(KEY_ENABLE_EMOTICON, true),
        customEmoticons = parseNonBlankLines(preferences.getString(KEY_CUSTOM_EMOTICONS, null)),
        rules = if (preferences.getBoolean(KEY_DEFAULT_RULES_SEEDED, false)) {
            parseRules(preferences.getString(KEY_RULES, null)).first
        } else {
            seedDefaultRules(parseRules(preferences.getString(KEY_RULES, null)).first)
        },
        appScopeMode = AppScopeMode.fromStored(preferences.getString(KEY_APP_SCOPE_MODE, null)),
        scopedPackages = when {
            preferences.contains(KEY_SCOPED_PACKAGES) ->
                preferences.getStringSet(KEY_SCOPED_PACKAGES, emptySet()).orEmpty()

            preferences.contains(KEY_EXCLUDED_PACKAGES) ->
                parseNonBlankLines(preferences.getString(KEY_EXCLUDED_PACKAGES, null))
                    .filterNot { it in ALWAYS_EXCLUDED_PACKAGES }
                    .toSet()

            else -> emptySet()
        },
        keepAliveEnabled = preferences.getBoolean(KEY_KEEP_ALIVE, false),
        themeMode = ThemeMode.fromStored(preferences.getString(KEY_THEME_MODE, null)),
        useMonet = preferences.getBoolean(KEY_USE_MONET, false),
        blurEnabled = preferences.getBoolean(KEY_BLUR_ENABLED, false),
        floatingBottomBar = preferences.getBoolean(KEY_FLOATING_BOTTOM_BAR, false),
        liquidGlassEnabled = preferences.getBoolean(KEY_LIQUID_GLASS, false),
    )

    fun save(config: AppConfig) {
        preferences.edit {
            putBoolean(KEY_ENABLED, config.enabled)
            putString(KEY_PROCESSING_MODE, config.processingMode.storedValue)
            putBoolean(KEY_ENABLE_SUFFIX, config.enableSentenceSuffix)
            putString(KEY_SUFFIX, config.sentenceSuffix.ifEmpty { "喵" })
            putBoolean(KEY_ENABLE_EMOTICON, config.enableRandomEmoticon)
            putString(KEY_CUSTOM_EMOTICONS, config.customEmoticons.joinToString("\n"))
            putString(KEY_RULES, config.rules.joinToString("\n") { it.serialize() })
            putBoolean(KEY_DEFAULT_RULES_SEEDED, true)
            putString(KEY_APP_SCOPE_MODE, config.appScopeMode.storedValue)
            putStringSet(KEY_SCOPED_PACKAGES, config.scopedPackages)
            remove(KEY_EXCLUDED_PACKAGES)
            putBoolean(KEY_KEEP_ALIVE, config.keepAliveEnabled)
            putString(KEY_THEME_MODE, config.themeMode.storedValue)
            putBoolean(KEY_USE_MONET, config.useMonet)
            putBoolean(KEY_BLUR_ENABLED, config.blurEnabled)
            putBoolean(KEY_FLOATING_BOTTOM_BAR, config.floatingBottomBar)
            putBoolean(KEY_LIQUID_GLASS, config.liquidGlassEnabled)
        }
    }

    fun registerListener(onConfigChanged: (AppConfig) -> Unit): SharedPreferences.OnSharedPreferenceChangeListener {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            onConfigChanged(load())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return listener
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        private const val PREFS_NAME = "miao_assistant_config"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PROCESSING_MODE = "processing_mode"
        private const val KEY_ENABLE_SUFFIX = "enable_suffix"
        private const val KEY_SUFFIX = "suffix"
        private const val KEY_ENABLE_EMOTICON = "enable_emoticon"
        private const val KEY_CUSTOM_EMOTICONS = "custom_emoticons"
        private const val KEY_RULES = "rules"
        private const val KEY_DEFAULT_RULES_SEEDED = "default_rules_seeded_v1"
        private const val KEY_APP_SCOPE_MODE = "app_scope_mode"
        private const val KEY_SCOPED_PACKAGES = "scoped_packages"
        // Kept only to migrate development builds that used a manual exclusion editor.
        private const val KEY_EXCLUDED_PACKAGES = "excluded_packages"
        private const val KEY_KEEP_ALIVE = "keep_alive"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_USE_MONET = "use_monet"
        private const val KEY_BLUR_ENABLED = "blur_enabled"
        private const val KEY_FLOATING_BOTTOM_BAR = "floating_bottom_bar"
        private const val KEY_LIQUID_GLASS = "liquid_glass"

        private val ALWAYS_EXCLUDED_PACKAGES = setOf("com.android.systemui")

        fun parseRules(value: String?): Pair<List<ReplacementRule>, Int> {
            var invalidCount = 0
            val rules = value.orEmpty().lineSequence().mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                ReplacementRule.parse(line) ?: run {
                    invalidCount += 1
                    null
                }
            }.toList()
            return rules to invalidCount
        }

        fun seedDefaultRules(existingRules: List<ReplacementRule>): List<ReplacementRule> {
            val existingSources = existingRules.mapTo(mutableSetOf(), ReplacementRule::from)
            return AppConfig.DEFAULT_RULES.filterNot { it.from in existingSources } + existingRules
        }

        fun parseNonBlankLines(value: String?): List<String> = value.orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .toList()
    }
}
