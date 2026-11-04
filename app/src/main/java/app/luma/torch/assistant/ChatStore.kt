package app.luma.torch.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// היסטוריית השיחות עם עזר: קובץ JSON לכל שיחה, בתיקייה הפרטית של עזרים
object ChatStore {
    class Msg(val me: Boolean, val text: String, val t: Long)
    class Chat(val id: String, var title: String, var updated: Long, val msgs: MutableList<Msg>, var archived: Boolean = false)

    private fun dir(c: Context) = File(c.filesDir, "chats").also { it.mkdirs() }

    // השיחות הרגילות, או (archived = true) השיחות שבארכיון
    fun list(c: Context, archived: Boolean = false): List<Chat> = dir(c).listFiles { f -> f.name.endsWith(".json") }.orEmpty()
        .mapNotNull { load(c, it.nameWithoutExtension) }.filter { it.msgs.isNotEmpty() && it.archived == archived }.sortedByDescending { it.updated }

    fun load(c: Context, id: String): Chat? = try {
        val o = JSONObject(File(dir(c), "$id.json").readText())
        val a = o.getJSONArray("m")
        Chat(id, o.optString("title"), o.optLong("updated"), (0 until a.length()).map { a.getJSONObject(it).let { m -> Msg(m.getBoolean("me"), m.getString("x"), m.getLong("t")) } }.toMutableList(),
            o.optBoolean("archived"))
    } catch (_: Exception) { null }

    // מוסיף הודעה לפי מה ששמור בקובץ (ולא רק בזיכרון) — כך הצ'אט והחלונית לא דורסים זה את זה
    @Synchronized fun add(c: Context, ch: Chat, me: Boolean, text: String) {
        load(c, ch.id)?.let { d -> if (d.msgs.size > ch.msgs.size) { ch.msgs.clear(); ch.msgs += d.msgs }; if (ch.title.isBlank()) ch.title = d.title; ch.archived = d.archived }
        ch.msgs += Msg(me, text, System.currentTimeMillis()); save(c, ch)
    }

    fun create() = System.currentTimeMillis().toString().let { Chat(it, "", System.currentTimeMillis(), mutableListOf()) }

    fun save(c: Context, ch: Chat) {
        if (ch.msgs.isEmpty()) return
        // כותרת: ההודעה הראשונה של המשתמש (ואם אין — הראשונה בכלל)
        if (ch.title.isBlank()) ch.title = (ch.msgs.firstOrNull { it.me } ?: ch.msgs.first()).text.take(40).ifBlank { "שיחה" }
        ch.updated = System.currentTimeMillis()
        write(c, ch)
    }

    private fun write(c: Context, ch: Chat) {
        val a = JSONArray(); ch.msgs.forEach { a.put(JSONObject().put("me", it.me).put("x", it.text).put("t", it.t)) }
        File(dir(c), "${ch.id}.json").writeText(JSONObject().put("title", ch.title).put("updated", ch.updated).put("m", a).put("archived", ch.archived).toString())
    }

    // העברה לארכיון / הוצאה ממנו, בלי לשנות את זמן השיחה
    fun setArchived(c: Context, id: String, archived: Boolean) {
        val ch = load(c, id) ?: return
        ch.archived = archived; write(c, ch)
    }

    fun delete(c: Context, id: String) { File(dir(c), "$id.json").delete() }

    // שינוי שם, בלי לשנות את מקום השיחה ברשימה
    fun rename(c: Context, id: String, title: String) {
        val ch = load(c, id) ?: return
        ch.title = title; write(c, ch)
    }

    // השיחה כטקסט (לייצוא)
    fun asText(ch: Chat, name: String): String {
        val f = java.text.SimpleDateFormat("d.M.yyyy HH:mm", java.util.Locale("he"))
        return "${ch.title}\n" + ch.msgs.joinToString("\n") { (if (it.me) "אני" else name) + " (" + f.format(java.util.Date(it.t)) + "): " + it.text }
    }
}
