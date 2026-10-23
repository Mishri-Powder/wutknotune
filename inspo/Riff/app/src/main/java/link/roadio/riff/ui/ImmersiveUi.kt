package link.roadio.riff.ui

import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController

/** Applies Riff's full-screen policy to either the activity or an overlay window. */
internal fun Window.applyRiffImmersiveMode() {
    setDecorFitsSystemWindows(false)
    decorView.windowInsetsController?.apply {
        hide(WindowInsets.Type.systemBars())
        systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
