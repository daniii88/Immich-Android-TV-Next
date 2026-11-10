package nl.giejay.android.tv.immich.ui.map

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.util.ApiUtil
import nl.giejay.android.tv.immich.card.Card
import nl.giejay.android.tv.immich.map.MapCluster
import nl.giejay.android.tv.immich.map.PlaceCity
import nl.giejay.android.tv.immich.map.WorldDots
import nl.giejay.android.tv.immich.ui.card.ImmichFocusableCard
import nl.giejay.android.tv.immich.ui.theme.ImmichFocus
import kotlin.math.sqrt

/** What the map page shows. */
sealed interface MapUiState {
    data object Loading : MapUiState
    data object Empty : MapUiState
    data class Failed(val message: String) : MapUiState
    data class Ready(val clusters: List<MapCluster>) : MapUiState
}

/**
 * The places of the library as a dot-matrix world map with glowing bubbles, sized by photo count, and a row of the
 * places in the focused bubble underneath. D-pad: move between bubbles, Down to the places, OK opens a place.
 */
@Composable
fun MapScreen(state: MapUiState, landDots: BooleanArray, onOpenCity: (PlaceCity) -> Unit, modifier: Modifier = Modifier) {
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
            is MapUiState.Ready -> MapContent(state.clusters, landDots, onOpenCity)
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MapContent(clusters: List<MapCluster>, landDots: BooleanArray, onOpenCity: (PlaceCity) -> Unit) {
    var focused by remember(clusters) { mutableStateOf(clusters.first()) }
    val firstCard = remember { FocusRequester() }
    val firstBubble = remember { FocusRequester() }
    val maxCount = remember(clusters) { clusters.maxOf { it.count } }

    Column(
        // the page is entered on the biggest bubble, not on the first place card
        Modifier.fillMaxSize().focusProperties { enter = { firstBubble } }.focusGroup(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // the biggest rectangle of the map's proportions that fits the room above the place cards
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val width = minOf(maxWidth, maxHeight * WorldDots.ASPECT)
            WorldMap(
                clusters = clusters,
                landDots = landDots,
                maxCount = maxCount,
                focused = focused,
                firstBubble = firstBubble,
                onFocused = { focused = it },
                onOpen = { cluster ->
                    if (cluster.cities.size == 1) onOpenCity(cluster.cities.first()) else firstCard.runCatching { requestFocus() }
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
    focused: MapCluster,
    firstBubble: FocusRequester,
    onFocused: (MapCluster) -> Unit,
    onOpen: (MapCluster) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dotColor = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(surfaceVariant.copy(alpha = 0.55f), surface)))
            .focusGroup(),
    ) {
        val mapWidth = maxWidth
        val mapHeight = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val cellW = size.width / WorldDots.COLS
            val cellH = size.height / WorldDots.ROWS
            val radius = minOf(cellW, cellH) * 0.30f
            for (row in 0 until WorldDots.ROWS) {
                for (col in 0 until WorldDots.COLS) {
                    if (landDots[row * WorldDots.COLS + col]) {
                        drawCircle(dotColor.copy(alpha = 0.22f), radius, Offset((col + 0.5f) * cellW, (row + 0.5f) * cellH))
                    }
                }
            }
        }
        clusters.forEachIndexed { index, cluster ->
            MapBubble(
                modifier = if (index == 0) Modifier.focusRequester(firstBubble) else Modifier,
                cluster = cluster,
                diameter = 14.dp + 26.dp * sqrt(cluster.count.toFloat() / maxCount),
                accent = accent,
                selected = cluster === focused,
                label = if (cluster === focused) cluster.label else null,
                onFocused = { onFocused(cluster) },
                onClick = { onOpen(cluster) },
                mapWidth = mapWidth,
                mapHeight = mapHeight,
            )
        }
    }
}

@Composable
private fun MapBubble(
    modifier: Modifier,
    cluster: MapCluster,
    diameter: Dp,
    accent: Color,
    selected: Boolean,
    label: String?,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    mapWidth: Dp,
    mapHeight: Dp,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (hasFocus) 1.35f else 1f,
        animationSpec = tween(ImmichFocus.AnimationMillis),
        label = "bubbleScale",
    )
    val x = mapWidth * WorldDots.x(cluster.lon) - diameter / 2
    val y = mapHeight * WorldDots.y(cluster.lat) - diameter / 2
    Box(
        modifier
            .offset(x, y)
            .size(diameter)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                // soft glow around the core, stronger for the bubble in focus
                val glow = size.minDimension * (if (selected) 2.6f else 1.9f)
                drawCircle(
                    Brush.radialGradient(listOf(accent.copy(alpha = if (selected) 0.55f else 0.35f), Color.Transparent), center, glow),
                    glow,
                )
            }
            .onFocusChanged {
                hasFocus = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // a lit sphere: bright in the middle, the accent colour at the rim, a fine ring that turns white in focus
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.95f), accent.copy(alpha = 0.9f)), radius = diameter.value * 1.3f),
                    CircleShape,
                )
                .border(if (hasFocus) 2.dp else 1.dp, Color.White.copy(alpha = if (hasFocus) 0.95f else 0.45f), CircleShape),
        )
        if (label != null) {
            // the name of the bubble in focus, above it (below at the top edge of the map)
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
                        val bubbleSize = diameter.roundToPx()
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
            shown.cities.take(MAX_CARDS).map { city ->
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
