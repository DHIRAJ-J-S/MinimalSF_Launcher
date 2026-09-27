package com.minimal.launcher

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat

class MusicNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {}
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    companion object {
        fun isEnabled(ctx: Context) =
            NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    }
}
