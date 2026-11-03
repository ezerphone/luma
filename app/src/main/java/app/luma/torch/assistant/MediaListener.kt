package app.luma.torch.assistant

import android.service.notification.NotificationListenerService

// "גישה להתראות" של עזרים: לדעת איזה שיר מתנגן (MediaSessionManager), ולהקריא את ההתראות כשמבקשים
// ("תקריא לי התראות"). לא שומרת ולא שולחת שום דבר.
class MediaListener : NotificationListenerService() {
    companion object {
        @Volatile var instance: MediaListener? = null
            private set
    }

    override fun onListenerConnected() { instance = this }
    override fun onListenerDisconnected() { if (instance === this) instance = null }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }
}
