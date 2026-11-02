package com.movierecapee.domain

import java.util.Locale

object SrtUtil {
    fun stamp(ms: Long): String {
        val t = ms.coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", t / 3_600_000, (t / 60_000) % 60, (t / 1000) % 60, t % 1000)
    }

    fun format(segments: List<SubtitleSegment>, mode: DisplayMode): String {
        val sb = StringBuilder()
        var n = 1
        for (s in segments.sortedBy { it.startMs }) {
            val lines = s.textFor(mode)
            if (lines.isEmpty()) continue
            sb.append(n++).append('\n')
            sb.append(stamp(s.startMs)).append(" --> ").append(stamp(s.endMs)).append('\n')
            lines.forEach { sb.append(it).append('\n') }
            sb.append('\n')
        }
        return sb.toString()
    }
}
