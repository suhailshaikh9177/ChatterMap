package com.example.locatorchat.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.locatorchat.databinding.ItemDiscoveredUserBinding
import com.example.locatorchat.model.DiscoveredUser

class DiscoveredUserAdapter(
    private val onSendRequestClicked: (DiscoveredUser) -> Unit
) : RecyclerView.Adapter<DiscoveredUserAdapter.UserViewHolder>() {

    private val userList = mutableListOf<DiscoveredUser>()

    fun updateUsers(users: List<DiscoveredUser>) {
        userList.clear()
        userList.addAll(users)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemDiscoveredUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(userList[position])
    }

    override fun getItemCount(): Int = userList.size

    inner class UserViewHolder(private val binding: ItemDiscoveredUserBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: DiscoveredUser) {
            binding.usernameText.text = user.username
            binding.shareableIdText.text = "ID: ${user.shareableId}"
            binding.sendRequestButton.setOnClickListener {
                onSendRequestClicked(user)
                binding.sendRequestButton.text = "Sent"
                binding.sendRequestButton.isEnabled = false
            }
        }
    }
}