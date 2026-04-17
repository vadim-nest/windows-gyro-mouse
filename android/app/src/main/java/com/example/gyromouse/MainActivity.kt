package com.example.gyromouse

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gyromouse.ui.theme.MyApplicationTheme // Assuming you have this

class MainActivity : ComponentActivity() {

    // Launcher for requesting notification permission on Android 13+
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) startGyroService()
        // If not granted, the service won't start as a foreground service,
        // which might lead to it being killed by the OS.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme { // Apply your app's theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("GyroMouse Controls", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(32.dp))

                        Button(onClick = { checkPermissionsAndStart() }) {
                            Text("Start Background Gyro")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            val intent = Intent(this@MainActivity, GyroService::class.java).apply {
                                action = GyroService.ACTION_STOP_SERVICE
                            }
                            startService(intent) // Send stop command to the service
                        }) {
                            Text("Stop Gyro")
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(32.dp))

                        Text("Tracking Settings", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(onClick = {
                            val intent = Intent(this@MainActivity, GyroService::class.java).apply {
                                action = GyroService.ACTION_TOGGLE_ROTATE_TRACKING
                            }
                            startService(intent) // Send command to existing service
                        }) {
                            Text("Toggle 90° Rotation (Sideways Phone)")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            val intent = Intent(this@MainActivity, GyroService::class.java).apply {
                                action = GyroService.ACTION_TOGGLE_REVERSE_PITCH
                            }
                            startService(intent) // Send command to existing service
                        }) {
                            Text("Toggle Reverse Pitch (Up/Down)")
                        }
                    }
                }
            }
        }
    }

    // Handles checking for POST_NOTIFICATIONS permission before starting service
    private fun checkPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startGyroService()
        }
    }

    // Starts the GyroService
    private fun startGyroService() {
        val intent = Intent(this, GyroService::class.java).apply {
            action = GyroService.ACTION_START_SERVICE // Custom action for initial start
        }
        // For Android 8.0 (O) and higher, must use startForegroundService()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    // You can remove onPause/onResume/onGenericMotionEvent/onKeyDown/onKeyUp from MainActivity
    // as the sensor logic is now entirely in GyroService, and controller input won't work here.
}