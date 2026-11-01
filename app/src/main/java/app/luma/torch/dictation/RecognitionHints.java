package app.luma.torch.dictation;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import java.text.Normalizer;
import java.util.*;

/** The personal dictionary: names and terms the listener uses, given a mild preference while
 *  decoding (sherpa-onnx hotwords). Nothing is replaced in the text afterwards.
 *
 *  1.1 drafts also had a fixed list of ~60 everyday phrases ("אם אתה", "עם חבר"...). It boosted
 *  both sides of every confusion it meant to settle, and it switched every dictation to beam
 *  search, which measured worse than greedy on the 66M model (dev90 17.7% vs 17.2% WER) and is
 *  slower on a weak phone. It is gone: beam search now runs only when the dictionary has words. */
final class RecognitionHints {
    static final int MAX_PHRASES = 100, MAX_LENGTH = 60;
    private static final float SCORE = .8f;

    static final class Snapshot {
        final String words, hotwords;
        final boolean personal, beam;
        final int count;

        Snapshot(String words, boolean personal) {
            this.words = words;
            this.personal = personal;
            List<String> entries = parse(words);
            count = entries.size();
            List<String> scored = new ArrayList<>();
            if (personal)
                for (String phrase : entries) scored.add(phrase + " :" + String.format(Locale.ROOT, "%.2f", SCORE));
            hotwords = TextUtils.join("/", scored);
            beam = !scored.isEmpty();
        }
    }

    static Snapshot snapshot(Context context) {
        SharedPreferences p = DictationPrefs.prefs(context);
        return new Snapshot(p.getString("personal_words", ""), p.getBoolean("personal_hints", true));
    }

    static List<String> parse(String input) {
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        for (String line : input.split("\\r\\n|[\\n\\r\\f\\u000B\\u0085\\u2028\\u2029]")) {
            String phrase = Normalizer.normalize(line, Normalizer.Form.NFKC).replaceAll("[\\p{M}\\p{Cf}]", "").trim().replaceAll("\\s+", " ");
            if (phrase.isEmpty()) continue;
            if (phrase.length() > MAX_LENGTH) throw new IllegalArgumentException("כל שם או ביטוי יכול להכיל עד " + MAX_LENGTH + " תווים.");
            if (!phrase.matches("[\\p{L}\\p{N} '\"׳״.־-]+")) throw new IllegalArgumentException("אפשר לכתוב שמות וביטויים עם אותיות, מספרים, רווחים וגרשיים. הסר סימנים מיוחדים.");
            if (!containsLetter(phrase)) throw new IllegalArgumentException("בכל שורה צריך להיות שם או ביטוי עם אותיות.");
            String key=phrase.toLowerCase(Locale.ROOT);
            if (!unique.containsKey(key)) unique.put(key, phrase);
            if (unique.size() > MAX_PHRASES) throw new IllegalArgumentException("אפשר לשמור עד " + MAX_PHRASES + " שמות וביטויים.");
        }
        return new ArrayList<>(unique.values());
    }

    private static boolean containsLetter(String phrase) {
        for(int i=0;i<phrase.length();) {
            int point=phrase.codePointAt(i);
            if(Character.isLetter(point))return true;
            i+=Character.charCount(point);
        }
        return false;
    }
    static String normalized(String input) { return TextUtils.join("\n", parse(input)); }
}
