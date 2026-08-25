package com.mazha0309.miaoassistant

import android.accessibilityservice.AccessibilityServiceInfo
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.mazha0309.miaoassistant.apps.InstalledApp
import com.mazha0309.miaoassistant.apps.InstalledAppsRepository
import com.mazha0309.miaoassistant.config.AppConfig
import com.mazha0309.miaoassistant.config.ConfigRepository
import com.mazha0309.miaoassistant.config.InputWriteMode
import com.mazha0309.miaoassistant.keepalive.KeepAliveService
import com.mazha0309.miaoassistant.privileged.RootPermission
import com.mazha0309.miaoassistant.service.GlobalInputAccessibilityService
import com.mazha0309.miaoassistant.ui.MiaoAssistantApp
import com.mazha0309.miaoassistant.ui.MiaoAssistantTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private lateinit var configRepository: ConfigRepository
    private var config by mutableStateOf(AppConfig())
    private var serviceEnabled by mutableStateOf(false)
    private var batteryOptimizationIgnored by mutableStateOf(false)
    private var installedApps by mutableStateOf<List<InstalledApp>?>(null)
    private var pendingInputWriteMode: InputWriteMode? = null
    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode != SHIZUKU_PERMISSION_REQUEST || pendingInputWriteMode != InputWriteMode.SHIZUKU) {
            return@OnRequestPermissionResultListener
        }
        pendingInputWriteMode = null
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            saveConfig(config.copy(inputWriteMode = InputWriteMode.SHIZUKU))
            Toast.makeText(this, R.string.shizuku_permission_granted, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.shizuku_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            enableKeepAlive()
        } else {
            Toast.makeText(this, R.string.notification_permission_required, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        configRepository = ConfigRepository(this)
        config = configRepository.load()
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        lifecycleScope.launch {
            installedApps = withContext(Dispatchers.IO) {
                InstalledAppsRepository.load(this@MainActivity)
            }
        }

        setContent {
            MiaoAssistantTheme(
                themeMode = config.themeMode,
                useMonet = config.useMonet,
            ) {
                MiaoAssistantApp(
                    config = config,
                    installedApps = installedApps,
                    serviceEnabled = serviceEnabled,
                    batteryOptimizationIgnored = batteryOptimizationIgnored,
                    onConfigChange = ::saveConfig,
                    onInputWriteModeChange = ::setInputWriteMode,
                    onKeepAliveChange = ::setKeepAlive,
                    onOpenAccessibilitySettings = ::openAccessibilitySettings,
                    onOpenBatterySettings = ::openBatteryOptimizationSettings,
                    onInvalidRules = { count ->
                        Toast.makeText(
                            this,
                            getString(R.string.invalid_rules_ignored, count),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        config = configRepository.load()
        serviceEnabled = isAccessibilityServiceEnabled()
        batteryOptimizationIgnored = getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(packageName)
            ?: false
        if (config.keepAliveEnabled) {
            if (!hasNotificationPermission()) {
                saveConfig(config.copy(keepAliveEnabled = false))
                KeepAliveService.stop(this)
            } else {
                try {
                    KeepAliveService.start(this)
                } catch (_: RuntimeException) {
                    saveConfig(config.copy(keepAliveEnabled = false))
                }
            }
        }
    }

    private fun saveConfig(updated: AppConfig) {
        config = updated
        configRepository.save(updated)
    }

    private fun setInputWriteMode(mode: InputWriteMode) {
        pendingInputWriteMode = null
        when (mode) {
            InputWriteMode.ACCESSIBILITY -> saveConfig(config.copy(inputWriteMode = mode))
            InputWriteMode.SHIZUKU -> requestShizukuMode()
            InputWriteMode.ROOT -> requestRootMode()
        }
    }

    private fun requestShizukuMode() {
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!running) {
            Toast.makeText(this, R.string.shizuku_not_running, Toast.LENGTH_SHORT).show()
            return
        }

        val permission = runCatching { Shizuku.checkSelfPermission() }.getOrDefault(PackageManager.PERMISSION_DENIED)
        if (permission == PackageManager.PERMISSION_GRANTED) {
            saveConfig(config.copy(inputWriteMode = InputWriteMode.SHIZUKU))
            Toast.makeText(this, R.string.shizuku_permission_granted, Toast.LENGTH_SHORT).show()
            return
        }

        pendingInputWriteMode = InputWriteMode.SHIZUKU
        runCatching { Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST) }
            .onFailure {
                pendingInputWriteMode = null
                Toast.makeText(this, R.string.shizuku_not_running, Toast.LENGTH_SHORT).show()
            }
    }

    private fun requestRootMode() {
        pendingInputWriteMode = InputWriteMode.ROOT
        lifecycleScope.launch {
            val granted = withContext(Dispatchers.IO) { RootPermission.request() }
            if (pendingInputWriteMode != InputWriteMode.ROOT) return@launch
            pendingInputWriteMode = null
            if (granted) {
                saveConfig(config.copy(inputWriteMode = InputWriteMode.ROOT))
                Toast.makeText(this@MainActivity, R.string.root_permission_granted, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, R.string.root_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setKeepAlive(enabled: Boolean) {
        if (!enabled) {
            saveConfig(config.copy(keepAliveEnabled = false))
            KeepAliveService.stop(this)
            return
        }

        if (!hasNotificationPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        enableKeepAlive()
    }

    private fun enableKeepAlive() {
        try {
            KeepAliveService.start(this)
            saveConfig(config.copy(keepAliveEnabled = true))
        } catch (_: RuntimeException) {
            saveConfig(config.copy(keepAliveEnabled = false))
            Toast.makeText(this, R.string.keep_alive_start_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                this,
                R.string.accessibility_settings_unavailable,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun openBatteryOptimizationSettings() {
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.battery_settings_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun isAccessibilityServiceEnabled(): Boolean {
        val manager = getSystemService(AccessibilityManager::class.java) ?: return false
        return manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
                serviceInfo.packageName == packageName &&
                    serviceInfo.name == GlobalInputAccessibilityService::class.java.name
            }
    }

    companion object {
        private const val SHIZUKU_PERMISSION_REQUEST = 1309
    }
}
