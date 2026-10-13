package app.web.oneonone.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.web.oneonone.ui.chat.AppearanceDialog
import app.web.oneonone.ui.chat.ExportDialog
import app.web.oneonone.R
import app.web.oneonone.data.api.CurrentConnection
import app.web.oneonone.data.api.Me
import app.web.oneonone.ui.components.*
import app.web.oneonone.ui.theme.OneOnOneTheme
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneTheme
import app.web.oneonone.ui.theme.OneColors
import app.web.oneonone.ui.chat.ChatScreen
import app.web.oneonone.ui.chat.ChatViewModel
import app.web.oneonone.ui.chat.FeatureViewModel
import app.web.oneonone.push.PushRegistration
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

internal const val LEGAL_ORIGIN = "https://one-on-one-mu.vercel.app"

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
        if (state.needsNotificationOnboarding && state.route == BootRoute.Chat) {
            nav.navigate("notifications?first=true") { launchSingleTop = true }
            viewModel.onboardingShown()
        }
    }
    NavHost(nav, startDestination = "home") {
        composable("home") {
            val connection = state.connection
            if (state.route == BootRoute.Chat && connection != null) {
                key(connection.myUserId, connection.id) {
                    ChatScreen(connection, chatViewModel, featureViewModel, onSettings = { nav.navigate("settings") }, onAccount = { nav.navigate("account") }, onRefresh = { viewModel.refresh() }, accountError = state.error)
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
            val connection = state.connection?.takeIf { state.route == BootRoute.Chat }
            var appearance by rememberSaveable { mutableStateOf(false) }
            var export by rememberSaveable { mutableStateOf(false) }
            val dark = OneTheme.colors.bg == OneColors.Dark.bg
            val theme = if (dark) "Dark" else "Light"
            val summary = if (connection == null) theme else
                "$theme · " + when (connection.wallpaper) { "love" -> "Love wallpaper"; "samurai" -> "Samurai wallpaper"; else -> "No wallpaper" }
            SettingsScreen(state, chat = connection != null, appearanceSummary = summary,
                onAppearance = { appearance = true }, onExport = { export = true },
                onNotifications = { nav.navigate("notifications") }, onAccount = { nav.navigate("account") }, onBack = { nav.popBackStack() })
            if (appearance) AppearanceDialog(connection, featureViewModel, { viewModel.refresh() }) { appearance = false }
            if (export && connection != null) ExportDialog(connection, featureViewModel) { export = false }
        }
        composable("account") {
            LaunchedEffect(Unit) { viewModel.loadBlocks() }
            AccountScreen(state, onBlocks = { nav.navigate("blocks") }, onSignOut = { viewModel.signOut() },
                onDelete = viewModel::deleteAccount, onBack = { nav.popBackStack() })
        }
        composable("blocks") {
            LaunchedEffect(Unit) { viewModel.loadBlocks() }
            BlocksScreen(state, onUnblock = { viewModel.unblock(it) }, onBack = { nav.popBackStack() })
        }
        composable("notifications?first={first}", arguments = listOf(navArgument("first") { type = NavType.BoolType; defaultValue = false })) { entry ->
            NotificationOnboarding(pushRegistration, firstRun = entry.arguments?.getBoolean("first") == true, onDone = { nav.popBackStack() })
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
    if (state.route == BootRoute.Loading) {
        Box(Modifier.fillMaxSize().background(OneColors.Dark.bg), contentAlignment = Alignment.Center) {
            // Same vector at the system splash icon's 240dp, so the hand-off doesn't jump.
            Image(painterResource(R.drawable.splash_logo), null, Modifier.size(240.dp))
        }
        return
    }
    // Connect has two steps: share your ID, or enter theirs. Resets whenever the route changes.
    var entering by rememberSaveable(state.route) { mutableStateOf(false) }
    BackHandler(entering) { entering = false }
    ScreenFrame(state, screenKey = state.route to entering) { _ ->
        when (state.route) {
            BootRoute.Loading -> Unit
            BootRoute.SignIn -> {
                // The splash vector has empty space above and below the circles; crop it so the stack stays tight.
                Image(painterResource(R.drawable.splash_logo), null, Modifier.size(width = 128.dp, height = 64.dp), contentScale = ContentScale.Crop)
                Eyebrow("ONE")
                Title("One on One")
                ScreenSubtitle(stringResource(R.string.tagline))
                PrimaryButton("Continue with Google", onSignIn, enabled = !state.busy)
                LegalLinks()
            }
            BootRoute.Age -> AgeScreen(state.busy, onVerify)
            BootRoute.UnderAge -> {
                Title("You need to be 18 to use One on One.")
                ScreenSubtitle("Thanks for checking it out.")
                SecondaryButton("Sign out", onSignOut, enabled = !state.busy)
            }
            BootRoute.Consent -> ConsentScreen(state.busy, onAcceptTerms)
            BootRoute.Connect -> if (entering) {
                EnterTheirId(state.busy, onRequest, onBack = { entering = false })
            } else {
                Title("Your connection ID")
                ScreenSubtitle("Share this ID to connect.")
                ConnectionId(state.me?.connectionCode.orEmpty(), state.busy, onRegenerate)
                PrimaryButton("I have their ID", onClick = { entering = true }, enabled = !state.busy)
                FooterLinks(state.busy, onSettings, onSignOut)
            }
            BootRoute.Waiting -> {
                Title("Request sent")
                ScreenSubtitle("Waiting for ${state.connection?.otherConnectionCode.orEmpty()} to accept.")
                SecondaryButton("Cancel request", onClick = { onConnectionAction("cancel") }, modifier = BlockWidth, enabled = !state.busy)
                FooterLinks(state.busy, onSettings, onSignOut)
            }
            BootRoute.Request -> {
                Title("Connection request")
                ScreenSubtitle("${state.connection?.otherConnectionCode.orEmpty()} wants to connect with you.")
                Row(BlockWidth, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DangerButton("Decline", onClick = { onConnectionAction("decline") }, modifier = Modifier.weight(1f), enabled = !state.busy)
                    PrimaryButton("Accept", onClick = { onConnectionAction("accept") }, modifier = Modifier.weight(1f), enabled = !state.busy)
                }
                FooterLinks(state.busy, onSettings, onSignOut)
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
    BoxWithConstraints(Modifier.fillMaxSize().background(OneTheme.colors.bg).safeDrawingPadding()) {
        val narrow = maxWidth < 480.dp
        val padding = if (narrow) OneTheme.spacing.lg16 else OneTheme.spacing.xl24
        Column(Modifier.fillMaxWidth().graphicsLayer {
            alpha = enter.value
            translationY = 8.dp.toPx() * (1f - enter.value)
        }.verticalScroll(rememberScrollState()).heightIn(min = this@BoxWithConstraints.maxHeight).padding(padding),
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

/** Shared width for pre-chat cards and buttons so every block lines up. */
private val BlockWidth = Modifier.widthIn(max = 340.dp).fillMaxWidth()

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConnectionId(code: String, busy: Boolean, onRegenerate: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1_500); copied = false } }
    val copy = {
        if (code.isNotEmpty()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Connection ID", code))
            copied = true
        }
    }
    Column(BlockWidth, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(OneTheme.radii.md10), color = OneTheme.colors.bgRaised,
            border = BorderStroke(1.dp, OneTheme.colors.border), shadowElevation = OneTheme.elevation.e1) {
            Column(Modifier.combinedClickable(enabled = code.isNotEmpty(), onClickLabel = "Copy connection ID", onLongClickLabel = "Copy connection ID",
                onClick = copy, onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); copy() })
                .padding(vertical = 18.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(code, style = OneTextStyles.connectionId, color = OneTheme.colors.text, textAlign = TextAlign.Center, maxLines = 1)
                Text(if (copied) "Copied" else "Tap or hold to copy", style = OneTextStyles.cardHint.copy(fontSize = 12.sp),
                    color = if (copied) OneTheme.colors.accentYou else OneTheme.colors.muted,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Copy", onClick = copy, modifier = Modifier.weight(1f), enabled = !busy && code.isNotEmpty())
            SecondaryButton("New ID", onClick = onRegenerate, modifier = Modifier.weight(1f), enabled = !busy)
        }
    }
}

@Composable
private fun EnterTheirId(busy: Boolean, onRequest: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var code by rememberSaveable { mutableStateOf("") }
    val accept = { text: String -> code = text.filter(Char::isLetterOrDigit).uppercase().take(8) }
    Title("Enter their ID")
    ScreenSubtitle("Paste the ID they shared with you.")
    OneTextField(code, accept, placeholder = "8-character ID",
        modifier = BlockWidth.semantics { contentDescription = "Their connection ID" },
        textStyle = OneTextStyles.connectionId.copy(fontSize = 22.sp, lineHeight = 30.sp, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { if (!busy && code.length == 8) onRequest(code) }))
    Row(BlockWidth, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SecondaryButton("Paste", onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.let { accept(it.toString()) }
        }, modifier = Modifier.weight(1f), enabled = !busy)
        PrimaryButton("Connect", onClick = { onRequest(code) }, modifier = Modifier.weight(1f), enabled = !busy && code.length == 8)
    }
    TextLink("Back", onBack, Modifier.defaultMinSize(minHeight = OneTheme.sizes.touch40).wrapContentHeight())
}

/** Pre-chat footer: sign out is one tap; Settings still holds blocks, legal and account deletion. */
@Composable
private fun FooterLinks(busy: Boolean, onSettings: () -> Unit, onSignOut: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(OneTheme.spacing.sm8), verticalAlignment = Alignment.CenterVertically) {
        TextLink("Settings", onSettings, Modifier.defaultMinSize(minHeight = OneTheme.sizes.touch40).wrapContentHeight())
        Text("·", color = OneTheme.colors.muted, style = OneTextStyles.cardHint)
        TextLink("Sign out", { if (!busy) onSignOut() }, Modifier.defaultMinSize(minHeight = OneTheme.sizes.touch40).wrapContentHeight())
    }
}

@Composable
private fun LegalLinks() {
    val uri = LocalUriHandler.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(OneTheme.spacing.xs4)) {
        // .screen__legal: Privacy Policy, Terms, Child Safety; 12sp text-dim, gap 4/14.
        listOf("Privacy Policy" to "privacy", "Terms" to "terms", "Child Safety" to "child-safety").forEach { (label, path) ->
            Text(label, Modifier.defaultMinSize(minHeight = OneTheme.sizes.touch40).wrapContentHeight()
                .clickable(role = Role.Button) { uri.openUri("$LEGAL_ORIGIN/$path") },
                style = OneTextStyles.cardHint.copy(fontSize = 12.sp), color = OneTheme.colors.textDim)
        }
    }
}

@Composable
private fun AgeScreen(busy: Boolean, onVerify: (LocalDate) -> Unit) {
    Title("What's your date of birth?")
    ScreenSubtitle("One on One is for adults only. Your date of birth stays on this device.")
    // Neutral age screen: starts on today, and Continue waits until the wheels have been moved.
    val today = remember { LocalDate.now() }
    var year by rememberSaveable { mutableIntStateOf(today.year) }
    var month by rememberSaveable { mutableIntStateOf(today.monthValue) }
    var day by rememberSaveable { mutableIntStateOf(today.dayOfMonth) }
    var touched by rememberSaveable { mutableStateOf(false) }
    val days = YearMonth.of(year, month).lengthOfMonth()
    val safeDay = day.coerceAtMost(days)
    val months = remember { Month.entries.map { it.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()) } }
    val years = remember { (1900..today.year).map(Int::toString) }
    Box(BlockWidth, contentAlignment = Alignment.Center) {
        // One selection band across all three wheels, like iOS.
        Box(Modifier.fillMaxWidth().height(WheelRow).background(OneTheme.colors.bgRaised, RoundedCornerShape(OneTheme.radii.md10))
            .border(1.dp, OneTheme.colors.border, RoundedCornerShape(OneTheme.radii.md10)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OneTheme.spacing.sm8)) {
            Wheel((1..days).map(Int::toString), safeDay - 1, "Day", Modifier.weight(1f)) { day = it + 1; touched = true }
            Wheel(months, month - 1, "Month", Modifier.weight(1.3f)) { month = it + 1; touched = true }
            Wheel(years, year - 1900, "Year", Modifier.weight(1.3f)) { year = it + 1900; touched = true }
        }
    }
    PrimaryButton("Continue", onClick = { onVerify(LocalDate.of(year, month, safeDay)) }, enabled = !busy && touched)
}

private val WheelRow = 40.dp
private const val WheelRows = 5

/** iOS-style scroll wheel (look after ty0x2333/WheelPicker): rows curve away and fade, a tick on each step, snaps to the centre row. */
@Composable
private fun Wheel(items: List<String>, index: Int, label: String, modifier: Modifier = Modifier, onIndex: (Int) -> Unit) {
    val c = OneTheme.colors
    val state = rememberLazyListState(index)
    val scope = rememberCoroutineScope()
    val rowPx = with(LocalDensity.current) { WheelRow.toPx() }
    val centre by remember(items.size) { derivedStateOf {
        (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > rowPx / 2) 1 else 0).coerceIn(0, items.lastIndex)
    } }
    val latest by rememberUpdatedState(onIndex)
    LaunchedEffect(state) { snapshotFlow { state.isScrollInProgress }.drop(1).filter { !it }.collect { latest(centre) } }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state) { snapshotFlow { centre }.drop(1).collect { haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) } }
    // A shorter month can push the day off the end; keep the wheel on the clamped value.
    LaunchedEffect(index, items.size) { if (!state.isScrollInProgress && centre != index) state.scrollToItem(index) }
    Box(modifier.height(WheelRow * WheelRows), contentAlignment = Alignment.Center) {
        LazyColumn(Modifier.fillMaxSize().semantics { contentDescription = "$label, ${items.getOrNull(centre).orEmpty()}" },
            state = state, flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = PaddingValues(vertical = WheelRow * (WheelRows / 2))) {
            itemsIndexed(items) { i, text ->
                Text(text, Modifier.fillMaxWidth().height(WheelRow).clickable { scope.launch { state.animateScrollToItem(i) } }
                    .wrapContentHeight().graphicsLayer {
                        // Continuous distance from the centre row drives the drum curve and fade.
                        val f = i - (state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / rowPx)
                        rotationX = (-f * 22f).coerceIn(-75f, 75f)
                        alpha = (1f - abs(f) * .3f).coerceAtLeast(.15f)
                        val scale = 1f + (1f - abs(f)).coerceAtLeast(0f) * .12f
                        scaleX = scale; scaleY = scale
                        cameraDistance = 12f * density
                    },
                    // Style is constant so scrolling only redraws (graphicsLayer), never recomposes or relayouts rows.
                    style = OneTextStyles.subtitle.copy(fontSize = 17.sp), color = c.text, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
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
internal fun DeleteAccountContent(busy: Boolean, confirmation: String, onConfirmation: (String) -> Unit,
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

@Preview(name = "Loading — logo only", widthDp = 390, heightDp = 844)
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
