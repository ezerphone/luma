package app.luma.torch.assistant

import android.service.notification.NotificationListenerService

// "גישה להתראות" של עזרים: משמשת רק כדי לדעת איזה שיר מתנגן (MediaSessionManager). לא קוראת ולא שומרת הודעות.
class MediaListener : NotificationListenerService()
