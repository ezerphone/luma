package app.luma.torch.dictation;

import android.content.Context;
import android.content.SharedPreferences;

/** הגדרות ההקלדה הקולית (קובץ voice.xml, מוחרג מהגיבוי). */
public final class DictationPrefs {
    private DictationPrefs() {}
    public static SharedPreferences prefs(Context context) { return context.getSharedPreferences("voice", Context.MODE_PRIVATE); }
}
