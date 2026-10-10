package app.web.oneonone.ui

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.net.toUri
import app.web.oneonone.push.PushRegistration
import app.web.oneonone.R
import app.web.oneonone.ui.components.*
import app.web.oneonone.ui.theme.OneOnOneTheme
import java.util.Locale

@Composable
fun NotificationOnboarding(registration: PushRegistration, firstRun: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val manager = context.getSystemService(NotificationManager::class.java)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var allowed by remember { mutableStateOf(manager.areNotificationsEnabled()) }
    var fullScreen by remember { mutableStateOf(Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent()) }
    val problem by registration.problem.collectAsState()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = manager.areNotificationsEnabled()
        if (it) registration.schedule()
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            allowed = manager.areNotificationsEnabled()
            fullScreen = Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent()
        } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    NotificationContent(allowed, fullScreen, Build.VERSION.SDK_INT, problem, firstRun,
        onAllow = { if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) },
        onNotificationSettings = { openSetting(context, listOf(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))) },
        onFullScreenSettings = { if (Build.VERSION.SDK_INT >= 34) openSetting(context, listOf(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).setData("package:${context.packageName}".toUri()))) },
        onAutostart = { openSetting(context, autostartIntents(Build.MANUFACTURER)) },
        onBattery = { openSetting(context, batteryIntents(context, Build.MANUFACTURER)) },
        onRetry = { registration.schedule() }, onDone = onDone)
}

@Composable
private fun NotificationContent(allowed: Boolean, fullScreen: Boolean, sdk: Int, problem: String?, firstRun: Boolean,
    onAllow: () -> Unit, onNotificationSettings: () -> Unit, onFullScreenSettings: () -> Unit,
    onAutostart: () -> Unit, onBattery: () -> Unit, onRetry: () -> Unit, onDone: () -> Unit) {
    SettingsScaffold(if (firstRun) "Stay connected" else "Notifications & background", onDone,
        bottomBar = if (firstRun) ({ PrimaryButton("Continue", onDone, Modifier.fillMaxWidth()) }) else null) {
        if (firstRun) SettingsCaption("Let messages reach you when One on One is closed. These settings are optional and can be changed in Settings later.")
        SettingsGroup("Permissions") {
            SettingsRow(R.drawable.ic_bell, "Notifications", subtitle = if (allowed) "Messages can reach you" else "Messages won't arrive while the app is closed",
                trailing = SettingsTrailing.Chip(if (allowed) "Allowed" else "Off", allowed), onClick = onNotificationSettings)
            if (sdk >= 34) SettingsRow(R.drawable.ic_maximize, "Full-screen alerts",
                subtitle = "Lets calls and emergency alarms appear over the lock screen",
                trailing = SettingsTrailing.Chip(if (fullScreen) "Allowed" else "Off", fullScreen), onClick = onFullScreenSettings)
        }
        if (sdk >= 33 && !allowed) PrimaryButton("Allow notifications", onAllow, Modifier.fillMaxWidth())
        SettingsGroup("Background") {
            SettingsRow(R.drawable.ic_power, "Autostart / background activity", subtitle = "Let the app start in the background",
                trailing = SettingsTrailing.Chevron, onClick = onAutostart)
            SettingsRow(R.drawable.ic_battery, "Battery restrictions", subtitle = "Set to no restrictions",
                trailing = SettingsTrailing.Chevron, onClick = onBattery)
        }
        SettingsExpandable("Why is this needed?",
            "On Xiaomi, Redmi and POCO (MIUI/HyperOS), enable Background autostart and set battery saver to No restrictions. Oppo, Vivo, OnePlus and Samsung may also restrict background activity; allow autostart/background use and remove this app from sleeping apps.\n\nIf a shortcut opens App info instead, look for battery/background settings there. Removing the app from recents is different from Force stop: Force stop blocks pushes until you reopen it.")
        SettingsGroup("Troubleshooting") {
            SettingsRow(R.drawable.ic_refresh, "Retry notification registration",
                subtitle = problem, trailing = SettingsTrailing.Chevron, onClick = onRetry)
        }
    }
}

@Preview(name = "Notifications", widthDp = 390, heightDp = 1000)
@Composable
private fun NotificationPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) {
    OneOnOneTheme(darkTheme = dark) { NotificationContent(false, false, 34, "Registration failed. Try again.", false, {}, {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Notifications allowed", widthDp = 390, heightDp = 1000)
@Composable
private fun NotificationAllowedPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) {
    OneOnOneTheme(darkTheme = dark) { NotificationContent(true, true, 34, null, false, {}, {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Notifications first run", widthDp = 360, heightDp = 800)
@Composable
private fun NotificationFirstRunPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) {
    OneOnOneTheme(darkTheme = dark) { NotificationContent(false, false, 34, null, true, {}, {}, {}, {}, {}, {}, {}) }
}

private fun component(packageName: String, className: String) = Intent().setComponent(ComponentName(packageName, className))

internal fun autostartIntents(manufacturer: String): List<Intent> = when (manufacturer.lowercase(Locale.ROOT)) {
    "xiaomi", "redmi", "poco" -> listOf(component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
    "oppo", "realme", "oneplus" -> listOf(
        component("com.oplus.safecenter", "com.oplus.safecenter.startupapp.StartupAppListActivity"),
        component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        component("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
    )
    "vivo", "iqoo" -> listOf(
        component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
    )
    "samsung" -> listOf(component("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"))
    else -> emptyList()
}

private fun batteryIntents(context: Context, manufacturer: String): List<Intent> =
    if (manufacturer.lowercase(Locale.ROOT) in setOf("xiaomi", "redmi", "poco")) listOf(
        component("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
            .putExtra("package_name", context.packageName).putExtra("package_label", "One on One"),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    ) else listOf(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

private fun openSetting(context: Context, candidates: List<Intent>) {
    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData("package:${context.packageName}".toUri())
    for (intent in candidates + fallback) {
        try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return }
        catch (_: ActivityNotFoundException) { }
        catch (_: SecurityException) { }
    }
}
