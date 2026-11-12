package nl.giejay.mediaslider.controller

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import com.zeuskartik.mediaslider.R
import java.util.Locale
import java.util.concurrent.TimeUnit

private val BarScrimTop = Color(0xF20F1115)
private val BarScrimMid = Color(0xCC0F1115)
private val TileColor = Color(0x26FFFFFF)
private val TileActive = Color(0x40ADC6FF)
private val TileFocused = Color(0xFFE4E6EC)
private val IconOnTile = Color(0xFFE4E6EC)
private val IconOnFocused = Color(0xFF0F1115)
private val Accent = Color(0xFFADC6FF)
private val TrackColor = Color(0x40FFFFFF)
private val TimeColor = Color(0xCCE4E6EC)
private val GroupGap = 20.dp
private val ControllerEntry.isPlugin get() = this is ControllerEntry.Plugin
private const val SeekStepMs = 1_000L
private const val FocusScale = 1.1f

/**
 * Bottom controller of the media slider: buttons for photos and videos plus position / seek bar for
 * videos. State and actions come from [SliderControllerUiState]; focus is requested through
 * [SliderControllerUiState.focusRequest] and reported back as [SliderControllerUiState.focusedId].
 */
@Composable
fun SliderControllerBar(state: SliderControllerUiState, modifier: Modifier = Modifier) {
    val isVideo = state.isVideo
    val entries = ControllerLayout.entries(isVideo, state.pluginButtons)
    val chain = ControllerLayout.focusChain(isVideo, state.pluginButtons)
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    var focusedLabelId by remember { mutableStateOf<String?>(null) }
    val rendered = entries.map { it.render(state) }
    fun requester(id: String) = requesters.getOrPut(id) { FocusRequester() }

    val request = state.focusRequest
    LaunchedEffect(request) {
        val target = request?.id ?: return@LaunchedEffect
        // the target's FocusRequester is only attached once the first layout pass happened
        withFrameNanos { }
        runCatching { requester(target).requestFocus() }
    }

    // fades (with a short slide) in and out instead of popping, also when Back closes the bar
    AnimatedVisibility(
        visible = state.visible,
        enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 6 },
        exit = fadeOut(tween(260)) + slideOutVertically(tween(260)) { it / 6 },
        modifier = modifier,
    ) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, BarScrimMid, BarScrimTop)))
            .padding(top = 40.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            entries.forEachIndexed { index, entry ->
                val id = entry.id
                // gap between the built-in navigation buttons and the action buttons (info, favorite, ...)
                if (index > 0 && entry.isPlugin != entries[index - 1].isPlugin) Spacer(Modifier.width(GroupGap))
                val (icon, description, active, onClick) = rendered[index]
                ControllerButton(
                    icon = icon,
                    description = description,
                    active = active,
                    onClick = onClick,
                    modifier = Modifier
                        .focusRequester(requester(id))
                        .focusProperties {
                            // never let a D-pad press at an edge escape into the pager behind the bar
                            if (index == 0) left = FocusRequester.Cancel
                            if (index == entries.lastIndex) right = FocusRequester.Cancel
                            up = FocusRequester.Cancel
                            down = if (isVideo) requester(ControllerIds.SEEK) else FocusRequester.Cancel
                        }
                        .onFocusChanged {
                            if (it.isFocused) {
                                state.focusedId = id
                                state.focusedIndex = chain.indexOf(id)
                                focusedLabelId = id
                            }
                        },
                )
            }
        }
        // One fixed-height line under the row names the focused button, so the layout never shifts.
        BasicText(
            entries.indexOfFirst { it.id == focusedLabelId }.let { if (it >= 0) rendered[it].description else "" },
            style = TextStyle(color = TimeColor, fontSize = 16.sp, fontWeight = FontWeight.Medium),
            modifier = Modifier.height(24.dp),
        )
        if (isVideo) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimeText(formatPosition(state.positionMs))
                SeekTrack(
                    state = state,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(requester(ControllerIds.SEEK))
                        .focusProperties {
                            up = requester(ControllerIds.PLAY_PAUSE)
                            down = FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        }
                        .onFocusChanged {
                            if (it.isFocused) {
                                state.focusedId = ControllerIds.SEEK
                                state.focusedIndex = chain.indexOf(ControllerIds.SEEK)
                            }
                        },
                )
                TimeText(formatPosition(state.durationMs))
            }
        }
    }
    }
}

private data class Rendered(
    val icon: Int,
    val description: String,
    val active: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun ControllerEntry.render(state: SliderControllerUiState): Rendered = when (this) {
    is ControllerEntry.Plugin -> Rendered(spec.icon.value, spec.description, onClick = spec.onClick)
    is ControllerEntry.BuiltIn -> when (id) {
        ControllerIds.MUTE -> Rendered(
            if (state.soundOn) R.drawable.ic_ctrl_volume_on else R.drawable.ic_ctrl_volume_off,
            stringResource(if (state.soundOn) R.string.media_mute else R.string.media_unmute),
            onClick = state.onMute,
        )
        ControllerIds.PREVIOUS -> Rendered(R.drawable.ic_ctrl_previous, stringResource(R.string.image_controller_previous)) { state.onPrevious() }
        ControllerIds.PLAY_PAUSE -> Rendered(
            if (state.isPlaying) R.drawable.ic_ctrl_pause else R.drawable.ic_ctrl_play,
            stringResource(if (state.isPlaying) R.string.media_pause else R.string.media_play),
            onClick = state.onPlayPause,
        )
        ControllerIds.SLIDESHOW -> Rendered(
            if (state.slideshowPlaying) R.drawable.ic_ctrl_slideshow_stop else R.drawable.ic_ctrl_slideshow,
            stringResource(if (state.slideshowPlaying) R.string.slideshow_stop else R.string.slideshow_start),
            active = state.slideshowPlaying,
            onClick = state.onSlideshow,
        )
        else -> Rendered(R.drawable.ic_ctrl_next, stringResource(R.string.image_controller_next)) { state.onNext() }
    }
}

@Composable
private fun ControllerButton(
    icon: Int,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) FocusScale else 1f, tween(120), label = "controllerButtonScale")
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .padding(6.dp)
            .size(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(if (focused) TileFocused else if (active) TileActive else TileColor, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (focused) IconOnFocused else if (active) Accent else IconOnTile),
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
private fun TimeText(text: String) {
    BasicText(
        text,
        style = TextStyle(color = TimeColor, fontSize = 16.sp, fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

/** Focusable progress bar: Left/Right seek one second per key (and per repeat while held). */
@Composable
private fun SeekTrack(state: SliderControllerUiState, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val duration = state.durationMs.coerceAtLeast(1L)
    val fraction = (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    Canvas(
        modifier
            .height(32.dp)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val delta = when (event.key) {
                    Key.DirectionLeft -> -SeekStepMs
                    Key.DirectionRight -> SeekStepMs
                    else -> return@onKeyEvent false
                }
                val target = (state.positionMs + delta).coerceIn(0L, state.durationMs)
                state.positionMs = target
                state.onSeekTo(target)
                true
            }
            .focusable(),
    ) {
        val trackHeight = 6.dp.toPx()
        val top = (size.height - trackHeight) / 2f
        val corner = CornerRadius(trackHeight / 2f)
        drawRoundRect(TrackColor, Offset(0f, top), Size(size.width, trackHeight), corner)
        drawRoundRect(Accent, Offset(0f, top), Size(size.width * fraction, trackHeight), corner)
        val thumbX = size.width * fraction
        if (focused) {
            drawCircle(Accent, 13.dp.toPx(), Offset(thumbX, size.height / 2f))
            drawCircle(Color.White, 10.dp.toPx(), Offset(thumbX, size.height / 2f))
        } else {
            drawCircle(Accent, 8.dp.toPx(), Offset(thumbX, size.height / 2f))
        }
    }
}

internal fun formatPosition(ms: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0L))
    return String.format(Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
