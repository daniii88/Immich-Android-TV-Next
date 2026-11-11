package nl.giejay.android.tv.immich.ui.map

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.produceState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.map.MapCluster
import nl.giejay.android.tv.immich.map.MapViewport
import nl.giejay.android.tv.immich.map.PlaceCity
import nl.giejay.android.tv.immich.map.WorldDots
import nl.giejay.android.tv.immich.map.canZoomInto
import nl.giejay.android.tv.immich.map.clustersFor
import nl.giejay.android.tv.immich.map.orderByProximity
import nl.giejay.android.tv.immich.map.viewportFor
import nl.giejay.android.tv.immich.ui.card.ImmichFocusableCard
import nl.giejay.android.tv.immich.ui.theme.ImmichFocus
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** What the map page shows. */
sealed interface MapUiState {
    data object Loading : MapUiState
    data object Empty : MapUiState
    data class Failed(val message: String) : MapUiState
    data class Ready(val cities: List<PlaceCity>) : MapUiState
}

/**
 * How far the map is zoomed: a stack of views, the world map when empty. Zooming into a bubble pushes a view framing
 * its cities, Back pops it. Lives outside of the screen so the fragment can take Back while zoomed.
 */
@Stable
class MapZoomState {
    private val views = mutableStateListOf<MapViewport>()
    private val anchors = mutableStateListOf<PlaceCity>()

    val viewport: MapViewport get() = views.lastOrNull() ?: MapViewport.WORLD
    val isZoomed: Boolean get() = views.isNotEmpty()

    /** Counts every zoom change, so the screen can move the focus when it happens (not when the page opens). */
    var changes by mutableIntStateOf(0)
        private set

    /** The city of the bubble that was zoomed into, until the next zoom in; the focus returns to its bubble when zooming out. */
    var anchorCity by mutableStateOf<PlaceCity?>(null)
        private set

    fun zoomInto(view: MapViewport, anchor: PlaceCity) {
        views += view
        anchors += anchor
        anchorCity = null
        changes++
    }

    /** Returns false at the world view, where Back is not for the map. */
    fun zoomOut(): Boolean {
        if (views.isEmpty()) return false
        views.removeAt(views.lastIndex)
        anchorCity = anchors.removeAt(anchors.lastIndex)
        changes++
        return true
    }
}

/**
 * The places of the library as a dot-matrix world map with beacons that show the photo count, and a row of the places
 * in the focused bubble underneath. D-pad: move between bubbles, Down to the places, OK opens a place, or zooms into a
 * bubble of several places; Back zooms out.
 */
@Composable
fun MapScreen(
    state: MapUiState,
    landDots: BooleanArray,
    vectors: MapVectors?,
    zoom: MapZoomState,
    onOpenCity: (PlaceCity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.map),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
        )
        when (state) {
            MapUiState.Loading -> Message(stringResource(R.string.info_loading))
            MapUiState.Empty -> Message(stringResource(R.string.map_empty))
            is MapUiState.Failed -> Message(state.message)
            is MapUiState.Ready -> MapContent(state.cities, landDots, vectors, zoom, onOpenCity)
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp),
    )
}

private fun MapCluster.key(): String = cities.first().let { "${it.city}|${it.country}" }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MapContent(
    cities: List<PlaceCity>,
    landDots: BooleanArray,
    vectors: MapVectors?,
    zoom: MapZoomState,
    onOpenCity: (PlaceCity) -> Unit,
) {
    val viewport = zoom.viewport
    val clusters = remember(cities, viewport) { clustersFor(cities, viewport) }
    if (clusters.isEmpty()) {
        // a view without any place (should not happen: zooming only goes where places are); Back zooms out
        Message(stringResource(R.string.map_empty))
        return
    }
    var focused by remember(clusters) { mutableStateOf(clusters.first()) }
    val firstCard = remember { FocusRequester() }
    val entry = remember { FocusRequester() }
    val maxCount = remember(clusters) { clusters.maxOf { it.count } }
    // the bubble to focus when the view changes: after zooming out the one that was zoomed into, else the biggest
    val preferredKey = remember(clusters, zoom.anchorCity) {
        zoom.anchorCity?.let { anchor -> clusters.firstOrNull { c -> c.cities.any { it === anchor } } }?.key() ?: clusters.first().key()
    }
    LaunchedEffect(zoom.changes) {
        if (zoom.changes == 0) return@LaunchedEffect
        withFrameNanos { }
        entry.runCatching { requestFocus() }
    }

    // one step in: half the view, centred on the bubble in focus; one step out: back to the view before
    val zoomIn = {
        val width = viewport.width / 2
        if (width >= MapViewport.MIN_WIDTH_DEGREES) {
            val view = MapViewport.around(focused.lat, focused.lon, width)
            // only into a view that has a place in it
            if (clustersFor(cities, view).isNotEmpty()) zoom.zoomInto(view, focused.cities.first())
        }
    }
    val zoomOut = { zoom.zoomOut(); Unit }

    Column(
        // the page is entered on the biggest bubble, not on the first place card
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                // remotes with zoom or channel keys: zoom without leaving the D-pad
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.ZoomIn, Key.ChannelUp, Key.Plus, Key.NumPadAdd -> { zoomIn(); true }
                    Key.ZoomOut, Key.ChannelDown, Key.Minus, Key.NumPadSubtract -> { zoomOut(); true }
                    else -> false
                }
            }
            .focusProperties { enter = { entry } }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // the biggest rectangle of the map's proportions that fits the room above the place cards
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val width = minOf(maxWidth, maxHeight * WorldDots.ASPECT)
            WorldMap(
                clusters = clusters,
                landDots = landDots,
                vectors = vectors,
                maxCount = maxCount,
                viewport = viewport,
                zoomed = zoom.isZoomed,
                focused = focused,
                preferredKey = preferredKey,
                entry = entry,
                onFocused = { focused = it },
                onOpen = { cluster ->
                    when {
                        cluster.cities.size == 1 -> onOpenCity(cluster.cities.first())
                        canZoomInto(cluster, viewport) -> zoom.zoomInto(viewportFor(cluster.cities), cluster.cities.first())
                        else -> firstCard.runCatching { requestFocus() }
                    }
                },
                onZoomIn = zoomIn,
                onZoomOut = zoomOut,
                modifier = Modifier.size(width, width / WorldDots.ASPECT),
            )
        }
        PlaceCards(focused, firstCard, onOpenCity)
    }
}

@Composable
private fun WorldMap(
    clusters: List<MapCluster>,
    landDots: BooleanArray,
    vectors: MapVectors?,
    maxCount: Int,
    viewport: MapViewport,
    zoomed: Boolean,
    focused: MapCluster,
    preferredKey: String,
    entry: FocusRequester,
    onFocused: (MapCluster) -> Unit,
    onOpen: (MapCluster) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dotColor = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    // the view glides to its new frame; bubbles and dots follow it
    val spec = tween<Float>(ZOOM_MILLIS)
    val lonMin = animateFloatAsState(viewport.lonMin.toFloat(), spec, label = "lonMin")
    val lonMax = animateFloatAsState(viewport.lonMax.toFloat(), spec, label = "lonMax")
    val latTop = animateFloatAsState(viewport.latTop.toFloat(), spec, label = "latTop")
    val latBottom = animateFloatAsState(viewport.latBottom.toFloat(), spec, label = "latBottom")
    // read only while drawing and laying out, so a frame of the glide does not recompose the map
    val shownView = { MapViewport(lonMin.value.toDouble(), lonMax.value.toDouble(), latTop.value.toDouble(), latBottom.value.toDouble()) }

    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(surfaceVariant.copy(alpha = 0.55f), surface)))
            .focusGroup(),
    ) {
        val mapWidth = maxWidth
        val mapHeight = maxHeight
        // with the vectors loaded, the dots come from the real coastlines at every zoom (finer when zoomed in); until
        // then, and as a fallback, from the coarse mask of the world view
        val landDotsFromVectors = remember(vectors) { vectors?.let { LandDots(it) } }
        val density = LocalDensity.current
        val widthPx = with(density) { mapWidth.roundToPx() }
        val heightPx = with(density) { mapHeight.roundToPx() }
        // The dots of the view the map is heading for, finished off the main thread. While the view glides there, the
        // previous picture is moved and scaled to fit (pixel-sharp, no smoothing) and then swapped for the fine one:
        // rasterising the coastlines in every frame of the glide was far too slow for a TV box.
        val dotsLayer by produceState<DotsLayer?>(null, landDotsFromVectors, viewport, widthPx, heightPx) {
            val dots = landDotsFromVectors ?: return@produceState
            if (widthPx < 1 || heightPx < 1) return@produceState
            val (step, dot) = dotPixels(dotStep(widthPx.toFloat() / WorldDots.COLS, viewport.width))
            val land = dotColor.copy(alpha = 0.26f).toArgb()
            val border = dotColor.copy(alpha = 0.55f).toArgb()
            value = withContext(Dispatchers.Default) {
                DotsLayer(viewport, dots.render(viewport, widthPx, heightPx, step, dot, land, border).asImageBitmap())
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val shown = shownView()
            val baseStep = size.width / WorldDots.COLS
            val layer = dotsLayer
            if (layer != null) {
                val from = layer.view
                withTransform({
                    translate(
                        left = ((from.lonMin - shown.lonMin) / shown.width * size.width).toFloat(),
                        top = ((shown.latTop - from.latTop) / shown.height * size.height).toFloat(),
                    )
                    scale((from.width / shown.width).toFloat(), (from.height / shown.height).toFloat(), Offset.Zero)
                }) {
                    drawImage(layer.image, filterQuality = FilterQuality.None)
                }
                return@Canvas
            }
            // fallback: each dot looks up the coarse 1.5 degree land mask at its position
            val step = baseStep
            val radius = step * 0.30f
            val cellDegrees = 360.0 / WorldDots.COLS
            val color = dotColor.copy(alpha = 0.22f)
            val columns = (size.width / step).toInt()
            val rows = (size.height / step).toInt()
            for (row in 0 until rows) {
                val lat = shown.latTop - (row + 0.5) * step / size.height * shown.height
                val maskRow = floor((WorldDots.LAT_TOP - lat) / cellDegrees).toInt()
                if (maskRow !in 0 until WorldDots.ROWS) continue
                for (col in 0 until columns) {
                    val lon = shown.lonMin + (col + 0.5) * step / size.width * shown.width
                    val maskCol = floor((lon + 180.0) / cellDegrees).toInt()
                    if (maskCol in 0 until WorldDots.COLS && landDots[maskRow * WorldDots.COLS + maskCol]) {
                        drawCircle(color, radius, Offset((col + 0.5f) * step, (row + 0.5f) * step))
                    }
                }
            }
        }
        clusters.forEach { cluster ->
            val key = cluster.key()
            MapBubble(
                modifier = if (key == preferredKey) Modifier.focusRequester(entry) else Modifier,
                cluster = cluster,
                size = 30.dp + 22.dp * sqrt(cluster.count.toFloat() / maxCount),
                accent = accent,
                selected = cluster === focused,
                label = if (cluster === focused) cluster.label else null,
                onFocused = { onFocused(cluster) },
                onClick = { onOpen(cluster) },
                viewport = shownView,
                mapWidth = mapWidth,
                mapHeight = mapHeight,
            )
        }
        Row(
            Modifier.align(Alignment.TopEnd).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ZoomButton("−", stringResource(R.string.map_zoom_out), onZoomOut)
            ZoomButton("+", stringResource(R.string.map_zoom_in), onZoomIn)
        }
        if (zoomed) {
            Text(
                text = stringResource(R.string.map_zoom_out_hint),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(surface.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/** A small round zoom button; the same as the zoom keys, for remotes without them. */
@Composable
private fun ZoomButton(symbol: String, description: String, onClick: () -> Unit) {
    var hasFocus by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(40.dp)
            .background(
                if (hasFocus) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                CircleShape,
            )
            .semantics { contentDescription = description }
            .onFocusChanged { hasFocus = it.isFocused }
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.titleLarge,
            color = if (hasFocus) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MapBubble(
    modifier: Modifier,
    cluster: MapCluster,
    size: Dp,
    accent: Color,
    selected: Boolean,
    label: String?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    viewport: () -> MapViewport,
    mapWidth: Dp,
    mapHeight: Dp,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (hasFocus) 1.25f else 1f,
        animationSpec = tween(ImmichFocus.AnimationMillis),
        label = "bubbleScale",
    )
    Box(
        modifier
            .offset {
                val view = viewport()
                IntOffset(
                    (mapWidth.toPx() * view.x(cluster.lon) - size.toPx() / 2).roundToInt(),
                    (mapHeight.toPx() * view.y(cluster.lat) - size.toPx() / 2).roundToInt(),
                )
            }
            .size(size)
            // the bubble in focus, with its name, lies above its neighbours
            .zIndex(if (selected || hasFocus) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                // a beacon: a core that carries the photo count, a fine ring and a soft glow; the focused one is
                // brighter and ringed in white. The core is a fraction of the touch area, so neighbours stay apart.
                val core = this.size.minDimension * 0.30f
                val ring = this.size.minDimension * 0.46f
                val glow = this.size.minDimension * (if (selected) 1.5f else 1.0f)
                drawCircle(
                    Brush.radialGradient(listOf(accent.copy(alpha = if (selected) 0.40f else 0.22f), Color.Transparent), center, glow),
                    glow,
                )
                drawCircle(
                    color = if (hasFocus) Color.White.copy(alpha = 0.95f) else accent.copy(alpha = 0.55f),
                    radius = ring,
                    style = Stroke(width = if (hasFocus) 2.5.dp.toPx() else 1.5.dp.toPx()),
                )
                drawCircle(accent, core)
            }
            .onFocusChanged {
                hasFocus = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = formatCount(cluster.count),
            style = MaterialTheme.typography.labelMedium,
            fontSize = if (cluster.count > 99) 9.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            softWrap = false,
        )
        if (label != null) {
            // the name of the bubble in focus, above it (below near the top edge of the map)
            // above unless the bubble is in the top quarter of the map, where the label would be cut off
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    // centred above (below near the top edge) the bubble, but kept inside the map
                    .layout { measurable, _ ->
                        val placeable = measurable.measure(Constraints())
                        val view = viewport()
                        val above = view.y(cluster.lat) > 0.25f
                        val bubbleLeft = (mapWidth.toPx() * view.x(cluster.lon) - size.toPx() / 2).roundToInt()
                        val bubbleSize = size.roundToPx()
                        val left = (bubbleLeft + bubbleSize / 2 - placeable.width / 2)
                            .coerceIn(0, (mapWidth.roundToPx() - placeable.width).coerceAtLeast(0)) - bubbleLeft
                        val top = if (above) -placeable.height - 8.dp.roundToPx() else bubbleSize + 8.dp.roundToPx()
                        layout(bubbleSize, bubbleSize) { placeable.place(left, top) }
                    }
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .graphicsLayer { scaleX = 1f / scale; scaleY = 1f / scale },
            )
        }
    }
}

@Composable
private fun PlaceCards(cluster: MapCluster, firstCard: FocusRequester, onOpenCity: (PlaceCity) -> Unit) {
    val context = LocalContext.current
    Crossfade(targetState = cluster, label = "placeCards") { shown ->
        val more = shown.cities.size - MAX_CARDS
        val cards = remember(shown) {
            // the biggest places of a large bubble, as a walk through the neighbourhood
            orderByProximity(shown.cities.take(MAX_CARDS)).map { city ->
                city to Card(
                    title = city.city,
                    description = listOfNotNull(
                        city.country,
                        context.resources.getQuantityString(R.plurals.map_photos, city.count, city.count),
                    ).joinToString(" · "),
                    id = city.city,
                    thumbnailUrl = ApiUtil.getThumbnailUrl(city.coverAssetId, "thumbnail"),
                    backgroundUrl = null,
                )
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().focusGroup(),
        ) {
            itemsIndexed(cards) { index, (city, card) ->
                ImmichFocusableCard(
                    card = card,
                    onClick = { onOpenCity(city) },
                    onFocused = {},
                    modifier = if (index == 0) Modifier.focusRequester(firstCard) else Modifier,
                )
            }
            if (more > 0) {
                item {
                    Text(
                        text = context.resources.getQuantityString(R.plurals.map_more_places, more, more),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 48.dp),
                    )
                }
            }
        }
    }
}

/** 7, 353, 1.2k, 15k: the photo count as it fits the marker. */
internal fun formatCount(count: Int): String = when {
    count < 1000 -> count.toString()
    count < 10_000 -> "${count / 1000}.${count % 1000 / 100}k"
    else -> "${count / 1000}k"
}

private const val MAX_CARDS = 40
/** The dot picture of one view of the map. */
private class DotsLayer(val view: MapViewport, val image: ImageBitmap)

private const val ZOOM_MILLIS = 450
