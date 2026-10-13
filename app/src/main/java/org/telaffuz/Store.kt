package org.telaffuz

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.TimeZone
import kotlin.math.roundToInt

data class Attempt(val w: String, val f: String, val s: Int, val t: Long)

data class Stats(
    val unique: Int,
    val avg: Int?,
    val week: Int?,
    val weak: String?,
    val streak: Int,
    val recent: List<Attempt>
)

class Store(private val file: File) {
    val attempts = mutableListOf<Attempt>()

    init {
        try {
            if (file.exists()) {
                val arr = JSONArray(file.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    attempts.add(Attempt(o.getString("w"), o.getString("f"), o.getInt("s"), o.getLong("t")))
                }
            }
        } catch (e: Exception) {
            attempts.clear()
        }
    }

    private fun save() {
        try {
            val arr = JSONArray()
            for (a in attempts) {
                arr.put(JSONObject().put("w", a.w).put("f", a.f).put("s", a.s).put("t", a.t))
            }
            file.writeText(arr.toString())
        } catch (e: Exception) {
            // storage problems must not crash the app
        }
    }

    fun add(word: String, focus: String, score: Int) {
        attempts.add(Attempt(word, focus, score, System.currentTimeMillis()))
        while (attempts.size > 2000) attempts.removeAt(0)
        save()
    }

    fun clear() {
        attempts.clear()
        save()
    }

    fun wordAvg(word: String, n: Int = 3): Double? {
        val v = attempts.filter { it.w == word }.takeLast(n).map { it.s }
        return if (v.isEmpty()) null else v.average()
    }

    private fun dayOf(t: Long): Long = (t + TimeZone.getDefault().getOffset(t)) / 86400000L

    fun stats(): Stats {
        val a = attempts
        val recent = a.takeLast(10).reversed()
        if (a.isEmpty()) return Stats(0, null, null, null, 0, recent)

        val now = System.currentTimeMillis()
        val week7 = 7L * 86400000L
        val cur = a.filter { now - it.t < week7 }.map { it.s }
        val prev = a.filter { now - it.t >= week7 && now - it.t < 2 * week7 }.map { it.s }
        val week = if (cur.isNotEmpty() && prev.isNotEmpty()) (cur.average() - prev.average()).roundToInt() else null

        val weak = a.groupBy { it.f }
            .filter { it.value.size >= 2 && it.key != "none" }
            .minByOrNull { entry -> entry.value.map { it.s }.average() }
            ?.key

        val days = a.map { dayOf(it.t) }.toSet()
        val today = dayOf(now)
        var d = if (today in days) today else today - 1
        var streak = 0
        while (d in days) {
            streak++
            d--
        }
        return Stats(
            unique = a.map { it.w }.toSet().size,
            avg = a.map { it.s }.average().roundToInt(),
            week = week,
            weak = weak,
            streak = streak,
            recent = recent
        )
    }
}
