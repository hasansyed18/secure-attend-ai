package com.example.autoattendance

import android.app.*
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class BleAdvertisingService : Service() {

    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private val MANUFACTURER_ID = 0x00E0 // 🚀 Use Google's ID for better compatibility

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sid = intent?.getStringExtra("SESSION_ID") ?: "Unknown"
        val securityToken = intent?.getStringExtra("SECURITY_TOKEN") ?: ""
        startForeground(1, createNotification(sid))

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter

        if (adapter == null || !adapter.isEnabled || intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        bleAdvertiser = adapter.bluetoothLeAdvertiser
        
        // Standard Online format: ON|<sessionId>|<token>
        val packet = "ON|$sid|$securityToken"
        
        Log.d("BLE_ADV", "Starting Advertising Packet: $packet")
        startAdvertising(packet)
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
            .addManufacturerData(MANUFACTURER_ID, packet.toByteArray())
            .build()

        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_ADVERTISE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                bleAdvertiser?.stopAdvertising(advertiseCallback)
                bleAdvertiser?.startAdvertising(settings, data, advertiseCallback)
                Log.d("BLE_ADV", "Broadcasting successfully (${packet.length} bytes)")
            }
        } catch (e: Exception) {
            Log.e("BLE", "Start advertising error: ${e.message}")
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) { Log.d("BLE_ADV", "Broadcasting successfully") }
        override fun onStartFailure(errorCode: Int) { 
            val msg = when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS"
                ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
                else -> "UNKNOWN_ERROR ($errorCode)"
            }
            Log.e("BLE_ADV", "Broadcast Failed: $msg") 
        }
    }

    private fun createNotification(sid: String): Notification {
        val channelId = "attendance_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Attendance Live", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Broadcasting Class Session")
            .setContentText("ID: $sid")
            .setSmallIcon(R.drawable.ic_secure_attend_logo) // Using renamed logo
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try { 
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_ADVERTISE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                bleAdvertiser?.stopAdvertising(advertiseCallback) 
            }
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
