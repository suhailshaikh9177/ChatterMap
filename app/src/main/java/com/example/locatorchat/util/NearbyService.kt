package com.example.locatorchat.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.locatorchat.R
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.firebase.auth.FirebaseAuth

class NearbyService : Service() {

    private lateinit var connectionsClient: ConnectionsClient
    private val TAG = "NearbyService"
    private val SERVICE_ID = "com.example.locatorchat.SERVICE_ID"
    private val NOTIFICATION_ID = 123
    private val CHANNEL_ID = "NearbyServiceChannel"

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(p0: String, p1: ConnectionInfo) {}
        override fun onConnectionResult(p0: String, p1: ConnectionResolution) {}
        override fun onDisconnected(p0: String) {}
    }

    override fun onCreate() {
        super.onCreate()
        connectionsClient = Nearby.getConnectionsClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        startAdvertising()
        return START_STICKY
    }

    private fun startAdvertising() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.w(TAG, "Cannot start advertising, user is not logged in.")
            stopSelf() // Stop the service if there's no user
            return
        }

        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        connectionsClient.startAdvertising(
            user.uid,
            SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            Log.i(TAG, "Background advertising started successfully.")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Background advertising failed", e)
            stopSelf() // Stop the service if advertising fails
        }
    }

    override fun onDestroy() {
        connectionsClient.stopAdvertising()
        Log.i(TAG, "NearbyService destroyed, advertising stopped.")
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Nearby Discoverable Service",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LocatorChat")
            .setContentText("You are currently discoverable by nearby users.")
            .setSmallIcon(R.drawable.ic_nearby_notification)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}