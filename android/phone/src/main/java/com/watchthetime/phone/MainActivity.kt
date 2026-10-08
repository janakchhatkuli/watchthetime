package com.watchthetime.phone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.watchthetime.brand.WttTheme
import com.watchthetime.phone.ui.BoxScoreScreen
import com.watchthetime.phone.ui.GamesScreen
import com.watchthetime.phone.ui.LiveScreen
import com.watchthetime.phone.ui.LogScreen
import com.watchthetime.phone.ui.NewGameScreen
import com.watchthetime.phone.ui.SettingsScreen
import com.watchthetime.phone.ui.SetupScreen
import com.watchthetime.phone.ui.TeamEditScreen
import com.watchthetime.phone.ui.TeamsScreen

class MainActivity : ComponentActivity() {

    private val deepLinkGame = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(0xFF0A0A0A.toInt()),
            navigationBarStyle = SystemBarStyle.dark(0xFF0A0A0A.toInt()),
        )
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        deepLinkGame.value = intent?.getStringExtra(EXTRA_GAME_ID)
        setContent {
            WttTheme {
                val nav = rememberNavController()
                val link = deepLinkGame.value
                LaunchedEffect(link) {
                    if (link != null) {
                        deepLinkGame.value = null
                        nav.navigate(Routes.live(link)) { launchSingleTop = true }
                    }
                }
                AppNav(nav)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_GAME_ID)?.let { deepLinkGame.value = it }
    }

    companion object {
        const val EXTRA_GAME_ID = "game_id"
    }
}

object Routes {
    const val GAMES = "games"
    const val NEW = "new"
    const val TEAMS = "teams"
    const val SETTINGS = "settings"
    fun team(id: String) = "team/$id"
    fun live(id: String) = "live/$id"
    fun log(id: String) = "log/$id"
    fun box(id: String) = "box/$id"
    fun setup(id: String) = "setup/$id"
}

@Composable
private fun AppNav(nav: NavHostController) {
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(nav, startDestination = Routes.GAMES) {
        composable(Routes.GAMES) {
            GamesScreen(
                onNew = { nav.navigate(Routes.NEW) },
                onOpen = { nav.navigate(Routes.live(it)) },
                onTeams = { nav.navigate(Routes.TEAMS) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.NEW) {
            NewGameScreen(onBack = back, onCreated = { id ->
                nav.navigate(Routes.live(id)) { popUpTo(Routes.GAMES) }
            })
        }
        composable(Routes.TEAMS) {
            TeamsScreen(onBack = back, onEdit = { nav.navigate(Routes.team(it)) })
        }
        composable("team/{id}") { e ->
            TeamEditScreen(teamId = e.arguments?.getString("id") ?: "new", onBack = back)
        }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = back) }
        composable("live/{id}") { e ->
            val id = e.arguments?.getString("id") ?: return@composable
            LiveScreen(
                gameId = id, onBack = back,
                onLog = { nav.navigate(Routes.log(id)) },
                onBox = { nav.navigate(Routes.box(id)) },
                onSetup = { nav.navigate(Routes.setup(id)) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable("log/{id}") { e -> LogScreen(e.arguments?.getString("id") ?: return@composable, onBack = back) }
        composable("box/{id}") { e -> BoxScoreScreen(e.arguments?.getString("id") ?: return@composable, onBack = back) }
        composable("setup/{id}") { e -> SetupScreen(e.arguments?.getString("id") ?: return@composable, onBack = back) }
    }
}
