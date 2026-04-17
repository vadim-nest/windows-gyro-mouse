package com.example.gyromouse

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

class GyroService : Service(), SensorEventListener {

    // Define actions for intents sent to the service
    companion object {
        const val ACTION_START_SERVICE = "com.example.gyromouse.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.example.gyromouse.STOP_SERVICE"
        const val ACTION_TOGGLE_ROTATE_TRACKING = "com.example.gyromouse.TOGGLE_ROTATE_TRACKING"
        const val ACTION_TOGGLE_REVERSE_PITCH = "com.example.gyromouse.TOGGLE_REVERSE_PITCH"
    }

    private val PC_IP = "192.168.1.182" // <<< Make sure this is still correct!
    private val PC_PORT = 26760
    private var udpSocket: DatagramSocket? = null
    private lateinit var sensorManager: SensorManager
    private var gyroSensor: Sensor? = null
    private val udpExecutor = Executors.newSingleThreadExecutor() // Use a single thread for UDP sending to prevent race conditions

    // Configuration states (can be saved to SharedPreferences if you want them to persist)
    private var is90DegreeRotation = false
    private var isPitchReversed = false

    override fun onCreate() {
        super.onCreate()

        // --- Setup Notification (Mandatory for Foreground Services) ---
        val channelId = "gyro_service_channel"
        val channel = NotificationChannel(
            channelId, "GyroMouse Background Service", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("GyroMouse Active")
            .setContentText("Streaming gyro data...")
            .setSmallIcon(android.R.drawable.ic_menu_compass) // You can change this to your app icon
            .setOngoing(true) // Makes the notification persistent
            .build()

        startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        // --- Sensor Setup ---
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        gyroSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            updateServiceNotification("Gyro sensor registered. Streaming data.")
        } ?: run {
            updateServiceNotification("ERROR: Gyro sensor not found!")
            stopSelf() // Stop the service if sensor is not available
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_ROTATE_TRACKING -> {
                is90DegreeRotation = !is90DegreeRotation
                updateServiceNotification("Tracking rotation: ${if (is90DegreeRotation) "90° (sideways)" else "0° (upright)"}")
            }
            ACTION_TOGGLE_REVERSE_PITCH -> {
                isPitchReversed = !isPitchReversed
                updateServiceNotification("Pitch (up/down) reversed: ${if (isPitchReversed) "Yes" else "No"}")
            }
            // Add other actions if needed
            ACTION_STOP_SERVICE -> {
                stopSelf() // Stop the service explicitly
                return START_NOT_STICKY // Don't try to restart after explicit stop
            }
        }
        // START_STICKY means the service will be restarted by the system if it gets killed,
        // handy for long-running background operations.
        return START_STICKY
    }

    // Helper to update the ongoing notification with current status
    private fun updateServiceNotification(content: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, "gyro_service_channel")
            .setContentTitle("GyroMouse Active")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
        notificationManager.notify(1, notification)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GYROSCOPE) return

        val dt = 0.02f // Approximate delta time for sensor updates
        var currentPitch = event.values[0] * dt // X-axis: controls pitch (up/down)
        var currentYaw   = -event.values[1] * dt // Y-axis: controls yaw (left/right). Negative to correct common inversion.

        // 1. Apply 90-degree rotation (for phone held sideways)
        if (is90DegreeRotation) {
            val temp = currentPitch
            currentPitch = currentYaw // New pitch is old yaw
            currentYaw = -temp        // New yaw is inverted old pitch (adjust signs based on testing if needed)
        }

        // 2. Apply pitch reversal (for inverted up/down)
        if (isPitchReversed) {
            currentPitch = -currentPitch
        }

        sendMotion(currentYaw, currentPitch)
    }

    private fun sendMotion(dyaw: Float, dpitch: Float) {
        // Execute UDP send on the dedicated single-thread executor
        udpExecutor.execute {
            try {
                if (udpSocket == null) udpSocket = DatagramSocket()
                val buf = ByteBuffer.allocate(8) // 4 bytes for yaw, 4 for pitch
                buf.order(ByteOrder.LITTLE_ENDIAN) // Match Windows BitConverter.ToSingle
                buf.putFloat(dyaw)
                buf.putFloat(dpitch)
                val data = buf.array()
                val packet = DatagramPacket(
                    data, data.size, InetAddress.getByName(PC_IP), PC_PORT
                )
                udpSocket!!.send(packet)
            } catch (e: Exception) {
                // Log and handle specific exceptions if critical for debugging,
                // but for background UDP errors, often best to fail silently or log to a file.
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used for Gyroscope usually
    }

    override fun onBind(intent: Intent?): IBinder? {
        // This service is not designed to be bound to, so return null
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this) // Unregister sensor listener
        udpSocket?.close() // Close the UDP socket
        udpExecutor.shutdown() // Shut down the executor thread
        updateServiceNotification("GyroMouse service stopped.") // Update notification one last time
    }
}