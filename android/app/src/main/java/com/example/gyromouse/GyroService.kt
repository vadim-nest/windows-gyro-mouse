package com.example.gyromouse

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
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

    companion object {
        const val ACTION_START_SERVICE = "START"
        const val ACTION_STOP_SERVICE = "STOP"
        const val ACTION_UPDATE_SETTINGS = "UPDATE"
        var isRunning = false // Tracks state for the UI
    }

    private var PC_IP = "192.168.1.182"
    private val PC_PORT = 26760
    private var udpSocket: DatagramSocket? = null
    private lateinit var sensorManager: SensorManager
    private var gyroSensor: Sensor? = null
    private val udpExecutor = Executors.newSingleThreadExecutor()

    private var is90DegreeRotation = false
    private var isPitchReversed = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        val channelId = "gyro_service_channel"
        val channel = NotificationChannel(
            channelId, "GyroMouse Background Service", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("GyroMouse Active")
            .setContentText("Streaming gyro data...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

        startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        gyroSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Load latest settings from SharedPreferences
        val prefs = getSharedPreferences("GyroPrefs", Context.MODE_PRIVATE)
        PC_IP = prefs.getString("ip", "192.168.1.182") ?: "192.168.1.182"
        is90DegreeRotation = prefs.getBoolean("rot90", false)
        isPitchReversed = prefs.getBoolean("revPitch", false)

        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GYROSCOPE) return

        val dt = 0.02f
        var currentPitch = event.values[0] * dt
        var currentYaw   = -event.values[1] * dt

        if (is90DegreeRotation) {
            val temp = currentPitch
            currentPitch = currentYaw
            currentYaw = -temp
        }

        if (isPitchReversed) {
            currentPitch = -currentPitch
        }

        sendMotion(currentYaw, currentPitch)
    }

    private fun sendMotion(dyaw: Float, dpitch: Float) {
        udpExecutor.execute {
            try {
                if (udpSocket == null) udpSocket = DatagramSocket()
                val buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                buf.putFloat(dyaw).putFloat(dpitch)
                val data = buf.array()
                val packet = DatagramPacket(data, data.size, InetAddress.getByName(PC_IP), PC_PORT)
                udpSocket!!.send(packet)
            } catch (e: Exception) { }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        sensorManager.unregisterListener(this)
        udpSocket?.close()
        udpExecutor.shutdown()
    }
}