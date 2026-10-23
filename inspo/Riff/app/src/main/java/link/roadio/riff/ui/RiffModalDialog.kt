@file:Suppress("FunctionName")

package link.roadio.riff.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal data class RiffDialogAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val onLongClick: (() -> Unit)? = null,
    val onLongClickLabel: String? = null,
)

/** All local modals, including pre-admission recovery, use this sole window and action renderer. */
@Composable
internal fun RiffModalDialog(
    title: String,
    confirmation: RiffDialogAction,
    cancellation: RiffDialogAction? = null,
    message: String? = null,
    errorMessage: String? = null,
    dismissible: Boolean = false,
    testTag: String = "riff:dialog",
    config: UiConfig = LocalRiffConfig.current,
    palette: UiPalette = LocalRiffPalette.current,
    content: @Composable () -> Unit = {},
) {
    require(!dismissible || cancellation != null)
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val canDismiss = dismissible && cancellation?.enabled == true
    val dismiss = { if (canDismiss) cancellation.onClick() }
    // AI-INTENT: Never copy a Dialog shell or neutralize root density/fontScale in a new window.
    // https://wiki.271.pics/Riff/LATEST/ui-modal-dialogs#permanent-shared-component-rule
    Dialog(
        onDismissRequest = dismiss,
        properties =
            DialogProperties(
                dismissOnBackPress = canDismiss,
                dismissOnClickOutside = canDismiss,
                usePlatformDefaultWidth = false,
            ),
    ) {
        CompositionLocalProvider(
            LocalDensity provides density,
            LocalLayoutDirection provides direction,
            LocalRiffConfig provides config,
            LocalRiffPalette provides palette,
            LocalRiffChromeText provides true,
        ) {
            val gap = config.float("geometry.targetSpacingMin").dp
            val shape = RoundedCornerShape(config.float("geometry.cornerRadius").dp)
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(dismissible, cancellation) { detectTapGestures { dismiss() } }
                    .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
                    .imePadding()
                    .padding(config.float("geometry.screenEdgeMargin").dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .heightIn(max = maxHeight)
                        .pointerInput(Unit) { detectTapGestures { } }
                        .clip(shape)
                        .background(Color(palette.surface))
                        .border(config.float("geometry.nowPlayingBorder").dp, Color(palette.primary), shape)
                        .padding(BUTTON_CONTENT_PADDING_DP.dp)
                        .testTag(testTag),
                ) {
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        RiffModalText(title)
                        Spacer(Modifier.height(gap))
                        message?.let { RiffModalText(it) }
                        content()
                        errorMessage?.let { message ->
                            Spacer(Modifier.height(gap))
                            RiffText(
                                message,
                                BREADCRUMB_TEXT_SP,
                                palette.text,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .semantics {
                                            liveRegion = LiveRegionMode.Polite
                                            error(message)
                                        }.testTag("riff:dialog-error"),
                            )
                        }
                    }
                    Spacer(Modifier.height(gap))
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            ModalAction("ok", confirmation)
                            cancellation?.let {
                                Spacer(Modifier.width(gap))
                                ModalAction("cancel", it)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModalAction(
    icon: String,
    action: RiffDialogAction,
) {
    val config = LocalRiffConfig.current
    RiffIconButton(
        icon,
        action.label,
        Modifier.size(config.float("geometry.navTarget").dp),
        enabled = action.enabled,
        wide = config.wide,
        onClick = action.onClick,
        onLongClick = action.onLongClick,
        onLongClickLabel = action.onLongClickLabel,
    )
}

@Composable
internal fun riffModalTextStyle(): TextStyle = riffTextStyle(BREADCRUMB_TEXT_SP, LocalRiffPalette.current.text, chrome = true)

@Composable
internal fun RiffModalText(
    value: String,
    muted: Boolean = false,
) {
    val palette = LocalRiffPalette.current
    RiffText(value, BREADCRUMB_TEXT_SP, if (muted) palette.muted else palette.text, modifier = Modifier.fillMaxWidth())
}
