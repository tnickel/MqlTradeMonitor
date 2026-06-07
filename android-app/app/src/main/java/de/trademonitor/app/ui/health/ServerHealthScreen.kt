package de.trademonitor.app.ui.health

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import de.trademonitor.app.model.ServerHealth
import de.trademonitor.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerHealthScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var health by remember { mutableStateOf<ServerHealth?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val fetchHealth = {
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            try {
                val api = ApiClient.getService(context)
                health = api.getServerHealth()
            } catch (e: Exception) {
                errorMessage = "Fehler beim Laden: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchHealth()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server Health", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { fetchHealth() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Aktualisieren")
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

                if (health == null && !isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Keine Daten verfügbar", color = TextSecondary)
                    }
                } else if (health != null) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            // CPU & System Card
                            HealthCard(title = "System & CPU") {
                                HealthRow(label = "Betriebssystem", value = health?.osName ?: "N/A")
                                val cpuLoadStr = health?.cpuLoad ?: "N/A"
                                HealthRow(
                                    label = "CPU-Auslastung",
                                    value = cpuLoadStr,
                                    valueColor = getCpuColor(cpuLoadStr)
                                )
                                
                                // Progress bar for CPU Load
                                val parsedCpu = cpuLoadStr.replace("%", "").trim().toDoubleOrNull()
                                if (parsedCpu != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = (parsedCpu / 100f).toFloat(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = getCpuColor(cpuLoadStr),
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }
                        }

                        item {
                            // Arbeitsspeicher Card
                            HealthCard(title = "Arbeitsspeicher (RAM)") {
                                Text(
                                    text = "SYSTEM PHYSICAL MEMORY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                HealthRow(label = "Gesamt", value = health?.systemTotalMemory ?: "N/A")
                                HealthRow(label = "Frei", value = health?.systemFreeMemory ?: "N/A", valueColor = NeonGreen)
                                HealthRow(label = "Genutzt", value = health?.systemUsedMemory ?: "N/A")

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "JVM MEMORY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                HealthRow(label = "Gesamt", value = health?.totalMemory ?: "N/A")
                                HealthRow(label = "Frei", value = health?.freeMemory ?: "N/A", valueColor = NeonGreen)
                                HealthRow(label = "Genutzt", value = health?.usedMemory ?: "N/A")
                            }
                        }

                        item {
                            // Festplatte Card
                            HealthCard(title = "Festplatte (Disk)") {
                                HealthRow(label = "Gesamtgröße", value = health?.diskTotal ?: "N/A")
                                HealthRow(label = "Freier Speicher", value = health?.diskFree ?: "N/A", valueColor = NeonGreen)
                                HealthRow(label = "Genutzter Speicher", value = health?.diskUsed ?: "N/A")
                            }
                        }

                        item {
                            // Dateien & Deployments Card
                            HealthCard(title = "Dateien & Deployments") {
                                HealthRow(label = "H2 Datenbank", value = health?.dbFileSize ?: "N/A")
                                HealthRow(label = "Server Logfile", value = health?.logFileSize ?: "N/A")
                                HealthRow(label = "ROOT.war", value = health?.rootWarSize ?: "N/A")
                                HealthRow(label = "AI Task Manager", value = health?.aiTaskManagerWarSize ?: "N/A")
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun HealthCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun HealthRow(
    label: String,
    value: String,
    valueColor: Color = TextPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextSecondary, fontSize = 14.sp)
        Text(text = value, fontWeight = FontWeight.Bold, color = valueColor, fontSize = 14.sp)
    }
}

fun getCpuColor(cpuLoad: String): Color {
    val parsedCpu = cpuLoad.replace("%", "").trim().toDoubleOrNull() ?: return TextPrimary
    return when {
        parsedCpu < 50.0 -> NeonGreen
        parsedCpu < 80.0 -> NeonOrange
        else -> NeonRed
    }
}
