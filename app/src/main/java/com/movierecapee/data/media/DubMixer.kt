package com.movierecapee.data.media

import kotlin.math.min
import kotlin.math.roundToInt

/** Puts every spoken line at its subtitle time and builds one voice track the length of the video. */
object DubMixer {
    const val RATE = 24000
    const val MAX_SPEED_UP = 1.4

    class Placed(val startMs: Long, val slotMs: Long, val clip: ShortArray, val rate: Int)

    /** Linear resampling. speed > 1 makes the clip shorter (and slightly higher). */
    fun resample(src: ShortArray, fromRate: Int, toRate: Int, speed: Double = 1.0): ShortArray {
        if (src.isEmpty()) return src
        val ratio = fromRate.toDouble() / toRate * speed
        val outLen = (src.size / ratio).toInt()
        val out = ShortArray(outLen)
        for (i in 0 until outLen) {
            val pos = i * ratio
            val i0 = pos.toInt()
            val i1 = min(i0 + 1, src.size - 1)
            val f = pos - i0
            out[i] = (src[i0] * (1 - f) + src[i1] * f).roundToInt().toShort()
        }
        return out
    }

    fun speedFor(clipMs: Double, slotMs: Long): Double =
        if (slotMs > 0 && clipMs > slotMs) min(clipMs / slotMs, MAX_SPEED_UP) else 1.0

    fun mix(totalMs: Long, clips: List<Placed>): ShortArray {
        val total = (totalMs * RATE / 1000).toInt().coerceAtLeast(1)
        val buf = ShortArray(total)
        for (c in clips) {
            val clipMs = c.clip.size * 1000.0 / c.rate
            val r = resample(c.clip, c.rate, RATE, speedFor(clipMs, c.slotMs))
            val off = (c.startMs * RATE / 1000).toInt()
            for (i in r.indices) {
                val idx = off + i
                if (idx >= total) break
                if (idx < 0) continue
                val sum = buf[idx] + r[i]
                buf[idx] = sum.coerceIn(-32768, 32767).toShort()
            }
        }
        return buf
    }
}
