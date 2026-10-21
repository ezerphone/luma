package app.luma.torch.dictation

import org.json.JSONObject
import java.io.File

// מיקום מודל זיהוי הדיבור העברי: בתוך הקושחה ולא בתוך ה-APK.
// הקבצים נכנסים לתמונת המערכת בזמן בניית הקושחה (ראה firmware/README-he.md),
// ממש ליד קובץ ההרשאות של עזרים ב-/system/etc/permissions.
object ModelLocator {
    // הנתיב היחיד שבו המודל אמור לשבת. אם משנים אותו כאן, צריך לשנות גם בסקריפטים שבתיקיית firmware.
    const val DIR = "/system/etc/ezrim/hebrew-asr"

    // קבצים שבלעדיהם אי אפשר לטעון את המודל (model-config.json ו-ATTRIBUTION.txt אינם חובה לריצה)
    private val REQUIRED = listOf("encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt", "bpe.vocab")

    @JvmStatic fun path(name: String): String = "$DIR/$name"

    // רשימת הקבצים החסרים או הריקים; רשימה ריקה = המודל מותקן בקושחה
    @JvmStatic fun missing(): List<String> = REQUIRED.filter {
        try { val f = File(path(it)); !(f.isFile && f.canRead() && f.length() > 0L) } catch (_: Exception) { true }
    }

    // המודל יושב ב-/system (קריאה בלבד), לכן אחרי שנמצא מותקן אין צורך לבדוק את הדיסק שוב בכל ציור של המקלדת
    @Volatile private var found = false

    @JvmStatic fun isInstalled(): Boolean {
        if (found) return true
        found = missing().isEmpty()
        return found
    }

    // מזהה הגרסה (7 תווים ראשונים של ה-revision) מתוך model-config.json, אם הקובץ קיים
    fun revision(): String? = try {
        JSONObject(File(path("model-config.json")).readText()).optString("revision").take(7).ifBlank { null }
    } catch (_: Exception) { null }
}
