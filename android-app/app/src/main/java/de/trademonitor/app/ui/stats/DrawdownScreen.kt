package de.trademonitor.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import de.trademonitor.app.model.MagicDrawdownItem
import de.trademonitor.app.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawdownScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var drawdowns by remember { mutableStateOf<List<MagicDrawdownItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val fetchDrawdowns = {
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            try {
                val api = ApiClient.getService(context)
                drawdowns = api.getMagicDrawdowns()
            } catch (e: Exception) {
                errorMessage = "Fehler beim Laden: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }
    
    LaunchedEffect(Unit) {
        fetchDrawdowns()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Magic Drawdowns") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { fetchDrawdowns() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
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
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = NeonRed,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (drawdowns.isEmpty() && !isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Aktuell keine aktiven Drawdowns", color = TextSecondary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(drawdowns) { item ->
                            DrawdownCard(item = item)
                        }
                    }
                }
            }

            if (isLoading && drawdowns.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun DrawdownCard(item: MagicDrawdownItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Strategy name and Account name
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = item.magicName ?: "Magic ${item.magicNumber}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Konto: ${item.accountName} (${item.accountId})",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                
                if (item.isReal) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFF9100).copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("REAL", color = Color(0xFFFF9100), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            // Drawdown details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Drawdown (EUR)", fontSize = 11.sp, color = TextSecondary)
                    Text(
                        text = String.format(Locale.GERMANY, "%,.2f EUR", item.currentDrawdownEur),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.currentDrawdownEur > 0) NeonOrange else TextPrimary
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text("Drawdown (%)", fontSize = 11.sp, color = TextSecondary)
                    Text(
                        text = String.format(Locale.GERMANY, "%.2f%%", item.currentDrawdownPercent),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.currentDrawdownPercent > 5.0) NeonRed else NeonOrange
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Ref-Balance: ${String.format(Locale.GERMANY, "%,.2f", item.balanceHigh)}",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                if (item.lastSeenString != null) {
                    Text(
                        text = "Aktiv: ${item.lastSeenString}",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
