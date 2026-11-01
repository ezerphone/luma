package app.luma.torch.dictation;

/**
 * טוענת את libsherpa-onnx-jni.so בטעינה הרגילה של המערכת בלבד, כלומר מהספרייה שהקושחה רשמה לאפליקציה
 * (lib/arm64 ליד ה-APK ב-priv-app). אין יותר גיבוי של חילוץ הספרייה מתוך ה-APK:
 * אם הספרייה לא נמצאת בקושחה, הטעינה נכשלת עם שגיאה ברורה.
 * חייבת לרוץ לפני הנגיעה הראשונה במחלקות של sherpa-onnx.
 */
final class NativeLoader {
    private static final String LIB = "sherpa-onnx-jni";
    private static boolean ready;

    private NativeLoader() {}

    static synchronized void ensure() {
        if (ready) return;
        System.loadLibrary(LIB);
        ready = true;
    }
}
