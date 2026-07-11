package de.trademonitor.app.ui.security

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.trademonitor.app.api.ApiClient
import de.trademonitor.app.model.SecurityAudit
import de.trademonitor.app.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityAuditScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var audit by remember { mutableStateOf<SecurityAudit?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    val fetchAudit = {
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            try {
                val api = ApiClient.getService(context)
                audit = api.getSecurityAudit()
            } catch (e: Exception) {
                errorMessage = "Fehler beim Laden: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    val triggerAudit = {
        isLoading = true
        errorMessage = null
        actionMessage = null
        coroutineScope.launch {
            try {
                val api = ApiClient.getService(context)
                audit = api.runSecurityAudit()
                actionMessage = "Security Audit erfolgreich durchgeführt!"
            } catch (e: Exception) {
                errorMessage = "Fehler beim Ausführen des Audits: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchAudit()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security Audit", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = { triggerAudit() }, enabled = !isLoading) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Jetzt prüfen")
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
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NeonRed.copy(alpha = 0.1f))
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = NeonRed,
                            modifier = Modifier.padding(16.dp),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (actionMessage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NeonGreen.copy(alpha = 0.1f))
                    ) {
                        Text(
                            text = actionMessage!!,
                            color = NeonGreen,
                            modifier = Modifier.padding(16.dp),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    LaunchedEffect(actionMessage) {
                        kotlinx.coroutines.delay(4000)
                        actionMessage = null
                    }
                }

                if (audit == null && !isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Keine Audit-Daten verfügbar", color = TextSecondary)
                    }
                } else if (audit != null) {
                    val currentAudit = audit!!
                    
                    val statusColor = when (currentAudit.overallStatus) {
                        "GREEN" -> NeonGreen
                        "YELLOW" -> NeonOrange
                        else -> NeonRed
                    }
                    val statusBg = statusColor.copy(alpha = 0.08f)
                    val statusDot = when (currentAudit.overallStatus) {
                        "GREEN" -> "🟢"
                        "YELLOW" -> "🟡"
                        else -> "🔴"
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Summary status card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = borderStroke(statusColor, 1.5.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(statusBg)
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(statusDot, fontSize = 28.sp)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = when (currentAudit.overallStatus) {
                                                "GREEN" -> "Server Sicher"
                                                "YELLOW" -> "Auffälligkeiten erkannt"
                                                else -> "KRITISCHE WARNUNG"
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = statusColor
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = currentAudit.statusMessage ?: "",
                                            fontSize = 13.sp,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Letzter Check: ${formatDateTime(currentAudit.checkTime)}",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        // Firewall & Ports Card
                        item {
                            SecurityCard(title = "🛡️ Firewall & Ports") {
                                SecurityRow(
                                    label = "UFW Firewall",
                                    value = if (currentAudit.ufwActive) "AKTIV" else "INAKTIV",
                                    valueColor = if (currentAudit.ufwActive) NeonGreen else NeonRed
                                )
                                
                                val unexpected = currentAudit.unexpectedPorts ?: emptyList()
                                SecurityRow(
                                    label = "Unerwartete offene Ports",
                                    value = "${unexpected.size}",
                                    valueColor = if (unexpected.isNotEmpty()) NeonRed else NeonGreen
                                )
                                
                                if (unexpected.isNotEmpty()) {
                                    unexpected.forEach { port ->
                                        Text(
                                            text = "🚨 $port",
                                            color = NeonRed,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                                        )
                                    }
                                }

                                var showRules by remember { mutableStateOf(false) }
                                Spacer(modifier = Modifier.height(8.dp))
                                DropdownHeader("UFW Rules & Status", showRules) { showRules = !showRules }
                                AnimatedVisibility(visible = showRules) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = currentAudit.ufwRules ?: "Keine Regeln geladen",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = TextPrimary
                                        )
                                    }
                                }

                                var showOpenPorts by remember { mutableStateOf(false) }
                                Spacer(modifier = Modifier.height(8.dp))
                                DropdownHeader("Alle offenen Ports (${currentAudit.openPorts?.size ?: 0})", showOpenPorts) { showOpenPorts = !showOpenPorts }
                                AnimatedVisibility(visible = showOpenPorts) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        currentAudit.openPorts?.forEach { portLine ->
                                            Text(
                                                text = portLine,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Brute Force & Intrusion Prevention Card
                        item {
                            SecurityCard(title = "🧱 Intrusion Prevention (Fail2ban)") {
                                SecurityRow(
                                    label = "Fehlgeschlagene SSH-Logins",
                                    value = "${currentAudit.failedSshCount}",
                                    valueColor = if (currentAudit.failedSshCount > 100) NeonRed else TextPrimary
                                )
                                SecurityRow(
                                    label = "Fail2Ban aktuell gesperrt",
                                    value = "${currentAudit.fail2banBannedCount}",
                                    valueColor = if (currentAudit.fail2banBannedCount > 0) NeonOrange else NeonGreen
                                )
                                SecurityRow(
                                    label = "Fail2Ban Sperren gesamt",
                                    value = "${currentAudit.fail2banTotalBans}"
                                )

                                val bannedIps = currentAudit.fail2banBannedIps ?: emptyList()
                                if (bannedIps.isNotEmpty()) {
                                    var showBannedIps by remember { mutableStateOf(false) }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    DropdownHeader("Banned IP Liste (${bannedIps.size})", showBannedIps) { showBannedIps = !showBannedIps }
                                    AnimatedVisibility(visible = showBannedIps) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            bannedIps.forEach { ip ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "🚫 $ip",
                                                        color = NeonRed,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 12.sp,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    IconButton(
                                                        onClick = {
                                                            isLoading = true
                                                            coroutineScope.launch {
                                                                try {
                                                                    val api = ApiClient.getService(context)
                                                                    val res = api.unbanIp(ip)
                                                                    if (res.isSuccessful) {
                                                                        actionMessage = "IP $ip entsperrt!"
                                                                        audit = api.getSecurityAudit()
                                                                    } else {
                                                                        errorMessage = "Fehler beim Entsperren"
                                                                    }
                                                                } catch (e: Exception) {
                                                                    errorMessage = e.localizedMessage
                                                                } finally {
                                                                    isLoading = false
                                                                }
                                                            }
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "IP entsperren",
                                                            tint = NeonRed,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Manual IP unban input field
                                var manualIpToUnban by remember { mutableStateOf("") }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = manualIpToUnban,
                                        onValueChange = { manualIpToUnban = it },
                                        label = { Text("IP manuell entsperren", fontSize = 11.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f).height(50.dp),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                        )
                                    )
                                    Button(
                                        onClick = {
                                            if (manualIpToUnban.isNotBlank()) {
                                                val ip = manualIpToUnban.trim()
                                                isLoading = true
                                                coroutineScope.launch {
                                                    try {
                                                        val api = ApiClient.getService(context)
                                                        val res = api.unbanIp(ip)
                                                        if (res.isSuccessful) {
                                                            actionMessage = "IP $ip entsperrt!"
                                                            manualIpToUnban = ""
                                                            audit = api.getSecurityAudit()
                                                        } else {
                                                            errorMessage = "Fehler beim Entsperren"
                                                        }
                                                    } catch (e: Exception) {
                                                        errorMessage = e.localizedMessage
                                                    } finally {
                                                        isLoading = false
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.height(50.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text("Go", fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        // Web Traffic Card
                        item {
                            SecurityCard(title = "🌐 Web Traffic & DDoS") {
                                SecurityRow(
                                    label = "Verdächtige Web-Requests",
                                    value = "${currentAudit.suspiciousRequestCount}",
                                    valueColor = if (currentAudit.suspiciousRequestCount > 50) NeonRed else NeonGreen
                                )

                                var showTopAttackerIps by remember { mutableStateOf(false) }
                                val topAttackers = currentAudit.topAttackerIps ?: emptyList()
                                DropdownHeader("Top Angreifer-IPs (SSH)", showTopAttackerIps) { showTopAttackerIps = !showTopAttackerIps }
                                AnimatedVisibility(visible = showTopAttackerIps) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(8.dp)
                                    ) {
                                        if (topAttackers.isEmpty()) {
                                            Text("Keine Angreifer gelistet", fontSize = 12.sp, color = TextSecondary)
                                        } else {
                                            topAttackers.forEach { item ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(text = item.ip ?: "Unknown", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                                    Text(text = "${item.count} Loginversuche", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonRed)
                                                }
                                            }
                                        }
                                    }
                                }

                                var showSuspiciousRequests by remember { mutableStateOf(false) }
                                val suspiciousLogs = currentAudit.suspiciousRequests ?: emptyList()
                                Spacer(modifier = Modifier.height(8.dp))
                                DropdownHeader("Verdächtige Request Logs (${suspiciousLogs.size})", showSuspiciousRequests) { showSuspiciousRequests = !showSuspiciousRequests }
                                AnimatedVisibility(visible = showSuspiciousRequests) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (suspiciousLogs.isEmpty()) {
                                            Text("Keine verdächtigen Requests gefunden", fontSize = 12.sp, color = TextSecondary)
                                        } else {
                                            suspiciousLogs.forEach { log ->
                                                Text(
                                                    text = log,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    color = NeonOrange
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Recent logins card
                        item {
                            SecurityCard(title = "🔑 Letzte Logins (SSH & Web)") {
                                val logins = currentAudit.recentLogins ?: emptyList()
                                if (logins.isEmpty()) {
                                    Text("Keine Logins protokolliert", fontSize = 12.sp, color = TextSecondary)
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        logins.forEach { login ->
                                            val isWeb = login.startsWith("WEB-ADMIN")
                                            val badgeColor = if (isWeb) MaterialTheme.colorScheme.primary else NeonOrange
                                            
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(badgeColor.copy(alpha = 0.15f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = if (isWeb) "WEB" else "SSH",
                                                        color = badgeColor,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = login.substring(if (isWeb) 9 else 4).trim(),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    color = TextPrimary
                                                )
                                            }
                                        }
                                    }
                                }
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
fun SecurityCard(
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
                text = title,
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
fun SecurityRow(
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

@Composable
fun DropdownHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun borderStroke(color: Color, width: androidx.compose.ui.unit.Dp): androidx.compose.foundation.BorderStroke {
    return androidx.compose.foundation.BorderStroke(width, color)
}

fun formatDateTime(dateTimeStr: String?): String {
    if (dateTimeStr == null) return "N/A"
    return try {
        // Handle ISO datetime strings
        val cleanedStr = dateTimeStr.split(".")[0] // ignore nano seconds
        val ldt = LocalDateTime.parse(cleanedStr)
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
        ldt.format(formatter)
    } catch (e: Exception) {
        dateTimeStr
    }
}
