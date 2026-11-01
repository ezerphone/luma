package app.luma.torch.dictation

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.View
import android.widget.TextView
import app.luma.torch.BaseActivity

// פרטיות, מקור המודל והקרדיט (חובה לפי CC BY 4.0), ורישיונות הרכיבים שנכללים ב-APK.
// הטקסטים הקצרים מוגדרים מראש ב-LegalTexts. רק ההודעות הארוכות של ONNX Runtime נטענות מקובץ, ורק בלחיצה.
class DictationLegalActivity : BaseActivity() {
    private val parts = listOf(
        "פרטיות" to LegalTexts.PRIVACY,
        "מקור המודל וקרדיט" to LegalTexts.NOTICES,
        "רישיון המודל — CC BY 4.0" to LegalTexts.CC_BY,
        "sherpa-onnx — Apache 2.0" to LegalTexts.APACHE,
        "ONNX Runtime" to LegalTexts.ORT_MIT
    )

    private fun body(content: String, links: Boolean) = TextView(this).apply {
        text = content; textSize = 13f; setTextColor(fg); setLineSpacing(0f, 1.2f)
        setTextIsSelectable(true)
        if (links) { Linkify.addLinks(this, Linkify.WEB_URLS); movementMethod = LinkMovementMethod.getInstance(); setLinkTextColor(acc) }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val views = mutableListOf<View>(header("פרטיות ורישיונות", null, "הקלדה קולית אופליין"))
        for ((title, content) in parts) views += section(title, body(content, true))

        // ~325KB: נקרא מהקובץ בחוט נפרד, רק כשלוחצים, וללא זיהוי קישורים
        val ort = body("", false).apply { visibility = View.GONE; setTextIsSelectable(false) }
        val load = tv("הצגת ההודעות המלאות", 14f) {}
        style(load, false)
        load.setOnClickListener {
            load.visibility = View.GONE
            ort.text = "טוען…"; ort.visibility = View.VISIBLE
            Thread {
                val text = try { assets.open("legal/ORT-ThirdPartyNotices.txt").use { String(it.readBytes(), Charsets.UTF_8) } }
                catch (_: Exception) { "הקובץ לא נמצא" }
                runOnUiThread { ort.text = text }
            }.start()
        }
        views += section("ONNX Runtime — רכיבי צד שלישי", load, ort)
        page(*views.toTypedArray())
    }
}
