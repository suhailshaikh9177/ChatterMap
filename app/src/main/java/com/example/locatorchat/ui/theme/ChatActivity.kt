package com.example.locatorchat.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater // ADD THIS IMPORT
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.locatorchat.R
import com.example.locatorchat.databinding.ActivityChatBinding
import com.example.locatorchat.model.Message
import com.example.locatorchat.model.MessageStatus
import com.example.locatorchat.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.text.SimpleDateFormat
import java.util.*

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: MessageAdapter
    private val messages = mutableListOf<Message>()
    private var chatId: String = ""
    private var currentUserId: String = ""
    private var isResumed = false

    // State holders for the translation flow
    private var messageToTranslate: Message? = null
    private var messageToTranslatePosition: Int = -1
    private var identifiedLanguageCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val receiverId = intent.getStringExtra("userId") ?: return
        currentUserId = auth.currentUser?.uid ?: return
        chatId = if (currentUserId < receiverId) "${currentUserId}_${receiverId}" else "${receiverId}_${currentUserId}"

        setupToolbar()
        fetchReceiverDetails(receiverId)
        setupRecyclerView(currentUserId)
        listenForMessages()
        setupSendMessage(currentUserId, receiverId)
        setupTranslationBar()
    }

    fun requestTranslation(message: Message, position: Int) {
        val originalText = message.originalText ?: message.text
        if (originalText.length < 3) {
            Toast.makeText(this, "Message is too short to translate", Toast.LENGTH_SHORT).show()
            return
        }

        messageToTranslate = message
        messageToTranslatePosition = position

        val languageIdentifier = LanguageIdentification.getClient()
        languageIdentifier.identifyPossibleLanguages(originalText)
            .addOnSuccessListener { identifiedLanguages ->
                val topLanguage = identifiedLanguages.firstOrNull()
                if (topLanguage == null || topLanguage.languageTag == "und") {
                    showLanguageSelectorDialog() // If unidentified, let the user choose
                    return@addOnSuccessListener
                }

                identifiedLanguageCode = topLanguage.languageTag
                val languageName = Locale(identifiedLanguageCode!!).displayLanguage
                val targetLanguage = Locale.getDefault().language

                if (identifiedLanguageCode == targetLanguage) {
                    Toast.makeText(this, "Message is already in $languageName", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                binding.translationBarText.text = "Translate from $languageName?"
                binding.translationBar.visibility = View.VISIBLE
            }
            .addOnFailureListener {
                Toast.makeText(this, "Language identification failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showLanguageSelectorDialog() {
        val dialogBinding = com.example.locatorchat.databinding.DialogLanguageSelectorBinding.inflate(LayoutInflater.from(this))
        val dialogView = dialogBinding.root

        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Source Language")
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .create()

        val allMlKitLanguages = TranslateLanguage.getAllLanguages()
            .map { code -> Language(code, Locale(code).displayName) }
            .sortedBy { it.name }

        val languageAdapter = LanguageAdapter(allMlKitLanguages) { selectedLanguage ->
            performTranslation(selectedLanguage.code)
            dialog.dismiss()
        }

        dialogBinding.languagesRecyclerView.layoutManager = LinearLayoutManager(this)
        dialogBinding.languagesRecyclerView.adapter = languageAdapter

        dialogBinding.searchLanguageInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                languageAdapter.filter(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        dialog.show()
    }

    private fun performTranslation(sourceLanguage: String) {
        val message = messageToTranslate ?: return
        val position = messageToTranslatePosition
        val originalText = message.originalText ?: message.text
        val targetLanguage = Locale.getDefault().language

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguage)
            .setTargetLanguage(targetLanguage)
            .build()
        val translator = Translation.getClient(options)
        val conditions = DownloadConditions.Builder().build()

        Toast.makeText(this, "Preparing translation...", Toast.LENGTH_SHORT).show()
        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                translator.translate(originalText)
                    .addOnSuccessListener { translatedText ->
                        message.originalText = originalText
                        message.text = translatedText
                        adapter.notifyItemChanged(position)
                    }
                    .addOnFailureListener { Toast.makeText(this, "Translation failed", Toast.LENGTH_SHORT).show() }
                    .addOnCompleteListener { translator.close() }
            }
            .addOnFailureListener { Toast.makeText(this, "Failed to download model", Toast.LENGTH_SHORT).show(); translator.close() }
            .addOnCompleteListener { hideTranslationBar() }
    }

    private fun setupTranslationBar() {
        binding.confirmTranslationButton.setOnClickListener {
            identifiedLanguageCode?.let { performTranslation(it) }
        }
        binding.changeLanguageButton.setOnClickListener { showLanguageSelectorDialog() }
    }

    private fun hideTranslationBar() {
        binding.translationBar.visibility = View.GONE
        messageToTranslate = null
        messageToTranslatePosition = -1
        identifiedLanguageCode = null
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        updateMessagesAsSeen()
    }
    override fun onPause() {
        super.onPause()
        isResumed = false
    }
    private fun listenForMessages() {
        db.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) { Log.e("ChatActivity", "Listen failed.", error); return@addSnapshotListener }
                val newMessages = mutableListOf<Message>()
                snapshots?.forEach { doc ->
                    val msg = doc.toObject(Message::class.java)
                    if (msg.receiverId == currentUserId && msg.status == MessageStatus.SENT.name) {
                        if (isResumed) { doc.reference.update("status", MessageStatus.SEEN.name) }
                        else { doc.reference.update("status", MessageStatus.DELIVERED.name) }
                    }
                    newMessages.add(msg)
                }
                adapter.updateMessages(newMessages)
                binding.messageList.scrollToPosition(messages.size - 1)
            }
    }
    private fun updateMessagesAsSeen() {
        val query = db.collection("chats").document(chatId).collection("messages")
            .whereEqualTo("receiverId", currentUserId)
            .whereNotEqualTo("status", MessageStatus.SEEN.name)

        query.get().addOnSuccessListener { snapshot ->
            if (snapshot.isEmpty) return@addOnSuccessListener
            val batch = db.batch()
            snapshot.documents.forEach { document ->
                batch.update(document.reference, "status", MessageStatus.SEEN.name)
            }
            batch.commit()
        }.addOnFailureListener { exception ->
            Log.e("ChatActivity", "Error updating messages to seen", exception)
        }
    }
    private fun setupSendMessage(senderId: String, receiverId: String) {
        binding.sendButton.setOnClickListener {
            val text = binding.messageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                val message = Message(
                    senderId = senderId, receiverId = receiverId, text = text,
                    timestamp = System.currentTimeMillis(), status = MessageStatus.SENT.name
                )
                db.collection("chats").document(chatId).collection("messages").add(message)
                    .addOnSuccessListener { binding.messageInput.setText("") }
                    .addOnFailureListener { Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show() }
            }
        }
    }
    private fun setupToolbar() {
        setSupportActionBar(binding.chatToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.chatToolbar.setNavigationOnClickListener { finish() }
    }
    private fun fetchReceiverDetails(receiverId: String) {
        db.collection("users").document(receiverId).get()
            .addOnSuccessListener { document ->
                val user = document.toObject(User::class.java)
                if (user != null) { supportActionBar?.title = "${user.name} ${user.surname}" }
            }
    }
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.chat_menu, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.clear_chat -> { clearChat(); true }
            android.R.id.home -> { finish(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }
    private fun setupRecyclerView(currentUserId: String) {
        adapter = MessageAdapter(messages, currentUserId)
        binding.messageList.layoutManager = LinearLayoutManager(this)
        binding.messageList.adapter = adapter
    }
    private fun clearChat() {
        db.collection("chats").document(chatId).collection("messages").get()
            .addOnSuccessListener { querySnapshot ->
                val batch = db.batch()
                querySnapshot.documents.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().addOnSuccessListener {
                    Toast.makeText(this, "Chat cleared", Toast.LENGTH_SHORT).show()
                }
            }
    }
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}