package de.trademonitor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.trademonitor.app.api.ApiClient
import de.trademonitor.app.ui.dashboard.DashboardScreen
import de.trademonitor.app.ui.detail.AccountDetailScreen
import de.trademonitor.app.ui.health.ServerHealthScreen
import de.trademonitor.app.ui.login.LoginScreen
import de.trademonitor.app.ui.security.SecurityAuditScreen
import de.trademonitor.app.ui.stats.DrawdownScreen
import de.trademonitor.app.ui.theme.TradeMonitorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TradeMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    // Check if we already have a saved server url to determine initial screen
    // Auto-login logic will be handled inside LoginScreen.
    val startDestination = "login"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        
        composable("dashboard") {
            DashboardScreen(
                onAccountClick = { accountId ->
                    navController.navigate("detail/$accountId")
                },
                onViewDrawdowns = {
                    navController.navigate("drawdowns")
                },
                onViewHealth = {
                    navController.navigate("health")
                },
                onViewSecurityAudit = {
                    navController.navigate("security_audit")
                },
                onLogout = {
                    navController.navigate("login") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            )
        }
        
        composable(
            route = "detail/{accountId}",
            arguments = listOf(navArgument("accountId") { type = NavType.LongType })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getLong("accountId") ?: 0L
            AccountDetailScreen(
                accountId = accountId,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
        
        composable("drawdowns") {
            DrawdownScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable("health") {
            ServerHealthScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable("security_audit") {
            SecurityAuditScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
