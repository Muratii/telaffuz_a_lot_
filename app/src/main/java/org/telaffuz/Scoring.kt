package org.telaffuz

import org.json.JSONArray
import kotlin.math.roundToInt

data class Word(
    val word: String,
    val ipa: String,
    val syl: String,
    val tr: String,
    val mean: String,
    val ex: String,
    val level: String,
    val focus: String,
    val same: List<String>,
    val errors: Map<String, String>,
    val custom: Boolean = false
)

data class Result(
    val score: Int,
    val heard: String,
    val msg: String,
    val rank: Int?,
    val tip: String
)

val FOCUS_NAMES = mapOf(
    "th" to "TH (θ)", "w" to "W", "v" to "V", "r" to "R", "l" to "L", "ae" to "Æ",
    "schwa" to "Schwa (ə)", "ih" to "Kısa / uzun i", "uu" to "Kısa / uzun u",
    "uh" to "ʌ sesi", "eh" to "E sesi", "none" to "Genel"
)

val TIPS = mapOf(
    "th" to "TH sesi: dilinin ucunu üst ve alt dişlerinin arasına hafifçe getir ve havayı üfle. T, S veya F gibi söyleme.",
    "w" to "W sesi: dudaklarını yuvarlayıp öne uzat, dişlerini dudağa değdirme. V gibi söyleme.",
    "v" to "V sesi: üst dişlerin alt dudağa hafifçe değsin ve ses titreşsin. B veya W gibi söyleme.",
    "r" to "R sesi: dilini damağa değdirme, geriye doğru kıvır. Türkçe R gibi titretme.",
    "l" to "L sesi: dil ucun üst dişlerin arkasına değsin. R ile karıştırma.",
    "ae" to "Æ sesi: ağzını geniş aç, \"a\" ile \"e\" arası bir ses çıkar. Düz E gibi söyleme.",
    "schwa" to "Schwa /ə/: vurgusuz hecelerde çok kısa ve gevşek bir \"ı\" sesi. Her heceyi tam okuma, vurguyu doğru heceye ver.",
    "ih" to "Kısa /ɪ/ gevşek ve kısadır, uzun /iː/ gergin ve uzundur. İkisi arasındaki süre farkına dikkat et.",
    "uu" to "Kısa /ʊ/ gevşek ve kısadır, uzun /uː/ dudakları iyice yuvarlayarak uzatılır.",
    "uh" to "/ʌ/ sesi: ağzını az aç, kısa ve gevşek söyle. Æ sesi ile karıştırma.",
    "eh" to "/e/ sesi: Æ kadar geniş değil, dar ve kısa.",
    "none" to "Kelimeyi net ve vurgusuna dikkat ederek söyle."
)

fun parseWords(txt: String, custom: Boolean): List<Word> {
    val arr = JSONArray(txt)
    val out = ArrayList<Word>()
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        val sameArr = o.optJSONArray("same")
        val same = if (sameArr == null) emptyList() else (0 until sameArr.length()).map { sameArr.getString(it) }
        val errs = HashMap<String, String>()
        val eo = o.optJSONObject("errors")
        if (eo != null) {
            val keys = eo.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                errs[k] = eo.getString(k)
            }
        }
        out.add(
            Word(
                o.getString("word"), o.optString("ipa", ""), o.optString("syl", ""), o.optString("tr", ""),
                o.optString("mean", ""), o.optString("ex", ""), o.optString("level", if (custom) "Benim" else "A1"),
                o.optString("focus", "none"), same, errs, custom
            )
        )
    }
    return out
}

fun band(score: Int): String = when {
    score >= 90 -> "Mükemmel! Telaffuzun hedef sese çok yakın."
    score >= 75 -> "Çok iyi. Küçük bir düzeltmeyle daha doğal olabilir."
    score >= 60 -> "Fena değil. Bazı sesleri daha net söylemelisin."
    score >= 40 -> "Biraz daha çalışmalıyız."
    else -> "Kelimeyi tekrar dinleyelim ve birlikte çalışalım."
}

private fun clean(s: String): String =
    s.lowercase().replace(Regex("[^a-z' ]"), "").trim()

private fun similarity(a: String, b: String): Double {
    if (a.isEmpty() && b.isEmpty()) return 1.0
    val d = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) d[i][0] = i
    for (j in 0..b.length) d[0][j] = j
    for (i in 1..a.length) {
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
        }
    }
    return 1.0 - d[a.length][b.length].toDouble() / maxOf(a.length, b.length)
}

/**
 * Estimates a score from speech-recognition output (alternatives + confidence)
 * and known Turkish-speaker confusion pairs. This is NOT phoneme-level analysis.
 */
fun evaluate(e: Word, results: List<String>, confs: List<Float>): Result {
    val ok = setOf(e.word) + e.same
    val res = results.map { clean(it) }.filter { it.isNotEmpty() }

    fun match(r: String): Boolean {
        val toks = r.split(" ").filter { it.isNotEmpty() }
        return r in ok || (toks.size in 1..3 && toks.any { it in ok })
    }

    val idx = res.indexOfFirst { match(it) }
    val rank: Int? = if (idx < 0) null else idx
    val heard = res.firstOrNull() ?: ""
    var msg = ""
    val score: Int

    if (rank == 0) {
        val conf = confs.firstOrNull()?.takeIf { it > 0f } ?: 0.7f
        score = (75 + 25 * minOf(conf, 1f)).roundToInt()
    } else if (rank != null) {
        score = maxOf(45, 62 - 4 * (rank - 1))
        msg = e.errors[heard] ?: ""
    } else {
        val sim = res.maxOfOrNull { similarity(it, e.word) } ?: 0.0
        score = (15 + 35 * sim).toInt()
        for (r in res) {
            val m = e.errors[r]
            if (m != null) {
                msg = m
                break
            }
        }
    }
    return Result(score, heard, msg, rank, TIPS[e.focus] ?: "")
}
