package nl.giejay.android.tv.immich.slider

import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.findViewTreeLifecycleOwner

/**
 * A back handler for something shown on top of the viewer (info panel, live photo). Back on newer Android versions
 * (predictive back) reaches the app through the activity's back dispatcher instead of as a key event, so the
 * overlays need this next to their key handling. The callback starts disabled; the overlay enables it while it is
 * open and [onBack] closes it. It is bound to the view's lifecycle: removed with the viewer, asked only while the
 * viewer is on screen, and asked before the navigation's own back handling since it is added last.
 */
internal fun registerViewerBackCallback(rootView: View, onBack: () -> Unit): OnBackPressedCallback? {
    var ctx: Context? = rootView.context
    while (ctx is ContextWrapper && ctx !is ComponentActivity) ctx = ctx.baseContext
    val activity = ctx as? ComponentActivity ?: return null
    val callback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            isEnabled = false
            onBack()
        }
    }
    val owner = rootView.findViewTreeLifecycleOwner()
    if (owner != null) activity.onBackPressedDispatcher.addCallback(owner, callback)
    else activity.onBackPressedDispatcher.addCallback(callback)
    return callback
}
