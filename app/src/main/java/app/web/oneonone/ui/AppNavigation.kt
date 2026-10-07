package app.web.oneonone.ui

import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.web.oneonone.R
import java.time.LocalDate

private const val LEGAL_ORIGIN = "https://one-on-one-mu.vercel.app"

@Composable
fun AppNavigation(viewModel: AppViewModel) {
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
    NavHost(nav, startDestination = "home", modifier = Modifier.safeDrawingPadding()) {
        composable("home") {
            ScreenFrame(state) {
                when (state.route) {
                    BootRoute.Loading -> { Text("Opening your One on One…"); CircularProgressIndicator() }
                    BootRoute.SignIn -> {
                        Image(painterResource(R.mipmap.ic_launcher), null, Modifier.size(96.dp))
                        Title("One on One")
                        Text(stringResource(R.string.tagline))
                        val context = LocalContext.current
                        Button(onClick = { viewModel.signIn(context) }, enabled = !state.busy) { Text("Continue with Google") }
                        LegalLinks()
                    }
                    BootRoute.Age -> AgeScreen(state.busy, viewModel::verifyAge)
                    BootRoute.UnderAge -> {
                        Title("You need to be 18 to use One on One.")
                        Text("Thanks for checking it out.")
                        TextButton(onClick = { viewModel.signOut() }, enabled = !state.busy) { Text("Sign out") }
                    }
                    BootRoute.Consent -> ConsentScreen(state.busy, viewModel::acceptTerms)
                    BootRoute.Connect -> {
                        Title("Your connection ID")
                        Text(state.me?.connectionCode.orEmpty(), style = MaterialTheme.typography.headlineLarge)
                        Text("Give this ID to the person you want to connect with.")
                        val context = LocalContext.current
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Connection ID", state.me?.connectionCode))
                            }, enabled = !state.busy && state.me != null) { Text("Copy") }
                            OutlinedButton(onClick = { viewModel.regenerate() }, enabled = !state.busy) { Text("Get a new ID") }
                        }
                        var code by rememberSaveable { mutableStateOf("") }
                        OutlinedTextField(code, { code = it.take(8) }, label = { Text("Their connection ID") }, singleLine = true)
                        Button(onClick = { viewModel.request(code) }, enabled = !state.busy) { Text("Connect") }
                        TextButton(onClick = { nav.navigate("settings") }) { Text("Settings") }
                    }
                    BootRoute.Waiting -> {
                        Title("Connection request sent")
                        Text("Waiting for ${state.connection?.otherConnectionCode.orEmpty()} to accept.")
                        Button(onClick = { viewModel.connectionAction("cancel") }, enabled = !state.busy) { Text("Cancel request") }
                        TextButton(onClick = { nav.navigate("settings") }) { Text("Settings") }
                    }
                    BootRoute.Request -> {
                        Title("Connection request")
                        Text("${state.connection?.otherConnectionCode.orEmpty()} wants to connect with you.")
                        Button(onClick = { viewModel.connectionAction("accept") }, enabled = !state.busy) { Text("Accept") }
                        OutlinedButton(onClick = { viewModel.connectionAction("decline") }, enabled = !state.busy) { Text("Decline") }
                        TextButton(onClick = { nav.navigate("settings") }) { Text("Settings") }
                    }
                    BootRoute.Chat -> {
                        Title(state.connection?.otherNickname ?: "Your One on One")
                        Text("Your connection is ready. Messaging arrives in A2.")
                        TextButton(onClick = { nav.navigate("settings") }) { Text("Settings") }
                    }
                }
            }
        }
        composable("settings") {
            ScreenFrame(state) {
                Title("Settings")
                TextButton(onClick = { nav.navigate("blocks") }) { Text("Blocked accounts") }
                LegalLinks()
                OutlinedButton(onClick = { viewModel.signOut() }, enabled = !state.busy) { Text("Sign out") }
                DeleteAccountButton(state.busy, viewModel::deleteAccount)
                TextButton(onClick = { nav.popBackStack() }) { Text("Back") }
            }
        }
        composable("blocks") {
            LaunchedEffect(Unit) { viewModel.loadBlocks() }
            ScreenFrame(state) {
                Title("Blocked accounts")
                Text("Unblocking allows a future connection request. It does not restore a conversation.")
                if (state.blocks.isEmpty()) Text("No blocked accounts.")
                state.blocks.forEach { block ->
                    Text(block.blockedUserId)
                    OutlinedButton(onClick = { viewModel.unblock(block.blockedUserId) }, enabled = !state.busy) { Text("Unblock") }
                }
                TextButton(onClick = { nav.popBackStack() }) { Text("Back") }
            }
        }
    }
}

@Composable
private fun ScreenFrame(state: AppState, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        content()
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    }
}

@Composable private fun Title(text: String) { Text(text, style = MaterialTheme.typography.headlineMedium) }

@Composable
private fun LegalLinks() {
    val uri = LocalUriHandler.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { uri.openUri("$LEGAL_ORIGIN/terms") }) { Text("Terms") }
        TextButton(onClick = { uri.openUri("$LEGAL_ORIGIN/privacy") }) { Text("Privacy Policy") }
    }
}

@Composable
private fun AgeScreen(busy: Boolean, onVerify: (LocalDate) -> Unit) {
    Title("What's your date of birth?")
    Text("One on One is for adults only. Your date of birth stays on this device.")
    val context = LocalContext.current
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    OutlinedButton(onClick = {
        val today = LocalDate.now()
        val initial = selected?.let(LocalDate::parse) ?: today
        DatePickerDialog(context, { _, year, month, day ->
            selected = LocalDate.of(year, month + 1, day).toString()
        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply {
            datePicker.maxDate = System.currentTimeMillis()
            show()
        }
    }, enabled = !busy) { Text(selected ?: "Choose date of birth") }
    Button(onClick = { selected?.let { onVerify(LocalDate.parse(it)) } }, enabled = !busy && selected != null) { Text("Continue") }
}

@Composable
private fun ConsentScreen(busy: Boolean, onAccept: () -> Unit) {
    Title("A few ground rules.")
    Text("One on One is a private space for two people. Harassment, hate, and any sexual content involving minors are not allowed and will be acted on.")
    var agreed by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().toggleable(agreed, role = Role.Checkbox, onValueChange = { agreed = it }).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = agreed, onCheckedChange = null)
        Text("I am 18 or older and I agree to the Terms and Privacy Policy.", Modifier.padding(start = 8.dp))
    }
    LegalLinks()
    Button(onClick = onAccept, enabled = agreed && !busy) { Text("Agree and continue") }
}

@Composable
private fun DeleteAccountButton(busy: Boolean, onDelete: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var confirmation by rememberSaveable { mutableStateOf("") }
    TextButton(onClick = { open = true }, enabled = !busy) { Text("Delete account", color = MaterialTheme.colorScheme.error) }
    if (open) {
        BackHandler(enabled = busy) { }
        AlertDialog(onDismissRequest = { if (!busy) open = false }, title = { Text("Delete your account?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("This permanently deletes your account, connection, and conversation for both of you. It can't be undone. Reports filed about you are kept for safety review.")
                OutlinedTextField(confirmation, { confirmation = it }, label = { Text("Type delete to confirm") }, singleLine = true)
            } },
            confirmButton = { TextButton(onClick = onDelete,
                enabled = !busy && confirmation.trim().equals("delete", ignoreCase = true)) { Text("Delete account") } },
            dismissButton = { TextButton(onClick = { open = false }, enabled = !busy) { Text("Cancel") } })
    }
}
