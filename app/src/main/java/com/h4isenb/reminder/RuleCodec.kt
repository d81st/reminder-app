package com.h4isenb.reminder

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Обмен наборами напоминаний: текст вида «RMD1.…» (сжатый JSON в base64url). Его кладут в QR-код
 * или пересылают сообщением. Мелодия будильника не передаётся: адреса мелодий на каждом телефоне свои.
 */
@OptIn(ExperimentalEncodingApi::class)
object RuleCodec {
    const val PREFIX = "RMD1."
    const val MAX_RULES = 30
    private const val MAX_NAME = 100
    private const val MAX_TEXT = 500
    private const val MAX_INFLATED = 256 * 1024
    private val b64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    class BadCode(msg: String) : Exception(msg)

    fun encode(rules: List<Rule>): String {
        val arr = JSONArray()
        for (r in rules) {
            arr.put(
                JSONObject()
                    .put("n", r.name)
                    .put("t", r.text)
                    .put("s", r.start)
                    .put("e", r.end)
                    .put("i", r.interval)
                    .put("o", if (r.skipOverlap) 1 else 0)
                    .put("on", if (r.enabled) 1 else 0)
                    .put("l", r.lead)
                    .put("c", r.color)
                    .put("re", r.repeatEvery)
                    .put("rc", r.repeatCount)
                    .put("rm", r.repeatMode)
                    .put("d", r.days and Rule.ALL_DAYS)
                    .put("k", r.kind)
                    .put("z", r.snoozeMin)
                    .put("ch", r.challenge)
                    .put("g", if (r.gradual) 1 else 0)
            )
        }
        val json = JSONObject().put("v", 1).put("r", arr).toString().toByteArray(Charsets.UTF_8)
        return PREFIX + b64.encode(deflate(json))
    }

    /** Достаёт код из любого текста (например, из сообщения, где вокруг есть ещё слова). */
    fun find(text: String): String? {
        val i = text.indexOf(PREFIX)
        if (i < 0) return null
        val body = text.substring(i + PREFIX.length).takeWhile { it.isLetterOrDigit() || it == '-' || it == '_' }
        return if (body.isEmpty()) null else PREFIX + body
    }

    /** Разбирает код. Значения приводятся к допустимым пределам; новые напоминания ещё не сохранены (id = 0). */
    fun decode(text: String): List<Rule> {
        val code = find(text.trim()) ?: throw BadCode("Это не код напоминаний")
        val bytes = try {
            inflate(b64.decode(code.substring(PREFIX.length)))
        } catch (e: BadCode) {
            throw e
        } catch (e: Exception) {
            throw BadCode("Код повреждён или обрезан")
        }
        val root = try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            throw BadCode("Код повреждён")
        }
        if (root.optInt("v", 0) != 1) throw BadCode("Код от более новой версии приложения: обновите его")
        val arr = root.optJSONArray("r") ?: throw BadCode("В коде нет напоминаний")
        val out = ArrayList<Rule>()
        for (k in 0 until minOf(arr.length(), MAX_RULES)) {
            val o = arr.optJSONObject(k) ?: continue
            out.add(
                Rule(
                    name = o.optString("n", "").trim().take(MAX_NAME).ifEmpty { "Напоминание" },
                    text = o.optString("t", "").take(MAX_TEXT),
                    start = o.optInt("s", 9 * 60).coerceIn(0, 1439),
                    end = o.optInt("e", 17 * 60).coerceIn(0, 1439),
                    interval = o.optInt("i", 60).coerceIn(1, 720),
                    skipOverlap = o.optInt("o", 0) == 1,
                    enabled = o.optInt("on", 1) == 1,
                    lead = o.optInt("l", 0).coerceIn(0, 180),
                    color = o.optInt("c", 0).coerceIn(0, 5),
                    repeatEvery = o.optInt("re", 5).coerceIn(1, 120),
                    repeatCount = o.optInt("rc", 0).coerceIn(0, 20),
                    repeatMode = if (o.optInt("rm", 0) == Rule.REPEAT_AFTER) Rule.REPEAT_AFTER else Rule.REPEAT_BEFORE,
                    days = o.optInt("d", Rule.ALL_DAYS) and Rule.ALL_DAYS,
                    kind = if (o.optInt("k", 0) == Rule.KIND_ALARM) Rule.KIND_ALARM else Rule.KIND_NOTIFY,
                    snoozeMin = o.optInt("z", 5).coerceIn(1, 60),
                    challenge = if (o.optInt("ch", 0) == Rule.CHALLENGE_MATH) Rule.CHALLENGE_MATH else Rule.CHALLENGE_NONE,
                    gradual = o.optInt("g", 1) == 1,
                )
            )
        }
        if (out.isEmpty()) throw BadCode("В коде нет напоминаний")
        return out
    }

    private fun deflate(data: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_COMPRESSION)
        d.setInput(data)
        d.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inf = Inflater()
        inf.setInput(data)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        try {
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) throw BadCode("Код повреждён или обрезан")
                out.write(buf, 0, n)
                if (out.size() > MAX_INFLATED) throw BadCode("Код слишком большой")
            }
        } finally {
            inf.end()
        }
        return out.toByteArray()
    }
}
