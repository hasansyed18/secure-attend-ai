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

    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    
    // ⚡ Industry standard 16-bit short UUID for "FEAF"
    // This saves 14 bytes per packet, allowing us to fit all metadata
    private val SERVICE_UUID = ParcelUuid.fromString("0000FEAF-0000-1000-8000-00805F9B34FB")

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sid = intent?.getStringExtra("SESSION_ID") ?: "Unknown"
        startForeground(1, createNotification(sid))

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter

        if (adapter == null || !adapter.isEnabled || intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        bleAdvertiser = adapter.bluetoothLeAdvertiser
        val sessionId = intent.getStringExtra("SESSION_ID") ?: run { stopSelf(); return START_NOT_STICKY }
        val dept = intent.getStringExtra("DEPT") ?: "XX"
        val sem = intent.getStringExtra("SEM") ?: "0"
        val sec = intent.getStringExtra("SECTION") ?: "X"

        // Compact Protocol: Dept|Sem|Sec|SessionID
        val packet = "$dept|$sem|$sec|$sessionId"
        
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
            // By using addServiceData with a 16-bit UUID format, we fit everything in 31 bytes
            .addServiceData(SERVICE_UUID, packet.toByteArray())
            .build()

        try {
            bleAdvertiser?.stopAdvertising(advertiseCallback)
            bleAdvertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: Exception) {
            Log.e("BLE", "Start advertising error: ${e.message}")
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) { Log.d("BLE", "Broadcasting successfully") }
        override fun onStartFailure(errorCode: Int) { Log.e("BLE", "Failed: $errorCode") }
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
            .setSmallIcon(R.drawable.ic_attendit_logo)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try { bleAdvertiser?.stopAdvertising(advertiseCallback) } catch (_: Exception) {}
        super.onDestroy()
    }
}
