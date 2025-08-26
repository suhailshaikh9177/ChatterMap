package com.example.locatorchat.model

import com.google.firebase.firestore.Exclude

data class Message(
    val senderId: String = "",
    val receiverId: String = "",
    var text: String = "", // Changed to var to allow modification
    val timestamp: Long = 0,
    val status: String = MessageStatus.SENT.name,

    // This field holds the original text after a translation.
    // @get:Exclude tells Firestore to ignore this field completely.
    @get:Exclude
    var originalText: String? = null
)