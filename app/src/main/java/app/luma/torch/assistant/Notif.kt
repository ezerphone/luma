package app.luma.torch.assistant

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build

// הרשאת התראות לתזכורות של עזר (אנדרואיד 13 ומעלה)
object Notif {
    const val REQ = 51
    const val DENIED = "בלי הרשאת התראות התזכורת לא תופיע"
    fun missing(a: Activity) = Build.VERSION.SDK_INT >= 33 &&
        a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    fun ask(a: Activity) { if (Build.VERSION.SDK_INT >= 33) a.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ) }
}
