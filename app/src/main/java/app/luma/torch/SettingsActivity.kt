package app.luma.torch

import android.os.Bundle

class SettingsActivity : BaseActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        page(
            header("הגדרות", null, "מראה האפליקציה"),
            section("תצוגה", group(listOf("אוטומטי", "בהיר", "כהה"), { Torch.theme }) { Torch.theme = it; recreate() })
        )
    }
}
