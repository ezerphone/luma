package app.luma.torch

import java.io.File

// עזרים רצה אך ורק כ-priv-app בקושחה שמכילה את קובץ ההרשאות הייעודי שלה.
// הבדיקה מתבצעת פעם אחת בלבד, ב-LumaApp.onCreate, לפני שכל רכיב באפליקציה נוצר.
// אם הקובץ חסר התהליך נסגר מיד, ולכן אין צורך לבדוק שוב באף Activity, Service או Receiver.
object FirmwareGuard {
    private const val PERMISSIONS_FILE_PATH = "/system/etc/permissions/privapp-permissions-ezrim.xml"

    fun isGenuine(): Boolean = try { File(PERMISSIONS_FILE_PATH).exists() } catch (_: Exception) { false }
}
