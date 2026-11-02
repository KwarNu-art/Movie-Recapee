package com.movierecapee.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.movierecapee.ui.editor.EditorScreen
import com.movierecapee.ui.home.HomeScreen
import com.movierecapee.ui.settings.SettingsScreen

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(onOpen = { nav.navigate("editor/$it") }, onSettings = { nav.navigate("settings") })
        }
        composable("editor/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            EditorScreen(projectId = entry.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
        }
        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
    }
}
