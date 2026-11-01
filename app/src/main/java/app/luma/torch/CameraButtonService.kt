package app.luma.torch

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import app.luma.torch.dictation.ModelLocator
import app.luma.torch.dictation.PushToTalk
import app.luma.torch.dictation.VoiceIme
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent

// שירות הנגישות "עזרים - קיצורים": כפתור המצלמה ומקש הבית שמתחת למסך.
// (שם המחלקה נשאר כמו שהיה, כדי שהשירות יישאר מופעל אצל מי שכבר הפעיל אותו)
class CameraButtonService : AccessibilityService() {
    private val h = Handler(Looper.getMainLooper())
    private var longFired = false
    private var waitingDouble = false
    private val singleRun = Runnable { waitingDouble = false; act(Torch.shortAct, Torch.shortPkg) }
    private var homeWaiting = false
    private var homeDown = false
    private val homeSingle = Runnable { homeWaiting = false; act(Torch.homeAct, "") }
    private var dictKey = false // הלחיצה הנוכחית על כפתור המצלמה שייכת להכתבה
    private var holdStarted = false // במצב החזקה: הלחיצה הזו התחילה הכתבה, והעזיבה תעצור אותה

    private var fromSystem = false // הלחיצה הנוכחית נקלטה כשהמסך כבוי
    private var screenOffPress = false // הלחיצה הנוכחית התחילה כשהמסך היה כבוי, והעזיבה שלה שייכת לכאן
    private var mediaListener: Any? = null
    private val wake by lazy {
        getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "luma:camera-key").apply { setReferenceCounted(false) }
    }

    companion object {
        // השירות הפעיל — העוזר משתמש בו לפעולות מערכת (מסך הבית, צילום מסך, נעילה…)
        @Volatile var instance: CameraButtonService? = null
            private set
    }

    override fun onServiceConnected() {
        Torch.init(this)
        instance = this
        registerMediaKeyListener()
    }

    // ---------- כפתור המצלמה כשהמסך כבוי, בלי root ובלי להדליק את המסך ----------
    // כשהמסך כבוי אנדרואיד זורק את רוב הלחיצות לפני ששירות הנגישות רואה אותן, אבל מקשי מדיה הוא ממשיך
    // להעביר (בלי להדליק את המסך) — כמו כפתור באוזניות. לכן בקושחה כפתור המצלמה ממופה ל-MEDIA_RECORD
    // (firmware/README-keys-he.md), ועזרים נרשמת כ"מאזין מקשי מדיה" של המערכת ותופסת רק את המקש הזה,
    // ורק כשהוא מגיע מהכפתור הפיזי. כל שאר מקשי המדיה (אוזניות, בלוטוס) ממשיכים לנגן כרגיל.
    // דורש את ההרשאה SET_MEDIA_KEY_LISTENER בקובץ ההרשאות של הקושחה. ה-API מוסתר, לכן דרך reflection.
    private fun registerMediaKeyListener() {
        try {
            val msm = getSystemService(android.media.session.MediaSessionManager::class.java) ?: return
            val cls = Class.forName("android.media.session.MediaSessionManager\$OnMediaKeyListener")
            val listener = java.lang.reflect.Proxy.newProxyInstance(cls.classLoader, arrayOf(cls)) { proxy, m, args ->
                when (m.name) {
                    "onMediaKey" -> onMediaKey(args!![0] as KeyEvent)
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.getOrNull(0)
                    "toString" -> "LumaMediaKeyListener"
                    else -> null
                }
            }
            msm.javaClass.getMethod("setOnMediaKeyListener", cls, Handler::class.java).invoke(msm, listener, h)
            mediaListener = listener
        } catch (e: Throwable) {
            android.util.Log.w("Luma", "media key listener not available (missing SET_MEDIA_KEY_LISTENER?)", e)
        }
    }

    private fun unregisterMediaKeyListener() {
        if (mediaListener == null) return
        try {
            val msm = getSystemService(android.media.session.MediaSessionManager::class.java)
            val cls = Class.forName("android.media.session.MediaSessionManager\$OnMediaKeyListener")
            msm.javaClass.getMethod("setOnMediaKeyListener", cls, Handler::class.java).invoke(msm, null, null)
        } catch (_: Throwable) {}
        mediaListener = null
    }

    // הכפתור הפיזי (אחרי המיפוי בקושחה מגיע כ-MEDIA_RECORD), ולא מקש מדיה מאוזניות או מבלוטוס
    private fun isCameraKey(e: KeyEvent): Boolean {
        if (e.keyCode == KeyEvent.KEYCODE_CAMERA) return true
        if (e.keyCode != KeyEvent.KEYCODE_MEDIA_RECORD) return false
        val d = android.view.InputDevice.getDevice(e.deviceId) ?: return false
        return !d.isVirtual && !d.isExternal
    }

    // מגיע מהמערכת כשהמסך כבוי (כשהמסך דולק שירות הנגישות תופס את הלחיצה קודם). true = טופל כאן
    private fun onMediaKey(e: KeyEvent): Boolean {
        if (!isCameraKey(e)) return false // כל מקש מדיה אחר ממשיך לאפליקציית המוזיקה
        wake.acquire(3000) // שהמעבד לא יירדם באמצע זיהוי לחיצה ארוכה/כפולה
        val down = e.action == KeyEvent.ACTION_DOWN
        if (down) { if (e.repeatCount > 0) return true; screenOffPress = true }
        else if (!screenOffPress) return true
        else screenOffPress = false
        fromSystem = true
        try { camera(e) } finally { fromSystem = false }
        return true
    }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { if (instance === this) instance = null; unregisterMediaKeyListener(); h.removeCallbacksAndMessages(null); super.onDestroy() }

    private val longRun = Runnable {
        longFired = true
        h.removeCallbacks(singleRun); waitingDouble = false
        act(Torch.longAct, Torch.longPkg)
    }

    override fun onKeyEvent(e: KeyEvent): Boolean = when {
        isCameraKey(e) -> camera(e)
        e.keyCode == KeyEvent.KEYCODE_HOME -> home(e)
        else -> false
    }

    // שירות נגישות מקבל רק לחיצה ושחרור (בלי אירועי "החזקה"), לכן לחיצה ארוכה נמדדת בטיימר
    private fun camera(e: KeyEvent): Boolean {
        // הכתבה קולית כשיש שדה טקסט פתוח: הכפתור כולו מוקדש להכתבה, בלי לחיצה ארוכה/כפולה
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0 && !dictKey && dictationMode()) {
            dictKey = true; holdStarted = false
            val busy = PushToTalk.active || PushToTalk.keyboard?.pttListening() == true
            if (Torch.dictHold) { if (!busy) { startDictation(); holdStarted = true } } // באמצע סיום של הכתבה קודמת: מתעלמים
            else if (busy) stopDictation()
            else startDictation()
            return true
        }
        if (dictKey) {
            if (e.action == KeyEvent.ACTION_UP) { dictKey = false; if (holdStarted) stopDictation() }
            return true
        }
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

    // מקש הבית. בהגדרת ברירת המחדל (לחיצה = מסך הבית, אין לחיצה כפולה) המקש עובר למערכת כרגיל,
    // כולל לחיצה ארוכה. אחרת הוא נבלע כאן ומבצע את מה שנבחר.
    private fun home(e: KeyEvent): Boolean {
        if (Torch.homeAct == 5 && Torch.homeDblAct == 0 && !homeDown) return false
        if (e.action == KeyEvent.ACTION_DOWN) { homeDown = true; return true }
        if (!homeDown) return false // שחרור של לחיצה שהתחילה לפני שההגדרה השתנתה
        homeDown = false
        if (homeWaiting) {
            h.removeCallbacks(homeSingle); homeWaiting = false
            act(Torch.homeDblAct, "")
        } else if (Torch.homeDblAct != 0) {
            homeWaiting = true
            h.postDelayed(homeSingle, 400)
        } else {
            act(Torch.homeAct, "")
        }
        return true
    }

    // ---------- הכתבה מכפתור המצלמה ----------
    private fun dictationMode(): Boolean {
        if (!Torch.dictInText || !ModelLocator.isInstalled() || fromSystem) return false
        // על מסך הנעילה אין הכתבה: הכפתור עושה את הפעולות הרגילות
        if (getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true) return false
        if (PushToTalk.active) return true // באמצע הכתבה: הלחיצה הבאה עוצרת אותה
        return try {
            val n = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            n != null && n.isEditable && !n.isPassword
        } catch (_: Exception) { false }
    }

    private fun currentIme(): String? = try { Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) } catch (_: Exception) { null }

    private fun switchIme(id: String?): Boolean {
        if (id.isNullOrEmpty()) return false
        if (Build.VERSION.SDK_INT >= 30) {
            try { if (softKeyboardController.switchToInputMethod(id)) return true } catch (_: Exception) {}
        }
        return try { Settings.Secure.putString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD, id) } catch (_: Exception) { false }
    }

    private fun startDictation() {
        val me = VoiceIme.id(this)
        val prev = currentIme()
        if (prev?.substringBefore(';') == me) {
            // המקלדת הקולית כבר פתוחה: רק מתחילים להקשיב, ובסיום לא מחליפים מקלדת
            PushToTalk.begin(null)
            PushToTalk.keyboard?.pttStart()
            return
        }
        if (!VoiceIme.enableNow(this)) { toast("המקלדת הקולית לא מופעלת"); return }
        PushToTalk.begin { switchIme(prev) }
        if (!switchIme(me)) { PushToTalk.done(); toast("לא ניתן לעבור למקלדת הקולית") }
        // מכאן המקלדת הקולית עולה ומתחילה להקשיב בעצמה (onStartInputView)
    }

    private fun stopDictation() {
        val k = PushToTalk.keyboard
        when {
            k != null -> k.pttStop()            // עוצרת, מכניסה את הטקסט, ואז done() מחזיר את המקלדת הקודמת
            PushToTalk.active -> PushToTalk.done() // עזיבה לפני שהמקלדת הקולית הספיקה לעלות
        }
    }

    private fun toast(t: String) { h.post { Toast.makeText(this, t, Toast.LENGTH_SHORT).show() } }

    private fun act(a: Int, pkg: String) {
        when (a) {
            1 -> Torch.set(!Torch.on)
            2 -> packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            3 -> performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            4 -> performGlobalAction(GLOBAL_ACTION_BACK)
            5 -> performGlobalAction(GLOBAL_ACTION_HOME)
            6 -> try { startActivity(Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
            // 7 היה "הקלטת מסך" והוסר
            8 -> mediaPlayPause()
            9 -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        }
    }

    // הפעלה/עצירה של המדיה שמתנגנת כרגע (מוזיקה, שיעור, פודקאסט), כמו מקש play באוזניות
    private fun mediaPlayPause() {
        val am = getSystemService(AudioManager::class.java) ?: return
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
    }
}
