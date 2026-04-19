package com.example.gyromouse

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.gyromouse.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) toggleService(true)
    }

    private fun parseIps(str: String): List<Pair<String, String>> {
        return str.split(";").filter { it.isNotEmpty() }.mapNotNull {
            val parts = it.split(",")
            if (parts.size >= 2) parts[0] to parts[1] else null
        }
    }

    private fun serializeIps(list: List<Pair<String, String>>): String {
        return list.joinToString(";") { "${it.first},${it.second}" }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("GyroPrefs", Context.MODE_PRIVATE)

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {

                    // App States
                    var ipAddress by remember { mutableStateOf(prefs.getString("ip", "192.168.1.182") ?: "192.168.1.182") }
                    var ipList by remember { mutableStateOf(parseIps(prefs.getString("savedIps", "Default,192.168.1.182")!!)) }

                    var sensitivity by remember { mutableFloatStateOf(prefs.getFloat("sens", 500f)) }
                    var toggleMode by remember { mutableStateOf(prefs.getBoolean("toggleMode", false)) }
                    var rot90 by remember { mutableStateOf(prefs.getBoolean("rot90", false)) }
                    var revPitch by remember { mutableStateOf(prefs.getBoolean("revPitch", false)) }

                    // Toggle visibility state
                    var showSettings by remember { mutableStateOf(prefs.getBoolean("showSettings", false)) }

                    var isServiceRunning by remember { mutableStateOf(GyroService.isRunning) }
                    var expandedDropdown by remember { mutableStateOf(false) }
                    var showSaveDialog by remember { mutableStateOf(false) }

                    fun saveSettings() {
                        prefs.edit()
                            .putString("ip", ipAddress)
                            .putFloat("sens", sensitivity)
                            .putBoolean("toggleMode", toggleMode)
                            .putBoolean("rot90", rot90)
                            .putBoolean("revPitch", revPitch)
                            .putBoolean("showSettings", showSettings)
                            .apply()

                        if (isServiceRunning) {
                            startService(Intent(this, GyroService::class.java).apply {
                                action = GyroService.ACTION_UPDATE_SETTINGS
                            })
                        }
                    }

                    if (showSaveDialog) {
                        var ipName by remember { mutableStateOf("") }
                        AlertDialog(
                            onDismissRequest = { showSaveDialog = false },
                            title = { Text("Save this IP Address") },
                            text = {
                                OutlinedTextField(value = ipName, onValueChange = { ipName = it }, label = { Text("Friendly Name") })
                            },
                            confirmButton = {
                                Button(onClick = {
                                    val newList = ipList + Pair(ipName.ifEmpty { "Saved IP" }, ipAddress)
                                    ipList = newList
                                    prefs.edit().putString("savedIps", serializeIps(newList)).apply()
                                    showSaveDialog = false
                                }) { Text("Save") }
                            },
                            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") } }
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 1. Added more top padding
                        Spacer(modifier = Modifier.height(64.dp))

                        Text("GyroMouse", style = MaterialTheme.typography.headlineLarge)
                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                            onClick = {
                                if (isServiceRunning) {
                                    toggleService(false)
                                    isServiceRunning = false
                                } else {
                                    saveSettings()
                                    checkPermissionsAndStart()
                                    isServiceRunning = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isServiceRunning) Color(0xFFD32F2F) else Color(0xFF388E3C)
                            ),
                            modifier = Modifier.fillMaxWidth().height(60.dp)
                        ) {
                            Text(if (isServiceRunning) "STOP TRACKING" else "START TRACKING", style = MaterialTheme.typography.titleMedium)
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = ipAddress,
                                onValueChange = { ipAddress = it; saveSettings() },
                                label = { Text("PC IP Address") },
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    TextButton(onClick = { expandedDropdown = true }) { Text("▼") }
                                }
                            )
                            DropdownMenu(
                                expanded = expandedDropdown,
                                onDismissRequest = { expandedDropdown = false },
                                modifier = Modifier.fillMaxWidth(0.8f)
                            ) {
                                ipList.forEach { savedIp ->
                                    DropdownMenuItem(
                                        text = { Text("${savedIp.first} (${savedIp.second})") },
                                        onClick = {
                                            ipAddress = savedIp.second
                                            expandedDropdown = false
                                            saveSettings()
                                        }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Save current IP...") },
                                    onClick = {
                                        expandedDropdown = false
                                        showSaveDialog = true
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        // 2. Settings Header with Toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showSettings = !showSettings
                                    saveSettings()
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                "Settings",
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                if (showSettings) "Hide ▲" else "Show ▼",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // 3. Conditional Visibility
                        if (showSettings) {
                            Spacer(modifier = Modifier.height(16.dp))

                            Text("Sensitivity: ${sensitivity.toInt()}", modifier = Modifier.align(Alignment.Start))
                            Slider(
                                value = sensitivity,
                                onValueChange = { sensitivity = it; saveSettings() },
                                valueRange = 100f..2000f,
                                steps = 18
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Activation Mode")
                                    Text(if (toggleMode) "Press to Toggle" else "Hold to Move", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(checked = toggleMode, onCheckedChange = { toggleMode = it; saveSettings() })
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text("Rotate 90° (Sideways Phone)", modifier = Modifier.weight(1f))
                                Switch(checked = rot90, onCheckedChange = { rot90 = it; saveSettings() })
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text("Reverse Pitch (Up/Down)", modifier = Modifier.weight(1f))
                                Switch(checked = revPitch, onCheckedChange = { revPitch = it; saveSettings() })
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            OutlinedButton(onClick = {
                                ipAddress = "192.168.1.182"
                                sensitivity = 500f
                                toggleMode = false
                                rot90 = false
                                revPitch = false
                                showSettings = false // Collapse on reset
                                ipList = listOf(Pair("Default", "192.168.1.182"))
                                prefs.edit().putString("savedIps", serializeIps(ipList)).apply()
                                saveSettings()
                            }) {
                                Text("Reset All Settings")
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    private fun checkPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            toggleService(true)
        }
    }

    private fun toggleService(start: Boolean) {
        val intent = Intent(this, GyroService::class.java).apply {
            action = if (start) GyroService.ACTION_START_SERVICE else GyroService.ACTION_STOP_SERVICE
        }
        if (start) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        } else {
            startService(intent)
        }
    }
}