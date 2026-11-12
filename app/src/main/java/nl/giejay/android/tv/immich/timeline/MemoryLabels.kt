package nl.giejay.android.tv.immich.timeline

import android.content.Context
import nl.giejay.android.tv.immich.R
import nl.giejay.android.tv.immich.api.model.Memory
import java.time.Year

/**
 * Labels for on-this-day memories: pure year math plus the localized "N years ago" text.
 */
object MemoryLabels {

    /** Years between [nowYear] and [memoryYear], never negative. */
    fun yearsAgo(nowYear: Int, memoryYear: Int): Int =
        (nowYear - memoryYear).coerceAtLeast(0)

    fun yearsAgoLabel(context: Context, memory: Memory): String {
        val years = yearsAgo(Year.now().value, memory.data.year)
        return context.resources.getQuantityString(R.plurals.years_ago, years, years)
    }
}
