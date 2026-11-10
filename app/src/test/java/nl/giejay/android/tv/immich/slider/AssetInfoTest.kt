package nl.giejay.android.tv.immich.slider

import com.google.common.truth.Truth.assertThat
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.model.Album
import nl.giejay.android.tv.immich.api.model.Asset
import nl.giejay.android.tv.immich.api.model.AssetExifInfo
import org.junit.Test
import java.util.Date

class AssetInfoTest {
    private fun asset(exif: AssetExifInfo?) = Asset("id", "IMAGE", null, exif, null, null, null, null, null, null, "IMG_1.jpg")
    private fun exif(
        make: String? = null, model: String? = null, city: String? = null, country: String? = null,
        w: Int? = null, h: Int? = null, size: Long? = null,
    ) = AssetExifInfo(null, null, w, h, city, country, null, make, model, fileSizeInByte = size)

    @Test
    fun `an asset without exif only yields the file name`() {
        val info = buildAssetInfo(asset(null), emptyList()) { "x" }
        assertThat(info.fileName).isEqualTo("IMG_1.jpg")
        assertThat(info.rows).isEmpty()
    }

    @Test
    fun `rows come in display order and empty ones are left out`() {
        val album = Album("Holiday", "", "a", null, Date(0), null, 0)
        val info = buildAssetInfo(
            asset(exif(make = "Google", model = "Pixel 8", city = "Bern", country = "Switzerland", w = 4000, h = 3000, size = 5 * 1024 * 1024)),
            listOf(album)
        ) { "x" }
        assertThat(info.rows.map { it.label }).containsExactly(
            R.string.info_camera, R.string.info_location, R.string.info_resolution, R.string.info_file_size, R.string.info_albums
        ).inOrder()
        assertThat(info.rows.first { it.label == R.string.info_albums }.value).isEqualTo("Holiday")
    }

    @Test
    fun `camera name does not repeat the make`() {
        assertThat(cameraName("Canon", "Canon EOS R5")).isEqualTo("Canon EOS R5")
        assertThat(cameraName("Google", "Pixel 8")).isEqualTo("Google Pixel 8")
        assertThat(cameraName(null, null)).isNull()
        assertThat(cameraName("Sony", null)).isEqualTo("Sony")
    }

    @Test
    fun `exposure shows the known parts`() {
        assertThat(exposureSummary(1.8, "1/120", 100, 6.9)).isEqualTo("f/1.8 · 1/120 s · ISO 100 · 6.9 mm")
        assertThat(exposureSummary(2.0, null, null, 50.0)).isEqualTo("f/2 · 50 mm")
        assertThat(exposureSummary(null, null, null, null)).isNull()
    }

    @Test
    fun `resolution and file size are formatted`() {
        assertThat(resolutionSummary(4032, 3024)).isEqualTo("4032 × 3024 · 12.2 MP")
        assertThat(resolutionSummary(0, 10)).isNull()
        assertThat(formatFileSize(900)).isEqualTo("900 B")
        assertThat(formatFileSize(1536)).isEqualTo("1.5 KB")
        assertThat(formatFileSize(5L * 1024 * 1024)).isEqualTo("5.0 MB")
    }

    @Test
    fun `location skips blanks and duplicates`() {
        assertThat(locationName("Bern", null, "Switzerland")).isEqualTo("Bern, Switzerland")
        assertThat(locationName("Monaco", null, "Monaco")).isEqualTo("Monaco")
        assertThat(locationName(" ", null, null)).isNull()
    }
}
