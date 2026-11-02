package com.movierecapee.util

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.movierecapee.domain.RawSegment

/** Reads JSON out of AI answers that may be wrapped in text or code fences. */
object JsonExtract {
    private fun clean(text: String): String =
        text.trim().removePrefix("```json").removePrefix("```JSON").removePrefix("```").removeSuffix("```").trim()

    fun array(text: String): JsonArray? {
        val c = clean(text)
        val s = c.indexOf('[')
        val e = c.lastIndexOf(']')
        if (s < 0 || e <= s) return null
        return try { JsonParser.parseString(c.substring(s, e + 1)).asJsonArray } catch (_: Exception) { null }
    }

    fun obj(text: String): JsonObject? {
        val c = clean(text)
        val s = c.indexOf('{')
        val e = c.lastIndexOf('}')
        if (s < 0 || e <= s) return null
        return try { JsonParser.parseString(c.substring(s, e + 1)).asJsonObject } catch (_: Exception) { null }
    }

    /** A list of strings from {"translations":[...]} or a bare [...] array. */
    fun stringList(text: String): List<String>? {
        val fromObject = obj(text)?.let { o ->
            (o.get("translations") ?: o.entrySet().firstOrNull { it.value.isJsonArray }?.value)?.takeIf { it.isJsonArray }?.asJsonArray
        }
        val arr = fromObject ?: array(text) ?: return null
        return arr.map { el ->
            when {
                el.isJsonPrimitive -> el.asString
                el.isJsonObject -> el.asJsonObject.entrySet().firstOrNull { it.value.isJsonPrimitive }?.value?.asString ?: ""
                else -> ""
            }
        }
    }

    fun seconds(el: JsonElement?): Double? {
        if (el == null || !el.isJsonPrimitive) return null
        val p = el.asJsonPrimitive
        if (p.isNumber) return p.asDouble
        val t = p.asString.trim()
        t.toDoubleOrNull()?.let { return it }
        val parts = t.split(':')
        if (parts.size in 2..3) {
            var total = 0.0
            for (part in parts) total = total * 60 + (part.replace(',', '.').toDoubleOrNull() ?: return null)
            return total
        }
        return null
    }

    fun segments(text: String): List<RawSegment>? {
        val arr = array(text) ?: return null
        val out = ArrayList<RawSegment>()
        for (el in arr) {
            if (!el.isJsonObject) continue
            val o = el.asJsonObject
            val start = seconds(o.get("start")) ?: continue
            val end = seconds(o.get("end")) ?: (start + 2.0)
            val t = o.get("text")?.takeIf { it.isJsonPrimitive }?.asString ?: continue
            out += RawSegment((start * 1000).toLong(), (end * 1000).toLong(), t)
        }
        return out
    }
}
