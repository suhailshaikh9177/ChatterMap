// =================================================================
// FINAL CLEAN & CORRECTED VERSION
// =================================================================
package com.example.locatorchat.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.locatorchat.R
import com.example.locatorchat.databinding.ActivityRegistrationBinding
import com.example.locatorchat.model.User
import com.example.locatorchat.util.LocationHelper
import com.example.locatorchat.util.PermissionHelper
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.yalantis.ucrop.UCrop
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class RegistrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegistrationBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var inflater: LayoutInflater

    private val calendar = Calendar.getInstance()
    private var step = 0
    private val stepsCount = 7

    // User input fields
    private var username = ""
    private var name = ""
    private var surname = ""
    private var phone = ""
    private var email = ""
    private var gender = ""
    private var dob = ""
    private var password = ""
    private var confirmPassword = ""
    private var locationMode = "none"

    // Profile image
    private var profileImageUri: Uri? = null
    private var profileImageView: ShapeableImageView? = null
    private lateinit var pickImageLauncher: ActivityResultLauncher<String>
    private lateinit var uCropLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        inflater = LayoutInflater.from(this)

        LocationHelper.initialize(this)
        setupImageLaunchers()
        loadStep(step)

        binding.nextButton.setOnClickListener {
            if (validateStep(step)) {
                if (step < stepsCount - 1) {
                    step++
                    loadStep(step)
                } else {
                    handleRegistrationAttempt()
                }
            }
        }

        binding.backButton.setOnClickListener {
            if (step > 0) {
                step--
                loadStep(step)
            }
        }
    }

    // ==============================
    // Image Pick + Crop
    // ==============================
    private fun setupImageLaunchers() {
        // 1. Launcher to pick an image from the gallery
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { sourceUri ->
            sourceUri?.let {
                // Create a destination file in the cache directory
                val destinationFileName = "cropped_${System.currentTimeMillis()}.jpg"
                val destinationUri = Uri.fromFile(File(cacheDir, destinationFileName))
                startUCrop(it, destinationUri)
            }
        }

        // 2. Launcher to handle the result from UCropActivity
        uCropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val resultUri = UCrop.getOutput(result.data!!)
                if (resultUri != null) {
                    profileImageUri = resultUri
                    profileImageView?.setImageURI(profileImageUri)
                } else {
                    showToast("Image cropping failed")
                }
            } else if (result.resultCode == UCrop.RESULT_ERROR) {
                val cropError = UCrop.getError(result.data!!)
                showToast("Image cropping failed")
                Log.e("UCrop", "Error: $cropError")
            }
        }
    }

    private fun startUCrop(sourceUri: Uri, destinationUri: Uri) {
        val options = UCrop.Options().apply {
            setCompressionQuality(90)
            setCircleDimmedLayer(true) // For an oval/circular crop shape
            setShowCropGrid(false)
            setShowCropFrame(true)
            withAspectRatio(1f, 1f) // Square aspect ratio
            // ✅ FIXED: Replaced missing colors with available ones from your colors.xml
            setToolbarColor(ContextCompat.getColor(this@RegistrationActivity, R.color.md_theme_light_primary))
            setStatusBarColor(ContextCompat.getColor(this@RegistrationActivity, R.color.md_theme_light_primary))
            setActiveControlsWidgetColor(ContextCompat.getColor(this@RegistrationActivity, R.color.md_theme_light_primary))
            setToolbarTitle("Crop Profile Picture")
        }

        val uCropIntent = UCrop.of(sourceUri, destinationUri)
            .withOptions(options)
            .getIntent(this)

        uCropLauncher.launch(uCropIntent)
    }


    // ==============================
    // Registration Flow
    // ==============================
    private fun handleRegistrationAttempt() {
        if ((locationMode == "precise" || locationMode == "approximate")
            && !PermissionHelper.hasRadarPermissions(this)
        ) {
            PermissionHelper.requestRadarPermissions(this)
        } else {
            registerUser()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PermissionHelper.RADAR_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                showToast("Permissions granted! Finishing registration.")
            } else {
                showToast("Permissions denied. Location will not be shared.")
                locationMode = "none"
            }
            registerUser()
        }
    }

    private fun registerUser() {
        setLoadingState(true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val firebaseUser = authResult.user ?: return@addOnSuccessListener
                if (profileImageUri != null) {
                    uploadProfileImage(firebaseUser)
                } else {
                    createFinalUser(firebaseUser, "")
                }
            }
            .addOnFailureListener { e ->
                setLoadingState(false)
                showToast("Registration failed: ${e.message}")
            }
    }

    private fun uploadProfileImage(firebaseUser: FirebaseUser) {
        val storageRef = storage.reference.child("profile_images/${firebaseUser.uid}/profile.jpg")
        profileImageUri?.let { uri ->
            storageRef.putFile(uri)
                .addOnSuccessListener {
                    storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        createFinalUser(firebaseUser, downloadUri.toString())
                    }
                }
                .addOnFailureListener { e ->
                    setLoadingState(false)
                    showToast("Failed to upload image: ${e.message}")
                }
        }
    }

    private fun createFinalUser(firebaseUser: FirebaseUser, imageUrl: String) {
        if (locationMode == "precise" || locationMode == "approximate") {
            LocationHelper.getCurrentLocation(
                this,
                onSuccess = { location ->
                    val newUser = createUserObject(firebaseUser, location.latitude, location.longitude, imageUrl)
                    saveUserToFirestore(newUser)
                },
                onFailure = {
                    val newUser = createUserObject(firebaseUser, 0.0, 0.0, imageUrl)
                    saveUserToFirestore(newUser)
                }
            )
        } else {
            val newUser = createUserObject(firebaseUser, 0.0, 0.0, imageUrl)
            saveUserToFirestore(newUser)
        }
    }

    private fun createUserObject(firebaseUser: FirebaseUser, lat: Double, lon: Double, photoUrl: String): User {
        return User(
            uid = firebaseUser.uid,
            username = username,
            name = name,
            surname = surname,
            contact = phone,
            email = email,
            gender = gender,
            dob = dob,
            locationMode = locationMode,
            photoUrl = photoUrl,
            shareableId = UUID.randomUUID().toString().take(10),
            latitude = lat,
            longitude = lon
        )
    }

    private fun saveUserToFirestore(user: User) {
        db.collection("users").document(user.uid).set(user)
            .addOnSuccessListener {
                setLoadingState(false)
                showToast("Registration successful!")
                startActivity(Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
            .addOnFailureListener { e ->
                setLoadingState(false)
                showToast("Failed to save user data: ${e.message}")
            }
    }

    // ==============================
    // Stepper Logic
    // ==============================
    private fun loadStep(index: Int) {
        val container = binding.stepContainer
        container.removeAllViews()

        val layoutId = when (index) {
            0 -> R.layout.fragment_username_step
            1 -> R.layout.step_name
            2 -> R.layout.step_contact
            3 -> R.layout.step_gender_dob
            4 -> R.layout.step_password
            5 -> R.layout.step_location
            else -> R.layout.step_profile_picture
        }

        val stepView = inflater.inflate(layoutId, container, false)
        stepView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_right))
        container.addView(stepView)

        setupStepSpecifics(index, stepView)
        updateButtons()
        updateProgress()
    }

    private fun setupStepSpecifics(index: Int, view: View) {
        when (index) {
            0 -> view.findViewById<TextInputEditText>(R.id.usernameEditText).setText(username)
            1 -> {
                view.findViewById<TextInputEditText>(R.id.nameInput).setText(name)
                view.findViewById<TextInputEditText>(R.id.surnameInput).setText(surname)
            }
            2 -> {
                view.findViewById<TextInputEditText>(R.id.phoneInput).setText(phone)
                view.findViewById<TextInputEditText>(R.id.emailInput).setText(email)
            }
            3 -> setupDobAndGender(view)
            4 -> {
                view.findViewById<TextInputEditText>(R.id.passwordInput).setText(password)
                view.findViewById<TextInputEditText>(R.id.confirmPasswordInput).setText(confirmPassword)
            }
            5 -> setupLocationMode(view)
            6 -> setupProfilePicture(view)
        }
    }

    private fun setupDobAndGender(view: View) {
        val dobInput = view.findViewById<TextInputEditText>(R.id.dobInput)
        dobInput.setText(dob)

        dobInput.setOnClickListener {
            DatePickerDialog(
                this, { _, year, month, day ->
                    calendar.set(year, month, day)
                    dob = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
                    dobInput.setText(dob)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
        }

        val genderGroup = view.findViewById<RadioGroup>(R.id.genderGroup)
        if (gender.isNotEmpty()) {
            view.findViewWithTag<RadioButton>(gender.lowercase())?.isChecked = true
        }
    }

    private fun setupLocationMode(view: View) {
        val radioPrecise = view.findViewById<RadioButton>(R.id.sharePrecise)
        val radioApproximate = view.findViewById<RadioButton>(R.id.shareApproximate)
        val radioNone = view.findViewById<RadioButton>(R.id.shareNone)

        when (locationMode) {
            "precise" -> radioPrecise.isChecked = true
            "approximate" -> radioApproximate.isChecked = true
            else -> radioNone.isChecked = true
        }

        view.findViewById<View>(R.id.cardPrecise).setOnClickListener {
            radioPrecise.isChecked = true; radioApproximate.isChecked = false; radioNone.isChecked = false
        }
        view.findViewById<View>(R.id.cardApproximate).setOnClickListener {
            radioPrecise.isChecked = false; radioApproximate.isChecked = true; radioNone.isChecked = false
        }
        view.findViewById<View>(R.id.cardNone).setOnClickListener {
            radioPrecise.isChecked = false; radioApproximate.isChecked = false; radioNone.isChecked = true
        }
    }

    private fun setupProfilePicture(view: View) {
        profileImageView = view.findViewById(R.id.profileImageView)
        profileImageUri?.let { profileImageView?.setImageURI(it) }

        view.findViewById<View>(R.id.addProfileImageButton).setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    private fun updateButtons() {
        binding.backButton.visibility = if (step == 0) View.GONE else View.VISIBLE
        binding.nextButton.text = if (step == stepsCount - 1) "Finish" else "Next"
    }

    private fun updateProgress() {
        binding.registrationProgress.setProgressCompat(((step + 1) * 100) / stepsCount, true)
        binding.progressIndicator.text = "Step ${step + 1} of $stepsCount"
    }

    // ==============================
    // Validation
    // ==============================
    private fun validateStep(index: Int): Boolean {
        val stepView = binding.stepContainer.getChildAt(0) ?: return false
        return when (index) {
            0 -> validateUsername(stepView)
            1 -> validateName(stepView)
            2 -> validateContact(stepView)
            3 -> validateGenderAndDob(stepView)
            4 -> validatePassword(stepView)
            5 -> validateLocation(stepView)
            else -> true
        }
    }

    private fun validateUsername(view: View): Boolean {
        username = view.findViewById<TextInputEditText>(R.id.usernameEditText).text.toString().trim()
        return if (username.isEmpty()) { showToast("Username required"); false } else true
    }

    private fun validateName(view: View): Boolean {
        name = view.findViewById<TextInputEditText>(R.id.nameInput).text.toString().trim()
        surname = view.findViewById<TextInputEditText>(R.id.surnameInput).text.toString().trim()
        return if (name.isEmpty()) { showToast("First name required"); false } else true
    }

    private fun validateContact(view: View): Boolean {
        phone = view.findViewById<TextInputEditText>(R.id.phoneInput).text.toString().trim()
        email = view.findViewById<TextInputEditText>(R.id.emailInput).text.toString().trim()
        return when {
            phone.isEmpty() || email.isEmpty() -> { showToast("Phone and Email required"); false }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> { showToast("Invalid email"); false }
            else -> true
        }
    }

    private fun validateGenderAndDob(view: View): Boolean {
        val genderGroup = view.findViewById<RadioGroup>(R.id.genderGroup)
        val dobInput = view.findViewById<TextInputEditText>(R.id.dobInput)

        val checkedId = genderGroup.checkedRadioButtonId
        if (checkedId == -1) { showToast("Select gender"); return false }
        gender = (view.findViewById<RadioButton>(checkedId).tag as String?) ?: ""

        dob = dobInput.text.toString().trim()
        if (dob.isEmpty()) { showToast("Date of birth required"); return false }

        val dobDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dob) ?: return false
        val age = Calendar.getInstance().get(Calendar.YEAR) -
                Calendar.getInstance().apply { time = dobDate }.get(Calendar.YEAR)

        return if (age < 10) { showToast("You must be at least 10"); false } else true
    }

    private fun validatePassword(view: View): Boolean {
        password = view.findViewById<TextInputEditText>(R.id.passwordInput).text.toString()
        confirmPassword = view.findViewById<TextInputEditText>(R.id.confirmPasswordInput).text.toString()
        return when {
            password.length < 6 -> { showToast("Password too short"); false }
            password != confirmPassword -> { showToast("Passwords don’t match"); false }
            else -> true
        }
    }

    private fun validateLocation(view: View): Boolean {
        val radioPrecise = view.findViewById<RadioButton>(R.id.sharePrecise)
        val radioApproximate = view.findViewById<RadioButton>(R.id.shareApproximate)
        locationMode = when {
            radioPrecise.isChecked -> "precise"
            radioApproximate.isChecked -> "approximate"
            else -> "none"
        }
        return true
    }

    // ==============================
    // Helpers
    // ==============================
    private fun setLoadingState(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.nextButton.isEnabled = !isLoading
        binding.backButton.isEnabled = !isLoading
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
