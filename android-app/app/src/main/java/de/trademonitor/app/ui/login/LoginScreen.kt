package de.trademonitor.app.ui.login

import android.content.Context
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
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val sharedPrefs = remember { context.getSharedPreferences("TradeMonitorPrefs", Context.MODE_PRIVATE) }
    val encryptedPrefs = remember {
        val masterKeyAlias = androidx.security.crypto.MasterKeys.getOrCreate(androidx.security.crypto.MasterKeys.AES256_GCM_SPEC)
        val securePrefs = androidx.security.crypto.EncryptedSharedPreferences.create(
            "TradeMonitorPrefsSecure",
            masterKeyAlias,
            context,
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        
        // Migrate old plaintext credentials if they exist
        if (sharedPrefs.contains("username") || sharedPrefs.contains("password")) {
            val oldUser = sharedPrefs.getString("username", "") ?: ""
            val oldPass = sharedPrefs.getString("password", "") ?: ""
            
            securePrefs.edit()
                .putString("username", oldUser)
                .putString("password", oldPass)
                .apply()
                
            sharedPrefs.edit()
                .remove("username")
                .remove("password")
                .apply()
        }
        
        securePrefs
    }
    
    var serverUrl by remember {
        val saved = sharedPrefs.getString("server_url", "") ?: ""
        mutableStateOf(if (saved.isEmpty()) "https://monitor.tnickel-ki.de" else saved)
    }
    var username by remember { mutableStateOf(encryptedPrefs.getString("username", "") ?: "") }
    var password by remember { mutableStateOf(encryptedPrefs.getString("password", "") ?: "") }
    
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Auto-login trigger
    LaunchedEffect(Unit) {
        if (serverUrl.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty()) {
            isLoading = true
            coroutineScope.launch {
                try {
                    ApiClient.saveServerUrl(context, serverUrl)
                    val api = ApiClient.getService(context)
                    val response = api.login(LoginRequest(username, password))
                    if (response.isSuccessful) {
                        onLoginSuccess()
                    } else {
                        errorMessage = "Auto-Login fehlgeschlagen. Bitte Daten prüfen."
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
                        // Save credentials securely for auto-login
                        encryptedPrefs.edit()
                            .putString("username", username)
                            .putString("password", password)
                            .apply()
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
            label = { Text("Server URL (z.B. http://192.168.0.50:8080)") },
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
        
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
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
