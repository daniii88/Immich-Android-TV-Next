package nl.giejay.android.tv.immich.ui.map

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType

/** The coastlines and borders as paths in map space: x is the longitude, y the negative latitude, in degrees. */
class MapVectors(val land: Path, val borders: Path)

/** Builds the paths from the parts [nl.giejay.android.tv.immich.map.WorldVectors.parse] returns. */
fun buildMapVectors(landParts: List<FloatArray>, borderParts: List<FloatArray>): MapVectors {
    fun path(parts: List<FloatArray>, closed: Boolean) = Path().apply {
        // exterior rings and holes (lakes, inland seas) alike: the even-odd rule leaves the holes empty
        fillType = PathFillType.EvenOdd
        for (part in parts) {
            moveTo(part[0], -part[1])
            for (i in 1 until part.size / 2) lineTo(part[i * 2], -part[i * 2 + 1])
            if (closed) close()
        }
    }
    return MapVectors(land = path(landParts, closed = true), borders = path(borderParts, closed = false))
}
