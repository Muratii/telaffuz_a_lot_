package org.telaffuz

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.Locale
import kotlin.random.Random

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private val BG = Color.parseColor("#0F1729")
    private val CARD = Color.parseColor("#1C2942")
    private val ACCENT = Color.parseColor("#1AC7C2")
    private val FG = Color.parseColor("#EDF4F9")
    private val MUTED = Color.parseColor("#9FB0C7")
    private val WARN = Color.parseColor("#FFB84D")
    private val BAD = Color.parseColor("#FF7373")
    private val GOOD = Color.parseColor("#59E68C")
    private val REQ = 4711

    private lateinit var repo: WordRepo
    private lateinit var store: Store
    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    private var screen = "home"
    private var mode = "free"
    private var current: Word? = null
    private var queue: List<Word> = emptyList()
    private var qi = 0
    private val session = LinkedHashMap<String, Int>()
    private var confirmClear = false
    private var msgView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = WordRepo(this)
        store = Store(File(filesDir, "progress.json"))
        tts = TextToSpeech(this, this)
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
        showHome()
    }

    override fun onDestroy() {
        try {
            tts.stop()
            tts.shutdown()
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val r = tts.setLanguage(Locale.US)
            ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (screen != "home") showHome() else super.onBackPressed()
    }

    // ---------- UI helpers ----------
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun tv(
        text: String, size: Float = 16f, color: Int = FG,
        bold: Boolean = false, center: Boolean = false
    ): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        if (center) t.gravity = Gravity.CENTER_HORIZONTAL
        t.setPadding(0, dp(4), 0, dp(4))
        t.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return t
    }

    private fun button(
        text: String, bg: Int = CARD, fg: Int = FG, h: Int = 56, onClick: () -> Unit
    ): Button {
        val b = Button(this)
        b.text = text
        b.textSize = 17f
        b.isAllCaps = false
        b.setTextColor(fg)
        val shape = GradientDrawable()
        shape.setColor(bg)
        shape.cornerRadius = dp(14).toFloat()
        b.background = shape
        b.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(h))
        lp.topMargin = dp(8)
        b.layoutParams = lp
        return b
    }

    private fun field(hint: String, multiline: Boolean = false): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setHintTextColor(MUTED)
        e.setTextColor(FG)
        e.textSize = 16f
        if (multiline) {
            e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            e.minLines = 6
            e.gravity = Gravity.TOP
        } else {
            e.inputType = InputType.TYPE_CLASS_TEXT
            e.setSingleLine(true)
        }
        val shape = GradientDrawable()
        shape.setColor(CARD)
        shape.cornerRadius = dp(10).toFloat()
        e.background = shape
        e.setPadding(dp(12), dp(12), dp(12), dp(12))
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(8)
        e.layoutParams = lp
        return e
    }

    private fun page(title: String, name: String, backTo: () -> Unit = { showHome() }): LinearLayout {
        screen = name
        msgView = null
        val outer = LinearLayout(this)
        outer.orientation = LinearLayout.VERTICAL
        outer.setBackgroundColor(BG)

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(12), dp(14), dp(12), dp(6))
        if (name != "home") {
            val back = button("Geri", CARD, FG, 44) { backTo() }
            back.layoutParams = LinearLayout.LayoutParams(dp(84), dp(44)).apply { rightMargin = dp(12) }
            bar.addView(back)
        }
        bar.addView(tv(title, 20f, ACCENT, bold = true))
        outer.addView(bar)

        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(16), dp(8), dp(16), dp(28))
        val sv = ScrollView(this)
        sv.addView(body)
        outer.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(outer)
        return body
    }

    private fun statsLine(s: Stats): String {
        val week = s.week?.let { String.format(Locale.US, "%+d puan", it) } ?: "-"
        val weak = s.weak?.let { FOCUS_NAMES[it] } ?: "-"
        return "Toplam kelime: ${s.unique}    Ortalama: ${s.avg ?: "-"}\n" +
            "Bu hafta: $week    Seri: ${s.streak} gün\nEn çok zorlandığın ses: $weak"
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    // ---------- screens ----------
    private fun showHome() {
        val b = page("Telaffuz Koçu", "home")
        b.addView(tv("Bugün İngilizceni geliştir.", 24f, bold = true))
        b.addView(tv(statsLine(store.stats()), 16f, MUTED))
        b.addView(button("Günlük Çalışma (10 kelime)", ACCENT, BG, 72) { startDaily() })
        b.addView(button("Kelimeler") { showList() })
        b.addView(button("Kelime Ekle / Çıkar") { showManage() })
        b.addView(button("Gelişimim") { showProgress() })
        b.addView(tv("Yakında: Sesler, Minimal Pairs", 14f, MUTED))
    }

    private fun matches(w: Word, q: String): Boolean {
        val qe = q.lowercase(Locale.ENGLISH)
        val qt = q.lowercase(Locale("tr"))
        return w.word.startsWith(qe) || w.mean.lowercase(Locale("tr")).contains(qt)
    }

    private fun showList() {
        val b = page("Kelimeler", "list")
        val all = repo.all()
        if (all.isEmpty()) {
            b.addView(tv("Hiç kelime yok. Ana ekrandan \"Kelime Ekle / Çıkar\" ile ekleyebilirsin.", 16f, MUTED))
            return
        }
        val search = field("Kelime ara (İngilizce veya Türkçe)")
        b.addView(search)
        val results = LinearLayout(this)
        results.orientation = LinearLayout.VERTICAL
        b.addView(results)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                results.removeAllViews()
                val q = (s?.toString() ?: "").trim()
                if (q.isEmpty()) return
                val found = repo.all().filter { matches(it, q) }.take(30)
                if (found.isEmpty()) results.addView(tv("Sonuç yok.", 14f, MUTED))
                for (w in found) {
                    results.addView(button("${w.word}  -  ${w.mean}") { openWord(w, "free") })
                }
            }
        })
        b.addView(tv("Seviyeler", 18f, ACCENT, bold = true))
        for (lvl in listOf("A1", "A2", "B1", "Benim")) {
            val n = all.count { it.level == lvl }
            if (n == 0) continue
            val name = if (lvl == "Benim") "Benim kelimelerim" else lvl
            b.addView(button("$name  ($n kelime)") { showLevel(lvl) })
        }
    }

    private fun showLevel(lvl: String) {
        val title = if (lvl == "Benim") "Benim kelimelerim" else "Seviye $lvl"
        val b = page(title, "level", { showList() })
        for (w in repo.all().filter { it.level == lvl }) {
            val avg = store.wordAvg(w.word)
            val tail = if (avg == null) "" else "   (${Math.round(avg)})"
            b.addView(button(w.word + tail) { openWord(w, "free") })
        }
    }

    private fun deleteRow(w: Word): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val label = tv(if (w.custom) "${w.word}  (benim)" else "${w.word}  (${w.level})", 16f)
        label.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(label)
        val del = button("Sil", CARD, BAD, 44) {}
        del.layoutParams = LinearLayout.LayoutParams(dp(120), dp(44)).apply { topMargin = dp(8) }
        del.setOnClickListener {
            if (del.text.toString() == "Sil") {
                del.text = "Emin misin?"
                del.postDelayed({ if (del.text.toString() == "Emin misin?") del.text = "Sil" }, 3000)
            } else {
                repo.remove(w)
                (row.parent as? ViewGroup)?.removeView(row)
            }
        }
        row.addView(del)
        return row
    }

    private fun showManage() {
        val b = page("Kelime Ekle / Çıkar", "manage")
        b.addView(button("Yeni kelime ekle", ACCENT, BG) { showAdd() })
        b.addView(button("Toplu ekle (birçok kelime)") { showBulk() })
        if (repo.hidden.isNotEmpty()) {
            b.addView(button("Silinen hazır kelimeleri geri getir (${repo.hidden.size})") {
                repo.restoreDefaults()
                showManage()
            })
        }
        val total = repo.all().size
        b.addView(tv("Toplam $total kelime (benim: ${repo.custom.size})", 18f, ACCENT, bold = true))
        val search = field("Silmek için kelime ara")
        b.addView(search)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        b.addView(box)

        fun fill(q: String) {
            box.removeAllViews()
            val list = if (q.isEmpty()) repo.custom.toList() else repo.all().filter { matches(it, q) }.take(40)
            if (list.isEmpty()) {
                box.addView(
                    tv(
                        if (q.isEmpty()) "Kendi eklediğin kelimeler burada görünür. Hazır bir kelimeyi silmek için yukarıdan ara."
                        else "Sonuç yok.",
                        14f, MUTED
                    )
                )
            }
            for (w in list) box.addView(deleteRow(w))
        }
        fill("")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                fill((s?.toString() ?: "").trim())
            }
        })
    }

    private fun showAdd() {
        val b = page("Yeni kelime", "add", { showManage() })
        b.addView(tv("Kelimeyi telefon okuyabilir, IPA yazmak zorunda değilsin.", 14f, MUTED))
        val fWord = field("İngilizce kelime (zorunlu)")
        val fMean = field("Türkçe anlamı")
        val fIpa = field("IPA (isteğe bağlı), örn: /ˈæp.əl/")
        val fTr = field("Türkçe okunuş (isteğe bağlı)")
        val fEx = field("Örnek cümle (isteğe bağlı)")
        b.addView(fWord)
        b.addView(fMean)
        b.addView(fIpa)
        b.addView(fTr)
        b.addView(fEx)

        val focusKeys = FOCUS_NAMES.keys.toList()
        var fi = focusKeys.indexOf("none")
        val fb = button("Odak ses: ${FOCUS_NAMES[focusKeys[fi]]} (değiştirmek için dokun)") {}
        fb.setOnClickListener {
            fi = (fi + 1) % focusKeys.size
            fb.text = "Odak ses: ${FOCUS_NAMES[focusKeys[fi]]} (değiştirmek için dokun)"
        }
        b.addView(fb)

        val info = tv("", 14f, WARN, center = true)
        b.addView(
            button("Ekle", ACCENT, BG) {
                val w = fWord.text.toString().trim().lowercase(Locale.ENGLISH)
                if (!Regex("[a-z' -]+").matches(w)) {
                    info.setTextColor(BAD)
                    info.text = "Kelime sadece İngilizce harflerden oluşmalı."
                } else if (repo.exists(w)) {
                    info.setTextColor(BAD)
                    info.text = "Bu kelime zaten listede."
                } else {
                    repo.add(
                        Word(
                            w, fIpa.text.toString().trim(), "", fTr.text.toString().trim(),
                            fMean.text.toString().trim(), fEx.text.toString().trim(),
                            "Benim", focusKeys[fi], emptyList(), emptyMap(), true
                        )
                    )
                    info.setTextColor(GOOD)
                    info.text = "\"$w\" eklendi. Yeni bir kelime ekleyebilirsin."
                    fWord.setText("")
                    fMean.setText("")
                    fIpa.setText("")
                    fTr.setText("")
                    fEx.setText("")
                    fWord.requestFocus()
                }
            }
        )
        b.addView(info)
    }

    private fun showBulk() {
        val b = page("Toplu ekle", "bulk", { showManage() })
        b.addView(tv("Her satıra bir kelime yaz:\nkelime ; Türkçe anlamı ; IPA (isteğe bağlı)\n\nÖrnek:\nrefrigerator ; buzdolabı\nthought ; düşünce ; /θɔːt/", 14f, MUTED))
        val box = field("Kelimeleri buraya yapıştır veya yaz", true)
        b.addView(box)
        val info = tv("", 14f, WARN, center = true)
        b.addView(
            button("Hepsini ekle", ACCENT, BG) {
                var added = 0
                var skipped = 0
                for (line in box.text.toString().lines()) {
                    if (line.isBlank()) continue
                    val parts = line.split(";").map { it.trim() }
                    val w = parts[0].lowercase(Locale.ENGLISH)
                    if (!Regex("[a-z' -]+").matches(w) || repo.exists(w)) {
                        skipped++
                        continue
                    }
                    repo.add(
                        Word(
                            w, parts.getOrElse(2) { "" }, "", "", parts.getOrElse(1) { "" }, "",
                            "Benim", "none", emptyList(), emptyMap(), true
                        )
                    )
                    added++
                }
                info.setTextColor(if (added > 0) GOOD else BAD)
                info.text = "$added kelime eklendi" + if (skipped > 0) ", $skipped satır atlandı (geçersiz veya zaten var)." else "."
                if (added > 0) box.setText("")
            }
        )
        b.addView(info)
    }

    private fun startDaily() {
        val all = repo.all()
        if (all.isEmpty()) {
            toast("Önce kelime ekle")
            return
        }
        val practiced = store.attempts.map { it.w }.toSet()
        val pool = (if (practiced.size < 15) all.filter { it.level == "A1" || it.level == "A2" || it.custom } else all)
            .toMutableList()
        if (pool.isEmpty()) pool.addAll(all)
        val q = ArrayList<Word>()
        while (pool.isNotEmpty() && q.size < 10) {
            // Hiç çalışılmamış (yeni) kelimeler ve düşük puanlılar daha sık gelir
            val weights = pool.map { (100 - (store.wordAvg(it.word) ?: 50.0)) + 10 }
            var r = Random.nextDouble() * weights.sum()
            var pick = 0
            for (i in weights.indices) {
                r -= weights[i]
                pick = i
                if (r <= 0) break
            }
            q.add(pool.removeAt(pick))
        }
        queue = q
        qi = 0
        session.clear()
        openWord(q[0], "daily")
    }

    private fun openWord(w: Word, m: String) {
        mode = m
        current = w
        showWord(null)
    }

    private fun showWord(result: Result?) {
        val e = current ?: return
        val title = if (mode == "daily") "Günlük Çalışma ${qi + 1}/${queue.size}" else "Kelime"
        val b = page(title, "word")
        b.addView(tv(e.word, 38f, FG, bold = true, center = true))
        if (e.ipa.isNotBlank()) b.addView(tv("IPA: ${e.ipa}", 22f, ACCENT, center = true))
        if (e.syl.isNotBlank()) b.addView(tv("Heceleme: ${e.syl}", 16f, MUTED, center = true))
        if (e.tr.isNotBlank()) {
            b.addView(tv("Türkçe yaklaşık okunuş: ${e.tr}", 16f, FG, center = true))
            b.addView(
                tv(
                    "Türkçe okunuş sadece başlangıç için yardımcıdır. Asıl hedef İngilizce sesleri doğru üretmektir.",
                    12f, MUTED, center = true
                )
            )
        }
        if (e.mean.isNotBlank()) b.addView(tv("Anlam: ${e.mean}", 17f, FG, center = true))
        if (e.ex.isNotBlank()) b.addView(tv("\"${e.ex}\"", 15f, MUTED, center = true))

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        for ((label, rate) in listOf("Dinle" to 1.0f, "Yavaş" to 0.7f, "Çok yavaş" to 0.5f)) {
            val bt = button(label) { speak(rate) }
            val lp = LinearLayout.LayoutParams(0, dp(56), 1f)
            lp.setMargins(dp(3), dp(8), dp(3), 0)
            bt.layoutParams = lp
            row.addView(bt)
        }
        b.addView(row)
        b.addView(button("Şimdi Sen Söyle (Mikrofon)", ACCENT, BG, 96) { listen() })

        val mv = tv("", 14f, WARN, center = true)
        msgView = mv
        b.addView(mv)

        if (result != null) renderResult(b, result)
    }

    private fun renderResult(b: LinearLayout, r: Result) {
        val color = if (r.score >= 75) GOOD else if (r.score >= 40) WARN else BAD
        b.addView(tv("TELAFFUZ PUANI (tahmini)", 14f, MUTED, center = true))
        b.addView(tv("${r.score}/100", 44f, color, bold = true, center = true))
        b.addView(tv(band(r.score), 17f, FG, center = true))
        if (r.rank != 0 && r.heard.isNotEmpty()) {
            b.addView(tv("Uygulamanın duyduğu: \"${r.heard}\"", 15f, WARN, center = true))
        }
        if (r.msg.isNotEmpty()) b.addView(tv(r.msg, 16f, BAD, center = true))
        if (r.rank != 0 || r.score < 90) b.addView(tv("İpucu: ${r.tip}", 15f, FG, center = true))
        b.addView(
            tv(
                "Not: Bu puan konuşma tanıma sonucuna dayalı bir tahmindir. Gerçek ses (fonem) analizi değildir ve hece hece hata göstermez.",
                12f, MUTED, center = true
            )
        )
        b.addView(button("Doğru sesi dinle") { speak(0.7f) })
        if (mode == "daily") {
            val last = qi >= queue.size - 1
            b.addView(button(if (last) "Bitir" else "Sonraki kelime", ACCENT, BG) { nextWord() })
        }
    }

    private fun nextWord() {
        if (qi >= queue.size - 1) {
            showSummary()
            return
        }
        qi++
        openWord(queue[qi], "daily")
    }

    private fun showSummary() {
        val b = page("Günlük Çalışma", "summary")
        val avg = if (session.isEmpty()) 0 else Math.round(session.values.average()).toInt()
        b.addView(tv("Bugünkü başarın: $avg/100", 28f, ACCENT, bold = true, center = true))
        for ((w, s) in session.entries.sortedBy { it.value }) {
            b.addView(tv("$w: $s", 16f, if (s >= 75) GOOD else WARN))
        }
        b.addView(tv("Düşük puanlı kelimeler bir sonraki çalışmada daha sık karşına çıkar.", 14f, MUTED))
        b.addView(button("Ana ekran", ACCENT, BG) { showHome() })
    }

    private fun showProgress() {
        val b = page("Gelişimim", "progress")
        val s = store.stats()
        if (s.avg == null) {
            b.addView(tv("Henüz kayıt yok. Bir kelime söyleyerek başla.", 16f, MUTED))
        } else {
            val week = s.week?.let { String.format(Locale.US, "%+d puan", it) } ?: "-"
            val weak = s.weak?.let { FOCUS_NAMES[it] } ?: "-"
            b.addView(
                tv(
                    "Çalışılan kelime: ${s.unique}\nOrtalama telaffuz: ${s.avg}\nBu hafta: $week\n" +
                        "Seri: ${s.streak} gün\nEn çok zorlandığın ses: $weak", 18f
                )
            )
            b.addView(tv("Son çalışmalar", 17f, ACCENT, bold = true))
            for (a in s.recent) b.addView(tv("${a.w}: ${a.s}", 15f, MUTED))
        }
        val label = if (confirmClear) "Emin misin? Tekrar dokun" else "Kayıtlarımı sil"
        b.addView(button(label) {
            if (!confirmClear) {
                confirmClear = true
            } else {
                store.clear()
                confirmClear = false
            }
            showProgress()
        })
    }

    // ---------- actions ----------
    private fun say(text: String) {
        msgView?.text = text
    }

    private fun speak(rate: Float) {
        val w = current ?: return
        if (!ttsReady) {
            say("Ses motoru hazır değil. Telefonda Google \"Metin okuma\" motorunun ve İngilizce ses paketinin kurulu olduğundan emin ol.")
            return
        }
        tts.setSpeechRate(rate)
        tts.speak(w.word, TextToSpeech.QUEUE_FLUSH, null, "word")
    }

    @Suppress("DEPRECATION")
    private fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the word")
        try {
            startActivityForResult(intent, REQ)
        } catch (e: ActivityNotFoundException) {
            say("Konuşma tanıma bulunamadı. Telefonda Google uygulamasının kurulu ve güncel olması gerekir.")
        } catch (e: Exception) {
            say("Konuşma tanıma başlatılamadı: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ) return
        if (resultCode != RESULT_OK || data == null) {
            say("Dinleme iptal edildi veya ses algılanmadı. Tekrar dene.")
            return
        }
        val cur = current ?: return
        val res: List<String> = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) ?: arrayListOf()
        val confs: List<Float> = data.getFloatArrayExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES)?.toList()
            ?: emptyList()
        val r = evaluate(cur, res, confs)
        store.add(cur.word, cur.focus, r.score)
        session[cur.word] = r.score
        showWord(r)
    }
}
