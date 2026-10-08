package com.watchthetime.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.watchthetime.domain.engine.ClockFormat
import com.watchthetime.domain.engine.Command
import com.watchthetime.domain.model.TeamSide

/**
 * Foreground service that keeps the process (and CPU, via a partial wake lock) alive while the
 * game clock or a timeout is running, so the buzzer and haptics fire on time with the screen
 * off. The engine itself lives in [GameController]; this only hosts it and shows the ongoing
 * notification (with a native countdown chronometer, so no per-second updates are needed).
 */
class ClockService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        ensureChannel(this)
        val n = buildNotification(latest)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "watchthetime:clock")
            .apply { setReferenceCounted(false); acquire(4 * 60 * 60 * 1000L) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_CLOCK -> container.controller.dispatch(Command.StopClock)
            ACTION_END_TIMEOUT -> container.controller.dispatch(Command.EndTimeout)
        }
        refresh()
        return START_NOT_STICKY
    }

    fun refresh() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(latest))
    }

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun buildNotification(live: LiveGame?): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_GAME_ID, live?.gameId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_clock)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setColor(0xFFFF8A00.toInt())

        if (live == null) return b.setContentTitle("Game clock").build()

        val s = live.state
        val home = s.team(TeamSide.HOME)
        val away = s.team(TeamSide.AWAY)
        b.setContentTitle("${home.info.shortName} ${home.score} – ${away.score} ${away.info.shortName}")
        val now = container.controller.now()
        val period = ClockFormat.periodShort(s.period, s.rules)
        val t = s.timeout
        if (t != null) {
            b.setContentText("$period · TIMEOUT ${s.team(t.side).info.shortName}")
            b.setUsesChronometer(true).setChronometerCountDown(true)
            b.setWhen(System.currentTimeMillis() + t.countdown.at(now))
            b.addAction(action("END TIMEOUT", ACTION_END_TIMEOUT))
        } else if (s.clock.running) {
            b.setContentText("$period · clock running")
            b.setUsesChronometer(true).setChronometerCountDown(true)
            b.setWhen(System.currentTimeMillis() + s.clock.at(now))
            b.addAction(action("STOP CLOCK", ACTION_STOP_CLOCK))
        } else {
            b.setContentText("$period · ${ClockFormat.plain(s.clock.at(now), true)} · stopped")
            b.setShowWhen(false)
        }
        return b.build()
    }

    private fun action(label: String, action: String): Notification.Action {
        val pi = PendingIntent.getService(
            this, action.hashCode(), Intent(this, ClockService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(null, label, pi).build()
    }

    companion object {
        private const val CHANNEL_ID = "live_game"
        private const val NOTIFICATION_ID = 24
        private const val ACTION_STOP_CLOCK = "com.watchthetime.STOP_CLOCK"
        private const val ACTION_END_TIMEOUT = "com.watchthetime.END_TIMEOUT"

        @Volatile private var latest: LiveGame? = null
        private var instance: ClockService? = null
        private var started = false

        private fun ensureChannel(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Live game clock", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Shown while the game clock or a timeout is running"
                        setSound(null, null)
                        enableVibration(false)
                    }
                )
            }
        }

        /** Called by [GameController] after every state change. */
        fun update(ctx: Context, live: LiveGame?) {
            val needed = live != null && !live.state.finalized && (live.state.clock.running || live.state.timeout != null)
            latest = live
            val intent = Intent(ctx, ClockService::class.java)
            if (needed) {
                if (!started) {
                    started = runCatching { ContextCompat.startForegroundService(ctx, intent) }.isSuccess
                } else {
                    instance?.refresh()
                }
            } else if (started) {
                started = false
                ctx.stopService(intent)
            }
        }
    }
}
