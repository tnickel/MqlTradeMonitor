package de.trademonitor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check for updates on startup
        de.trademonitor.app.api.UpdateManager.checkForUpdates(this)

        setContent {
            TradeMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()

                    if (de.trademonitor.app.api.UpdateManager.showUpdateDialog.value) {
                        UpdateDialog(
                            versionName = de.trademonitor.app.api.UpdateManager.latestVersionName,
                            isDownloading = de.trademonitor.app.api.UpdateManager.isDownloading.value,
                            progress = de.trademonitor.app.api.UpdateManager.downloadProgress.value,
                            onConfirm = {
                                de.trademonitor.app.api.UpdateManager.startDownload(this@MainActivity)
                            },
                            onDismiss = {
                                if (!de.trademonitor.app.api.UpdateManager.isDownloading.value) {
                                    de.trademonitor.app.api.UpdateManager.showUpdateDialog.value = false
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateDialog(
    versionName: String,
    isDownloading: Boolean,
    progress: Float,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isDownloading) "Update wird geladen..." else "Update verfügbar",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isDownloading) {
                    if (progress < 0f) {
                        Text(
                            text = "Fehler beim Download. Bitte versuche es später erneut.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    Text(
                        text = "Eine neue Version ($versionName) der TradeMonitor App steht bereit. Möchtest du sie jetzt installieren?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            if (!isDownloading || progress < 0f) {
                Button(
                    onClick = {
                        if (progress < 0f) {
                            onDismiss()
                        } else {
                            onConfirm()
                        }
                    }
                ) {
                    Text(text = if (progress < 0f) "Schließen" else "Update laden")
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                TextButton(onClick = onDismiss) {
                    Text(text = "Später")
                }
            }
        }
    )
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
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

