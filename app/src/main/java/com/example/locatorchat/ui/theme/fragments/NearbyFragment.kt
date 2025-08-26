package com.example.locatorchat.ui.fragments

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.locatorchat.databinding.FragmentNearbyBinding
import com.example.locatorchat.model.DiscoveredUser
import com.example.locatorchat.model.User
import com.example.locatorchat.ui.RadarView
import com.example.locatorchat.util.NearbyService
import com.example.locatorchat.util.PermissionHelper
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class NearbyFragment : Fragment() {

    private var _binding: FragmentNearbyBinding? = null
    private val binding get() = _binding!!

    private lateinit var connectionsClient: ConnectionsClient
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private val SERVICE_ID = "com.example.locatorchat.SERVICE_ID"
    private val TAG = "NearbyFragment"

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            startDiscovery()
            Toast.makeText(requireContext(), "Permissions granted. You can now become discoverable.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(requireContext(), "Radar permissions are required for this feature.", Toast.LENGTH_LONG).show()
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val discoveredUid = info.endpointName

            // --- ADD THIS CHECK TO IGNORE YOURSELF ---
            if (discoveredUid == auth.currentUser?.uid) {
                Log.i(TAG, "Ignored own device.")
                return
            }

            Log.i(TAG, "Endpoint found: ID $endpointId, UID: $discoveredUid")
            fetchUserDetails(endpointId, discoveredUid)
        }

        override fun onEndpointLost(endpointId: String) {
            Log.i(TAG, "Endpoint lost: $endpointId")
            binding.radarView.removeUser(endpointId)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNearbyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        if (auth.currentUser == null) {
            Toast.makeText(requireContext(), "User not logged in!", Toast.LENGTH_LONG).show()
            activity?.finish()
            return
        }

        connectionsClient = Nearby.getConnectionsClient(requireContext())
        setupRadarView()
        setupSwitch()

        if (!PermissionHelper.hasRadarPermissions(requireContext())) {
            PermissionHelper.requestRadarPermissions(requireActivity())
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
        PopupMenu(requireContext(), anchorView).apply {
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
                        DiscoveredUser(endpointId, user.uid, user.username, user.shareableId)
                    )
                }
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
                Toast.makeText(requireContext(), "Friend request sent to ${recipient.username}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupSwitch() {
        binding.discoverableSwitch.isChecked = isServiceRunning(NearbyService::class.java)
        binding.discoverableSwitch.setOnCheckedChangeListener { _, isChecked ->
            val serviceIntent = Intent(requireContext(), NearbyService::class.java)
            if (isChecked) {
                if (PermissionHelper.hasRadarPermissions(requireContext())) {
                    requireContext().startService(serviceIntent)
                } else {
                    binding.discoverableSwitch.isChecked = false
                    PermissionHelper.requestRadarPermissions(requireActivity())
                }
            } else {
                requireContext().stopService(serviceIntent)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (PermissionHelper.hasRadarPermissions(requireContext())) {
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
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
            .addOnSuccessListener { Log.i(TAG, "Discovery started successfully.") }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}