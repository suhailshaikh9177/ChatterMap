package com.example.locatorchat.model

data class DiscoveredUser(
        val endpointId: String,
        val uid: String,
        val username: String,
        val shareableId: String
)