package com.movierecapee.data.media

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmClip(val samples: ShortArray, val rate: Int)

object WavUtil {
    fun write(file: File, samples: ShortArray, rate: Int) {
        val dataLen = samples.size * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + dataLen).put("WAVE".toByteArray())
        header.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        header.put("data".toByteArray()).putInt(dataLen)
        FileOutputStream(file).use { out ->
            out.write(header.array())
            val chunk = ByteBuffer.allocate(64 * 1024).order(ByteOrder.LITTLE_ENDIAN)
            var i = 0
            while (i < samples.size) {
                chunk.clear()
                while (i < samples.size && chunk.remaining() >= 2) chunk.putShort(samples[i++])
                out.write(chunk.array(), 0, chunk.position())
            }
        }
    }

    /** Reads a 16-bit PCM WAV. Stereo is mixed down to mono. */
    fun read(file: File): PcmClip = parse(file.readBytes())

    fun parse(bytes: ByteArray): PcmClip {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes.size < 44 || String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") throw IOException("Not a WAV file.")
        var pos = 12
        var rate = 0
        var channels = 1
        var bits = 16
        var dataStart = -1
        var dataLen = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            val len = bb.getInt(pos + 4)
            val body = pos + 8
            if (id == "fmt ") {
                channels = bb.getShort(body + 2).toInt()
                rate = bb.getInt(body + 4)
                bits = bb.getShort(body + 14).toInt()
            } else if (id == "data") {
                dataStart = body
                dataLen = if (len < 0 || body + len > bytes.size) bytes.size - body else len
                break
            }
            pos = body + len + (len and 1)
        }
        if (dataStart < 0 || rate <= 0) throw IOException("The WAV file is incomplete.")
        if (bits != 16) throw IOException("Only 16-bit audio is supported.")
        val frames = dataLen / (2 * channels)
        val out = ShortArray(frames)
        for (f in 0 until frames) {
            var sum = 0
            for (c in 0 until channels) sum += bb.getShort(dataStart + (f * channels + c) * 2).toInt()
            out[f] = (sum / channels).toShort()
        }
        return PcmClip(out, rate)
    }

    fun pcm16le(bytes: ByteArray): ShortArray {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return ShortArray(bytes.size / 2) { bb.getShort(it * 2) }
    }
}
