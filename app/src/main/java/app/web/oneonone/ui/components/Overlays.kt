package app.web.oneonone.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import app.web.oneonone.R
import app.web.oneonone.ui.theme.OneTextStyles
import app.web.oneonone.ui.theme.OneTheme

enum class ModalPlacement { Top, Bottom }

/**
 * .modal-overlay + .modal-panel: scrim, bg-raised panel, radius 10, padding 46/22/22/22, max 620, X at top-right 12dp.
 * [ModalPlacement.Bottom] is a full-width sheet: top corners rounded, flush with the bottom edge, sliding up.
 */
@Composable
fun OneModal(onDismiss: () -> Unit, modifier: Modifier = Modifier, placement: ModalPlacement = ModalPlacement.Top, content: @Composable () -> Unit) {
    val c = OneTheme.colors
    val bottom = placement == ModalPlacement.Bottom
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // The platform dims behind dialogs; the web scrim (rgba 0,0,0,.6) is the only dim we want.
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f) }
        val scrim = Modifier
            .fillMaxSize()
            .background(c.scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
        val shown = remember { MutableTransitionState(!bottom) }.apply { targetState = true }
        val panel = @Composable {
            AnimatedVisibility(
                visibleState = shown,
                enter = if (bottom) slideInVertically(tween(OneTheme.motion.base200, easing = OneTheme.motion.standard)) { it } else EnterTransition.None,
            ) {
                Surface(
                    modifier = modifier
                        .widthIn(max = if (bottom) Dp.Unspecified else 620.dp)
                        .fillMaxWidth()
                        // Swallow taps so they never reach the scrim and dismiss the dialog.
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
                    shape = if (bottom) RoundedCornerShape(topStart = OneTheme.radii.md10, topEnd = OneTheme.radii.md10) else RoundedCornerShape(OneTheme.radii.md10),
                    color = c.bgRaised,
                    border = BorderStroke(1.dp, c.border),
                ) {
                    Box {
                        Column(
                            Modifier
                                .then(if (bottom) Modifier.navigationBarsPadding() else Modifier)
                                .padding(PaddingValues(start = 22.dp, top = if (bottom) 28.dp else 46.dp, end = 22.dp, bottom = 22.dp)),
                        ) { content() }
                        OneIconButton(
                            icon = R.drawable.ic_x,
                            contentDescription = stringResource(R.string.close),
                            onClick = onDismiss,
                            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                        )
                    }
                }
            }
        }
        if (bottom) Box(scrim.imePadding(), contentAlignment = Alignment.BottomCenter) { panel() }
        else Box(scrim.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), contentAlignment = Alignment.TopCenter) { panel() }
    }
}

/** .menu: bg-raised, 1px border, radius 8 (bubble); items are 40dp / 13sp. */
@Composable
fun OneMenu(expanded: Boolean, onDismissRequest: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = OneTheme.colors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = c.bgRaised,
        shape = RoundedCornerShape(OneTheme.radii.bubble8),
        border = BorderStroke(1.dp, c.border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) { content() }
}

/** .menu__item (and --danger / :disabled). */
@Composable
fun OneMenuItem(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false, enabled: Boolean = true) {
    val c = OneTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minWidth = 200.dp, minHeight = 40.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text,
            style = OneTextStyles.menuItem,
            color = when {
                !enabled -> c.muted
                danger -> c.danger
                else -> c.text
            },
        )
    }
}
