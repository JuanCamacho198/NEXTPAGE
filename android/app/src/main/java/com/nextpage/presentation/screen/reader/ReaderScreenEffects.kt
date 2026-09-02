package com.nextpage.presentation.screen.reader

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.nextpage.presentation.viewmodel.ReaderViewModel
import kotlinx.coroutines.delay

private const val FULLSCREEN_AUTOHIDE_MS = 3_000L

/**
 * Hosts 5 reader lifecycle effects + rememberUpdatedState bridge.
 * Keeps WindowInsetsController + auto-hide timer isolated from overlay/content hosts.
 */
@Composable
fun ReaderScreenEffects(
    isFullscreen: Boolean,
    selectedBookId: String,
    bookFilePath: String?,
    bookFormat: String,
    lastInteractionAt: Long,
    currentChapterIndex: Int,
    currentPdfPage: Int,
    lastBookmarkTrigger: Long,
    viewModel: ReaderViewModel,
    view: View,
    controlsVisible: Boolean,
    onControlsVisibleChange: (Boolean) -> Unit,
    onLastInteractionChange: (Long) -> Unit,
    onBookmarkRibbonVisibleChange: (Boolean) -> Unit,
    onShowChrome: () -> Unit
) {
    val context = LocalContext.current

    @Suppress("UNUSED_VARIABLE")
    val currentOnShowChrome by rememberUpdatedState(onShowChrome)

    DisposableEffect(Unit) {
        val activity = context as? Activity
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose {
            activity?.let { it.requestedOrientation = originalOrientation }
        }
    }

    DisposableEffect(isFullscreen) {
        if (isFullscreen) {
            view.windowInsetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            view.windowInsetsController?.let { controller ->
                controller.show(android.view.WindowInsets.Type.systemBars())
            }
        }
        onDispose {
            view.windowInsetsController?.let { controller ->
                controller.show(android.view.WindowInsets.Type.systemBars())
            }
        }
    }

    DisposableEffect(selectedBookId) {
        viewModel.onReaderOpened()
        onDispose { viewModel.onReaderPaused() }
    }

    LaunchedEffect(selectedBookId, bookFilePath, bookFormat) {
        if (selectedBookId.isNotBlank() && bookFilePath != null) {
            viewModel.loadBook(selectedBookId, bookFilePath, bookFormat)
        }
    }

    LaunchedEffect(isFullscreen, lastInteractionAt) {
        if (!isFullscreen) return@LaunchedEffect
        val snapshot = lastInteractionAt
        delay(FULLSCREEN_AUTOHIDE_MS)
        if (lastInteractionAt == snapshot) {
            val currentFullscreen = isFullscreen
            if (currentFullscreen) {
                onControlsVisibleChange(false)
            }
        }
    }

    LaunchedEffect(currentChapterIndex, currentPdfPage) {
        if (isFullscreen) {
            onLastInteractionChange(SystemClock.elapsedRealtime())
        }
    }

    LaunchedEffect(lastBookmarkTrigger) {
        if (lastBookmarkTrigger != 0L) {
            onBookmarkRibbonVisibleChange(true)
            delay(2_200L)
            onBookmarkRibbonVisibleChange(false)
        }
    }
}
