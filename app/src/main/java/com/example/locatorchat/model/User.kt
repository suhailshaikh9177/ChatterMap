package com.example.locatorchat.model

data class User(
    val uid: String = "",
    val username: String = "",
    val name: String = "",
    val surname: String = "",
    val email: String = "",
    val contact: String = "",
    val gender: String = "",
    val dob: String = "",
    val locationMode: String = "none",
    val shareableId: String = "",
    val photoUrl: String = "",
    val city: String = "",
    val state: String = "",
    val country: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val friends: List<String> = emptyList(),
    val fcmToken: String = "" // ADDED: To store the notification token
)