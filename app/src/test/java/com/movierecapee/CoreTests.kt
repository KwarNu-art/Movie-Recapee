package com.movierecapee

import com.movierecapee.data.media.DubMixer
import com.movierecapee.data.media.WavUtil
import com.movierecapee.domain.DisplayMode
import com.movierecapee.domain.RawSegment
import com.movierecapee.domain.SegmentUtil
import com.movierecapee.domain.SrtUtil
import com.movierecapee.domain.SubtitleSegment
import com.movierecapee.util.JsonExtract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CoreTests {
    @Test fun srtTimestampsAndLayout() {
        assertEquals("00:01:05,250", SrtUtil.stamp(65_250))
        val segs = listOf(SubtitleSegment(1, 1000, 3500, "Hello", "မင်္ဂလာပါ"))
        val dual = SrtUtil.format(segs, DisplayMode.DUAL)
        assertEquals("1\n00:00:01,000 --> 00:00:03,500\nမင်္ဂလာပါ\nHello\n\n", dual)
        assertEquals("1\n00:00:01,000 --> 00:00:03,500\nHello\n\n", SrtUtil.format(segs, DisplayMode.SOURCE))
    }

    @Test fun textForUsesSourceWhenNotTranslated() {
        val s = SubtitleSegment(1, 0, 1000, "Hi")
        assertEquals(listOf("Hi"), s.textFor(DisplayMode.TARGET))
        assertEquals(listOf("Hi"), s.textFor(DisplayMode.DUAL))
        assertEquals("Hi", s.spokenText())
    }

    @Test fun normalizeCleansRawSegments() {
        val raw = listOf(
            RawSegment(5000, 6000, " second "),
            RawSegment(0, 0, "first"),
            RawSegment(2000, 3000, "   "),
            RawSegment(99_000, 100_000, "after the end")
        )
        val out = SegmentUtil.normalize(raw, 10_000)
        assertEquals(listOf("first", "second"), out.map { it.source })
        assertEquals(listOf(1, 2), out.map { it.id })
        assertTrue(out[0].endMs > out[0].startMs)
    }

    @Test fun timeParsing() {
        assertEquals(1500L, SegmentUtil.parseSeconds("1,5"))
        assertNull(SegmentUtil.parseSeconds("abc"))
        assertEquals("01:05.2", SegmentUtil.timecode(65_250))
    }

    @Test fun jsonExtractHandlesFencesAndShapes() {
        val segs = JsonExtract.segments("```json\n[{\"start\":1.5,\"end\":\"00:03\",\"text\":\"Hi\"},{\"start\":4,\"text\":\"Bye\"}]\n```")
        assertNotNull(segs)
        assertEquals(2, segs!!.size)
        assertEquals(1500L, segs[0].startMs)
        assertEquals(3000L, segs[0].endMs)
        assertEquals(listOf("a", "b"), JsonExtract.stringList("{\"translations\":[\"a\",\"b\"]}"))
        assertEquals(listOf("a", "b"), JsonExtract.stringList("Here: [\"a\",\"b\"]"))
        assertNull(JsonExtract.stringList("no json here"))
    }

    @Test fun resampleChangesLengthBySpeed() {
        val src = ShortArray(24000) { 1000 }
        assertEquals(24000, DubMixer.resample(src, 24000, 24000).size)
        assertEquals(12000, DubMixer.resample(src, 24000, 24000, 2.0).size)
        assertEquals(48000, DubMixer.resample(src, 12000, 24000).size)
    }

    @Test fun mixPlacesClipAtItsTimeAndSpeedsUpLongClips() {
        val clip = ShortArray(24000) { 1000 }            // one second
        val track = DubMixer.mix(5000, listOf(DubMixer.Placed(2000, 500, clip, 24000)))
        assertEquals(5 * DubMixer.RATE, track.size)
        assertEquals(0, track[100].toInt())
        assertEquals(1000, track[2000 * DubMixer.RATE / 1000 + 10].toInt())
        // 1 s into a 0.5 s slot is capped at 1.4x, so it lasts about 0.714 s
        val lastLoud = track.indexOfLast { it.toInt() != 0 }
        val lengthMs = (lastLoud - 2000 * DubMixer.RATE / 1000) * 1000.0 / DubMixer.RATE
        assertEquals(714.0, lengthMs, 10.0)
    }

    @Test fun mixClampsOverlaps() {
        val loud = ShortArray(100) { 30000 }
        val track = DubMixer.mix(1000, listOf(DubMixer.Placed(0, 1000, loud, 24000), DubMixer.Placed(0, 1000, loud, 24000)))
        assertEquals(32767, track[10].toInt())
    }

    @Test fun wavRoundTrip() {
        val f = File.createTempFile("t", ".wav")
        try {
            val samples = ShortArray(500) { (it * 7 - 1000).toShort() }
            WavUtil.write(f, samples, 22050)
            val clip = WavUtil.read(f)
            assertEquals(22050, clip.rate)
            assertEquals(samples.toList(), clip.samples.toList())
        } finally { f.delete() }
    }
}
