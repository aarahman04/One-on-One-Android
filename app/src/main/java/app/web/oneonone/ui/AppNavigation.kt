package app.web.oneonone.ui

import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.web.oneonone.R
import app.web.oneonone.data.api.BlockedUser
import app.web.oneonone.data.api.CurrentConnection
import app.web.oneonone.data.api.Me
import app.web.oneonone.ui.components.*
import app.web.oneonone.ui.theme.OneOnOneTheme
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneTheme
import app.web.oneonone.ui.chat.ChatScreen
import app.web.oneonone.ui.chat.ChatViewModel
import app.web.oneonone.ui.chat.FeatureViewModel
import app.web.oneonone.push.PushRegistration
import java.time.LocalDate

private const val LEGAL_ORIGIN = "https://one-on-one-mu.vercel.app"

@Composable
fun AppNavigation(viewModel: AppViewModel, chatViewModel: ChatViewModel, featureViewModel: FeatureViewModel, pushRegistration: PushRegistration) {
    val state by viewModel.state.collectAsState()
    val nav = rememberNavController()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, _ ->
            viewModel.foreground(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        lifecycle.addObserver(observer)
        viewModel.foreground(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose { lifecycle.removeObserver(observer); viewModel.foreground(false) }
    }
    LaunchedEffect(state.route) {
        if (state.route in setOf(BootRoute.SignIn, BootRoute.Age, BootRoute.Consent, BootRoute.UnderAge)) {
            nav.navigate("home") { popUpTo("home") { inclusive = true }; launchSingleTop = true }
        }
    }
    LaunchedEffect(state.route, state.connection?.id) {
        if (state.route != BootRoute.Chat) chatViewModel.close(clear = state.route == BootRoute.Connect)
    }
    LaunchedEffect(state.route, state.needsNotificationOnboarding) {
        if (state.needsNotificationOnboarding && state.route in setOf(BootRoute.Connect, BootRoute.Waiting, BootRoute.Request, BootRoute.Chat)) {
            nav.navigate("notifications") { launchSingleTop = true }
            viewModel.onboardingShown()
        }
    }
    NavHost(nav, startDestination = "home", modifier = Modifier.safeDrawingPadding()) {
        composable("home") {
            val connection = state.connection
            if (state.route == BootRoute.Chat && connection != null) {
                key(connection.myUserId, connection.id) {
                    ChatScreen(connection, chatViewModel, featureViewModel, onSettings = { nav.navigate("settings") }, onRefresh = { viewModel.refresh() }, accountError = state.error)
                }
            } else {
                val context = LocalContext.current
                HomeScreen(state, onSignIn = { viewModel.signIn(context) }, onVerify = viewModel::verifyAge,
                    onSignOut = { viewModel.signOut() }, onAcceptTerms = viewModel::acceptTerms,
                    onRegenerate = { viewModel.regenerate() }, onRequest = { viewModel.request(it) },
                    onConnectionAction = { viewModel.connectionAction(it) }, onSettings = { nav.navigate("settings") })
            }
        }
        composable("settings") {
            SettingsScreen(state, onNotifications = { nav.navigate("notifications") }, onBlocks = { nav.navigate("blocks") },
                onSignOut = { viewModel.signOut() }, onDelete = viewModel::deleteAccount, onBack = { nav.popBackStack() })
        }
        composable("blocks") {
            LaunchedEffect(Unit) { viewModel.loadBlocks() }
            BlocksScreen(state, onUnblock = { viewModel.unblock(it) }, onBack = { nav.popBackStack() })
        }
        composable("notifications") {
            NotificationOnboarding(pushRegistration, onDone = { nav.popBackStack() })
        }
    }
}

@Composable
private fun HomeScreen(
    state: AppState,
    onSignIn: () -> Unit,
    onVerify: (LocalDate) -> Unit,
    onSignOut: () -> Unit,
    onAcceptTerms: () -> Unit,
    onRegenerate: () -> Unit,
    onRequest: (String) -> Unit,
    onConnectionAction: (String) -> Unit,
    onSettings: () -> Unit,
) {
    ScreenFrame(state) { narrow ->
        when (state.route) {
            BootRoute.Loading -> {
                Subtitle("Opening your One on One…")
                CircularProgressIndicator(color = OneTheme.colors.accentYou, trackColor = OneTheme.colors.border)
            }
            BootRoute.SignIn -> {
                Image(painterResource(R.mipmap.ic_launcher_foreground), null, Modifier.size(96.dp))
                Eyebrow("ONE")
                Title("One on One")
                ScreenSubtitle(stringResource(R.string.tagline))
                PrimaryButton("Continue with Google", onSignIn, enabled = !state.busy)
                LegalLinks()
            }
            BootRoute.Age -> AgeScreen(state.busy, onVerify)
            BootRoute.UnderAge -> {
                Eyebrow("SORRY")
                Title("You need to be 18 to use One on One.")
                ScreenSubtitle("Thanks for checking it out.")
                SecondaryButton("Sign out", onSignOut, enabled = !state.busy)
            }
            BootRoute.Consent -> ConsentScreen(state.busy, onAcceptTerms)
            BootRoute.Connect -> {
                Title("Your connection ID")
                ConnectionId(state.me?.connectionCode.orEmpty(), narrow)
                ScreenSubtitle("Give this ID to the person you want to connect with.")
                val context = LocalContext.current
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Copy", onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Connection ID", state.me?.connectionCode))
                    }, enabled = !state.busy && state.me != null)
                    SecondaryButton("Get a new ID", onRegenerate, enabled = !state.busy)
                }
                var code by rememberSaveable { mutableStateOf("") }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(OneTheme.spacing.sm8)) {
                    Text("Their connection ID", style = OneTextStyles.cardHint, color = OneTheme.colors.textDim)
                    OneTextField(code, { code = it.take(8) },
                        modifier = Modifier.widthIn(max = 240.dp).fillMaxWidth().semantics { contentDescription = "Their connection ID" },
                        textStyle = (if (narrow) MaterialTheme.typography.bodyLarge else OneTextStyles.subtitle)
                            .copy(textAlign = TextAlign.Center, letterSpacing = .08.em))
                }
                PrimaryButton("Connect", onClick = { onRequest(code) }, enabled = !state.busy)
                TextLink("Settings", onSettings, Modifier.defaultMinSize(minHeight = OneTheme.sizes.touch40))
            }
            BootRoute.Waiting -> {
                Title("Connection request sent")
                ScreenSubtitle("Waiting for ${state.connection?.otherConnectionCode.orEmpty()} to accept.")
                SecondaryButton("Cancel request", onClick = { onConnectionAction("cancel") }, enabled = !state.busy)
                TextLink("Settings", onSettings, Modifier.defaultMinSize(minHeight = OneTheme.sizes.touch40))
            }
            BootRoute.Request -> {
                Title("Connection request")
                ScreenSubtitle("${state.connection?.otherConnectionCode.orEmpty()} wants to connect with you.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("Accept", onClick = { onConnectionAction("accept") }, enabled = !state.busy)
                    DangerButton("Decline", onClick = { onConnectionAction("decline") }, enabled = !state.busy)
                }
                TextLink("Settings", onSettings, Modifier.defaultMinSize(minHeight = OneTheme.sizes.touch40))
            }
            BootRoute.Chat -> Unit
        }
    }
}

@Composable
internal fun ScreenFrame(state: AppState, screenKey: Any = state.route, content: @Composable ColumnScope.(Boolean) -> Unit) {
    val preview = LocalInspectionMode.current
    val enter = remember(screenKey) { Animatable(if (preview) 1f else 0f) }
    val easing = OneTheme.motion.standard
    LaunchedEffect(screenKey) { enter.animateTo(1f, tween(280, easing = easing)) }
    BoxWithConstraints(Modifier.fillMaxSize().background(OneTheme.colors.bg)) {
        val narrow = maxWidth < 480.dp
        val padding = if (narrow) OneTheme.spacing.lg16 else OneTheme.spacing.xl24
        Column(Modifier.fillMaxSize().graphicsLayer {
            alpha = enter.value
            translationY = 8.dp.toPx() * (1f - enter.value)
        }.verticalScroll(rememberScrollState()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            content(narrow)
            if (state.busy) LinearProgressIndicator(Modifier.widthIn(max = 320.dp).fillMaxWidth().height(2.dp),
                color = OneTheme.colors.accentYou, trackColor = OneTheme.colors.border)
            state.error?.let { Text(it, color = OneTheme.colors.danger, style = OneTextStyles.cardHint, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp).semantics { liveRegion = LiveRegionMode.Polite }) }
        }
    }
}

@Composable private fun Title(text: String) { ScreenTitle(text, Modifier.widthIn(max = 340.dp)) }

@Composable private fun ScreenSubtitle(text: String) { Subtitle(text, Modifier.widthIn(max = 320.dp)) }

@Composable
private fun ConnectionId(code: String, narrow: Boolean) {
    Surface(shape = RoundedCornerShape(OneTheme.radii.md10), color = OneTheme.colors.bgRaised,
        border = BorderStroke(1.dp, OneTheme.colors.border), shadowElevation = OneTheme.elevation.e1) {
        Text(code, Modifier.padding(horizontal = if (narrow) 18.dp else 28.dp, vertical = if (narrow) 12.dp else 16.dp),
            style = if (narrow) OneTextStyles.connectionId.copy(fontSize = 24.sp, lineHeight = 36.sp) else OneTextStyles.connectionId,
            color = OneTheme.colors.text, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SettingsScreen(state: AppState, onNotifications: () -> Unit, onBlocks: () -> Unit,
    onSignOut: () -> Unit, onDelete: () -> Unit, onBack: () -> Unit) {
    ScreenFrame(state, screenKey = "settings") {
        Title("Settings")
        Column(Modifier.widthIn(max = 320.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(OneTheme.spacing.sm8)) {
            Text("PREFERENCES", style = OneTextStyles.groupLabel, color = OneTheme.colors.muted)
            TextLink("Notifications and background settings", onNotifications, Modifier.defaultMinSize(minHeight = OneTheme.sizes.touch40))
            TextLink("Blocked accounts", onBlocks, Modifier.defaultMinSize(minHeight = OneTheme.sizes.touch40))
        }
        LegalLinks()
        Text("ACCOUNT", style = OneTextStyles.groupLabel, color = OneTheme.colors.muted)
        SecondaryButton("Sign out", onSignOut, enabled = !state.busy)
        DeleteAccountButton(state.busy, onDelete)
        TextLink("Back", onBack, Modifier.defaultMinSize(minHeight = OneTheme.sizes.touch40))
    }
}

@Composable
private fun BlocksScreen(state: AppState, onUnblock: (String) -> Unit, onBack: () -> Unit) {
    ScreenFrame(state, screenKey = "blocks") {
        Title("Blocked accounts")
        ScreenSubtitle("Unblocking allows a future connection request. It does not restore a conversation.")
        if (state.blocks.isEmpty()) ScreenSubtitle("No blocked accounts.")
        if (state.blocks.isNotEmpty()) Text("ACCOUNTS", style = OneTextStyles.groupLabel, color = OneTheme.colors.muted)
        state.blocks.forEach { block ->
            Column(Modifier.widthIn(max = 320.dp).fillMaxWidth().defaultMinSize(minHeight = OneTheme.sizes.touch40),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(OneTheme.spacing.sm8)) {
                Text(block.blockedUserId, style = OneTextStyles.subtitle, color = OneTheme.colors.text, textAlign = TextAlign.Center)
                SecondaryButton("Unblock", onClick = { onUnblock(block.blockedUserId) }, enabled = !state.busy)
            }
        }
        TextLink("Back", onBack, Modifier.defaultMinSize(minHeight = OneTheme.sizes.touch40))
    }
}

@Composable
private fun LegalLinks() {
    val uri = LocalUriHandler.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(OneTheme.spacing.xs4)) {
        TextLink("Terms", onClick = { uri.openUri("$LEGAL_ORIGIN/terms") }, Modifier.defaultMinSize(minHeight = OneTheme.sizes.touch40))
        TextLink("Privacy Policy", onClick = { uri.openUri("$LEGAL_ORIGIN/privacy") }, Modifier.defaultMinSize(minHeight = OneTheme.sizes.touch40))
    }
}

@Composable
private fun AgeScreen(busy: Boolean, onVerify: (LocalDate) -> Unit) {
    Eyebrow("ONE MORE THING")
    Title("What's your date of birth?")
    ScreenSubtitle("One on One is for adults only. Your date of birth stays on this device.")
    val context = LocalContext.current
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    SecondaryButton(selected ?: "Choose date of birth", onClick = {
        val today = LocalDate.now()
        val initial = selected?.let(LocalDate::parse) ?: today
        DatePickerDialog(context, { _, year, month, day ->
            selected = LocalDate.of(year, month + 1, day).toString()
        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply {
            datePicker.maxDate = System.currentTimeMillis()
            show()
        }
    }, enabled = !busy)
    PrimaryButton("Continue", onClick = { selected?.let { onVerify(LocalDate.parse(it)) } }, enabled = !busy && selected != null)
}

@Composable
private fun ConsentScreen(busy: Boolean, onAccept: () -> Unit) {
    Eyebrow("BEFORE YOU START")
    Title("A few ground rules.")
    ScreenSubtitle("One on One is a private space for two people. Harassment, hate, and any sexual content involving minors are not allowed and will be acted on.")
    var agreed by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.widthIn(max = 320.dp).fillMaxWidth().toggleable(agreed, role = Role.Checkbox, onValueChange = { agreed = it }).padding(OneTheme.spacing.sm8),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = agreed, onCheckedChange = null, colors = CheckboxDefaults.colors(
            checkedColor = OneTheme.colors.accentYou, uncheckedColor = OneTheme.colors.border, checkmarkColor = OneTheme.colors.onPrimary))
        Text("I am 18 or older and I agree to the Terms and Privacy Policy.", Modifier.padding(start = OneTheme.spacing.sm8),
            style = OneTextStyles.subtitle, color = OneTheme.colors.text)
    }
    LegalLinks()
    PrimaryButton("Agree and continue", onAccept, enabled = agreed && !busy)
}

@Composable
private fun DeleteAccountButton(busy: Boolean, onDelete: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var confirmation by rememberSaveable { mutableStateOf("") }
    DangerButton("Delete account", onClick = { open = true }, enabled = !busy)
    if (open) {
        BackHandler(enabled = busy) { }
        OneModal(onDismiss = { if (!busy) open = false }) {
            DeleteAccountContent(busy, confirmation, onConfirmation = { confirmation = it }, onDelete, onCancel = { open = false })
        }
    }
}

@Composable
private fun DeleteAccountContent(busy: Boolean, confirmation: String, onConfirmation: (String) -> Unit,
    onDelete: () -> Unit, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(OneTheme.spacing.md12)) {
        Text("Delete your account?", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = OneTheme.colors.text)
        Text("This permanently deletes your account, connection, and conversation for both of you. It can't be undone. Reports filed about you are kept for safety review.",
            style = OneTextStyles.cardHint, color = OneTheme.colors.textDim)
        Text("Type delete to confirm", style = OneTextStyles.cardHint, color = OneTheme.colors.textDim)
        OneTextField(confirmation, onConfirmation,
            Modifier.fillMaxWidth().semantics { contentDescription = "Type delete to confirm" },
            textStyle = MaterialTheme.typography.bodyLarge)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Cancel", onCancel, enabled = !busy)
            DangerButton("Delete account", onDelete, enabled = !busy && confirmation.trim().equals("delete", ignoreCase = true))
        }
    }
}

class ScreenThemePreviews : PreviewParameterProvider<Boolean> {
    override val values = sequenceOf(true, false)
    override fun getDisplayName(index: Int) = if (index == 0) "Dark" else "Light"
}

@Composable
private fun HomePreview(route: BootRoute, dark: Boolean, busy: Boolean = false, error: String? = null) {
    val connection = CurrentConnection("preview", "pending", "me", true, null, "K7F29PQ2", 0, 0, null, false, false, null, null, "off", "bubbles")
    OneOnOneTheme(darkTheme = dark) {
        HomeScreen(AppState(route = route, me = Me("me", "K7F29PQ2"), connection = connection, busy = busy, error = error),
            {}, {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(name = "Loading", widthDp = 390, heightDp = 844)
@Composable private fun LoadingPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) = HomePreview(BootRoute.Loading, dark)

@Preview(name = "Sign in", widthDp = 390, heightDp = 844)
@Composable private fun SignInPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) = HomePreview(BootRoute.SignIn, dark)

@Preview(name = "Age", widthDp = 390, heightDp = 844)
@Composable private fun AgePreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) = HomePreview(BootRoute.Age, dark)

@Preview(name = "Under age", widthDp = 390, heightDp = 844)
@Composable private fun UnderAgePreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) = HomePreview(BootRoute.UnderAge, dark)

@Preview(name = "Consent", widthDp = 390, heightDp = 844)
@Composable private fun ConsentPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) = HomePreview(BootRoute.Consent, dark)

@Preview(name = "Connect", widthDp = 390, heightDp = 844)
@Preview(name = "Connect tablet", widthDp = 600, heightDp = 960)
@Composable private fun ConnectPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) = HomePreview(BootRoute.Connect, dark)

@Preview(name = "Waiting", widthDp = 390, heightDp = 844)
@Composable private fun WaitingPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) = HomePreview(BootRoute.Waiting, dark)

@Preview(name = "Request", widthDp = 390, heightDp = 844)
@Composable private fun RequestPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) = HomePreview(BootRoute.Request, dark)

@Preview(name = "Busy and error", widthDp = 390, heightDp = 844)
@Composable private fun BusyErrorPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) =
    HomePreview(BootRoute.Connect, dark, busy = true, error = "Unable to connect. Please try again.")

@Preview(name = "Settings", widthDp = 390, heightDp = 844)
@Composable private fun SettingsPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) {
    OneOnOneTheme(darkTheme = dark) { SettingsScreen(AppState(), {}, {}, {}, {}, {}) }
}

@Preview(name = "Blocks", widthDp = 390, heightDp = 844)
@Composable private fun BlocksPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) {
    OneOnOneTheme(darkTheme = dark) { BlocksScreen(AppState(blocks = listOf(BlockedUser("blocked-account", ""))), {}, {}) }
}

@Preview(name = "Blocks empty", widthDp = 390, heightDp = 844)
@Composable private fun EmptyBlocksPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) {
    OneOnOneTheme(darkTheme = dark) { BlocksScreen(AppState(), {}, {}) }
}

@Preview(name = "Delete account", widthDp = 390, heightDp = 844)
@Composable private fun DeleteAccountPreview(@PreviewParameter(ScreenThemePreviews::class) dark: Boolean) {
    OneOnOneTheme(darkTheme = dark) {
        Box(Modifier.fillMaxSize().background(OneTheme.colors.bg)) {
            OneModal(onDismiss = {}) {
                DeleteAccountContent(false, "delete", {}, {}, {})
            }
        }
    }
}
