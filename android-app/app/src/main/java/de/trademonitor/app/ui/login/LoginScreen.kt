package de.trademonitor.app.ui.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.trademonitor.app.api.ApiClient
import de.trademonitor.app.model.LoginRequest
import de.trademonitor.app.util.SecurePrefsManager
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val savedCredentials = remember { SecurePrefsManager.getCredentials(context) }
    var serverUrl by remember { mutableStateOf(SecurePrefsManager.getServerUrl(context)) }
    var username by remember { mutableStateOf(savedCredentials.first) }
    var password by remember { mutableStateOf(savedCredentials.second) }
    var rememberCredentials by remember { mutableStateOf(SecurePrefsManager.isRememberEnabled(context)) }
    
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Auto-login trigger
    LaunchedEffect(Unit) {
        if (rememberCredentials && serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
            isLoading = true
            coroutineScope.launch {
                try {
                    ApiClient.saveServerUrl(context, serverUrl)
                    val api = ApiClient.getService(context)
                    val response = api.login(LoginRequest(username, password))
                    if (response.isSuccessful) {
                        ApiClient.updateCsrfToken(response.body())
                        SecurePrefsManager.saveCredentials(context, username, password, rememberCredentials)
                        onLoginSuccess()
                    } else {
                        errorMessage = "Auto-Login fehlgeschlagen. Bitte Zugangsdaten prüfen."
                    }
                } catch (e: Exception) {
                    errorMessage = "Verbindungsfehler: ${e.localizedMessage}"
                } finally {
                    isLoading = false
                }
            }
        }
    }
    
    val performLogin = {
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
            errorMessage = "Bitte alle Felder ausfüllen"
        } else {
            isLoading = true
            errorMessage = null
            coroutineScope.launch {
                try {
                    ApiClient.saveServerUrl(context, serverUrl)
                    val api = ApiClient.getService(context)
                    val response = api.login(LoginRequest(username, password))
                    if (response.isSuccessful) {
                        ApiClient.updateCsrfToken(response.body())
                        SecurePrefsManager.saveServerUrl(context, serverUrl)
                        SecurePrefsManager.saveCredentials(context, username, password, rememberCredentials)
                        onLoginSuccess()
                    } else {
                        errorMessage = "Ungültige Anmeldedaten"
                    }
                } catch (e: Exception) {
                    errorMessage = "Verbindung fehlgeschlagen: ${e.localizedMessage}"
                } finally {
                    isLoading = false
                }
            }
        }
        Unit
    }
    
    val performDemoLogin = {
        if (serverUrl.isBlank()) {
            errorMessage = "Bitte Server URL eingeben"
        } else {
            isLoading = true
            errorMessage = null
            coroutineScope.launch {
                try {
                    ApiClient.saveServerUrl(context, serverUrl)
                    val api = ApiClient.getService(context)
                    val response = api.demoLogin()
                    if (response.isSuccessful) {
                        ApiClient.updateCsrfToken(response.body())
                        onLoginSuccess()
                    } else {
                        errorMessage = "Demo-Login fehlgeschlagen"
                    }
                } catch (e: Exception) {
                    errorMessage = "Verbindung fehlgeschlagen: ${e.localizedMessage}"
                } finally {
                    isLoading = false
                }
            }
        }
        Unit
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "MQL TradeMonitor",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("Server URL (z.B. https://monitor.tnickel-ki.de)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Benutzername") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Passwort") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { rememberCredentials = !rememberCredentials },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = rememberCredentials,
                onCheckedChange = { rememberCredentials = it }
            )
            Text(
                text = "Zugangsdaten merken & Auto-Login",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (isLoading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        } else {
            Button(
                onClick = performLogin,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Anmelden")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            TextButton(onClick = performDemoLogin) {
                Text("Demo-Zugang nutzen", color = MaterialTheme.colorScheme.secondary)
            }
        }

        val versionName = remember {
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                packageInfo.versionName ?: "1.1"
            } catch (e: Exception) {
                "1.1"
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "v$versionName",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
    }
}

