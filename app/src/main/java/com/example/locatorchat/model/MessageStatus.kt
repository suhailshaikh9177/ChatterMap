package com.example.locatorchat.model

enum class MessageStatus {
    SENT,       // Not delivered yet
    DELIVERED,  // Reached the recipient's device
    SEEN        // Recipient has opened the chat
}