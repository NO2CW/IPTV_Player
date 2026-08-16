package com.example.iptvplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.iptvplayer.ui.channels.ChannelsRoute
import com.example.iptvplayer.ui.player.PlayerRoute
import com.example.iptvplayer.ui.setup.SetupRoute
import com.example.iptvplayer.ui.theme.IPTVTheme
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IPTVTheme {
                val app = application as IptvApplication
                val navController = rememberNavController()

                // Pick the start screen based on whether a source already exists locally.
                var start by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) {
                    val hasSource = app.container.repository.sources().first().isNotEmpty()
                    start = if (hasSource) "channels" else "setup"
                }

                start?.let { initial ->
                    NavHost(navController = navController, startDestination = initial) {
                        composable("setup") {
                            SetupRoute(onNavigateChannels = {
                                navController.navigate("channels") {
                                    popUpTo("setup") { inclusive = true }
                                }
                            })
                        }
                        composable("channels") {
                            ChannelsRoute(
                                onPlay = { channel ->
                                    navController.navigate("player/${channel.id}")
                                },
                                onAddSource = { navController.navigate("setup") }
                            )
                        }
                        composable(
                            route = "player/{channelId}",
                            arguments = listOf(navArgument("channelId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val channelId = backStackEntry.arguments?.getLong("channelId") ?: -1L
                            PlayerRoute(
                                channelId = channelId,
                                onExit = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
