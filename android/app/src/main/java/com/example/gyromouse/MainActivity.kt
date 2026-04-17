package com.example.gyromouse

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gyromouse.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity(), SensorEventListener {

    private val PC_IP   = "192.168.1.182"  // ← your PC's local IP
    private val PC_PORT = 26760
    private var udpSocket: java.net.DatagramSocket? = null

    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var isActivated = false
    private var lastYaw   = 0f
    private var lastPitch = 0f
    private var justActivated = false

    // UI state
    private val logLines = mutableStateListOf<String>()

    private fun log(msg: String) {
        logLines.add(msg)
        if (logLines.size > 100) logLines.removeAt(0) // keep last 100 lines
    }

    private fun sendMotion(dyaw: Float, dpitch: Float) {
        Thread {
            try {
                if (udpSocket == null) udpSocket = java.net.DatagramSocket()
                val buf = java.nio.ByteBuffer.allocate(8)
                buf.order(java.nio.ByteOrder.LITTLE_ENDIAN)
                buf.putFloat(Math.toRadians(dyaw.toDouble()).toFloat())
                buf.putFloat(Math.toRadians(dpitch.toDouble()).toFloat())
                val data = buf.array()
                val packet = java.net.DatagramPacket(
                    data, data.size,
                    java.net.InetAddress.getByName(PC_IP), PC_PORT
                )
                udpSocket!!.send(packet)
            } catch (e: Exception) {
                log("UDP error: ${e.message}")
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

        if (rotationSensor == null) {
            log("ERROR: Game Rotation Vector sensor not found!")
        } else {
            log("Sensor found: ${rotationSensor!!.name}")
        }

        setContent {
            MyApplicationTheme {
                AppUI(logLines)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            log("Sensor registered")
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        log("Sensor unregistered")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GAME_ROTATION_VECTOR) return

        val rotationMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)

        val yaw   = Math.toDegrees(orientation[0].toDouble()).toFloat()
        val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()

        // On first frame after activation, set reference — don't move yet
        if (justActivated) {
            lastYaw   = yaw
            lastPitch = pitch
            justActivated = false
            return
        }

        val dyaw   = yaw - lastYaw
        // The minus sign here fixes the "inverted" feel
        val dpitch = -(pitch - lastPitch)

        lastYaw   = yaw
        lastPitch = pitch

        if (isActivated) {
            sendMotion(dyaw, dpitch)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        val l2 = event.getAxisValue(android.view.MotionEvent.AXIS_LTRIGGER)
        val wasActivated = isActivated
        isActivated = l2 > 0.5f

        if (isActivated && !wasActivated) {
            justActivated = true
            log(">>> ACTIVATED")
        } else if (!isActivated && wasActivated) {
            log(">>> DEACTIVATED")
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        log("KeyDown: $keyCode")
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean {
        log("KeyUp: $keyCode")
        return super.onKeyUp(keyCode, event)
    }
}

@Composable
fun AppUI(logLines: List<String>) {
    val scrollState = rememberScrollState()

    LaunchedEffect(logLines.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
    ) {
        Text("GyroMouse Android", fontSize = 20.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            logLines.forEach { line ->
                Text(line, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
        }
    }
}