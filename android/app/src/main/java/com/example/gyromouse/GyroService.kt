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

class GyroService : Service(), SensorEventListener {

    private val PC_IP = "192.168.1.182" // Keep this updated!
    private val PC_PORT = 26760
    private var udpSocket: java.net.DatagramSocket? = null
    private lateinit var sensorManager: SensorManager
    private var gyroSensor: Sensor? = null

    override fun onCreate() {
        super.onCreate()

        // 1. Create a notification (Mandatory for Foreground Services)
        val channelId = "gyro_service_channel"
        val channel = NotificationChannel(
            channelId, "GyroMouse Background Service", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("GyroMouse Active")
            .setContentText("Sending gyro data in the background")
            .setSmallIcon(android.R.drawable.ic_menu_compass) // default icon
            .build()

        startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        // 2. Start the sensor
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        gyroSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GYROSCOPE) return

        // Send data constantly, let the PC decide whether to use it
        val dt = 0.02f
        val dpitch = event.values[0] * dt
        val dyaw = -event.values[1] * dt

        sendMotion(dyaw, dpitch)
    }

    private fun sendMotion(dyaw: Float, dpitch: Float) {
        Thread {
            try {
                if (udpSocket == null) udpSocket = java.net.DatagramSocket()
                val buf = java.nio.ByteBuffer.allocate(8)
                buf.order(java.nio.ByteOrder.LITTLE_ENDIAN)
                buf.putFloat(dyaw)
                buf.putFloat(dpitch)
                val data = buf.array()
                val packet = java.net.DatagramPacket(
                    data, data.size, java.net.InetAddress.getByName(PC_IP), PC_PORT
                )
                udpSocket!!.send(packet)
            } catch (e: Exception) { /* ignore in background */ }
        }.start()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        udpSocket?.close()
    }
}