package com.example.locatorchat.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.locatorchat.databinding.ItemFriendRequestBinding
import com.example.locatorchat.model.FriendRequest

class FriendRequestAdapter(
    private val onAccept: (FriendRequest) -> Unit,
    private val onReject: (FriendRequest) -> Unit
) : RecyclerView.Adapter<FriendRequestAdapter.RequestViewHolder>() {

    private var requests = listOf<FriendRequest>()

    fun setRequests(newRequests: List<FriendRequest>) {
        requests = newRequests
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val binding = ItemFriendRequestBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RequestViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        holder.bind(requests[position])
    }

    override fun getItemCount(): Int = requests.size

    inner class RequestViewHolder(private val binding: ItemFriendRequestBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(request: FriendRequest) {
            binding.senderUsernameText.text = "${request.senderUsername} wants to be your friend."
            binding.acceptButton.setOnClickListener { onAccept(request) }
            binding.rejectButton.setOnClickListener { onReject(request) }
        }
    }
}