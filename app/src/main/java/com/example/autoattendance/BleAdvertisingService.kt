package com.example.autoattendance

import android.app.*
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.*

class BleAdvertisingService : Service() {

    private val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    
    // Efficient 16-bit UUID: 0xFEAF
    private val SERVICE_UUID = ParcelUuid.fromString("0000FEAF-0000-1000-8000-00805F9B34FB")

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter

        if (adapter == null || !adapter.isEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }

        bleAdvertiser = adapter.bluetoothLeAdvertiser
        val sessionId = intent?.getStringExtra("SESSION_ID") ?: return START_NOT_STICKY
        val dept = intent.getStringExtra("DEPT") ?: "XX"
        val sem = intent.getStringExtra("SEM") ?: "0"
        val section = intent.getStringExtra("SECTION") ?: "X"

        // Construct a compact packet to fit in BLE limits (max ~26 bytes for service data)
        // Format: DEPT|SEM|SEC|SESSION_ID
        val packet = "$dept|$sem|$section|$sessionId"
        
        startForeground(1, createNotification(sessionId))
        startAdvertising(packet)
        
        timeoutHandler.postDelayed({ stopSelf() }, 600000) 
        return START_STICKY
    }

    private fun startAdvertising(packet: String) {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(false)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(SERVICE_UUID)
            .addServiceData(SERVICE_UUID, packet.toByteArray())
            .build()

        try {
            bleAdvertiser?.stopAdvertising(advertiseCallback)
            bleAdvertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: Exception) {
            Log.e("BLE", "Error: ${e.message}")
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) { Log.d("BLE", "Broadcasting packet") }
        override fun onStartFailure(errorCode: Int) { Log.e("BLE", "Failed: $errorCode") }
    }

    private fun createNotification(sid: String): Notification {
        val channelId = "attendance_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Attendance", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Attendance Active")
            .setContentText("Broadcasting session ID: $sid")
            .setSmallIcon(R.drawable.ic_secure_attend_logo)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try { bleAdvertiser?.stopAdvertising(advertiseCallback) } catch (_: Exception) {}
        timeoutHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
