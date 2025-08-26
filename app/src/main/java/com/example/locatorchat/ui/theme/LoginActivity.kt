package com.example.locatorchat.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.locatorchat.databinding.ActivityLoginBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient

    companion object {
        private const val RC_SIGN_IN = 9001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(com.example.locatorchat.R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
        binding.googleSignInBtn.setSize(SignInButton.SIZE_WIDE)
        binding.googleSignInBtn.setColorScheme(SignInButton.COLOR_DARK)

        binding.loginButton.setOnClickListener {
            val input = binding.emailUsernameInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            if (input.isEmpty() || password.isEmpty()) {
                showToast("Please fill in all fields")
                return@setOnClickListener
            }

            if (Patterns.EMAIL_ADDRESS.matcher(input).matches()) {
                loginWithEmail(input, password)
            } else {
                db.collection("users")
                    .whereEqualTo("username", input)
                    .get()
                    .addOnSuccessListener { result ->
                        if (!result.isEmpty) {
                            val email = result.documents[0].getString("email")
                            if (email != null) {
                                loginWithEmail(email, password)
                            } else { showToast("Email not found for this user.") }
                        } else { showToast("Username not found") }
                    }
                    .addOnFailureListener { showToast("Error: ${it.message}") }
            }
        }

        binding.registerRedirect.setOnClickListener {
            startActivity(Intent(this, RegistrationActivity::class.java))
        }

        binding.googleSignInBtn.setOnClickListener {
            signInWithGoogle()
        }
    }

    private fun loginWithEmail(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                // MODIFIED: Get and save token on successful login
                getAndSaveFcmToken()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .addOnFailureListener {
                showToast("Login failed: ${it.message}")
            }
    }

    private fun signInWithGoogle() {
        startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account: GoogleSignInAccount = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                showToast("Google sign-in failed: ${e.message}")
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener {
                // MODIFIED: Get and save token on successful login
                getAndSaveFcmToken()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .addOnFailureListener {
                showToast("Authentication Failed: ${it.message}")
            }
    }

    // NEW FUNCTION: Gets the current device token and saves it to the user's Firestore document.
    private fun getAndSaveFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            val userId = auth.currentUser?.uid ?: return@addOnCompleteListener

            db.collection("users").document(userId)
                .update("fcmToken", token)
                .addOnSuccessListener { Log.d("FCM", "FCM token saved successfully.") }
                .addOnFailureListener { e -> Log.w("FCM", "Error saving FCM token.", e) }
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}