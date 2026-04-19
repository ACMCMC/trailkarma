package fyi.acmc.trailkarma.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import fyi.acmc.trailkarma.ui.ble.BleScreen
import fyi.acmc.trailkarma.ui.camera.CameraScreen
import fyi.acmc.trailkarma.ui.history.ReportHistoryScreen
import fyi.acmc.trailkarma.ui.login.LoginScreen
import fyi.acmc.trailkarma.ui.map.MapScreen
import fyi.acmc.trailkarma.ui.report.CreateReportScreen

object Routes {
    const val LOGIN = "login"
    const val MAP = "map"
    const val CAMERA = "camera"
    const val CREATE_REPORT = "create_report"
    const val HISTORY = "history"
    const val BLE = "ble"
}

@Composable
fun TrailKarmaNavGraph(navController: NavHostController, startDestination: String) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(onLoginSuccess = {
                navController.navigate(Routes.MAP) {
                    popUpTo(Routes.LOGIN) { inclusive = true }
                }
            })
        }
        composable(Routes.MAP) {
            MapScreen(
                onNavigateToCamera = { navController.navigate(Routes.CAMERA) },
                onNavigateToReport = { navController.navigate(Routes.CREATE_REPORT) },
                onNavigateToBle = { navController.navigate(Routes.BLE) },
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) }
            )
        }
        composable(Routes.CAMERA) {
            CameraScreen(onSpeciesIdentified = { navController.popBackStack() })
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
    }
}
