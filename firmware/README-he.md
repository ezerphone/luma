# מודל זיהוי הדיבור בקושחה

המודל (~72MB) **לא** נמצא ב-APK של עזרים. הוא קבצים רגילים בתמונת `/system`, ועזרים טוענת אותם משם.

## הנתיב
```
/system/etc/ezrim/hebrew-asr/
    encoder.int8.onnx   decoder.int8.onnx   joiner.int8.onnx
    tokens.txt          bpe.vocab
    model-config.json   ATTRIBUTION.txt     (שני האחרונים לא נדרשים לריצה; ATTRIBUTION נדרש לפי הרישיון)
```
הנתיב מוגדר בקבוע אחד: `ModelLocator.DIR` ב-`app/src/main/java/app/luma/torch/dictation/ModelLocator.kt`.
אם משנים אותו, צריך לשנות גם כאן בשני הסקריפטים.

## בניית קושחה
1. חלץ את `ezrim-hebrew-asr-model.zip`. התוצאה: `system/etc/ezrim/hebrew-asr/…`
2. העתק את התיקייה `hebrew-asr` ליד `ezrim-asr.mk` בעץ הקושחה.
3. ב-`device.mk`: `$(call inherit-product, <נתיב>/ezrim-asr.mk)`.
4. בדוק שיש מקום פנוי ב-system (נוספים כ-72MB).
5. הרשאות צפויות: בעלים root:root, קבצים 0644, תיקייה 0755, תווית SELinux רגילה של `system_file` (נקבעת אוטומטית ב-/system/etc). אפליקציה ב-priv-app קוראת קבצים כאלה בלי הרשאה מיוחדת.

## בדיקה מהירה על מכשיר עם root (בלי לבנות קושחה)
`./install-model-adb.sh <תיקיית hebrew-asr>` — מאמת SHA256, עושה remount ודוחף את הקבצים.
אחרי הפעלה מחדש הקבצים נשארים כל עוד ה-system לא נצרב מחדש.

## איך מוודאים שזה עובד
- בעזרים מופיע כרטיס **הקלדה קולית**. אם המודל חסר, הכרטיס מאפיר ומופיעה הודעה, ובתוך המסך מוצג איזה קובץ חסר.
- אם הקבצים קיימים אבל הטעינה נכשלת, המסך מציג את סיבת השגיאה.
- `adb shell ls -lZ /system/etc/ezrim/hebrew-asr`

## עדכון מודל
מחליפים את הקבצים בקושחה. אין צורך לעדכן את ה-APK, כל עוד שמות הקבצים והנתיב זהים.

## ספריית ה-native (libsherpa-onnx-jni.so)
הספרייה נטענת **תמיד מהקושחה**, בטעינה הרגילה של המערכת (`System.loadLibrary`). אין יותר חילוץ שלה מתוך ה-APK. אין יותר חילוץ שלה מתוך ה-APK.
לכן בקושחה חייבים לשים אותה ליד ה-APK: `/system/priv-app/Ezrim/lib/arm64/libsherpa-onnx-jni.so`
(ואם יש גם מעבד 32 ביט: `lib/arm/`). אם היא חסרה, ההקלדה הקולית תציג שגיאת טעינה (`UnsatisfiedLinkError`).
את הקובץ אפשר להוציא מתוך ה-AAR: `jni/arm64-v8a/libsherpa-onnx-jni.so`.

## בדיקת הקושחה
האפליקציה בודקת פעם אחת, ב-`LumaApp.onCreate`, שקיים `/system/etc/permissions/privapp-permissions-ezrim.xml`.
אם הוא חסר, התהליך נסגר מיד ושום חלק באפליקציה לא רץ.
