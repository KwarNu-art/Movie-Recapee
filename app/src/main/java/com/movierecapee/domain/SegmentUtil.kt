package com.movierecapee.domain

import java.util.Locale

object SegmentUtil {
    /** Cleans raw recogniser output: drops empty lines, sorts, fixes impossible times and numbers the lines. */
    fun normalize(raw: List<RawSegment>, durationMs: Long): List<SubtitleSegment> {
        val out = ArrayList<SubtitleSegment>()
        raw.map { it.copy(text = it.text.trim()) }
            .filter { it.text.isNotEmpty() }
            .sortedBy { it.startMs }
            .forEach { r ->
                val start = r.startMs.coerceAtLeast(0)
                if (durationMs > 0 && start >= durationMs) return@forEach
                var end = r.endMs
                if (end <= start) end = start + maxOf(1200L, r.text.length * 60L)
                if (durationMs > 0) end = minOf(end, durationMs)
                if (end <= start) return@forEach
                out += SubtitleSegment(out.size + 1, start, end, r.text)
            }
        return out
    }

    fun renumber(list: List<SubtitleSegment>): List<SubtitleSegment> =
        list.sortedBy { it.startMs }.mapIndexed { i, s -> s.copy(id = i + 1) }

    fun timecode(ms: Long, withMillis: Boolean = true): String {
        val t = ms.coerceAtLeast(0)
        val h = t / 3_600_000
        val m = (t / 60_000) % 60
        val s = (t / 1000) % 60
        val milli = t % 1000
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else if (withMillis) String.format(Locale.US, "%02d:%02d.%d", m, s, milli / 100)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    fun parseSeconds(text: String): Long? {
        val v = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        return if (v < 0) null else (v * 1000).toLong()
    }
}
