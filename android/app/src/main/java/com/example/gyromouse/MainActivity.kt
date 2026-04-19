package com.example.gyromouse

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("GyroPrefs", Context.MODE_PRIVATE)

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {

                    // App State loaded from saved preferences
                    var ipAddress by remember { mutableStateOf(prefs.getString("ip", "192.168.1.182") ?: "192.168.1.182") }
                    var rot90 by remember { mutableStateOf(prefs.getBoolean("rot90", false)) }
                    var revPitch by remember { mutableStateOf(prefs.getBoolean("revPitch", false)) }
                    var isServiceRunning by remember { mutableStateOf(GyroService.isRunning) }

                    // Helper to save settings and notify the service to reload them
                    fun saveSettings() {
                        prefs.edit()
                            .putString("ip", ipAddress)
                            .putBoolean("rot90", rot90)
                            .putBoolean("revPitch", revPitch)
                            .apply()

                        if (isServiceRunning) {
                            startService(Intent(this, GyroService::class.java).apply {
                                action = GyroService.ACTION_UPDATE_SETTINGS
                            })
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()) // Allows scrolling when rotated sideways
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("GyroMouse", style = MaterialTheme.typography.headlineLarge)
                        Spacer(modifier = Modifier.height(32.dp))

                        // Dynamic Start/Stop Button
                        Button(
                            onClick = {
                                if (isServiceRunning) {
                                    toggleService(false)
                                    isServiceRunning = false
                                } else {
                                    saveSettings() // Save before starting
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

                        Spacer(modifier = Modifier.height(32.dp))

                        // IP Address Input
                        OutlinedTextField(
                            value = ipAddress,
                            onValueChange = { ipAddress = it; saveSettings() },
                            label = { Text("ROG Ally IP Address") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(24.dp))

                        // Toggles
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Rotate 90° (Sideways Phone)", modifier = Modifier.weight(1f))
                            Switch(checked = rot90, onCheckedChange = { rot90 = it; saveSettings() })
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Reverse Pitch (Up/Down)", modifier = Modifier.weight(1f))
                            Switch(checked = revPitch, onCheckedChange = { revPitch = it; saveSettings() })
                        }

                        Spacer(modifier = Modifier.height(48.dp))

                        // Reset Button
                        OutlinedButton(onClick = {
                            ipAddress = "192.168.1.182"
                            rot90 = false
                            revPitch = false
                            saveSettings()
                        }) {
                            Text("Reset Default Settings")
                        }
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
            startService(intent) // Sending stop action to running service
        }
    }
}