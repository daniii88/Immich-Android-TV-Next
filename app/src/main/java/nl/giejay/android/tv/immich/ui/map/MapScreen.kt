package nl.giejay.android.tv.immich.ui.map

import android.content.res.Resources
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import nl.giejay.android.tv.immich.map.MapPin
import nl.giejay.android.tv.immich.map.PinSize
import nl.giejay.android.tv.immich.map.choosePins
import nl.giejay.android.tv.immich.map.hangsBelow
import nl.giejay.android.tv.immich.map.pin
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.map.MapFootprint
import nl.giejay.android.tv.immich.map.MapRegion
import nl.giejay.android.tv.immich.map.MapViewport
import nl.giejay.android.tv.immich.map.PlaceCity
import nl.giejay.android.tv.immich.map.PlaceCountry
import nl.giejay.android.tv.immich.map.WorldDots
import nl.giejay.android.tv.immich.map.contents
import nl.giejay.android.tv.immich.map.viewportFor
import nl.giejay.android.tv.immich.ui.card.ImmichFocusableCard
import nl.giejay.android.tv.immich.ui.theme.ImmichFocus
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** Everything the map page shows: the places of the library and where each photo was taken. */
class MapData(val cities: List<PlaceCity>, val countries: List<PlaceCountry>, val footprint: MapFootprint) {
    val photoCount: Int get() = footprint.size
}

/** What the map page shows. */
sealed interface MapUiState {
    data object Loading : MapUiState
    data object Empty : MapUiState
    data class Failed(val message: String) : MapUiState
    data class Ready(val data: MapData) : MapUiState
}

/** Where the page is: the world (all countries), one country, or one region of a country. */
sealed interface MapLevel {
    data object World : MapLevel
    data class Country(val country: PlaceCountry) : MapLevel
    data class Region(val country: PlaceCountry, val region: MapRegion) : MapLevel
}

/**
 * The levels the page went down: OK on a country opens it, Back goes up again and puts the focus back on the card that
 * was opened. Lives outside of the screen so the fragment can take Back while a country is open.
 */
@Stable
class MapLevelState {
    private val levels = mutableStateListOf<MapLevel>()
    private val returnIndexes = mutableListOf<Int>()

    val level: MapLevel get() = levels.lastOrNull() ?: MapLevel.World
    val isNested: Boolean get() = levels.isNotEmpty()

    /** The card to focus when the level appears. */
    var focusIndex by mutableIntStateOf(0)
        private set

    /** Counts every change of level, so the screen moves the focus when it happens (not when the page opens). */
    var changes by mutableIntStateOf(0)
        private set

    fun open(next: MapLevel, fromIndex: Int) {
        levels += next
        returnIndexes += fromIndex
        focusIndex = 0
        changes++
    }

    /** Returns false at the world, where Back is not for the map. */
    fun back(): Boolean {
        if (levels.isEmpty()) return false
        levels.removeAt(levels.lastIndex)
        focusIndex = returnIndexes.removeAt(returnIndexes.lastIndex)
        changes++
        return true
    }
}

/**
 * Where the photos of the library were taken: the dots of a world map light up wherever there are photos. A row of
 * cards underneath leads from the countries to the regions and places and on to the photos; the map follows the
 * focused card (its country lights up, a place gets a ring). D-pad: left and right through the cards, OK goes in,
 * Back goes out.
 */
@Composable
fun MapScreen(
    state: MapUiState,
    landDots: BooleanArray,
    vectors: MapVectors?,
    levels: MapLevelState,
    onOpenCity: (PlaceCity) -> Unit,
    onOpenRegion: (MapRegion) -> Unit,
    onOpenCountry: (PlaceCountry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 24.dp),
    ) {
        when (state) {
            MapUiState.Loading -> { Title(stringResource(R.string.map), null); Message(stringResource(R.string.info_loading)) }
            MapUiState.Empty -> { Title(stringResource(R.string.map), null); Message(stringResource(R.string.map_empty)) }
            is MapUiState.Failed -> { Title(stringResource(R.string.map), null); Message(state.message) }
            is MapUiState.Ready -> MapContent(state.data, landDots, vectors, levels, onOpenCity, onOpenRegion, onOpenCountry)
        }
    }
}

@Composable
private fun Title(title: String, facts: String?) {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        if (facts != null) {
            Text(
                text = facts,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 20.dp, bottom = 4.dp),
            )
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

/** A card of the row: what it shows, what the map shows while it has the focus, and what OK does. */
private class MapEntry(
    val card: Card,
    /** The country that lights up on the map. */
    val highlight: String?,
    /** The photo pin on the map that stands for the card (shown first and larger while the card has the focus). */
    val pinKey: String?,
    val open: () -> Unit,
)

private fun Resources.places(n: Int) = getQuantityString(R.plurals.map_group_places, n, n)
private fun Resources.photos(n: Int) = getQuantityString(R.plurals.map_photos, n, n)
private fun Resources.countries(n: Int) = getQuantityString(R.plurals.map_countries, n, n)

private fun card(id: String, title: String, description: String, coverAssetId: String) =
    Card(title = title, description = description, id = id, thumbnailUrl = ApiUtil.getThumbnailUrl(coverAssetId, "thumbnail"), backgroundUrl = null)

private fun placeEntry(res: Resources, city: PlaceCity, withRegion: Boolean, onOpenCity: (PlaceCity) -> Unit) = MapEntry(
    card = card("city|${city.city}", city.city, listOfNotNull(city.state.takeIf { withRegion }, res.photos(city.count)).joinToString(" · "), city.coverAssetId),
    highlight = city.country,
    pinKey = city.pin().key,
    open = { onOpenCity(city) },
)

private fun entriesFor(
    level: MapLevel,
    data: MapData,
    res: Resources,
    levels: MapLevelState,
    onOpenCity: (PlaceCity) -> Unit,
    onOpenRegion: (MapRegion) -> Unit,
    onOpenCountry: (PlaceCountry) -> Unit,
): List<MapEntry> = when (level) {
    MapLevel.World -> data.countries.mapIndexed { index, country ->
        MapEntry(
            card = card("country|${country.name}", country.name, res.places(country.cities.size) + " · " + res.photos(country.count), country.coverAssetId),
            highlight = country.name,
            pinKey = country.pin().key,
            open = { levels.open(MapLevel.Country(country), index) },
        )
    }
    is MapLevel.Country -> {
        val country = level.country
        val contents = country.contents()
        val all = MapEntry(
            card = card("all|${country.name}", res.getString(R.string.map_all_photos), res.photos(country.count), country.coverAssetId),
            highlight = country.name,
            pinKey = null,
            open = { onOpenCountry(country) },
        )
        val regions = contents.regions.mapIndexed { index, region ->
            MapEntry(
                card = card("region|${region.state}", region.state, res.places(region.cities.size) + " · " + res.photos(region.count), region.coverAssetId),
                highlight = country.name,
                pinKey = region.pin().key,
                // the card index in the row: "all photos" comes first
                open = { levels.open(MapLevel.Region(country, region), index + 1) },
            )
        }
        listOf(all) + regions + contents.places.map { placeEntry(res, it, withRegion = contents.regions.isEmpty(), onOpenCity) }
    }
    is MapLevel.Region -> {
        val region = level.region
        val all = MapEntry(
            card = card("all|${region.state}", res.getString(R.string.map_all_photos), res.photos(region.count), region.coverAssetId),
            highlight = level.country.name,
            pinKey = null,
            open = { onOpenRegion(region) },
        )
        listOf(all) + region.cities.map { placeEntry(res, it, withRegion = false, onOpenCity) }
    }
}

/** The photo pins a level can show: its countries, regions or places. */
private fun pinsOf(level: MapLevel, data: MapData): List<MapPin> = when (level) {
    MapLevel.World -> data.countries.map { it.pin() }
    is MapLevel.Country -> level.country.contents().let { c -> c.regions.map { it.pin() } + c.places.map { it.pin() } }
    is MapLevel.Region -> level.region.cities.map { it.pin() }
}

/** The part of the world the map shows on a level: the whole world, or the places of the country / region framed. */
private fun viewportOf(level: MapLevel): MapViewport = when (level) {
    MapLevel.World -> MapViewport.WORLD
    is MapLevel.Country -> viewportFor(level.country.cities)
    is MapLevel.Region -> viewportFor(level.region.cities)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MapContent(
    data: MapData,
    landDots: BooleanArray,
    vectors: MapVectors?,
    levels: MapLevelState,
    onOpenCity: (PlaceCity) -> Unit,
    onOpenRegion: (MapRegion) -> Unit,
    onOpenCountry: (PlaceCountry) -> Unit,
) {
    val res = LocalContext.current.resources
    val level = levels.level
    val entries = remember(level, data) { entriesFor(level, data, res, levels, onOpenCity, onOpenRegion, onOpenCountry) }
    var focused by remember(level) { mutableIntStateOf(levels.focusIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))) }
    val entry = entries.getOrNull(focused)
    val pins = remember(level, data) { pinsOf(level, data) }
    val requester = remember(level) { FocusRequester() }
    val start = remember(level) { levels.focusIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0)) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = start)
    LaunchedEffect(levels.changes) {
        if (levels.changes == 0) return@LaunchedEffect
        withFrameNanos { }
        requester.runCatching { requestFocus() }
    }

    when (level) {
        MapLevel.World -> Title(
            stringResource(R.string.map),
            listOf(res.countries(data.countries.size), res.places(data.cities.size), res.photos(data.photoCount)).joinToString(" · "),
        )
        is MapLevel.Country -> Title(level.country.name, res.places(level.country.cities.size) + " · " + res.photos(level.country.count))
        is MapLevel.Region -> Title(
            level.region.state,
            listOf(level.country.name, res.places(level.region.cities.size), res.photos(level.region.count)).joinToString(" · "),
        )
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // the biggest rectangle of the map's proportions that fits the room above the cards
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val width = minOf(maxWidth, maxHeight * WorldDots.ASPECT)
            WorldMap(
                footprint = data.footprint,
                landDots = landDots,
                vectors = vectors,
                viewport = viewportOf(level),
                highlight = entry?.highlight,
                pins = pins,
                focusedPin = entry?.pinKey,
                modifier = Modifier.size(width, width / WorldDots.ASPECT),
            )
        }
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .focusProperties { enter = { requester } }
                .focusGroup(),
        ) {
            itemsIndexed(entries, key = { _, it -> it.card.id }) { index, item ->
                ImmichFocusableCard(
                    card = item.card,
                    onClick = item.open,
                    onFocused = { focused = index },
                    modifier = if (index == start) Modifier.focusRequester(requester) else Modifier,
                )
            }
        }
    }
}

/** The map itself: land, borders and the lit dots where photos were taken. It shows, it is not focused. */
@Composable
private fun WorldMap(
    footprint: MapFootprint,
    landDots: BooleanArray,
    vectors: MapVectors?,
    viewport: MapViewport,
    highlight: String?,
    pins: List<MapPin>,
    focusedPin: String?,
    modifier: Modifier = Modifier,
) {
    val dotColor = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    // the view glides to its new frame; the dots and the ring follow it
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
            // flat, not a gradient: a gradient over the whole map cost a quarter of the GPU time of a frame on a TV box
            .background(surfaceVariant.copy(alpha = 0.28f).compositeOver(surface)),
    ) {
        val mapWidth = maxWidth
        val mapHeight = maxHeight
        // with the vectors loaded, the dots come from the real coastlines at every zoom (finer when zoomed in); until
        // then the coarse mask of the world view draws the land, without the photos
        val landDotsFromVectors = remember(vectors) { vectors?.let { LandDots(it) } }
        val density = LocalDensity.current
        val widthPx = with(density) { mapWidth.roundToPx() }
        val heightPx = with(density) { mapHeight.roundToPx() }
        // The picture of the view the map is heading for, finished off the main thread. While the view glides there,
        // the previous picture is moved and scaled to fit (pixel-sharp, no smoothing) and then swapped for the fine one:
        // rasterising the coastlines in every frame of the glide was far too slow for a TV box.
        // the land a little quieter than before, so the photos stand out: the focused country nearly white, the rest of
        // the places you have been in the accent colour
        val land = dotColor.copy(alpha = 0.20f).toArgb()
        val border = dotColor.copy(alpha = 0.38f).toArgb()
        val style = FootprintStyle(footprint, highlight, lerp(accent, Color.White, 0.55f).toArgb(), accent.toArgb())
        val readyLayer by produceState<DotsLayer?>(null, landDotsFromVectors, viewport, widthPx, heightPx, highlight, footprint) {
            val dots = landDotsFromVectors ?: return@produceState
            if (widthPx < 1 || heightPx < 1) return@produceState
            value = renderDotsLayer(dots, viewport, widthPx, heightPx, land, border, style)
        }
        // The picture on screen. A finished one that covers the old one (going out, a new highlight, or the first)
        // takes over at once; one for a smaller view (going in) waits until the glide has arrived, else the margins it
        // does not cover would go blank while the view is still wider.
        var dotsLayer by remember { mutableStateOf<DotsLayer?>(null) }
        LaunchedEffect(readyLayer) {
            val next = readyLayer ?: return@LaunchedEffect
            val old = dotsLayer
            if (old != null && !next.view.covers(old.view)) {
                if (!old.view.covers(next.view)) {
                    // the new view reaches beyond the old one and the old beyond the new: a picture of both views
                    // together covers every view of the glide
                    val both = MapViewport(
                        minOf(old.view.lonMin, next.view.lonMin), maxOf(old.view.lonMax, next.view.lonMax),
                        maxOf(old.view.latTop, next.view.latTop), minOf(old.view.latBottom, next.view.latBottom),
                    )
                    val dots = landDotsFromVectors ?: return@LaunchedEffect
                    dotsLayer = renderDotsLayer(dots, both, widthPx, heightPx, land, border, style)
                }
                snapshotFlow {
                    abs(lonMin.value - next.view.lonMin) < 1e-3 && abs(lonMax.value - next.view.lonMax) < 1e-3 &&
                        abs(latTop.value - next.view.latTop) < 1e-3 && abs(latBottom.value - next.view.latBottom) < 1e-3
                }.first { it }
            }
            dotsLayer = next
        }
        Canvas(Modifier.fillMaxSize()) {
            val shown = shownView()
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
            // until the coastlines are loaded: each dot looks up the coarse 1.5 degree land mask at its position
            val step = size.width / WorldDots.COLS
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
        // photos at the places with the most photos, never on top of each other; the focused one larger and named
        val pinSize = with(density) { PinSize(PIN_SIZE.toPx(), (PIN_SIZE * PIN_FOCUS_SCALE).toPx(), PIN_TIP.toPx(), 6.dp.toPx(), labelPx = 30.dp.toPx()) }
        val shownPins = remember(pins, focusedPin, viewport, widthPx, heightPx) {
            choosePins(pins, focusedPin, viewport, widthPx.toFloat(), heightPx.toFloat(), pinSize, MAX_PINS)
        }
        shownPins.asReversed().forEach { pin ->
            val focused = pin.key == focusedPin
            // near the top edge the photo hangs below its place, so it is not cut off
            val below = hangsBelow(viewport.y(pin.lat) * heightPx, if (focused) pinSize.focusedPhotoPx else pinSize.photoPx, pinSize.tipPx)
            key(pin.key) { PhotoPin(pin, focused, below, shownView, mapWidth, mapHeight) }
        }
    }
}

/**
 * A photo standing on its place: a rounded picture with a white frame whose tip ends in a small white spot on the
 * place. Frame and tip stay white (with a dark edge) so they stand out from the accent-coloured dots under them; the pin
 * of the focused card is larger, gets an accent ring inside the frame and is named underneath.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun PhotoPin(pin: MapPin, focused: Boolean, below: Boolean, viewport: () -> MapViewport, mapWidth: Dp, mapHeight: Dp) {
    val pinSize = if (focused) PIN_SIZE * PIN_FOCUS_SCALE else PIN_SIZE
    val frame = Color.White
    val tip = PIN_TIP
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .zIndex(if (focused) 1f else 0f)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(Constraints())
                val view = viewport()
                // the tip of the frame on the place, kept inside the map sideways
                val x = (mapWidth.toPx() * view.x(pin.lon) - placeable.width / 2f).roundToInt()
                    .coerceIn(0, (mapWidth.roundToPx() - placeable.width).coerceAtLeast(0))
                val spot = (mapHeight.toPx() * view.y(pin.lat)).roundToInt()
                val y = if (below) spot else spot - (pinSize + tip).roundToPx()
                layout(constraints.minWidth, constraints.minHeight) { placeable.place(x, y) }
            },
    ) {
        // the tip pointing at the place: under the photo, or above it when the photo hangs below its place
        if (below) PinTip(pointsUp = true, frame)
        Box(
            Modifier
                .size(pinSize)
                .background(frame, RoundedCornerShape(12.dp))
                .border(1.dp, PIN_EDGE, RoundedCornerShape(12.dp))
                .padding(3.dp),
        ) {
            GlideImage(
                model = ApiUtil.getThumbnailUrl(pin.coverAssetId, "thumbnail"),
                contentDescription = pin.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(9.dp))
                    .then(if (focused) Modifier.border(ImmichFocus.BorderWidth, MaterialTheme.colorScheme.border, RoundedCornerShape(9.dp)) else Modifier),
            )
        }
        if (!below) PinTip(pointsUp = false, frame)
        if (focused) {
            Text(
                text = pin.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * The little triangle between a pin's photo and its place, with a dark edge on its slanted sides and a white spot with
 * a dark ring right on the place, so it reads against the bright dots there.
 */
@Composable
private fun PinTip(pointsUp: Boolean, color: Color) {
    Canvas(Modifier.size(PIN_TIP * 2, PIN_TIP)) {
        val base = if (pointsUp) size.height else 0f
        val point = Offset(size.width / 2, if (pointsUp) 0f else size.height)
        val fill = Path().apply {
            moveTo(0f, base)
            lineTo(size.width, base)
            lineTo(point.x, point.y)
            close()
        }
        val edge = Path().apply {
            moveTo(0f, base)
            lineTo(point.x, point.y)
            lineTo(size.width, base)
        }
        drawPath(fill, color)
        drawPath(edge, PIN_EDGE, style = Stroke(width = 1.dp.toPx()))
        // drawn past the canvas on purpose: the spot is centred on the place
        drawCircle(PIN_EDGE, radius = 4.5.dp.toPx(), center = point)
        drawCircle(color, radius = 3.dp.toPx(), center = point)
    }
}

private val PIN_SIZE = 52.dp
private const val PIN_FOCUS_SCALE = 1.35f
private val PIN_TIP = 9.dp

/** The dark edge of a pin, so its white frame and tip stand out from the bright dots and glows under them. */
private val PIN_EDGE = Color(0xB3101418)
private const val MAX_PINS = 12

/** The dot picture of one view of the map. */
private class DotsLayer(val view: MapViewport, val image: ImageBitmap)

/** Renders the dots of [view], the photos lit on top, off the main thread. */
private suspend fun renderDotsLayer(
    dots: LandDots, view: MapViewport, widthPx: Int, heightPx: Int, land: Int, border: Int, style: FootprintStyle,
): DotsLayer = withContext(Dispatchers.Default) {
    val (step, dot) = dotPixels(dotStep(widthPx.toFloat() / WorldDots.COLS, view.width))
    DotsLayer(view, dots.render(view, widthPx, heightPx, step, dot, land, border, style).asImageBitmap())
}

internal fun MapViewport.covers(other: MapViewport, eps: Double = 1e-6): Boolean =
    lonMin <= other.lonMin + eps && lonMax >= other.lonMax - eps && latTop >= other.latTop - eps && latBottom <= other.latBottom + eps

private const val ZOOM_MILLIS = 450
