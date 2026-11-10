package nl.giejay.android.tv.immich.slider

import androidx.annotation.StringRes
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.model.Album
import nl.giejay.android.tv.immich.api.model.Asset
import java.util.Date
import java.util.Locale

/** One line of the viewer's info panel: a label and its (already formatted) value. */
data class InfoRow(@StringRes val label: Int, val value: String)

/** What the info panel shows for one asset. */
data class AssetInfo(val fileName: String?, val rows: List<InfoRow>)

/**
 * The rows of the info panel, in display order; rows without data are left out. Kept free of Android classes
 * (the date format comes in as [formatDate]) so it can be unit tested.
 */
fun buildAssetInfo(asset: Asset, albums: List<Album>, formatDate: (Date) -> String): AssetInfo {
    val exif = asset.exifInfo
    val rows = buildList {
        (exif?.dateTimeOriginal ?: asset.fileCreatedAt)?.let { add(InfoRow(R.string.info_date, formatDate(it))) }
        exif?.description?.takeIf { it.isNotBlank() }?.let { add(InfoRow(R.string.info_description, it)) }
        cameraName(exif?.make, exif?.model)?.let { add(InfoRow(R.string.info_camera, it)) }
        exif?.lensModel?.takeIf { it.isNotBlank() }?.let { add(InfoRow(R.string.info_lens, it)) }
        exposureSummary(exif?.fNumber, exif?.exposureTime, exif?.iso, exif?.focalLength)?.let { add(InfoRow(R.string.info_exposure, it)) }
        locationName(exif?.city, exif?.state, exif?.country)?.let { add(InfoRow(R.string.info_location, it)) }
        resolutionSummary(exif?.exifImageWidth, exif?.exifImageHeight)?.let { add(InfoRow(R.string.info_resolution, it)) }
        exif?.fileSizeInByte?.takeIf { it > 0 }?.let { add(InfoRow(R.string.info_file_size, formatFileSize(it))) }
        if (albums.isNotEmpty()) add(InfoRow(R.string.info_albums, albums.joinToString("\n") { it.albumName }))
    }
    return AssetInfo(asset.originalFileName, rows)
}

/** "Google Pixel 8", the make is left out when the model already starts with it ("Canon" + "Canon EOS R5"). */
internal fun cameraName(make: String?, model: String?): String? {
    val m = make?.trim().orEmpty()
    val d = model?.trim().orEmpty()
    return when {
        m.isEmpty() && d.isEmpty() -> null
        d.isEmpty() -> m
        m.isEmpty() || d.startsWith(m, ignoreCase = true) -> d
        else -> "$m $d"
    }
}

/** "f/1.8 · 1/120 s · ISO 100 · 6.9 mm" with the parts that are known. */
internal fun exposureSummary(fNumber: Double?, exposureTime: String?, iso: Int?, focalLength: Double?): String? {
    val parts = listOfNotNull(
        fNumber?.let { "f/${trimmed(it)}" },
        exposureTime?.takeIf { it.isNotBlank() }?.let { "$it s" },
        iso?.let { "ISO $it" },
        focalLength?.let { "${trimmed(it)} mm" },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** "4032 × 3024 · 12.2 MP". */
internal fun resolutionSummary(width: Int?, height: Int?): String? {
    if (width == null || height == null || width <= 0 || height <= 0) return null
    val megapixels = width.toLong() * height / 1_000_000.0
    val mp = if (megapixels >= 0.1) " · ${String.format(Locale.US, "%.1f", megapixels)} MP" else ""
    return "$width × $height$mp"
}

internal fun locationName(city: String?, state: String?, country: String?): String? =
    listOf(city, state, country).mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.distinct()
        .takeIf { it.isNotEmpty() }?.joinToString(", ")

/** Binary units like the Immich web client: 1536 -> "1.5 KB", 5 MiB -> "5.0 MB". */
internal fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unit])
}

private fun trimmed(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
