package app.luma.torch.dictation

// גשר בין שירות הנגישות (כפתור המצלמה) לבין המקלדת הקולית. שניהם רצים באותו תהליך ובחוט הראשי.
// הזרימה: לחיצה → מעבר למקלדת הקולית → היא מתחילה להקשיב → עזיבה/לחיצה נוספת → עצירה →
// הטקסט נכנס לשדה → done() → חזרה למקלדת שהייתה קודם.
object PushToTalk {
    interface Keyboard {
        fun pttStart()
        fun pttStop()
        fun pttListening(): Boolean
    }

    // המקלדת הקולית רושמת את עצמה כשהיא פתוחה על שדה, ומסירה כשהיא נסגרת
    var keyboard: Keyboard? = null
    // הכתבה שהתחילה מכפתור המצלמה ועוד לא הסתיימה
    var active = false
        private set
    // מה עושים בסיום: בדרך כלל חזרה למקלדת הקודמת (null אם המקלדת הקולית כבר הייתה פתוחה מלכתחילה)
    private var onDone: (() -> Unit)? = null

    fun begin(after: (() -> Unit)?) { active = true; onDone = after }

    fun done() {
        if (!active) return
        val d = onDone
        active = false; onDone = null
        d?.invoke()
    }
}
