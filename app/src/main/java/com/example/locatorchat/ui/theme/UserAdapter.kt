package com.example.locatorchat.ui

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.locatorchat.R
import com.example.locatorchat.databinding.ItemUserBinding
import com.example.locatorchat.model.User

class UserAdapter(
    private val onUserClicked: (User) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    private val userList = mutableListOf<User>()

    fun submitList(newList: List<User>) {
        userList.clear()
        userList.addAll(newList)
        notifyDataSetChanged()
    }

    inner class UserViewHolder(private val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: User) {
            val context = itemView.context
            binding.nameText.text = user.username

            // --- ADDED: Logic to load profile picture ---
            val placeholder = when (user.gender.lowercase()) {
                "male" -> R.drawable.ic_male
                "female" -> R.drawable.ic_female
                else -> R.drawable.ic_person_placeholder
            }

            Glide.with(context)
                .load(user.photoUrl)
                .placeholder(placeholder)
                .error(placeholder)
                .circleCrop()
                .into(binding.profileImageView)
            // --- END OF CHANGE ---

            itemView.setOnClickListener {
                onUserClicked(user)
            }

            when (user.locationMode) {
                "precise", "approximate" -> {
                    binding.locationIcon.setImageResource(R.drawable.ic_location_pin_blue)
                    val clickListener = if (user.locationMode == "precise") {
                        View.OnClickListener {
                            val gmmIntentUri = Uri.parse("geo:${user.latitude},${user.longitude}?q=${user.latitude},${user.longitude}(${user.username})")
                            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                            if (mapIntent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(mapIntent)
                            } else {
                                Toast.makeText(context, "No map application found.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        View.OnClickListener {
                            val locationInfo = "${user.city}, ${user.state}, ${user.country}"
                            Toast.makeText(context, locationInfo, Toast.LENGTH_LONG).show()
                        }
                    }
                    binding.locationIcon.setOnClickListener(clickListener)
                }
                else -> {
                    binding.locationIcon.setImageResource(R.drawable.ic_location_off)
                    binding.locationIcon.setOnClickListener(null)
                }
            }

            binding.menuIcon.setOnClickListener {
                val popup = PopupMenu(context, binding.menuIcon)
                popup.menu.add("View Profile")
                popup.setOnMenuItemClickListener { item ->
                    when (item.title) {
                        "View Profile" -> {
                            val intent = Intent(context, ProfileActivity::class.java).putExtra("userId", user.uid)
                            context.startActivity(intent)
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(userList[position])
    }

    override fun getItemCount(): Int = userList.size
}