package com.example.locatorchat.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.locatorchat.R
import com.example.locatorchat.databinding.ActivityProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val currentUserId = auth.currentUser?.uid
        val userIdToLoad = intent.getStringExtra("userId") ?: currentUserId ?: return

        loadProfile(userIdToLoad, currentUserId)

        binding.shareQrButton.setOnClickListener {
            val shareableId = binding.shareableIdValue.text.toString()
            if (shareableId.isNotEmpty() && shareableId != "-") {
                showQrCodeDialog(shareableId)
            } else {
                Toast.makeText(this, "Shareable ID is not available.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadProfile(uidToLoad: String, currentUid: String?) {
        db.collection("users").document(uidToLoad).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val name = doc.getString("name") ?: "-"
                val surname = doc.getString("surname") ?: "-"
                // ✅ FIXED: Handles empty gender string from Firestore
                val gender = doc.getString("gender")?.takeIf { it.isNotEmpty() } ?: "Not specified"
                val photoUrl = doc.getString("photoUrl") ?: ""

                // Set text values
                binding.nameValue.text = name
                binding.surnameValue.text = surname
                binding.phoneValue.text = doc.getString("contact") ?: "-"
                binding.emailValue.text = doc.getString("email") ?: "-"
                binding.genderValue.text = gender.replaceFirstChar { it.uppercase() } // Capitalize first letter
                binding.dobValue.text = doc.getString("dob") ?: "-"
                binding.shareableIdValue.text = doc.getString("shareableId") ?: "-"
                binding.locationModeValue.text = when (doc.getString("locationMode")) {
                    "precise" -> "Precise Location"
                    "approximate" -> "Approximate Location"
                    else -> "Not Sharing Location"
                }

                val placeholder = when (gender.lowercase()) {
                    "male" -> R.drawable.ic_male
                    "female" -> R.drawable.ic_female
                    else -> R.drawable.ic_person_placeholder
                }

                Glide.with(this)
                    .load(photoUrl)
                    .placeholder(placeholder)
                    .error(placeholder)
                    .circleCrop()
                    .into(binding.profileImageView)

                if (uidToLoad == currentUid) {
                    binding.shareQrButton.visibility = View.VISIBLE
                }

            }
        }.addOnFailureListener {
            binding.nameValue.text = "Error loading profile"
        }
    }

    // ✅ FIXED: Implemented QR code generation and dialog
    private fun showQrCodeDialog(shareableId: String) {
        try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(shareableId, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }

            val imageView = ImageView(this)
            imageView.setImageBitmap(bmp)

            AlertDialog.Builder(this)
                .setTitle("Your Shareable QR Code")
                .setView(imageView)
                .setPositiveButton("Close", null)
                .show()

        } catch (e: Exception) {
            Toast.makeText(this, "Error generating QR code", Toast.LENGTH_SHORT).show()
        }
    }
}
