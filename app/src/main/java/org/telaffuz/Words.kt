package org.telaffuz

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Hazır kelimeler (assets) + kullanıcının eklediği kelimeler + gizlenen hazır kelimeler. */
class WordRepo(ctx: Context) {
    private val customFile = File(ctx.filesDir, "custom_words.json")
    private val hiddenFile = File(ctx.filesDir, "hidden_words.json")
    private val builtIn: List<Word> =
        parseWords(ctx.assets.open("words.json").bufferedReader(Charsets.UTF_8).use { it.readText() }, false)
    val custom = mutableListOf<Word>()
    val hidden = mutableSetOf<String>()

    init {
        try {
            if (customFile.exists()) custom.addAll(parseWords(customFile.readText(), true))
        } catch (e: Exception) {
            custom.clear()
        }
        try {
            if (hiddenFile.exists()) {
                val a = JSONArray(hiddenFile.readText())
                for (i in 0 until a.length()) hidden.add(a.getString(i))
            }
        } catch (e: Exception) {
            hidden.clear()
        }
    }

    fun all(): List<Word> {
        val names = custom.map { it.word }.toSet()
        return builtIn.filter { it.word !in hidden && it.word !in names } + custom
    }

    fun exists(word: String): Boolean = all().any { it.word.equals(word, ignoreCase = true) }

    fun add(w: Word): Boolean {
        if (exists(w.word)) return false
        custom.add(w)
        saveCustom()
        return true
    }

    fun remove(w: Word) {
        if (w.custom) {
            custom.removeAll { it.word == w.word }
            saveCustom()
        } else {
            hidden.add(w.word)
            saveHidden()
        }
    }

    fun restoreDefaults() {
        hidden.clear()
        saveHidden()
    }

    private fun saveCustom() {
        try {
            val arr = JSONArray()
            for (w in custom) {
                arr.put(
                    JSONObject()
                        .put("word", w.word).put("ipa", w.ipa).put("syl", w.syl).put("tr", w.tr)
                        .put("mean", w.mean).put("ex", w.ex).put("level", "Benim").put("focus", w.focus)
                        .put("same", JSONArray()).put("errors", JSONObject())
                )
            }
            customFile.writeText(arr.toString())
        } catch (e: Exception) {
        }
    }

    private fun saveHidden() {
        try {
            val arr = JSONArray()
            for (h in hidden) arr.put(h)
            hiddenFile.writeText(arr.toString())
        } catch (e: Exception) {
        }
    }
}
