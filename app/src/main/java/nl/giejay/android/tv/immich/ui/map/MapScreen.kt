package nl.giejay.android.tv.immich.ui.map

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            is MapUiState.Ready -> MapContent(state.cities, landDots, zoom, onOpenCity)
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
private fun MapContent(cities: List<PlaceCity>, landDots: BooleanArray, zoom: MapZoomState, onOpenCity: (PlaceCity) -> Unit) {
    val viewport = zoom.viewport
    val clusters = remember(cities, viewport) { clustersFor(cities, viewport) }
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

    Column(
        // the page is entered on the biggest bubble, not on the first place card
        Modifier.fillMaxSize().focusProperties { enter = { entry } }.focusGroup(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // the biggest rectangle of the map's proportions that fits the room above the place cards
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val width = minOf(maxWidth, maxHeight * WorldDots.ASPECT)
            WorldMap(
                clusters = clusters,
                landDots = landDots,
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
    maxCount: Int,
    viewport: MapViewport,
    zoomed: Boolean,
    focused: MapCluster,
    preferredKey: String,
    entry: FocusRequester,
    onFocused: (MapCluster) -> Unit,
    onOpen: (MapCluster) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dotColor = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    // the view glides to its new frame; bubbles and dots follow it
    val spec = tween<Float>(ZOOM_MILLIS)
    val lonMin by animateFloatAsState(viewport.lonMin.toFloat(), spec, label = "lonMin")
    val lonMax by animateFloatAsState(viewport.lonMax.toFloat(), spec, label = "lonMax")
    val latTop by animateFloatAsState(viewport.latTop.toFloat(), spec, label = "latTop")
    val latBottom by animateFloatAsState(viewport.latBottom.toFloat(), spec, label = "latBottom")
    val shown = MapViewport(lonMin.toDouble(), lonMax.toDouble(), latTop.toDouble(), latBottom.toDouble())

    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(surfaceVariant.copy(alpha = 0.55f), surface)))
            .focusGroup(),
    ) {
        val mapWidth = maxWidth
        val mapHeight = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            // Dots keep their size and spacing on screen at every zoom; each looks up the land mask at its position.
            // Zoomed in, the 1.5 degree mask gets coarse, so the land only hints at where things are.
            val step = size.width / WorldDots.COLS
            val radius = step * 0.30f
            val cellDegrees = 360.0 / WorldDots.COLS
            val zoomFade = (shown.width / MapViewport.WORLD.width).toFloat().coerceIn(0.35f, 1f)
            val color = dotColor.copy(alpha = 0.22f * zoomFade)
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
                viewport = shown,
                mapWidth = mapWidth,
                mapHeight = mapHeight,
            )
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
    viewport: MapViewport,
    mapWidth: Dp,
    mapHeight: Dp,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (hasFocus) 1.25f else 1f,
        animationSpec = tween(ImmichFocus.AnimationMillis),
        label = "bubbleScale",
    )
    val x = mapWidth * viewport.x(cluster.lon) - size / 2
    val y = mapHeight * viewport.y(cluster.lat) - size / 2
    Box(
        modifier
            .offset(x, y)
            .size(size)
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
            text = if (cluster.count > 99) "99+" else cluster.count.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontSize = if (cluster.count > 99) 9.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            softWrap = false,
        )
        if (label != null) {
            // the name of the bubble in focus, above it (below near the top edge of the map)
            val above = cluster.lat < 55.0
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
                        val bubbleLeft = x.roundToPx()
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
        val cards = remember(shown) {
            orderByProximity(shown.cities).take(MAX_CARDS).map { city ->
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
        }
    }
}

private const val MAX_CARDS = 40
private const val ZOOM_MILLIS = 450
