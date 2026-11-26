package dev.codeforge.ide.terminal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import dev.codeforge.ide.MainActivity
import dev.codeforge.ide.R

/**
 * Foreground service that keeps terminal processes alive while the app is in the background
 * (e.g. a long `apk add` or a compile). It holds no logic of its own.
 */
class TerminalService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Terminal sessions", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val running = TerminalHub.runningCount()
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_terminal)
            .setContentTitle("CodeForge")
            .setContentText(if (running == 1) "1 terminal session running" else "$running terminal sessions running")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        startForeground(1, n)
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "terminal"

        fun sync(context: Context) {
            val intent = Intent(context, TerminalService::class.java)
            if (TerminalHub.runningCount() > 0) context.startForegroundService(intent) else context.stopService(intent)
        }
    }
}
