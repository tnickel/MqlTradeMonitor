package de.trademonitor.app.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.trademonitor.app.api.ApiClient
import de.trademonitor.app.model.*
import de.trademonitor.app.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    accountId: Long,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var account by remember { mutableStateOf<Account?>(null) }
    var openTrades by remember { mutableStateOf<List<Trade>>(emptyList()) }
    var closedTrades by remember { mutableStateOf<List<ClosedTrade>>(emptyList()) }
    var equityHistory by remember { mutableStateOf<List<EquitySnapshot>>(emptyList()) }
    
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Open Trades, 1: Closed Trades, 2: Info
    
    LaunchedEffect(accountId) {
        isLoading = true
        coroutineScope.launch {
            try {
                val api = ApiClient.getService(context)
                
                // Fetch account list first to find specific account details
                val accounts = api.getAccounts()
                account = accounts.find { it.accountId == accountId }
                
                // Fetch details in parallel
                openTrades = api.getOpenTrades().filter { it.accountId == accountId }
                closedTrades = api.getClosedTrades(accountId)
                equityHistory = api.getEquityHistory(accountId)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(account?.name ?: "Konto Details") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (account == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Konto nicht gefunden oder Zugriff verweigert.", color = TextSecondary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Header Details Row
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Balance", fontSize = 12.sp, color = TextSecondary)
                            Text(
                                text = String.format(Locale.GERMANY, "%,.2f %s", account!!.balance, account!!.currency ?: "EUR"),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Equity", fontSize = 12.sp, color = TextSecondary)
                            Text(
                                text = String.format(Locale.GERMANY, "%,.2f %s", account!!.equity, account!!.currency ?: "EUR"),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Equity Curve Chart
                if (equityHistory.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant)
                    ) {
                        EquityChart(history = equityHistory)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Keine Equity-Snapshot-Historie vorhanden", color = TextSecondary, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tabs Navigation
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Offen (${openTrades.size})") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Historie (${closedTrades.size})") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Info") }
                    )
                }

                // Tab Content
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (selectedTab) {
                        0 -> OpenTradesList(trades = openTrades, currency = account!!.currency ?: "EUR")
                        1 -> ClosedTradesList(trades = closedTrades, currency = account!!.currency ?: "EUR")
                        2 -> AccountInfoTab(account = account!!)
                    }
                }
            }
        }
    }
}

@Composable
fun EquityChart(history: List<EquitySnapshot>) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp, bottom = 8.dp, start = 8.dp, end = 8.dp)
    ) {
        val width = size.width
        val height = size.height

        val maxVal = history.maxOf { maxOf(it.equity, it.balance) }
        val minVal = history.minOf { minOf(it.equity, it.balance) }
        val valueRange = maxVal - minVal
        
        // Add 5% padding to top/bottom of chart
        val paddingFactor = if (valueRange > 0) valueRange * 0.05 else 100.0
        val chartMax = maxVal + paddingFactor
        val chartMin = minVal - paddingFactor
        val chartRange = chartMax - chartMin

        val pointsCount = history.size
        
        // Draw grid lines
        val gridLines = 4
        for (i in 0..gridLines) {
            val y = height - (i * (height / gridLines))
            drawLine(
                color = BorderColor.copy(alpha = 0.3f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
        }

        if (pointsCount < 2) return@Canvas

        val equityPath = Path()
        val fillPath = Path()
        val balancePath = Path()

        val stepX = width / (pointsCount - 1)

        history.forEachIndexed { index, snapshot ->
            val x = index * stepX
            val yEquity = height - (((snapshot.equity - chartMin) / chartRange) * height).toFloat()
            val yBalance = height - (((snapshot.balance - chartMin) / chartRange) * height).toFloat()

            if (index == 0) {
                equityPath.moveTo(x, yEquity)
                fillPath.moveTo(x, height)
                fillPath.lineTo(x, yEquity)
                balancePath.moveTo(x, yBalance)
            } else {
                equityPath.lineTo(x, yEquity)
                fillPath.lineTo(x, yEquity)
                balancePath.lineTo(x, yBalance)
            }

            if (index == pointsCount - 1) {
                fillPath.lineTo(x, height)
                fillPath.close()
            }
        }

        // Draw filled gradient under equity
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(NeonBlue.copy(alpha = 0.25f), Color.Transparent),
                startY = 0f,
                endY = height
            )
        )

        // Draw Balance line (gray dashed or plain)
        drawPath(
            path = balancePath,
            color = TextSecondary.copy(alpha = 0.7f),
            style = Stroke(width = 3f)
        )

        // Draw Equity line (solid blue)
        drawPath(
            path = equityPath,
            color = NeonBlue,
            style = Stroke(width = 4f)
        )
    }
}

@Composable
fun OpenTradesList(trades: List<Trade>, currency: String) {
    if (trades.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Keine offenen Positionen", color = TextSecondary)
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(trades) { trade ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (trade.type == "BUY") NeonGreen.copy(alpha = 0.15f) else NeonRed.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = trade.type ?: "BUY",
                                    color = if (trade.type == "BUY") NeonGreen else NeonRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${trade.volume} Lot ${trade.symbol ?: ""}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        
                        Text(
                            text = String.format(Locale.GERMANY, "%+.2f %s", trade.profit, currency),
                            fontWeight = FontWeight.Bold,
                            color = if (trade.profit >= 0) NeonGreen else NeonRed,
                            fontSize = 15.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Open Price: ${trade.openPrice} • SL: ${trade.stopLoss} • TP: ${trade.takeProfit}",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        if (trade.comment != null) {
                            Text(
                                text = "Comment: ${trade.comment}",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    if (trade.syncStatus != null && trade.syncStatus != "MATCHED") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Sync Status: ${trade.syncStatus}",
                            color = NeonOrange,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ClosedTradesList(trades: List<ClosedTrade>, currency: String) {
    if (trades.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Keine geschlossenen Trades geladen", color = TextSecondary)
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(trades) { trade ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = trade.type ?: "BUY",
                                color = if (trade.type == "BUY") NeonGreen else NeonRed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${trade.volume} Lot ${trade.symbol ?: ""}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            text = "Closed: ${trade.closeTime ?: ""}",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = String.format(Locale.GERMANY, "%+.2f %s", trade.profit, currency),
                            fontWeight = FontWeight.Bold,
                            color = if (trade.profit >= 0) NeonGreen else NeonRed,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Com: ${trade.commission} • Swap: ${trade.swap}",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AccountInfoTab(account: Account) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            InfoRow(label = "Konto ID", value = account.accountId.toString())
            InfoRow(label = "Broker", value = account.broker ?: "Unbekannt")
            InfoRow(label = "Kontotyp", value = account.type ?: "Unbekannt")
            InfoRow(label = "Währung", value = account.currency ?: "EUR")
            InfoRow(label = "Online Status", value = if (account.online) "ONLINE" else "OFFLINE", valueColor = if (account.online) NeonGreen else NeonRed)
            InfoRow(label = "Letzter Kontakt", value = account.lastSeen ?: "N/A")
        }
        
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text("ALARM CONFIG", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            InfoRow(label = "Alarme Aktiviert", value = if (account.openProfitAlarmEnabled) "Ja" else "Nein")
            InfoRow(label = "Max Drawdown Absolut", value = account.openProfitAlarmAbs?.let { String.format("%,.2f %s", it, account.currency ?: "EUR") } ?: "Deaktiviert")
            InfoRow(label = "Max Drawdown Prozentual", value = account.openProfitAlarmPct?.let { String.format("%.1f%%", it) } ?: "Deaktiviert")
            InfoRow(label = "Alarm Ausgelöst", value = if (account.openProfitAlarmTriggered) "JA ⚠️" else "Nein", valueColor = if (account.openProfitAlarmTriggered) NeonRed else TextPrimary)
        }
        
        if (account.copierError) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = NeonRed.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Copier Fehler", color = NeonRed, fontWeight = FontWeight.Bold)
                        Text(account.copierErrorMessage ?: "Ein Fehler ist aufgetreten", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color = TextPrimary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextSecondary, fontSize = 14.sp)
        Text(text = value, color = valueColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}
