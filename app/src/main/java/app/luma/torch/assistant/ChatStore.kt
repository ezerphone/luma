package app.luma.torch.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// היסטוריית השיחות עם עזר: קובץ JSON לכל שיחה, בתיקייה הפרטית של עזרים
object ChatStore {
    class Msg(val me: Boolean, val text: String, val t: Long)
    class Chat(val id: String, var title: String, var updated: Long, val msgs: MutableList<Msg>)

    private fun dir(c: Context) = File(c.filesDir, "chats").also { it.mkdirs() }

    fun list(c: Context): List<Chat> = dir(c).listFiles { f -> f.name.endsWith(".json") }.orEmpty()
        .mapNotNull { load(c, it.nameWithoutExtension) }.filter { it.msgs.isNotEmpty() }.sortedByDescending { it.updated }

    fun load(c: Context, id: String): Chat? = try {
        val o = JSONObject(File(dir(c), "$id.json").readText())
        val a = o.getJSONArray("m")
        Chat(id, o.optString("title"), o.optLong("updated"), (0 until a.length()).map { a.getJSONObject(it).let { m -> Msg(m.getBoolean("me"), m.getString("x"), m.getLong("t")) } }.toMutableList())
    } catch (_: Exception) { null }

    // השיחה הנוכחית: האחרונה אם עודכנה בחצי השעה האחרונה, אחרת חדשה (משותף לצ'אט, לחלונית ול"היי עזר")
    fun current(c: Context): Chat = list(c).firstOrNull()?.takeIf { System.currentTimeMillis() - it.updated < 30 * 60_000 } ?: create()

    // מוסיף הודעה לפי מה ששמור בקובץ (ולא רק בזיכרון) — כך הצ'אט והחלונית לא דורסים זה את זה
    @Synchronized fun add(c: Context, ch: Chat, me: Boolean, text: String) {
        load(c, ch.id)?.let { d -> if (d.msgs.size > ch.msgs.size) { ch.msgs.clear(); ch.msgs += d.msgs }; if (ch.title.isBlank()) ch.title = d.title }
        ch.msgs += Msg(me, text, System.currentTimeMillis()); save(c, ch)
    }

    fun create() = System.currentTimeMillis().toString().let { Chat(it, "", System.currentTimeMillis(), mutableListOf()) }

    fun save(c: Context, ch: Chat) {
        if (ch.msgs.isEmpty()) return
        if (ch.title.isBlank()) ch.title = ch.msgs.first { it.me }.text.take(40)
        ch.updated = System.currentTimeMillis()
        val a = JSONArray(); ch.msgs.forEach { a.put(JSONObject().put("me", it.me).put("x", it.text).put("t", it.t)) }
        File(dir(c), "${ch.id}.json").writeText(JSONObject().put("title", ch.title).put("updated", ch.updated).put("m", a).toString())
    }

    fun delete(c: Context, id: String) { File(dir(c), "$id.json").delete() }
}
