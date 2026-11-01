package app.luma.torch

import android.os.Bundle

// קיצורים: פעולות לכפתורים הפיזיים — כפתור המצלמה ומקש הבית
class ShortcutsActivity : BaseActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        page(
            header("קיצורים"),
            big(R.drawable.ic_camera, "כפתור המצלמה", "מה יקרה בלחיצה רגילה, כפולה או ארוכה", CameraButtonActivity::class.java) { hasCameraButton() },
            big(R.drawable.ic_home, "מקש הבית", "מה יקרה בלחיצה אחת או כפולה", HomeKeyActivity::class.java)
        )
    }
}
