package com.example.locatorchat.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.locatorchat.R
import com.example.locatorchat.databinding.FragmentChatsBinding
import com.example.locatorchat.model.User
import com.example.locatorchat.ui.ChatActivity
import com.example.locatorchat.ui.UserAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ChatsFragment : Fragment() {

    private var _binding: FragmentChatsBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: UserAdapter
    private val allFriendsList = mutableListOf<User>()
    private var chatListListener: ListenerRegistration? = null

    // Tell the fragment it has an options menu
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        setupRecyclerView()
    }

    // This method creates the search icon in the toolbar
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.main_menu, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = "Search friends..."

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filter(newText.orEmpty())
                return true
            }
        })
        super.onCreateOptionsMenu(menu, inflater)
    }


    private fun filter(query: String) {
        val filteredList = if (query.isEmpty()) {
            allFriendsList
        } else {
            allFriendsList.filter {
                it.username.lowercase().contains(query.lowercase())
            }
        }
        adapter.submitList(filteredList)
    }

    override fun onResume() {
        super.onResume()
        if (auth.currentUser != null) {
            loadChatUsers()
        }
    }

    override fun onStop() {
        super.onStop()
        chatListListener?.remove()
    }

    private fun loadChatUsers() {
        val currentUid = auth.currentUser?.uid ?: return
        chatListListener?.remove()
        chatListListener = db.collection("users").document(currentUid)
            .addSnapshotListener { document, _ ->
                val friendIds = document?.get("friends") as? List<String> ?: emptyList()
                if (friendIds.isNotEmpty()) {
                    db.collection("users").whereIn(FieldPath.documentId(), friendIds)
                        .addSnapshotListener { querySnapshot, _ ->
                            allFriendsList.clear()
                            querySnapshot?.forEach { doc ->
                                allFriendsList.add(doc.toObject(User::class.java))
                            }
                            filter("") // Display full list initially
                        }
                } else {
                    allFriendsList.clear()
                    adapter.submitList(emptyList())
                }
            }
    }

    private fun setupRecyclerView() {
        binding.chatListRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = UserAdapter { user ->
            val intent = Intent(requireContext(), ChatActivity::class.java)
            intent.putExtra("userId", user.uid)
            startActivity(intent)
        }
        binding.chatListRecyclerView.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}