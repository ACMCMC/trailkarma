package fyi.acmc.trailkarma.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import fyi.acmc.trailkarma.ui.ble.BleScreen
import fyi.acmc.trailkarma.ui.history.ReportHistoryScreen
import fyi.acmc.trailkarma.ui.home.HomeScreen
import fyi.acmc.trailkarma.ui.login.LoginScreen
import fyi.acmc.trailkarma.ui.map.MapScreen
import fyi.acmc.trailkarma.ui.report.CreateReportScreen

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val CREATE_REPORT = "create_report"
    const val HISTORY = "history"
    const val BLE = "ble"
    const val MAP = "map"
}

@Composable
fun TrailKarmaNavGraph(navController: NavHostController, startDestination: String) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(onLoginSuccess = { navController.navigate(Routes.HOME) {
                popUpTo(Routes.LOGIN) { inclusive = true }
            }})
        }
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToReport = { navController.navigate(Routes.CREATE_REPORT) },
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateToBle = { navController.navigate(Routes.BLE) },
                onNavigateToMap = { navController.navigate(Routes.MAP) }
            )
        }
        composable(Routes.CREATE_REPORT) {
            CreateReportScreen(onReportSaved = { navController.popBackStack() })
        }
        composable(Routes.HISTORY) {
            ReportHistoryScreen()
        }
        composable(Routes.BLE) {
            BleScreen()
        }
        composable(Routes.MAP) {
            MapScreen()
        }
    }
}
