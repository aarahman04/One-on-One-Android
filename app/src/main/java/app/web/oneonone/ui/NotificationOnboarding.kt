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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.net.toUri
import app.web.oneonone.push.PushRegistration
import app.web.oneonone.ui.components.PrimaryButton
import app.web.oneonone.ui.components.SecondaryButton
import app.web.oneonone.ui.theme.OneOnOneTheme
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneTheme
import java.util.Locale

@Composable
fun NotificationOnboarding(registration: PushRegistration, onDone: () -> Unit) {
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
    NotificationContent(allowed, fullScreen, Build.VERSION.SDK_INT, problem,
        onAllow = { if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) },
        onNotificationSettings = { openSetting(context, listOf(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))) },
        onFullScreenSettings = { if (Build.VERSION.SDK_INT >= 34) openSetting(context, listOf(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).setData("package:${context.packageName}".toUri()))) },
        onAutostart = { openSetting(context, autostartIntents(Build.MANUFACTURER)) },
        onBattery = { openSetting(context, batteryIntents(context, Build.MANUFACTURER)) },
        onRetry = { registration.schedule() }, onDone = onDone)
}

@Composable
private fun NotificationContent(allowed: Boolean, fullScreen: Boolean, sdk: Int, problem: String?,
    onAllow: () -> Unit, onNotificationSettings: () -> Unit, onFullScreenSettings: () -> Unit,
    onAutostart: () -> Unit, onBattery: () -> Unit, onRetry: () -> Unit, onDone: () -> Unit) {
    ScreenFrame(AppState(), screenKey = "notifications") {
        Surface(Modifier.widthIn(max = 364.dp).fillMaxWidth(), color = OneTheme.colors.bgRaised,
            shape = RoundedCornerShape(OneTheme.radii.md10), border = BorderStroke(1.dp, OneTheme.colors.border)) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Stay connected", style = OneTextStyles.subtitle.copy(fontWeight = FontWeight.Bold), color = OneTheme.colors.text)
                NotificationBody("Let messages reach you when One on One is closed. These settings are optional and can be changed here later.")
                NotificationBody(if (allowed) "Notifications allowed" else "Notifications are turned off")
                if (sdk >= 33 && !allowed) PrimaryButton("Allow notifications", onAllow)
                SecondaryButton("Notification settings", onNotificationSettings)
                if (sdk >= 34) {
                    NotificationBody(if (fullScreen) "Full-screen alerts allowed" else "Full-screen alerts are off")
                    NotificationBody("Full-screen permission lets emergency alarms and calls appear over the lock screen when those features are available.")
                    SecondaryButton("Full-screen alerts", onFullScreenSettings)
                }
                Text("Phone background settings", style = OneTextStyles.subtitle.copy(fontWeight = FontWeight.Bold), color = OneTheme.colors.text)
                NotificationBody("On Xiaomi, Redmi and POCO (MIUI/HyperOS), enable Background autostart and set battery saver to No restrictions. Oppo, Vivo, OnePlus and Samsung may also restrict background activity; allow autostart/background use and remove this app from sleeping apps.")
                SecondaryButton("Autostart / background activity", onAutostart)
                SecondaryButton("Battery restrictions", onBattery)
                NotificationBody("If a shortcut opens App info instead, look for battery/background settings there. Removing the app from recents is different from Force stop: Force stop blocks pushes until you reopen it.")
                problem?.let { Text(it, style = OneTextStyles.cardHint, color = OneTheme.colors.danger) }
                SecondaryButton("Retry notification registration", onRetry)
                PrimaryButton("Continue", onDone)
            }
        }
    }
}

@Composable
private fun NotificationBody(text: String) {
    Text(text, Modifier.widthIn(max = 320.dp), style = OneTextStyles.subtitle.copy(fontSize = 14.sp, lineHeight = 21.sp), color = OneTheme.colors.textDim)
}

@Preview(name = "Notifications", widthDp = 390, heightDp = 1200)
@Composable
private fun NotificationPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) {
    OneOnOneTheme(darkTheme = dark) { NotificationContent(false, false, 34, null, {}, {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Notifications allowed", widthDp = 390, heightDp = 1200)
@Composable
private fun NotificationAllowedPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) {
    OneOnOneTheme(darkTheme = dark) { NotificationContent(true, true, 34, null, {}, {}, {}, {}, {}, {}, {}) }
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
