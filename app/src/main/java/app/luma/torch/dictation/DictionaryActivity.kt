package app.luma.torch.dictation

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import app.luma.torch.BaseActivity

// המילון האישי: שם או ביטוי בכל שורה. המילים מקבלות עדיפות מתונה בזמן הזיהוי (hotwords), ולא נכפות על הטקסט.
class DictionaryActivity : BaseActivity() {
    private val warnC get() = if (isDark) 0xFFFFB020.toInt() else 0xFFC26A00.toInt()
    private lateinit var words: EditText
    private lateinit var personal: Switch
    private lateinit var error: TextView
    private var initialWords = ""
    private var initialPersonal = true

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val saved = RecognitionHints.snapshot(this)
        initialWords = saved.words; initialPersonal = saved.personal

        personal = Switch(this).apply { text = "שימוש במילון"; setTextColor(fg); isChecked = initialPersonal }
        tint(personal)

        words = EditText(this).apply {
            hint = "למשל:\nיונתן\nגבעת שמואל"; setHintTextColor(dim); setTextColor(fg)
            contentDescription = "שמות וביטויים במילון האישי"
            textSize = 17f; minLines = 5; gravity = Gravity.TOP or Gravity.START
            textDirection = View.TEXT_DIRECTION_RTL
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            background = shape(chip, 16); setPadding(dp(14), dp(12), dp(14), dp(12))
            setText(initialWords)
        }
        words.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }

        error = TextView(this).apply { textSize = 14f; setTextColor(warnC); setPadding(0, dp(6), 0, 0) }

        val explain = TextView(this).apply {
            text = "שם או ביטוי בכל שורה, עד ${RecognitionHints.MAX_PHRASES} שורות. המילים מקבלות עדיפות מתונה בזיהוי, ולא נכפות על הטקסט."
            textSize = 13f; setTextColor(dim); setLineSpacing(0f, 1.2f); setPadding(0, dp(12), 0, 0)
        }
        val saveBtn = tv("שמירה", 16f) { save() }
        style(saveBtn, true)
        saveBtn.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }

        val note = TextView(this).apply {
            text = "מילים במילון מעבירות את הזיהוי לשיטה שבודקת כמה אפשרויות, והיא קצת איטית יותר. " +
                "בלי מילים הזיהוי נשאר בשיטה המהירה. המילון נשמר במכשיר בלבד."
            textSize = 13f; setTextColor(dim); setLineSpacing(0f, 1.2f)
        }

        page(
            header("מילון אישי", null, "שמות ומונחים שאתה משתמש בהם, כדי שיזוהו נכון"),
            section("המילון", personal, explain, words, error, saveBtn),
            section("מידע", note)
        )
        if (b != null) {
            words.setText(b.getString("draft", initialWords))
            personal.isChecked = b.getBoolean("personal", initialPersonal)
        }
    }

    private fun changed() = words.text.toString() != initialWords || personal.isChecked != initialPersonal

    private fun save() {
        val normalized = try { RecognitionHints.normalized(words.text.toString()) }
        catch (e: IllegalArgumentException) { error.text = e.message; words.requestFocus(); return }
        DictationPrefs.prefs(this).edit()
            .putString("personal_words", normalized).putBoolean("personal_hints", personal.isChecked).apply()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(words.windowToken, 0)
        SpeechEngine.get(this).reconfigure()
        Toast.makeText(this, "המילון נשמר", Toast.LENGTH_SHORT).show()
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!changed()) { super.onBackPressed(); return }
        dlg().setTitle("לשמור את השינויים?").setMessage("עדיין לא שמרת את המילון.")
            .setPositiveButton("שמירה") { _, _ -> save() }
            .setNegativeButton("יציאה בלי לשמור") { _, _ -> finish() }
            .setNeutralButton("המשך עריכה", null).show()
    }

    override fun onSaveInstanceState(state: Bundle) {
        state.putString("draft", words.text.toString()); state.putBoolean("personal", personal.isChecked)
        super.onSaveInstanceState(state)
    }
}
