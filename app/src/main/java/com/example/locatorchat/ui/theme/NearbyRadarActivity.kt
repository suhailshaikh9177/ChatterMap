package com.example.locatorchat.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.locatorchat.R
import com.example.locatorchat.databinding.ActivityNearbyRadarBinding
import com.example.locatorchat.model.DiscoveredUser
import com.example.locatorchat.model.User
import com.example.locatorchat.util.NearbyService
import com.example.locatorchat.util.PermissionHelper
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class NearbyRadarActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNearbyRadarBinding
    private lateinit var connectionsClient: ConnectionsClient
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private val SERVICE_ID = "com.example.locatorchat.SERVICE_ID"
    private val TAG = "NearbyRadarActivity"

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val discoveredUid = info.endpointName
            Log.i(TAG, "Endpoint found: ID $endpointId, UID: $discoveredUid")
            fetchUserDetails(endpointId, discoveredUid)
        }

        override fun onEndpointLost(endpointId: String) {
            Log.i(TAG, "Endpoint lost: $endpointId")
            binding.radarView.removeUser(endpointId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNearbyRadarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        if (auth.currentUser == null) {
            Toast.makeText(this, "User not logged in!", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        connectionsClient = Nearby.getConnectionsClient(this)
        setupRadarView()
        setupSwitch()

        if (!PermissionHelper.hasRadarPermissions(this)) {
            PermissionHelper.requestRadarPermissions(this)
        }
    }

    private fun setupRadarView() {
        binding.radarView.userClickListener = object : RadarView.OnUserClickListener {
            override fun onUserClick(user: DiscoveredUser) {
                showUserOptions(binding.radarView, user)
            }
        }
    }

    private fun showUserOptions(anchorView: View, user: DiscoveredUser) {
        PopupMenu(this, anchorView).apply {
            menu.add("Send Friend Request").setOnMenuItemClickListener {
                sendFriendRequest(user)
                true
            }
            show()
        }
    }

    private fun fetchUserDetails(endpointId: String, uid: String) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                val user = document.toObject(User::class.java)
                if (user != null) {
                    binding.radarView.addUser(
                        DiscoveredUser(
                            endpointId = endpointId,
                            uid = user.uid,
                            username = user.username,
                            shareableId = user.shareableId
                        )
                    )
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error fetching user details for UID: $uid", e)
            }
    }

    private fun sendFriendRequest(recipient: DiscoveredUser) {
        val currentUser = auth.currentUser ?: return
        val senderUid = currentUser.uid
        val recipientUid = recipient.uid

        val requestData = hashMapOf(
            "senderUid" to senderUid,
            "status" to "pending",
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("users").document(recipientUid)
            .collection("friend_requests").document(senderUid)
            .set(requestData)
            .addOnSuccessListener {
                Toast.makeText(this, "Friend request sent to ${recipient.username}", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to send request", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Error sending friend request", e)
            }
    }

    private fun setupSwitch() {
        binding.discoverableSwitch.isChecked = isServiceRunning(NearbyService::class.java)
        binding.discoverableSwitch.setOnCheckedChangeListener { _, isChecked ->
            val serviceIntent = Intent(this, NearbyService::class.java)
            if (isChecked) {
                if (PermissionHelper.hasRadarPermissions(this)) {
                    startService(serviceIntent)
                } else {
                    binding.discoverableSwitch.isChecked = false
                    PermissionHelper.requestRadarPermissions(this)
                }
            } else {
                stopService(serviceIntent)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (PermissionHelper.hasRadarPermissions(this)) {
            startDiscovery()
        }
    }

    override fun onStop() {
        super.onStop()
        connectionsClient.stopDiscovery()
        Log.i(TAG, "Stopped discovery.")
    }

    private fun startDiscovery() {
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            Log.i(TAG, "Discovery started successfully.")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Discovery failed", e)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionHelper.RADAR_PERMISSION_REQUEST_CODE) {
            if (PermissionHelper.hasRadarPermissions(this)) {
                startDiscovery()
            } else {
                Toast.makeText(this, "Radar permissions are required for this feature.", Toast.LENGTH_LONG).show()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}