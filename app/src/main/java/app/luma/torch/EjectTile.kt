package app.luma.torch

import android.os.storage.StorageManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.os.Handler
import android.util.Log
import android.widget.Toast

// אריח "הוצא": מנתק (unmount) את כרטיס הזיכרון הנשלף.
// דרך StorageManager בלבד (דורש חתימת מערכת + MOUNT_UNMOUNT_FILESYSTEMS). בלי root.
class EjectTile : TileService() {
    private val TAG = "EjectTile"
    override fun onStartListening() { sync() }

    // מזהי כרטיסים נשלפים (TYPE_PUBLIC=0) שמורכבים כרגע (STATE_MOUNTED=2)
    private fun mountedIds(): List<String> {
        try {
            val sm = getSystemService(StorageManager::class.java)
            val vi = Class.forName("android.os.storage.VolumeInfo")
            val vols = StorageManager::class.java.getMethod("getVolumes").invoke(sm) as List<*>
            val getType = vi.getMethod("getType"); val getState = vi.getMethod("getState"); val getId = vi.getMethod("getId")
            val ids = vols.filterNotNull().filter { getType.invoke(it) == 0 && getState.invoke(it) == 2 }.map { getId.invoke(it) as String }
            Log.i(TAG, "volumes=${vols.size} mounted public=$ids")
            return ids
        } catch (t: Throwable) { Log.w(TAG, "getVolumes failed", t) }
        return emptyList()
    }

    private fun unmount(id: String) {
        try {
            StorageManager::class.java.getMethod("unmount", String::class.java).invoke(getSystemService(StorageManager::class.java), id)
            Log.i(TAG, "unmount($id) via StorageManager ok")
        } catch (t: Throwable) { Log.w(TAG, "unmount($id) failed", t) }
    }

    private fun sync() {
        Thread {
            val has = try { mountedIds().isNotEmpty() } catch (_: Throwable) { false }
            // אין כרטיס מחובר: האריח אפור ולא לחיץ (UNAVAILABLE)
            qsTile?.apply { state = if (has) Tile.STATE_ACTIVE else Tile.STATE_UNAVAILABLE; label = "הוצא"; updateTile() }
        }.start()
    }

    private fun toast(t: String) { Handler(mainLooper).post { Toast.makeText(applicationContext, t, Toast.LENGTH_LONG).show() } }

    override fun onClick() {
        Thread {
            try {
                val ids = mountedIds()
                if (ids.isEmpty()) { toast("לא נמצא כרטיס זיכרון מחובר") }
                else {
                    ids.forEach { unmount(it) }
                    // הניתוק אסינכרוני: ממתינים עד ~6 שניות עד שהכרטיס באמת כבר לא מורכב
                    var done = false
                    for (i in 0 until 12) {
                        Thread.sleep(500)
                        if (mountedIds().none { it in ids }) { done = true; break }
                    }
                    toast(if (done) "הכרטיס הוצא בהצלחה" else "הוצאת הכרטיס נכשלה")
                }
            } catch (t: Throwable) { Log.e(TAG, "click", t); toast("הוצאת הכרטיס נכשלה") }
            sync()
        }.start()
    }
}
