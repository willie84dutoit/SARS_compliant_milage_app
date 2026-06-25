package com.mileagetracker.app.ui.common

import android.app.Activity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import timber.log.Timber

private const val SCAFFOLD_LOG_TAG = "MT-UI"

/**
 * Shared Scaffold wrapper used by every screen in the app (T-024 "never trapped" UX contract).
 *
 * Every screen must give the user a way to dismiss the app to the background without destroying
 * state. [MileageTrackerScaffold] satisfies that contract by placing a "send to background"
 * [IconButton] (downward-arrow icon) as the default trailing action in the [TopAppBar].
 *
 * Callers may supply:
 * - [screenTitle] — displayed in the [TopAppBar] title slot.
 * - [navigationIconContent] — optional composable for a leading icon (e.g. back arrow). Null
 *   means no leading icon is rendered.
 * - [trailingActionsContent] — optional composable that REPLACES the default "send to background"
 *   button. Pass null (the default) to keep the standard behaviour. Screens that need additional
 *   trailing actions should include their own send-to-background button alongside them.
 * - [screenContent] — the screen body; receives the [PaddingValues] from [Scaffold] and is
 *   responsible for applying them via [androidx.compose.ui.Modifier.padding].
 *
 * Activity access for [Activity.moveTaskToBack]: obtained from [LocalContext] inside the
 * onClick lambda. The lambda is short-lived and never stored, so there is no Activity leak.
 * The `?.` safe-call guards Compose Preview environments where [LocalContext] is not an
 * [Activity].
 *
 * Pure UI — no ViewModel, no repository access.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MileageTrackerScaffold(
    screenTitle: String,
    navigationIconContent: (@Composable () -> Unit)? = null,
    trailingActionsContent: (@Composable () -> Unit)? = null,
    screenContent: @Composable (PaddingValues) -> Unit,
) {
    val activityContext = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screenTitle) },
                navigationIcon = {
                    navigationIconContent?.invoke()
                },
                actions = {
                    if (trailingActionsContent != null) {
                        trailingActionsContent()
                    } else {
                        SendToBackgroundButton(
                            screenName = screenTitle,
                            onSendToBackground = {
                                (activityContext as? Activity)?.moveTaskToBack(true)
                            },
                        )
                    }
                },
            )
        },
        content = screenContent,
    )
}

/**
 * The default trailing "send to background" icon button rendered by [MileageTrackerScaffold].
 * Extracted as a named composable so it can be reused by callers that supply a custom
 * [trailingActionsContent] but still want the standard background-send button present.
 *
 * @param screenName Used only for the Timber log line — pass the screen's display title.
 * @param onSendToBackground Invoked when the user taps the button; caller supplies the
 *   [Activity.moveTaskToBack] call so this composable stays preview-safe.
 */
@Composable
fun SendToBackgroundButton(
    screenName: String,
    onSendToBackground: () -> Unit,
) {
    IconButton(
        onClick = {
            Timber.tag(SCAFFOLD_LOG_TAG).i(
                "MileageTrackerScaffold: send-to-background tapped from screen=\"%s\"",
                screenName,
            )
            onSendToBackground()
        },
    ) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = "Send app to background",
        )
    }
}
