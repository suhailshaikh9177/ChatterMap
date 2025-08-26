package com.example.locatorchat.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.locatorchat.databinding.ActivityFriendRequestsBinding
import com.example.locatorchat.model.FriendRequest
import com.example.locatorchat.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FriendRequestsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFriendRequestsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: FriendRequestAdapter
    private val requestsList = mutableListOf<FriendRequest>()

    private val TAG = "FriendRequestsActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFriendRequestsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setupRecyclerView()
        fetchFriendRequests()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun setupRecyclerView() {
        adapter = FriendRequestAdapter(
            onAccept = { request ->
                acceptRequest(request)
            },
            onReject = { request ->
                rejectRequest(request)
            }
        )
        binding.requestsRecyclerView.adapter = adapter
    }

    private fun acceptRequest(request: FriendRequest) {
        val currentUserUid = auth.currentUser?.uid ?: return
        val senderUid = request.senderUid

        // --- ADD THIS CHECK TO PREVENT ADDING YOURSELF ---
        if (senderUid == currentUserUid) {
            Toast.makeText(this, "You cannot accept a request from yourself.", Toast.LENGTH_SHORT).show()
            return
        }

        val batch = db.batch()

        // 1. Add sender to current user's friends list
        val currentUserRef = db.collection("users").document(currentUserUid)
        batch.update(currentUserRef, "friends", FieldValue.arrayUnion(senderUid))

        // 2. Add current user to sender's friends list
        val senderRef = db.collection("users").document(senderUid)
        batch.update(senderRef, "friends", FieldValue.arrayUnion(currentUserUid))

        // 3. Update the request status to "accepted"
        val requestRef = db.collection("users").document(currentUserUid)
            .collection("friend_requests").document(senderUid)
        batch.update(requestRef, "status", "accepted")

        batch.commit().addOnSuccessListener {
            Toast.makeText(this, "Accepted ${request.senderUsername}'s request", Toast.LENGTH_SHORT).show()
            removeRequestFromUI(request)
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to accept request", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Error accepting request", e)
        }
    }

    private fun rejectRequest(request: FriendRequest) {
        val currentUserUid = auth.currentUser?.uid ?: return
        db.collection("users").document(currentUserUid)
            .collection("friend_requests").document(request.senderUid)
            .delete() // Simply delete the request
            .addOnSuccessListener {
                Toast.makeText(this, "Rejected ${request.senderUsername}'s request", Toast.LENGTH_SHORT).show()
                removeRequestFromUI(request)
            }.addOnFailureListener { e ->
                Toast.makeText(this, "Failed to reject request", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Error rejecting request", e)
            }
    }

    private fun removeRequestFromUI(request: FriendRequest) {
        val position = requestsList.indexOf(request)
        if (position != -1) {
            requestsList.removeAt(position)
            adapter.setRequests(requestsList)
            if (requestsList.isEmpty()) {
                binding.emptyView.visibility = View.VISIBLE
            }
        }
    }

    private fun fetchFriendRequests() {
        val currentUserUid = auth.currentUser?.uid ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val requestsSnapshot = db.collection("users").document(currentUserUid)
                    .collection("friend_requests")
                    .whereEqualTo("status", "pending")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get().await()

                if (requestsSnapshot.isEmpty) {
                    withContext(Dispatchers.Main) {
                        binding.emptyView.visibility = View.VISIBLE
                        binding.requestsRecyclerView.visibility = View.GONE
                    }
                    return@launch
                }

                requestsList.clear()
                for (document in requestsSnapshot.documents) {
                    val request = document.toObject(FriendRequest::class.java)
                    if (request != null) {
                        val userDoc = db.collection("users").document(request.senderUid).get().await()
                        val sender = userDoc.toObject(User::class.java)
                        request.senderUsername = sender?.username ?: "Unknown User"
                        requestsList.add(request)
                    }
                }

                withContext(Dispatchers.Main) {
                    binding.emptyView.visibility = View.GONE
                    binding.requestsRecyclerView.visibility = View.VISIBLE
                    adapter.setRequests(requestsList)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error fetching friend requests", e)
            }
        }
    }
}