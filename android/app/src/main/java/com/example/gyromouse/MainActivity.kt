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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.gyromouse.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

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

                    var ipAddress by remember { mutableStateOf(prefs.getString("ip", "") ?: "") }
                    var ipList by remember { mutableStateOf(parseIps(prefs.getString("savedIps", "") ?: "")) }
                    var sensitivity by remember { mutableFloatStateOf(prefs.getFloat("sens", 500f)) }
                    var toggleMode by remember { mutableStateOf(prefs.getBoolean("toggleMode", false)) }
                    var rot90 by remember { mutableStateOf(prefs.getBoolean("rot90", false)) }
                    var revPitch by remember { mutableStateOf(prefs.getBoolean("revPitch", true)) }
                    var actBtn by remember { mutableIntStateOf(prefs.getInt("actBtn", 0)) }
                    var showSettings by remember { mutableStateOf(prefs.getBoolean("showSettings", false)) }
                    var isServiceRunning by remember { mutableStateOf(GyroService.isRunning) }
                    var expandedIpDropdown by remember { mutableStateOf(false) }
                    var expandedBtnDropdown by remember { mutableStateOf(false) }
                    var showSaveDialog by remember { mutableStateOf(false) }
                    var showResetAllConfirm by remember { mutableStateOf(false) }
                    var showResetIpsConfirm by remember { mutableStateOf(false) }
                    val snackbarHostState = remember { SnackbarHostState() }
                    val scope = rememberCoroutineScope()

                    val buttonOptions = listOf("L2 (Left Trigger)", "R2 (Right Trigger)", "L1 (Left Bumper)", "R1 (Right Bumper)")

                    fun saveSettings() {
                        prefs.edit()
                            .putString("ip", ipAddress)
                            .putFloat("sens", sensitivity)
                            .putBoolean("toggleMode", toggleMode)
                            .putBoolean("rot90", rot90)
                            .putBoolean("revPitch", revPitch)
                            .putBoolean("showSettings", showSettings)
                            .putInt("actBtn", actBtn)
                            .apply()

                        if (isServiceRunning) {
                            startService(Intent(this, GyroService::class.java).apply {
                                action = GyroService.ACTION_UPDATE_SETTINGS
                            })
                        }
                    }

                    // Save IP dialog
                    if (showSaveDialog) {
                        var ipName by remember { mutableStateOf("") }
                        AlertDialog(
                            onDismissRequest = { showSaveDialog = false },
                            title = { Text("Save this IP Address") },
                            text = {
                                OutlinedTextField(value = ipName, onValueChange = { ipName = it }, label = { Text("Friendly Name (e.g. Gaming PC)") })
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

                    // Reset All Settings confirmation
                    if (showResetAllConfirm) {
                        AlertDialog(
                            onDismissRequest = { showResetAllConfirm = false },
                            title = { Text("Reset All Settings?") },
                            text = { Text("This will restore sensitivity and toggles to defaults. Your saved IPs will stay.") },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        sensitivity = 500f
                                        toggleMode = false
                                        rot90 = false
                                        revPitch = true
                                        actBtn = 0
                                        saveSettings()
                                        showResetAllConfirm = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) { Text("Reset") }
                            },
                            dismissButton = { TextButton(onClick = { showResetAllConfirm = false }) { Text("Cancel") } }
                        )
                    }

                    // Clear IP List confirmation
                    if (showResetIpsConfirm) {
                        AlertDialog(
                            onDismissRequest = { showResetIpsConfirm = false },
                            title = { Text("Clear Saved IPs?") },
                            text = { Text("This will remove all saved IP addresses. This cannot be undone.") },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        ipAddress = ""
                                        ipList = emptyList()
                                        prefs.edit().putString("savedIps", "").apply()
                                        saveSettings()
                                        showResetIpsConfirm = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) { Text("Clear IPs") }
                            },
                            dismissButton = { TextButton(onClick = { showResetIpsConfirm = false }) { Text("Cancel") } }
                        )
                    }

                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val isTablet = maxWidth >= 600.dp
                        val horizontalPadding = if (isTablet) 64.dp else 24.dp
                        // Limit width of controls so they don't look stretched on tablets
                        val controlMaxWidth = 500.dp

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = horizontalPadding),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.height(if (isTablet) 64.dp else 48.dp))

                            Text("GyroMouse", style = MaterialTheme.typography.headlineLarge)
                            Spacer(modifier = Modifier.height(32.dp))

                            // Main Control Group (Button and IP Field)
                            Column(
                                modifier = Modifier.widthIn(max = controlMaxWidth),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Button(
                                    onClick = {
                                        if (isServiceRunning) {
                                            toggleService(false)
                                            isServiceRunning = false
                                        } else {
                                            // Check if IP is blank before starting
                                            if (ipAddress.isBlank()) {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        message = "Please enter the PC IP address first",
                                                        duration = SnackbarDuration.Short
                                                    )
                                                }
                                                return@Button
                                            }
                                            saveSettings()
                                            checkPermissionsAndStart()
                                            isServiceRunning = true
                                        }
                                    },
                                    // The button stays colored even if IP is blank, but we can dim it slightly if we want
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isServiceRunning) Color(0xFFD32F2F) else Color(0xFF388E3C)
                                    ),
                                    modifier = Modifier.fillMaxWidth().height(64.dp)
                                ) {
                                    Text(
                                        if (isServiceRunning) "STOP TRACKING" else "START TRACKING",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = ipAddress,
                                        onValueChange = { ipAddress = it; saveSettings() },
                                        label = { Text("PC IP Address") },
                                        placeholder = { Text("e.g. 192.168.1.50") },
                                        modifier = Modifier.fillMaxWidth(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        trailingIcon = {
                                            IconButton(onClick = { expandedIpDropdown = true }) {
                                                Text("▼", color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    )
                                    DropdownMenu(
                                        expanded = expandedIpDropdown,
                                        onDismissRequest = { expandedIpDropdown = false },
                                        modifier = Modifier.fillMaxWidth(0.7f)
                                    ) {
                                        if (ipList.isEmpty()) {
                                            DropdownMenuItem(text = { Text("No saved IPs", color = Color.Gray) }, onClick = {})
                                        }
                                        ipList.forEach { savedIp ->
                                            DropdownMenuItem(
                                                text = { Text("${savedIp.first} (${savedIp.second})") },
                                                onClick = {
                                                    ipAddress = savedIp.second
                                                    expandedIpDropdown = false
                                                    saveSettings()
                                                }
                                            )
                                        }
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text("Save current IP...") },
                                            onClick = { expandedIpDropdown = false; showSaveDialog = true }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Clear all saved IPs...", color = MaterialTheme.colorScheme.error) },
                                            onClick = { expandedIpDropdown = false; showResetIpsConfirm = true }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))
                            HorizontalDivider(modifier = Modifier.widthIn(max = controlMaxWidth))

                            // Settings Section
                            Column(modifier = Modifier.widthIn(max = controlMaxWidth)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showSettings = !showSettings; saveSettings() }
                                        .padding(vertical = 16.dp)
                                ) {
                                    Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                                    Text(if (showSettings) "Hide ▲" else "Show ▼", color = MaterialTheme.colorScheme.primary)
                                }

                                if (showSettings) {
                                    Text("Sensitivity: ${sensitivity.toInt()}")
                                    Slider(
                                        value = sensitivity,
                                        onValueChange = { sensitivity = it; saveSettings() },
                                        valueRange = 100f..2000f,
                                        steps = 18
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Button Selection
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Activation Button", modifier = Modifier.weight(1f))
                                        Box {
                                            OutlinedButton(onClick = { expandedBtnDropdown = true }) {
                                                Text("${buttonOptions[actBtn]} ▼")
                                            }
                                            DropdownMenu(expanded = expandedBtnDropdown, onDismissRequest = { expandedBtnDropdown = false }) {
                                                buttonOptions.forEachIndexed { index, name ->
                                                    DropdownMenuItem(text = { Text(name) }, onClick = {
                                                        actBtn = index
                                                        expandedBtnDropdown = false
                                                        saveSettings()
                                                    })
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Toggle Mode Switch
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Activation Mode")
                                            Text(if (toggleMode) "Press to Toggle" else "Hold to Move", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                                        }
                                        Switch(checked = toggleMode, onCheckedChange = { toggleMode = it; saveSettings() })
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Rotate 90° (Sideways Phone)", modifier = Modifier.weight(1f))
                                        Switch(checked = rot90, onCheckedChange = { rot90 = it; saveSettings() })
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Reverse Pitch (Up/Down)", modifier = Modifier.weight(1f))
                                        Switch(
                                            checked = !revPitch, // Show the opposite
                                            onCheckedChange = {
                                                revPitch = !it  // Flip it back before saving
                                                saveSettings()
                                            }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(32.dp))

                                    // Reset Footer
                                    OutlinedButton(
                                        onClick = { showResetAllConfirm = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("Reset All Settings to Default")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(64.dp))
                        }

                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                            SnackbarHost(
                                hostState = snackbarHostState,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
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
            stopService(intent)
        }
    }
}