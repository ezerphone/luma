package app.luma.torch

import android.content.Context
import android.content.SharedPreferences
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper

object Torch {
    var on = false
    var blink = false
    var busy = false // camera used by another app
    val listeners = mutableListOf<() -> Unit>()
    lateinit var sp: SharedPreferences
    private var cm: CameraManager? = null
    private var id: String? = null
    private var lastSet = 0L
    private val h = Handler(Looper.getMainLooper())
    private const val BLINK_MS = 300L

    var timer: Int get() = sp.getInt("t", 0); set(v) = sp.edit().putInt("t", v).apply() // שניות, 0 = כבוי
    var timerLast: Int get() = sp.getInt("tl", 300); set(v) = sp.edit().putInt("tl", v).apply()
    var shake: Boolean get() = sp.getBoolean("s", false); set(v) = sp.edit().putBoolean("s", v).apply()
    var sens: Int get() = sp.getInt("sn", 0); set(v) = sp.edit().putInt("sn", v).apply() // רגישות ניעור: 0 נמוכה, 1 בינונית, 2 גבוהה
    var mockLocationApps: Set<String> get() = sp.getStringSet("mock_loc_apps", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("mock_loc_apps", HashSet(v)).apply()
    var cameraChoice: Int get() = sp.getInt("cam_choice", 0); set(v) = sp.edit().putInt("cam_choice", v).apply() // 0 לא נבחר, 1 מקורית, 2 משודרגת
    var btMac: String get() = sp.getString("btm", "") ?: ""; set(v) = sp.edit().putString("btm", v).apply()
    var sound: Boolean get() = sp.getBoolean("snd", true); set(v) = sp.edit().putBoolean("snd", v).apply()
    var theme: Int get() = sp.getInt("th", 0); set(v) = sp.edit().putInt("th", v).apply() // 0 auto,1 light,2 dark
    var shortAct: Int get() = sp.getInt("sa", 0); set(v) = sp.edit().putInt("sa", v).apply() // 0 none,1 torch,2 app
    var shortPkg: String get() = sp.getString("spk", "") ?: ""; set(v) = sp.edit().putString("spk", v).apply()
    var longAct: Int get() = sp.getInt("la", 1); set(v) = sp.edit().putInt("la", v).apply() // 0 none,1 torch,2 app
    var dblAct: Int get() = sp.getInt("da", 0); set(v) = sp.edit().putInt("da", v).apply()
    var longPkg: String get() = sp.getString("lp", "") ?: ""; set(v) = sp.edit().putString("lp", v).apply()
    var dblPkg: String get() = sp.getString("dp", "") ?: ""; set(v) = sp.edit().putString("dp", v).apply()

    fun init(c: Context) {
        if (cm != null) return
        sp = c.applicationContext.getSharedPreferences("luma", 0)
        val m = c.getSystemService(CameraManager::class.java)
        cm = m
        id = m.cameraIdList.firstOrNull { m.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        m.registerTorchCallback(object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(c: String, e: Boolean) {
                if (c != id) return
                busy = false
                if (!blink && e != on && System.currentTimeMillis() - lastSet > 700) on = e
                emit()
            }
            override fun onTorchModeUnavailable(c: String) {
                if (c != id) return
                busy = true; h.removeCallbacksAndMessages(null); on = false; blink = false; emit()
            }
        }, h)
    }

    private fun hw(v: Boolean) {
        val i = id ?: return
        val m = cm ?: return
        try { m.setTorchMode(i, v) } catch (_: Exception) {}
    }

    private fun emit() = listeners.toList().forEach { it() }

    fun set(v: Boolean) {
        if (v && busy) { emit(); return }
        h.removeCallbacksAndMessages(null)
        lastSet = System.currentTimeMillis()
        blink = false; on = v; hw(v)
        if (v && timer > 0) h.postDelayed({ set(false) }, timer * 1000L)
        emit()
    }

    // הבהוב פנס: דלוק/כבוי לסירוגין עד שעוצרים (או עד שהטיימר מכבה)
    fun blink(v: Boolean) {
        if (!v) { set(false); return }
        set(true)
        if (!on) return
        blink = true; emit()
        var lit = true
        val r = object : Runnable {
            override fun run() {
                if (!blink) return
                lit = !lit; hw(lit)
                h.postDelayed(this, BLINK_MS)
            }
        }
        h.postDelayed(r, BLINK_MS)
    }
}
