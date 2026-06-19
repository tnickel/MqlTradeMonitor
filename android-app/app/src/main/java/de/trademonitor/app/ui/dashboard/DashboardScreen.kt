package de.trademonitor.app.ui.dashboard

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    onAccountClick: (Long) -> Unit,
    onViewDrawdowns: () -> Unit,
    onViewHealth: () -> Unit,
    onViewSecurityAudit: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isAdmin by remember { mutableStateOf(false) }

    // SharedPreferences setup for totals config
    val sharedPrefs = remember { context.getSharedPreferences("TradeMonitorPrefs", Context.MODE_PRIVATE) }
    
    var selectedPeriodReal by remember { 
        mutableStateOf(sharedPrefs.getString("summary_period_real", "gesamt") ?: "gesamt") 
    }
    var selectedAccountIdsReal by remember {
        mutableStateOf(
            if (sharedPrefs.getBoolean("summary_configured_real", false)) {
                sharedPrefs.getStringSet("summary_accounts_real", emptySet()) ?: emptySet()
            } else {
                emptySet()
            }
        )
    }

    var selectedPeriodDemo by remember { 
        mutableStateOf(sharedPrefs.getString("summary_period_demo", "gesamt") ?: "gesamt") 
    }
    var selectedAccountIdsDemo by remember {
        mutableStateOf(
            if (sharedPrefs.getBoolean("summary_configured_demo", false)) {
                sharedPrefs.getStringSet("summary_accounts_demo", emptySet()) ?: emptySet()
            } else {
                emptySet()
            }
        )
    }

    var showConfigDialog by remember { mutableStateOf(false) }
    var configType by remember { mutableStateOf("REAL") } // "REAL" or "DEMO"
    
    var tempPeriod by remember { mutableStateOf("gesamt") }
    var tempSelectedIds by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(showConfigDialog) {
        if (showConfigDialog) {
            if (configType == "REAL") {
                tempPeriod = selectedPeriodReal
                tempSelectedIds = selectedAccountIdsReal
            } else {
                tempPeriod = selectedPeriodDemo
                tempSelectedIds = selectedAccountIdsDemo
            }
        }
    }

    val realAccounts = remember(accounts) {
        accounts.filter { "REAL".equals(it.type, ignoreCase = true) || it.type == null }
    }
    val demoAccounts = remember(accounts) {
        accounts.filter { "DEMO".equals(it.type, ignoreCase = true) }
    }

    // Default to all monitored accounts when loaded if not configured
    LaunchedEffect(realAccounts) {
        if (!sharedPrefs.getBoolean("summary_configured_real", false) && realAccounts.isNotEmpty()) {
            selectedAccountIdsReal = realAccounts.map { it.accountId.toString() }.toSet()
        }
    }

    LaunchedEffect(demoAccounts) {
        if (!sharedPrefs.getBoolean("summary_configured_demo", false) && demoAccounts.isNotEmpty()) {
            selectedAccountIdsDemo = demoAccounts.map { it.accountId.toString() }.toSet()
        }
    }

    
    val fetchAccounts = {
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            try {
                val api = ApiClient.getService(context)
                accounts = api.getAccounts().filter { it.monitored && !"CSV".equals(it.type, ignoreCase = true) }
                try {
                    val status = api.getSystemStatus()
                    isAdmin = status.isAdmin
                } catch (e: Exception) {
                    isAdmin = false
                }
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
                accounts = api.getAccounts().filter { it.monitored && !"CSV".equals(it.type, ignoreCase = true) }
                try {
                    val status = api.getSystemStatus()
                    isAdmin = status.isAdmin
                } catch (e: Exception) {
                    // Ignore silent errors
                }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TradeMonitor", fontWeight = FontWeight.Bold) },
                actions = {
                    var showMenu by remember { mutableStateOf(false) }

                    IconButton(onClick = { fetchAccounts() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Menü öffnen")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Drawdown-Statistiken") },
                                onClick = {
                                    showMenu = false
                                    onViewDrawdowns()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                            if (isAdmin) {
                                DropdownMenuItem(
                                    text = { Text("Server Health") },
                                    onClick = {
                                        showMenu = false
                                        onViewHealth()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Favorite,
                                            contentDescription = null,
                                            tint = NeonGreen
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Security Audit") },
                                    onClick = {
                                        showMenu = false
                                        onViewSecurityAudit()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = NeonRed
                                        )
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Abmelden") },
                                onClick = {
                                    showMenu = false
                                    performLogout()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ExitToApp,
                                        contentDescription = null,
                                        tint = NeonRed
                                    )
                                }
                            )
                        }
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
            val pagerState = rememberPagerState(pageCount = { 2 })

            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text("Real", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text("Demo", fontWeight = FontWeight.Bold) }
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = NeonRed,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().weight(1f)
                ) { page ->
                    val isRealPage = (page == 0)
                    val displayAccounts = if (isRealPage) realAccounts else demoAccounts
                    val selectedIds = if (isRealPage) selectedAccountIdsReal else selectedAccountIdsDemo
                    val selectedPeriod = if (isRealPage) selectedPeriodReal else selectedPeriodDemo

                    val filteredPageAccounts = remember(displayAccounts, selectedIds) {
                        displayAccounts.filter { it.accountId.toString() in selectedIds }
                    }

                    // Totals calculations
                    val totalBalance = filteredPageAccounts.sumOf { it.balance }
                    val totalEquity = filteredPageAccounts.sumOf { it.equity }
                    val totalOpenProfit = filteredPageAccounts.sumOf { it.profit }
                    val totalProfit = when (selectedPeriod) {
                        "daily" -> filteredPageAccounts.sumOf { it.dailyProfit }
                        "weekly" -> filteredPageAccounts.sumOf { it.weeklyProfit }
                        "monthly" -> filteredPageAccounts.sumOf { it.monthlyProfit }
                        else -> filteredPageAccounts.sumOf { it.profit } // "gesamt" is open profit
                    }
                    val activeTradesCount = filteredPageAccounts.sumOf { it.trades }
                    val profitLabel = when (selectedPeriod) {
                        "daily" -> "Tages-Profit"
                        "weekly" -> "Wochen-Profit"
                        "monthly" -> "Monats-Profit"
                        else -> "Offener Profit"
                    }
                    val currency = filteredPageAccounts.firstOrNull()?.currency ?: "EUR"

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Summary Header Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .combinedClickable(
                                    onDoubleClick = {
                                        configType = if (isRealPage) "REAL" else "DEMO"
                                        showConfigDialog = true
                                    },
                                    onClick = { /* No-op */ }
                                ),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = if (isRealPage) "GESAMTSTATISTIK REAL" else "GESAMTSTATISTIK DEMO",
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
                                        Text("Open Profit", fontSize = 13.sp, color = TextSecondary)
                                        Text(
                                            text = String.format(Locale.GERMANY, "%+.2f %s", totalOpenProfit, currency),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (totalOpenProfit >= 0) NeonGreen else NeonRed
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
                                        Text(profitLabel, fontSize = 13.sp, color = TextSecondary)
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

                        // Accounts List
                        if (filteredPageAccounts.isEmpty() && !isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Keine Konten ausgewählt oder gefunden", color = TextSecondary)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(filteredPageAccounts) { account ->
                                    AccountItem(account = account, onClick = { onAccountClick(account.accountId) })
                                }
                            }
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

            // Summary Configuration Dialog
            if (showConfigDialog) {
                val dialogAccounts = if (configType == "REAL") realAccounts else demoAccounts
                AlertDialog(
                    onDismissRequest = { showConfigDialog = false },
                    title = { Text("Gesamtstatistik (${if (configType == "REAL") "Real" else "Demo"}) konfigurieren", fontWeight = FontWeight.Bold) },
                    text = {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Text("Zeitraum auswählen", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Column {
                                    listOf(
                                        "gesamt" to "Gesamt (Offener Profit)",
                                        "daily" to "Tagesstatistik",
                                        "weekly" to "Wochenstatistik",
                                        "monthly" to "Ganzer Monat"
                                    ).forEach { (value, label) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { tempPeriod = value }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = (tempPeriod == value),
                                                onClick = { tempPeriod = value }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(label)
                                        }
                                    }
                                }
                            }
                            
                            item {
                                Spacer(modifier = Modifier.height(6.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Konten auswählen", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        text = if (tempSelectedIds.size == dialogAccounts.size) "Keine" else "Alle",
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clickable {
                                                if (tempSelectedIds.size == dialogAccounts.size) {
                                                    tempSelectedIds = emptySet()
                                                } else {
                                                    tempSelectedIds = dialogAccounts.map { it.accountId.toString() }.toSet()
                                                }
                                            }
                                            .padding(4.dp),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            items(dialogAccounts) { account ->
                                val accIdStr = account.accountId.toString()
                                val isSelected = accIdStr in tempSelectedIds
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            tempSelectedIds = if (isSelected) {
                                                tempSelectedIds - accIdStr
                                            } else {
                                                tempSelectedIds + accIdStr
                                            }
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            tempSelectedIds = if (checked) {
                                                tempSelectedIds + accIdStr
                                            } else {
                                                tempSelectedIds - accIdStr
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(account.name ?: "Konto ${account.accountId}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Text(text = "${account.broker ?: "Unknown"} • ${account.accountId}", fontSize = 11.sp, color = TextSecondary)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (configType == "REAL") {
                                    selectedPeriodReal = tempPeriod
                                    selectedAccountIdsReal = tempSelectedIds
                                    sharedPrefs.edit()
                                        .putString("summary_period_real", tempPeriod)
                                        .putStringSet("summary_accounts_real", tempSelectedIds)
                                        .putBoolean("summary_configured_real", true)
                                        .apply()
                                } else {
                                    selectedPeriodDemo = tempPeriod
                                    selectedAccountIdsDemo = tempSelectedIds
                                    sharedPrefs.edit()
                                        .putString("summary_period_demo", tempPeriod)
                                        .putStringSet("summary_accounts_demo", tempSelectedIds)
                                        .putBoolean("summary_configured_demo", true)
                                        .apply()
                                }
                                showConfigDialog = false
                            }
                        ) {
                            Text("Speichern")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfigDialog = false }) {
                            Text("Abbrechen")
                        }
                    }
                )
            }

        }
    }
}

@Composable
fun AccountItem(account: Account, onClick: () -> Unit) {
    val cardBorder = when {
        account.openProfitAlarmTriggered || account.copierError -> BorderStroke(1.5.dp, NeonRed)
        account.syncWarning -> BorderStroke(1.5.dp, NeonOrange)
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = cardBorder
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .alpha(if (account.online) 1.0f else 0.7f)
        ) {
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
