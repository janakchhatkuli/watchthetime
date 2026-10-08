package com.watchthetime.phone

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.watchthetime.data.DataModule
import com.watchthetime.data.GameRepository
import com.watchthetime.data.SettingsRepository
import com.watchthetime.data.TeamRepository
import com.watchthetime.domain.engine.TimeSource
import com.watchthetime.domain.event.Stamp
import com.watchthetime.feedback.FeedbackPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Monotonic + wall time. The boot counter lets the engine know when monotonic stamps from
 * before a reboot can no longer be compared (it falls back to wall time then).
 */
class AndroidTime(context: Context) : TimeSource {
    private val boot: Int = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
    }.getOrDefault(-1)

    override fun now() = Stamp(SystemClock.elapsedRealtime(), System.currentTimeMillis(), boot)
}

/** Manual DI container: one instance of everything for the process. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val db = DataModule.database(app)
    val games = GameRepository(db)
    val teams = TeamRepository(db)
    val settings = SettingsRepository(app)
    val time = AndroidTime(app)
    val feedback = FeedbackPlayer(app)
    val controller = GameController(app, games, settings, feedback, time, scope)
}

class WttApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.container: AppContainer get() = (applicationContext as WttApp).container
