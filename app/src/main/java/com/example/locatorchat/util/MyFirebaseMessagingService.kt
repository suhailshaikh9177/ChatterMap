package com.example.locatorchat.util

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    // This is called when a message is received while the app is in the foreground.
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM", "From: ${remoteMessage.from}")

        // Check if message contains a notification payload.
        remoteMessage.notification?.let {
            Log.d("FCM", "Message Notification Body: ${it.body}")
            // Here is where we will later build a notification.
        }
    }

    // This is called when a new token is generated for the device.
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token generated: $token")
        // If a user is logged in, update their token in Firestore.
        FirebaseAuth.getInstance().currentUser?.uid?.let { userId ->
            updateTokenInFirestore(userId, token)
        }
    }

    private fun updateTokenInFirestore(userId: String, token: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(userId)
            .update("fcmToken", token)
            .addOnSuccessListener { Log.d("FCM", "FCM token updated successfully for user $userId") }
            .addOnFailureListener { e -> Log.w("FCM", "Error updating FCM token for user $userId", e) }
    }
}