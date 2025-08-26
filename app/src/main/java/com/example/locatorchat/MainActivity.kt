package com.example.locatorchat.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import com.example.locatorchat.R
import com.example.locatorchat.databinding.ActivityMainBinding
import com.example.locatorchat.util.LocationHelper
import com.example.locatorchat.util.PermissionHelper
import com.google.android.material.navigation.NavigationView
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.util.ArrayDeque

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var toggle: ActionBarDrawerToggle

    private val qrScannerLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val scannedId = result.contents
            addUserByShareableId(scannedId)
        } else {
            Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    private val permissionQueue = ArrayDeque<String>()
    private val singlePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val currentPermission = permissionQueue.pollFirst()
        if (!isGranted && currentPermission != null) {
            if (!shouldShowRequestPermissionRationale(currentPermission)) {
                showGoToSettingsDialog(listOf(currentPermission))
            }
        }
        requestNextPermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        if (auth.currentUser == null) {
            navigateToLogin()
            return
        }

        LocationHelper.initialize(this)
        setupNavigationDrawer()
        setupViewPagerAndTabs()
        requestAllNecessaryPermissions()
    }

    override fun onResume() {
        super.onResume()
        if (PermissionHelper.hasRadarPermissions(this)) {
            LocationHelper.startLocationUpdates(this)
        }
    }

    override fun onStop() {
        super.onStop()
        LocationHelper.stopLocationUpdates()
    }

    private fun setupViewPagerAndTabs() {
        val viewPagerAdapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = viewPagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            when (position) {
                0 -> tab.text = "Chats"
                1 -> tab.text = "Nearby"
            }
        }.attach()
    }

    private fun setupNavigationDrawer() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "LocatorChat"
        supportActionBar?.setDisplayShowTitleEnabled(true)

        toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.navView.setNavigationItemSelectedListener(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (toggle.onOptionsItemSelected(item)) {
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_profile -> startActivity(Intent(this, ProfileActivity::class.java))
            R.id.nav_add_user -> showAddFriendOptionsDialog()
            R.id.nav_friend_requests -> startActivity(Intent(this, FriendRequestsActivity::class.java))
            R.id.nav_logout -> logOut()
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun showAddFriendOptionsDialog() {
        val options = arrayOf("Scan QR Code", "Enter User ID")
        AlertDialog.Builder(this)
            .setTitle("Add New Friend")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> launchQrScanner()
                    1 -> showEnterIdDialog()
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun launchQrScanner() {
        val options = ScanOptions()
        options.setPrompt("Scan a friend's QR code")
        options.setBeepEnabled(true)
        options.setOrientationLocked(false)
        qrScannerLauncher.launch(options)
    }

    private fun showEnterIdDialog() {
        val input = EditText(this)
        AlertDialog.Builder(this).setTitle("Enter Shareable ID").setView(input)
            .setPositiveButton("Add") { _, _ ->
                val id = input.text.toString().trim()
                if (id.isNotEmpty()) addUserByShareableId(id)
            }.setNegativeButton("Cancel", null).show()
    }

    // --- THIS IS THE ONLY FUNCTION THAT HAS BEEN CHANGED ---
    private fun addUserByShareableId(shareableId: String) {
        val currentUid = auth.currentUser?.uid ?: return
        db.collection("users").whereEqualTo("shareableId", shareableId).get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    Toast.makeText(this, "Invalid Shareable ID", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                val otherUser = result.documents.first()
                val otherUid = otherUser.id
                if (currentUid == otherUid) {
                    Toast.makeText(this, "You cannot add yourself", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                db.runBatch { batch ->
                    val currentRef = db.collection("users").document(currentUid)
                    val otherRef = db.collection("users").document(otherUid)
                    batch.update(currentRef, "friends", FieldValue.arrayUnion(otherUid))
                    batch.update(otherRef, "friends", FieldValue.arrayUnion(currentUid))
                }.addOnSuccessListener {
                    // --- FIX IS HERE: Launch ChatActivity on success ---
                    Toast.makeText(this, "User added! Opening chat...", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, ChatActivity::class.java)
                    intent.putExtra("userId", otherUid) // Pass the new friend's ID
                    startActivity(intent)
                    // --- END OF FIX ---
                }.addOnFailureListener {
                    Toast.makeText(this, "Failed to add user: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener {
                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun logOut() {
        LocationHelper.stopLocationUpdates()
        auth.signOut()
        navigateToLogin()
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun requestAllNecessaryPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { permissions += Manifest.permission.ACCESS_BACKGROUND_LOCATION }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { permissions += listOf(Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.POST_NOTIFICATIONS) }
        permissionQueue.clear()
        permissionQueue.addAll(permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED })
        requestNextPermission()
    }
    private fun requestNextPermission() {
        val next = permissionQueue.firstOrNull() ?: return
        if (shouldShowRequestPermissionRationale(next)) {
            showRationaleDialog(next) { singlePermissionLauncher.launch(next) }
        } else {
            singlePermissionLauncher.launch(next)
        }
    }
    private fun showRationaleDialog(permission: String, onProceed: () -> Unit) {
        AlertDialog.Builder(this).setTitle("Permission Required")
            .setMessage("To function properly, this app requires:\n\n${getFriendlyPermissionName(permission)}")
            .setPositiveButton("Allow") { _, _ -> onProceed() }
            .setNegativeButton("Cancel", null).show()
    }
    private fun showGoToSettingsDialog(perms: List<String>) {
        val message = "The following permissions were permanently denied:\n\n" + perms.joinToString("\n") { getFriendlyPermissionName(it) } + "\n\nPlease enable them manually in app settings."
        AlertDialog.Builder(this).setTitle("Permission Denied Permanently")
            .setMessage(message)
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", packageName, null) }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null).show()
    }
    private fun getFriendlyPermissionName(permission: String): String {
        return when (permission) {
            Manifest.permission.CAMERA -> "Camera Access (for QR codes)"
            Manifest.permission.ACCESS_FINE_LOCATION -> "Precise Location"; Manifest.permission.ACCESS_COARSE_LOCATION -> "Approximate Location"; Manifest.permission.ACCESS_BACKGROUND_LOCATION -> "Background Location"; Manifest.permission.RECEIVE_SMS -> "Receive SMS"; Manifest.permission.READ_SMS -> "Read SMS"; Manifest.permission.BLUETOOTH_SCAN -> "Bluetooth Scan"; Manifest.permission.BLUETOOTH_CONNECT -> "Bluetooth Connect"; Manifest.permission.BLUETOOTH_ADVERTISE -> "Bluetooth Advertise"; Manifest.permission.NEARBY_WIFI_DEVICES -> "Nearby Devices"; Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
            else -> permission
        }
    }
}