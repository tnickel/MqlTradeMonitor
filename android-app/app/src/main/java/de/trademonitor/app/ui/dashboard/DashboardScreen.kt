package de.trademonitor.app.ui.dashboard

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.trademonitor.app.api.ApiClient
import de.trademonitor.app.model.Account
import de.trademonitor.app.ui.theme.NeonGreen
import de.trademonitor.app.ui.theme.NeonOrange
import de.trademonitor.app.ui.theme.NeonRed
import de.trademonitor.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAccountClick: (Long) -> Unit,
    onViewDrawdowns: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val fetchAccounts = {
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            try {
                val api = ApiClient.getService(context)
                accounts = api.getAccounts()
            } catch (e: Exception) {
                errorMessage = "Fehler beim Laden: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }
    
    // Auto-refresh loop every 30 seconds
    LaunchedEffect(Unit) {
        fetchAccounts()
        while (true) {
            delay(30000)
            try {
                val api = ApiClient.getService(context)
                accounts = api.getAccounts()
            } catch (e: Exception) {
                // Ignore silent errors during background refresh
            }
        }
    }
    
    val performLogout = {
        coroutineScope.launch {
            try {
                val api = ApiClient.getService(context)
                api.logout()
            } catch (e: Exception) {
                // Ignore network errors on logout
            } finally {
                ApiClient.clearSession()
                val sharedPrefs = context.getSharedPreferences("TradeMonitorPrefs", Context.MODE_PRIVATE)
                sharedPrefs.edit()
                    .remove("password") // Remove password so auto-login doesn't trigger again
                    .apply()
                onLogout()
            }
        }
    }

    // Totals calculations
    val totalBalance = accounts.sumOf { it.balance }
    val totalEquity = accounts.sumOf { it.equity }
    val totalProfit = accounts.sumOf { it.profit }
    val activeTradesCount = accounts.sumOf { it.trades }
    val currency = accounts.firstOrNull()?.currency ?: "EUR"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TradeMonitor", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onViewDrawdowns) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Drawdown Stats",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { fetchAccounts() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { performLogout() }) {
                        Icon(imageVector = Icons.Default.ExitToApp, contentDescription = "Abmelden", tint = NeonRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Summary Header Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "GESAMTSTATISTIK",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Gesamt-Balance", fontSize = 13.sp, color = TextSecondary)
                                Text(
                                    text = String.format(Locale.GERMANY, "%,.2f %s", totalBalance, currency),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Gesamt-Equity", fontSize = 13.sp, color = TextSecondary)
                                Text(
                                    text = String.format(Locale.GERMANY, "%,.2f %s", totalEquity, currency),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Offener Profit", fontSize = 13.sp, color = TextSecondary)
                                Text(
                                    text = String.format(Locale.GERMANY, "%+.2f %s", totalProfit, currency),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (totalProfit >= 0) NeonGreen else NeonRed
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Aktive Positionen", fontSize = 13.sp, color = TextSecondary)
                                Text(
                                    text = "$activeTradesCount",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = NeonRed,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Accounts List
                if (accounts.isEmpty() && !isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Keine Konten gefunden", color = TextSecondary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(accounts) { account ->
                            AccountItem(account = account, onClick = { onAccountClick(account.accountId) })
                        }
                    }
                }
            }

            if (isLoading && accounts.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun AccountItem(account: Account, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = account.name ?: "Konto ${account.accountId}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "${account.broker ?: "Unknown Broker"} • Account ${account.accountId}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                
                // Status badges
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if ("REAL".equals(account.type, ignoreCase = true)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFFF9100).copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("REAL", color = Color(0xFFFF9100), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    
                    // Online dot
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (account.online) NeonGreen else NeonRed)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            // Values Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Balance / Equity", fontSize = 11.sp, color = TextSecondary)
                    Text(
                        text = String.format(Locale.GERMANY, "%,.2f / %,.2f", account.balance, account.equity),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Offener Profit", fontSize = 11.sp, color = TextSecondary)
                    Text(
                        text = String.format(Locale.GERMANY, "%+.2f %s", account.profit, account.currency ?: "EUR"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (account.profit >= 0) NeonGreen else NeonRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Stats row (Drawdown & Positionen)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    Text("Drawdown: ", fontSize = 11.sp, color = TextSecondary)
                    Text(
                        text = String.format(Locale.GERMANY, "%.2f%%", account.maxDrawdownPct),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (account.maxDrawdownPct > 5.0) NeonOrange else TextSecondary
                    )
                }
                
                Text(
                    text = "${account.trades} offene Trades",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
