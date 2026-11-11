package nl.giejay.android.tv.immich.slider

import android.view.KeyEvent
import androidx.activity.OnBackPressedCallback
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.constraintlayout.widget.ConstraintLayout
import arrow.core.Some
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.ApiClient
import nl.giejay.android.tv.immich.api.ApiClientConfig
import nl.giejay.android.tv.immich.shared.prefs.API_KEY
import nl.giejay.android.tv.immich.shared.prefs.DEBUG_MODE
import nl.giejay.android.tv.immich.shared.prefs.DISABLE_SSL_VERIFICATION
import nl.giejay.android.tv.immich.shared.prefs.PreferenceManager
import nl.giejay.android.tv.immich.ui.slider.InfoPanel
import nl.giejay.mediaslider.config.MediaSliderConfiguration
import nl.giejay.mediaslider.controller.ControllerIds
import nl.giejay.mediaslider.model.SliderItemViewHolder
import nl.giejay.mediaslider.overlay.createOverlayView
import nl.giejay.mediaslider.plugin.ControllerButtonPlacement
import nl.giejay.mediaslider.plugin.ControllerButtonSpec
import nl.giejay.mediaslider.plugin.ControllerPluginContext
import nl.giejay.mediaslider.plugin.SliderControllerPlugin
import nl.giejay.mediaslider.plugin.SliderKeyEventPlugin
import nl.giejay.mediaslider.plugin.SliderKeyEventResult
import nl.giejay.mediaslider.plugin.SliderKeyEventState
import nl.giejay.mediaslider.plugin.SliderViewPlugin
import nl.giejay.mediaslider.plugin.SliderViewPluginContext
import timber.log.Timber
import android.os.Handler
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import java.text.DateFormat as JavaDateFormat

/** What the panel shows right now; `info == null` while it loads. */
class InfoPanelState {
    var open by mutableStateOf(false)
    var info by mutableStateOf<AssetInfo?>(null)

    /** The panel can be taller than the screen; Up/Down scroll it while it is open (it takes no focus). */
    val scrollState = ScrollState(0)
}

/**
 * Info panel of the viewer: a button in the controller bar opens a side panel with camera, lens, location, file size
 * and the albums of the current asset; paging keeps it open and updates it, Back closes it. Read only.
 */
class InfoPanelPlugin : SliderControllerPlugin, SliderViewPlugin<InfoPanelState>, SliderKeyEventPlugin {
    private var state: InfoPanelState? = null
    private var loadJob: Job? = null

    // The slider view runs onDestroy when the viewer is paused (Home, an external player) and does not create the
    // plugin state again on resume, so nothing here may be torn down or cancelled in onDestroy: the plugin keeps its
    // own scope, state and back handling, which end with the fragment's view lifecycle.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var backDownConsumed = false

    private var backCallback: OnBackPressedCallback? = null

    override fun provideControllerButton(context: ControllerPluginContext): ControllerButtonSpec {
        return ControllerButtonSpec(
            description = context.context.getString(R.string.info_title),
            icon = mutableStateOf(R.drawable.ic_info),
            onClick = {
                val itemId = context.sliderItem.mainItem.id
                context.controller.hideOverlayControls()
                state?.open = true
                backCallback?.isEnabled = true
                load(itemId)
            },
            placement = ControllerButtonPlacement.RIGHT_OF,
            anchorId = ControllerIds.NEXT
        )
    }

    override fun createState(context: SliderViewPluginContext, config: MediaSliderConfiguration): InfoPanelState {
        val panelState = InfoPanelState()
        state = panelState
        return panelState
    }

    override fun attachView(rootView: ConstraintLayout, state: InfoPanelState?) {
        val panelState = state ?: return
        backCallback = registerViewerBackCallback(rootView) { state?.open = false }
        val view = createOverlayView(rootView.context) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                AnimatedVisibility(
                    visible = panelState.open,
                    enter = slideInHorizontally { it },
                    exit = slideOutHorizontally { it },
                ) { InfoPanel(panelState.info, panelState.scrollState) }
            }
        }
        // the overlay only draws; it must not take the D-pad focus away from the pager
        view.isFocusable = false
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        rootView.addView(view, ConstraintLayout.LayoutParams(0, 0).apply {
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        })
    }

    override fun onPageSettled(
        context: SliderViewPluginContext,
        config: MediaSliderConfiguration,
        sliderItem: SliderItemViewHolder,
        sliderItemIndex: Int,
        handler: Handler,
        state: InfoPanelState?
    ) {
        if (state?.open == true) load(sliderItem.mainItem.id)
    }

    override fun onDestroy(context: SliderViewPluginContext, state: InfoPanelState?) {
        // nothing to release on a pause, see the note on [scope]; a running request may finish
    }


    override fun onKeyDown(event: KeyEvent, state: SliderKeyEventState): SliderKeyEventResult {
        val panel = this.state ?: return SliderKeyEventResult.UNHANDLED
        if (panel.open && event.keyCode == KeyEvent.KEYCODE_BACK) {
            panel.open = false
            backCallback?.isEnabled = false
            backDownConsumed = true
            return SliderKeyEventResult.HANDLED_CONSUME
        }
        if (panel.open && (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN || event.keyCode == KeyEvent.KEYCODE_DPAD_UP)) {
            val step = if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) SCROLL_STEP_PX else -SCROLL_STEP_PX
            panel.scrollState.dispatchRawDelta(step)
            return SliderKeyEventResult.HANDLED_CONSUME
        }
        return SliderKeyEventResult.UNHANDLED
    }

    override fun onKeyUp(event: KeyEvent, state: SliderKeyEventState): SliderKeyEventResult {
        // the key-up of the Back that closed the panel must not reach the activity, it would leave the viewer
        val panelOpen = this.state?.open == true
        if (panelOpen && (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN || event.keyCode == KeyEvent.KEYCODE_DPAD_UP)) {
            return SliderKeyEventResult.HANDLED_CONSUME
        }
        if (event.keyCode == KeyEvent.KEYCODE_BACK && backDownConsumed) {
            backDownConsumed = false
            return SliderKeyEventResult.HANDLED_CONSUME
        }
        return SliderKeyEventResult.UNHANDLED
    }

    private fun load(assetId: String) {
        val panel = state ?: return
        loadJob?.cancel()
        panel.info = null
        panel.scrollState.dispatchRawDelta(-panel.scrollState.value.toFloat())
        loadJob = scope.launch {
            val client = ApiClient.getClient(
                ApiClientConfig(
                    PreferenceManager.hostName,
                    PreferenceManager.get(API_KEY),
                    PreferenceManager.get(DISABLE_SSL_VERIFICATION),
                    PreferenceManager.get(DEBUG_MODE)
                )
            )
            val asset = client.getAsset(assetId).getOrNull()
            val albums = client.listAlbums(Some(assetId)).getOrNull().orEmpty()
            if (asset == null) {
                Timber.w("Could not load the details of asset %s", assetId)
                return@launch
            }
            val formatter = JavaDateFormat.getDateTimeInstance(JavaDateFormat.MEDIUM, JavaDateFormat.SHORT)
            val info = buildAssetInfo(asset, albums) { formatter.format(it) }
            withContext(Dispatchers.Main) { panel.info = info }
        }
    }

    private companion object {
        const val SCROLL_STEP_PX = 240f
    }
}
