package com.superglobalpeq.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.superglobalpeq.app.MainActivity
import com.superglobalpeq.app.R
import com.superglobalpeq.app.data.QuveSettings
import com.superglobalpeq.app.dsp.ShootoutProcessor
import com.superglobalpeq.app.model.ShootoutOutcome

class PeqForegroundService : Service() {
    companion object {
        private const val TAG = "PeqForegroundService"
        private const val CHANNEL_ID = "super_peq_engine"
        private const val NOTIFICATION_ID = 31401
        private const val ACTION_STOP = "com.superglobalpeq.app.ACTION_STOP_ENGINE"
        private const val ACTION_SHOOTOUT_A = "com.superglobalpeq.app.ACTION_SHOOTOUT_A"
        private const val ACTION_SHOOTOUT_B = "com.superglobalpeq.app.ACTION_SHOOTOUT_B"
        private const val ACTION_SHOOTOUT_VOTE_A = "com.superglobalpeq.app.ACTION_SHOOTOUT_VOTE_A"
        private const val ACTION_SHOOTOUT_VOTE_B = "com.superglobalpeq.app.ACTION_SHOOTOUT_VOTE_B"
        private const val ACTION_SHOOTOUT_TIE = "com.superglobalpeq.app.ACTION_SHOOTOUT_TIE"

        fun startOrUpdate(context: Context, title: String, text: String) {
            val intent = Intent(context, PeqForegroundService::class.java).apply {
                putExtra("title", title)
                putExtra("text", text)
            }
            try {
                context.startForegroundService(intent)
            } catch (t: Throwable) {
                Log.w(TAG, "Unable to startForegroundService right now", t)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PeqForegroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        QuveSettings.initialize(applicationContext)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        QuveSettings.initialize(applicationContext)
        PeqEngineManager.initialize(applicationContext)

        if (intent?.action == ACTION_STOP) {
            PeqEngineManager.setMasterPower(false)
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_SHOOTOUT_A -> ShootoutManager.selectSide(ShootoutProcessor.SIDE_A)
            ACTION_SHOOTOUT_B -> ShootoutManager.selectSide(ShootoutProcessor.SIDE_B)
            ACTION_SHOOTOUT_VOTE_A -> ShootoutManager.vote(ShootoutOutcome.A)
            ACTION_SHOOTOUT_VOTE_B -> ShootoutManager.vote(ShootoutOutcome.B)
            ACTION_SHOOTOUT_TIE -> ShootoutManager.vote(ShootoutOutcome.TIE)
        }

        createChannel()
        val localized = QuveSettings.localizedContext(this)
        val stickyRestart = intent == null
        val keepSticky = PeqEngineManager.shouldKeepServiceSticky()

        if (stickyRestart && !keepSticky) {
            val notification = buildNotification(
                localized = localized,
                title = localized.getString(R.string.notif_default_title),
                text = localized.getString(R.string.notif_default_text)
            )
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
            stopSelf()
            return START_NOT_STICKY
        }

        val fallbackContent = PeqEngineManager.buildForegroundNotificationContent()
        val title = intent?.getStringExtra("title")
            ?: fallbackContent?.first
            ?: localized.getString(R.string.notif_default_title)
        val text = intent?.getStringExtra("text")
            ?: fallbackContent?.second
            ?: localized.getString(R.string.notif_default_text)
        val notification = buildNotification(localized, title, text)
        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )

        if (stickyRestart) {
            PeqEngineManager.onForegroundServiceRestored()
        }
        return if (keepSticky) START_STICKY else START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val localized = QuveSettings.localizedContext(this)
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(
            CHANNEL_ID,
            localized.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = localized.getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(localized: Context, title: String, text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, PeqForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_waveform)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, localized.getString(R.string.notif_action_stop), stopIntent)
        if (ShootoutManager.showsNotificationControls) {
            builder
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomBigContentView(buildShootoutControls(localized))
        }
        return builder.build()
    }

    /** Fallback controls for when drawing over other apps has not been allowed. */
    private fun buildShootoutControls(localized: Context): RemoteViews {
        val running = ShootoutManager.state.value.running
        val views = RemoteViews(packageName, R.layout.notification_shootout)
        views.setTextViewText(
            R.id.shootout_notification_status,
            localized.getString(
                when {
                    running == null -> R.string.shootout_notif_text
                    running.side == ShootoutProcessor.SIDE_A -> R.string.shootout_playing_a
                    else -> R.string.shootout_playing_b
                }
            )
        )
        views.setTextViewText(R.id.shootout_notification_a, localized.getString(R.string.shootout_side_a))
        views.setTextViewText(R.id.shootout_notification_b, localized.getString(R.string.shootout_side_b))
        views.setTextViewText(R.id.shootout_notification_vote_a, localized.getString(R.string.shootout_vote_a))
        views.setTextViewText(R.id.shootout_notification_tie, localized.getString(R.string.shootout_vote_tie))
        views.setTextViewText(R.id.shootout_notification_vote_b, localized.getString(R.string.shootout_vote_b))
        val canVote = running?.canVote == true
        views.setBoolean(R.id.shootout_notification_vote_a, "setEnabled", canVote)
        views.setBoolean(R.id.shootout_notification_tie, "setEnabled", canVote)
        views.setBoolean(R.id.shootout_notification_vote_b, "setEnabled", canVote)
        listOf(
            R.id.shootout_notification_a to ACTION_SHOOTOUT_A,
            R.id.shootout_notification_b to ACTION_SHOOTOUT_B,
            R.id.shootout_notification_vote_a to ACTION_SHOOTOUT_VOTE_A,
            R.id.shootout_notification_tie to ACTION_SHOOTOUT_TIE,
            R.id.shootout_notification_vote_b to ACTION_SHOOTOUT_VOTE_B
        ).forEachIndexed { index, (viewId, action) ->
            views.setOnClickPendingIntent(
                viewId,
                PendingIntent.getService(
                    this,
                    10 + index,
                    Intent(this, PeqForegroundService::class.java).setAction(action),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }
        return views
    }
}
