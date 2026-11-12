package nl.giejay.android.tv.immich.slider

import android.os.Handler
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.ApiAuthHeaders
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
import nl.giejay.mediaslider.view.MediaSliderController
import timber.log.Timber

class LivePhotoState {
    /** The url of the motion video that is playing over the still; null when nothing plays. */
    var playingUrl by mutableStateOf<String?>(null)

    /** The viewer's mute setting when the playback started (it is not on the controller while a photo is showing). */
    var muted by mutableStateOf(false)
}

/**
 * Live photos: a button in the controller bar plays the motion video of the current photo over the still. It ends
 * with the video, with Back, or when the viewer pages on. Read only.
 */
class LivePhotoPlugin : SliderControllerPlugin, SliderViewPlugin<LivePhotoState>, SliderKeyEventPlugin {
    private var state: LivePhotoState? = null
    private var backCallback: OnBackPressedCallback? = null
    private var backDownConsumed = false

    /** Set while the video plays during a running slideshow: its auto-advance timer is suspended until playback ends. */
    private var resumeTimerWith: MediaSliderController? = null

    override fun provideControllerButton(context: ControllerPluginContext): ControllerButtonSpec? {
        val url = context.sliderItem.mainItem.livePhotoVideoUrl
        if (url == null || context.hasSecondaryItem) return null
        return ControllerButtonSpec(
            description = context.context.getString(R.string.live_photo_play),
            icon = mutableStateOf(R.drawable.ic_live_photo),
            onClick = {
                context.controller.hideOverlayControls()
                // the still's auto-advance timer would page on and cut the video off
                if (context.controller.slideShowPlaying) {
                    context.controller.cancelNextAssetTimer()
                    resumeTimerWith = context.controller
                }
                state?.muted = !context.config.isVideoSoundEnable
                state?.playingUrl = url
                backCallback?.isEnabled = true
            },
            placement = ControllerButtonPlacement.END
        )
    }

    override fun createState(context: SliderViewPluginContext, config: MediaSliderConfiguration): LivePhotoState =
        LivePhotoState().also { state = it }

    override fun attachView(rootView: ConstraintLayout, state: LivePhotoState?) {
        val liveState = state ?: return
        backCallback = registerViewerBackCallback(rootView) { stopPlayback() }
        val view = createOverlayView(rootView.context) {
            val url = liveState.playingUrl
            if (url != null) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    LivePhotoPlayer(url, liveState.muted) { stopPlayback() }
                }
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
        state: LivePhotoState?
    ) {
        // the viewer paged on: the video belongs to the photo before, and the controller started the timer of the new page
        resumeTimerWith = null
        state?.playingUrl = null
        backCallback?.isEnabled = false
    }

    override fun onDestroy(context: SliderViewPluginContext, state: LivePhotoState?) {
        // the slider view runs this when the viewer is paused (Home, another app): the video must not keep playing,
        // and its audio must not go on in the background; the composition is not disposed by a pause
        resumeTimerWith = null
        state?.playingUrl = null
        backCallback?.isEnabled = false
    }

    /** Ends the playback (video done, Back) and lets a running slideshow go on with its timer. */
    private fun stopPlayback() {
        state?.playingUrl = null
        backCallback?.isEnabled = false
        resumeTimerWith?.let { if (it.slideShowPlaying) it.startTimerNextAsset() }
        resumeTimerWith = null
    }

    override fun onKeyDown(event: KeyEvent, state: SliderKeyEventState): SliderKeyEventResult {
        val live = this.state ?: return SliderKeyEventResult.UNHANDLED
        if (live.playingUrl != null && event.keyCode == KeyEvent.KEYCODE_BACK) {
            stopPlayback()
            backDownConsumed = true
            return SliderKeyEventResult.HANDLED_CONSUME
        }
        return SliderKeyEventResult.UNHANDLED
    }

    override fun onKeyUp(event: KeyEvent, state: SliderKeyEventState): SliderKeyEventResult {
        // the key-up of the Back that stopped the video must not reach the activity, it would leave the viewer
        if (event.keyCode == KeyEvent.KEYCODE_BACK && backDownConsumed) {
            backDownConsumed = false
            return SliderKeyEventResult.HANDLED_CONSUME
        }
        return SliderKeyEventResult.UNHANDLED
    }
}

@Composable
private fun LivePhotoPlayer(url: String, muted: Boolean, onEnded: () -> Unit) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(DefaultHttpDataSource.Factory().setDefaultRequestProperties(ApiAuthHeaders.forPlayerFromPrefs()))
            )
            .build()
            .apply {
                volume = if (muted) 0f else 1f
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) onEnded()
                    }

                    // a video that cannot be loaded or decoded never ends: take the overlay down, the still is still there
                    override fun onPlayerError(error: PlaybackException) {
                        Timber.w(error, "Live photo video failed")
                        Toast.makeText(context, R.string.live_photo_failed, Toast.LENGTH_SHORT).show()
                        onEnded()
                    }
                })
            }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { PlayerView(it).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; this.player = player } },
        modifier = Modifier.fillMaxSize(),
    )
}
