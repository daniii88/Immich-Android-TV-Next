package nl.giejay.android.tv.immich.map

/** A photo shown on the map at a place, like the photo tiles of Google Photos' map: the picture of a country, region or place. */
data class MapPin(
    val key: String,
    val lat: Double,
    val lon: Double,
    val coverAssetId: String,
    val name: String,
    val count: Int,
)

/** The size of a pin on screen: a photo of [photoPx] (the focused one [focusedPhotoPx], with its name of [labelPx] under it) on a tip of [tipPx]. */
data class PinSize(val photoPx: Float, val focusedPhotoPx: Float, val tipPx: Float, val gapPx: Float, val labelPx: Float = 0f)

/**
 * The pins to show in [view] on a map of [widthPx] x [heightPx]: the focused one first (always, when in view), then
 * the others with the most photos, each only if its photo does not overlap one already placed (with [PinSize.gapPx]
 * room), so photos never lie on top of each other; at most [max]. A photo stands above its place, or hangs below it
 * when there is no room above ([hangsBelow]).
 */
fun choosePins(
    candidates: List<MapPin>,
    focusedKey: String?,
    view: MapViewport,
    widthPx: Float,
    heightPx: Float,
    size: PinSize,
    max: Int,
): List<MapPin> {
    val ordered = candidates.filter { it.key == focusedKey } + candidates.filter { it.key != focusedKey }.sortedByDescending { it.count }
    val chosen = mutableListOf<MapPin>()
    val placed = mutableListOf<FloatArray>() // left, top, right, bottom
    for (pin in ordered) {
        if (chosen.size >= max) break
        if (!view.contains(pin.lat, pin.lon)) continue
        val photo = if (pin.key == focusedKey) size.focusedPhotoPx else size.photoPx
        val x = view.x(pin.lon) * widthPx
        val y = view.y(pin.lat) * heightPx
        val top = if (hangsBelow(y, photo, size.tipPx)) y else y - photo - size.tipPx
        val label = if (pin.key == focusedKey) size.labelPx else 0f
        val box = floatArrayOf(x - photo / 2 - size.gapPx, top - size.gapPx, x + photo / 2 + size.gapPx, top + photo + size.tipPx + label + size.gapPx)
        if (placed.any { it[0] < box[2] && box[0] < it[2] && it[1] < box[3] && box[1] < it[3] }) continue
        chosen += pin
        placed += box
    }
    return chosen
}

/** Whether a photo hangs below its place at [yPx] from the top of the map: when it would not fit above. */
fun hangsBelow(yPx: Float, photoPx: Float, tipPx: Float): Boolean = yPx < photoPx + tipPx + 8f

/** The pin of a country: its picture at its place with the most photos (a real place, not a middle that may lie in the sea). */
fun PlaceCountry.pin() = MapPin("country|$name", cities.first().lat, cities.first().lon, coverAssetId, name, count)

fun MapRegion.pin() = MapPin("region|$state", cities.first().lat, cities.first().lon, coverAssetId, state, count)

fun PlaceCity.pin() = MapPin("city|$city|$country", lat, lon, coverAssetId, city, count)

/**
 * Where a pin's photo of [photoPx] starts on a map of [mapWidthPx] when its tip (half as wide as [tipHalfPx]) is on the
 * place at [spotXPx]: centred over the place, moved inside the map near its edges, but never so far that the tip
 * leaves the straight part of the photo's edge (past its corner of [cornerPx]). The tip itself never moves.
 */
fun pinPhotoLeft(spotXPx: Float, photoPx: Float, mapWidthPx: Float, tipHalfPx: Float, cornerPx: Float): Float {
    val inMap = (spotXPx - photoPx / 2).coerceIn(0f, (mapWidthPx - photoPx).coerceAtLeast(0f))
    val reach = (photoPx / 2 - tipHalfPx - cornerPx).coerceAtLeast(0f)
    return inMap.coerceIn(spotXPx - photoPx / 2 - reach, spotXPx - photoPx / 2 + reach)
}
