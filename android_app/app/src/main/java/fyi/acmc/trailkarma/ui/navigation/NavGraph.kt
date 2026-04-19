package fyi.acmc.trailkarma.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import fyi.acmc.trailkarma.ui.ble.BleScreen
import fyi.acmc.trailkarma.ui.camera.CameraScreen
import fyi.acmc.trailkarma.ui.history.ReportHistoryScreen
import fyi.acmc.trailkarma.ui.login.LoginScreen
import fyi.acmc.trailkarma.ui.map.MapScreen
import fyi.acmc.trailkarma.ui.report.CreateReportScreen
import fyi.acmc.trailkarma.ui.report.ReportDetailScreen
import fyi.acmc.trailkarma.ui.info.AboutScreen
import fyi.acmc.trailkarma.ui.info.SyncStatusScreen
import fyi.acmc.trailkarma.ui.info.ContactScreen
import fyi.acmc.trailkarma.ui.info.ContactTracingScreen
import fyi.acmc.trailkarma.ui.profile.ProfileScreen
import fyi.acmc.trailkarma.ui.rewards.RewardsScreen

object Routes {
    const val LOGIN           = "login"
    const val MAP             = "map"
    const val CAMERA          = "camera"
    const val CREATE_REPORT   = "create_report"
    const val REWARDS         = "rewards"
    const val PROFILE         = "profile"
    const val HISTORY         = "history"
    const val BLE             = "ble"
    const val REPORT_DETAIL   = "report/{reportId}"
    const val ABOUT           = "about"
    const val SYNC_STATUS     = "sync_status"
    const val CONTACT         = "contact"
    const val CONTACT_TRACING = "contact_tracing"

    fun reportDetail(reportId: String) = "report/$reportId"
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
                onNavigateToReportDetail = { reportId ->
                    navController.navigate(Routes.reportDetail(reportId))
                },
                onNavigateToRewards = { navController.navigate(Routes.REWARDS) },
                onNavigateToProfile = { navController.navigate(Routes.PROFILE) },
                onNavigateToBle = { navController.navigate(Routes.BLE) },
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateToAbout = { navController.navigate(Routes.ABOUT) },
                onNavigateToSyncStatus = { navController.navigate(Routes.SYNC_STATUS) },
                onNavigateToContact = { navController.navigate(Routes.CONTACT) },
                onNavigateToContactTracing = { navController.navigate(Routes.CONTACT_TRACING) }
            )
        }
        composable(Routes.REWARDS) {
            RewardsScreen(
                onBack = { navController.popBackStack() },
                onOpenRelayMissions = { navController.navigate(Routes.BLE) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) }
            )
        }
        composable(Routes.PROFILE) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
                onOpenSyncStatus = { navController.navigate(Routes.SYNC_STATUS) },
                onOpenContact = { navController.navigate(Routes.CONTACT) },
                onOpenTracing = { navController.navigate(Routes.CONTACT_TRACING) }
            )
        }
        composable(Routes.CAMERA) {
            CameraScreen(onSpeciesIdentified = { navController.popBackStack() })
        }
        composable(Routes.CREATE_REPORT) {
            CreateReportScreen(onReportSaved = { navController.popBackStack() })
        }
        composable(Routes.HISTORY) {
            ReportHistoryScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.BLE) {
            BleScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.REPORT_DETAIL,
            arguments = listOf(navArgument("reportId") { type = NavType.StringType })
        ) {
            ReportDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SYNC_STATUS) {
            SyncStatusScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CONTACT) {
            ContactScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CONTACT_TRACING) {
            ContactTracingScreen(onBack = { navController.popBackStack() })
        }
    }
}
