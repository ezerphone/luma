package app.luma.torch

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class CameraButtonService : AccessibilityService() {
    private val h = Handler(Looper.getMainLooper())
    private var longFired = false
    private var waitingDouble = false
    private val singleRun = Runnable { waitingDouble = false; act(Torch.shortAct, Torch.shortPkg) }

    override fun onServiceConnected() {
        Torch.init(this)
    }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { h.removeCallbacks(singleRun); h.removeCallbacks(longRun); super.onDestroy() }

    private val longRun = Runnable {
        longFired = true
        h.removeCallbacks(singleRun); waitingDouble = false
        act(Torch.longAct, Torch.longPkg)
    }

    // שירות נגישות מקבל רק לחיצה ושחרור (בלי אירועי "החזקה"), לכן לחיצה ארוכה נמדדת בטיימר
    override fun onKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode != KeyEvent.KEYCODE_CAMERA) return false
        if (e.action == KeyEvent.ACTION_DOWN) {
            if (e.repeatCount == 0) {
                longFired = false
                h.removeCallbacks(longRun)
                if (Torch.longAct != 0) h.postDelayed(longRun, ViewConfiguration.getLongPressTimeout().toLong())
            }
            return true
        }
        // עזיבת המקש
        h.removeCallbacks(longRun)
        if (longFired) { longFired = false; return true }
        if (waitingDouble) {
            h.removeCallbacks(singleRun); waitingDouble = false
            act(Torch.dblAct, Torch.dblPkg)
        } else if (Torch.dblAct != 0) {
            waitingDouble = true
            h.postDelayed(singleRun, 400)
        } else {
            act(Torch.shortAct, Torch.shortPkg)
        }
        return true
    }

    private fun act(a: Int, pkg: String) {
        when (a) {
            1 -> Torch.set(!Torch.on)
            2 -> packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            3 -> performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            4 -> performGlobalAction(GLOBAL_ACTION_BACK)
            5 -> performGlobalAction(GLOBAL_ACTION_HOME)
            6 -> try { startActivity(Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
            7 -> startSystemRecorder()
        }
    }

    // הקלטת מסך: פותח את מקליט המסך של המערכת (SystemUI). הפעלה והפסקה נעשות בו (התראה / האריח).
    // קודם ניסיון רגיל, ואם הרכיב לא פתוח לאפליקציות אחרות, פתיחה דרך root.
    private fun startSystemRecorder() {
        val comps = listOf("com.android.systemui/.screenrecord.ScreenRecordDialog",
                           "com.android.systemui/com.android.systemui.screenrecord.ScreenRecordDialog")
        for (c in comps) {
            try {
                startActivity(Intent().setComponent(android.content.ComponentName.unflattenFromString(c)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {}
        }
        Thread {
            val (_, out) = RootShell.run("am start -n " + comps[0])
            if (out.contains("Error", true) || out.contains("Exception", true)) toast("מקליט המסך של המערכת לא נפתח")
        }.start()
    }

    private fun toast(t: String) { h.post { Toast.makeText(this, t, Toast.LENGTH_SHORT).show() } }
}
