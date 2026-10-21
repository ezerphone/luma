package app.luma.torch.dictation;

/**
 * עיבוד פיסוק לטקסט ההכתבה (הטקסט המצטבר של ההכתבה הנוכחית, מחושב מחדש בכל עדכון):
 *  1. פיסוק אוטומטי כבוי: מסירים סימני פיסוק שהמודל הוסיף בעצמו.
 *  2. פקודות פיסוק קוליות פעילות: "פסיק", "סימן שאלה" וכו' הופכים לתו עצמו, עם הרווחים הנכונים.
 */
final class Punctuation {
    private Punctuation() {}

    private enum Mode { ATTACH_LEFT, OPEN, JOIN, NEWLINE }

    private static final class Command {
        final String[] words; final String chars; final Mode mode;
        Command(String phrase, String chars, Mode mode) { this.words = phrase.split(" "); this.chars = chars; this.mode = mode; }
    }

    // לפי אורך יורד, כדי ש"נקודה פסיק" ייבחר לפני "נקודה"
    private static final Command[] COMMANDS = {
        new Command("שלוש נקודות", "...", Mode.ATTACH_LEFT),
        new Command("פתח סוגריים", "(", Mode.OPEN),
        new Command("סגור סוגריים", ")", Mode.ATTACH_LEFT),
        new Command("פתח מרכאות", "\"", Mode.OPEN),
        new Command("סגור מרכאות", "\"", Mode.ATTACH_LEFT),
        new Command("נקודה פסיק", ";", Mode.ATTACH_LEFT),
        new Command("נקודה ופסיק", ";", Mode.ATTACH_LEFT),
        new Command("סימן שאלה", "?", Mode.ATTACH_LEFT),
        new Command("סימן קריאה", "!", Mode.ATTACH_LEFT),
        new Command("שורה חדשה", "\n", Mode.NEWLINE),
        new Command("ירידת שורה", "\n", Mode.NEWLINE),
        new Command("פסקה חדשה", "\n\n", Mode.NEWLINE),
        new Command("נקודתיים", ":", Mode.ATTACH_LEFT),
        new Command("פסיק", ",", Mode.ATTACH_LEFT),
        new Command("נקודה", ".", Mode.ATTACH_LEFT),
        new Command("מקף", "-", Mode.JOIN),
    };

    static String process(String text, boolean autoPunctuation, boolean spokenCommands) {
        if (text == null || text.isEmpty()) return "";
        String t = text;
        if (!autoPunctuation) t = stripModelPunctuation(t);
        if (spokenCommands) t = applyCommands(t);
        return t;
    }

    /** האם הטקסט מתחיל בתו שנצמד למילה הקודמת (אז אין להוסיף לפניו רווח). */
    static boolean attachesLeft(String body) {
        return !body.isEmpty() && ",.:;?!)\n-".indexOf(body.charAt(0)) >= 0;
    }

    // מסיר סימני פיסוק של משפט. נקודה או פסיק בין ספרות (3.5) נשארים.
    private static String stripModelPunctuation(String t) {
        String s = t.replaceAll("[?!:;…]+", "");
        s = s.replaceAll("(?<!\\d)[.,]+|[.,]+(?!\\d)", "");
        return s.replaceAll("[ \\t]{2,}", " ").trim();
    }

    private static String normalize(String token) { return token.replaceAll("[,.?!:;]+$", ""); }

    private static String applyCommands(String t) {
        String[] tokens = t.trim().split("\\s+");
        String[] norm = new String[tokens.length];
        for (int i = 0; i < tokens.length; i++) norm[i] = normalize(tokens[i]);
        StringBuilder out = new StringBuilder();
        boolean needSpace = false;
        for (int i = 0; i < tokens.length; ) {
            Command hit = null;
            for (Command c : COMMANDS) {
                if (i + c.words.length > tokens.length) continue;
                boolean same = true;
                for (int k = 0; k < c.words.length && same; k++) same = c.words[k].equals(norm[i + k]);
                if (same) { hit = c; break; }
            }
            if (hit == null) {
                if (needSpace && out.length() > 0) out.append(' ');
                out.append(tokens[i]); needSpace = true; i++;
                continue;
            }
            switch (hit.mode) {
                case ATTACH_LEFT: out.append(hit.chars); needSpace = true; break;
                case OPEN: if (needSpace && out.length() > 0) out.append(' '); out.append(hit.chars); needSpace = false; break;
                case JOIN: case NEWLINE: out.append(hit.chars); needSpace = false; break;
            }
            i += hit.words.length;
        }
        return out.toString();
    }
}
