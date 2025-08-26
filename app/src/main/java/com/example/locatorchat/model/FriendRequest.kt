package com.example.locatorchat.model

// Add a 'var' for status so we can update it easily.
data class FriendRequest(
    val senderUid: String = "",
    var status: String = "",
    val timestamp: Long = 0,
    // We'll add the sender's username here after fetching it.
    @Transient var senderUsername: String = ""
)