# כפתור המצלמה כשהמסך כבוי — בלי root ובלי להדליק את המסך

כשהמסך כבוי אנדרואיד זורק את רוב הלחיצות לפני ששירות הנגישות רואה אותן. מקשי מדיה הם יוצא הדופן:
את אלה המערכת ממשיכה להעביר גם כשהמסך כבוי, בלי להדליק אותו (כך עובד כפתור ה-play באוזניות).
לכן בקושחה ממפים את כפתור המצלמה למקש המדיה `MEDIA_RECORD`, ועזרים תופסת אותו ומבצעת את הקיצור
לפי ההגדרות הרגילות (עזרים ← קיצורים ← כפתור המצלמה). כשהמסך דולק הכול עובד כמו קודם.
מקשי מדיה אחרים (אוזניות, בלוטוס) לא מושפעים: עזרים תופסת רק את MEDIA_RECORD שמגיע מהכפתור הפיזי.

## שינוי 1: קובץ ההרשאות של הקושחה — חובה, ולפני התקנת ה-APK!

ב-`/system/etc/permissions/privapp-permissions-ezrim.xml`, בתוך הבלוק של `com.android.adds`, מוסיפים:
```xml
<permission name="android.permission.SET_MEDIA_KEY_LISTENER"/>
```
זו הרשאת signature|privileged, כמו MOUNT_UNMOUNT_FILESYSTEMS: אם ה-APK החדש מותקן בלי השורה הזו,
המכשיר עלול לא לעלות. לכן קודם מוסיפים את השורה, ורק אז מכניסים את ה-APK.

## שינוי 2: מיפוי הכפתור (`/system/usr/keylayout/mtk-kpd.kl`)

במכשיר אין קובץ ייעודי לכפתורים וכל ההתקנים טוענים את `Generic.kl`. לכן יוצרים קובץ בשם של התקן
הכפתורים (`mtk-kpd`, שבו יושבים ווליום ומצלמה) כעותק של `Generic.kl`, ומשנים בו רק את שורת המצלמה:
```
key 212   MEDIA_RECORD
```
(בלי WAKE — אחרת המסך יידלק.)

עם root, לבדיקה:
```
mount -o remount,rw /
cp /system/usr/keylayout/Generic.kl /system/usr/keylayout/mtk-kpd.kl
sed -i -E 's/^(key[[:space:]]+212[[:space:]]+)CAMERA([[:space:]].*)?$/\1MEDIA_RECORD/' /system/usr/keylayout/mtk-kpd.kl
grep -n "^key 212" /system/usr/keylayout/mtk-kpd.kl
chmod 644 /system/usr/keylayout/mtk-kpd.kl
chcon u:object_r:system_file:s0 /system/usr/keylayout/mtk-kpd.kl
reboot
```
ה-`grep` צריך להראות `key 212   MEDIA_RECORD`. בקושחה עצמה שמים את אותו קובץ ב-`system/usr/keylayout/`.

## בדיקה
- `adb shell dumpsys input | grep KeyLayoutFile` — אחת השורות: `/system/usr/keylayout/mtk-kpd.kl`.
- `adb shell dumpsys media_session | grep -i "key listener"` — אמור להופיע ה-uid של עזרים.
- מגדירים "לחיצה רגילה = פנס", מכבים מסך ולוחצים: הפנס נדלק, המסך נשאר כבוי.

## חשוב לדעת
- שירות הנגישות "עזרים - קיצורים" חייב להיות מופעל. אם הוא כבוי, הכפתור לא יעשה כלום
  (גם לא יפתח את אפליקציית המצלמה).
- כשהמסך כבוי כדאי להגדיר פעולות שלא צריכות מסך: פנס, הפעלה/עצירה של מדיה וכדומה.
  הכתבה קולית לא פועלת כשהמסך כבוי.
- אם ההרשאה מהשינוי הראשון חסרה, הכפתור פשוט לא יעבוד כשהמסך כבוי (כשהמסך דולק — כרגיל).
