package com.example.locatorchat.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.locatorchat.R
import com.example.locatorchat.databinding.ItemMessageLeftBinding
import com.example.locatorchat.databinding.ItemMessageRightBinding
import com.example.locatorchat.model.Message
import com.example.locatorchat.model.MessageStatus
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private val messages: MutableList<Message>,
    private val currentUid: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_LEFT = 0
    private val TYPE_RIGHT = 1

    fun updateMessages(newMessages: List<Message>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].senderId == currentUid) TYPE_RIGHT else TYPE_LEFT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_RIGHT) {
            val binding =
                ItemMessageRightBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            RightMessageViewHolder(binding)
        } else {
            val binding =
                ItemMessageLeftBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            LeftMessageViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        val time = formatTimestamp(message.timestamp)

        when (holder) {
            is RightMessageViewHolder -> {
                holder.binding.messageText.text = message.text
                holder.binding.messageTime.text = time
                holder.binding.translationIndicator.visibility = if (message.originalText != null) View.VISIBLE else View.GONE
                applyStatusColor(holder, message)
                setupLongPressMenu(holder.itemView, message, position)
            }
            is LeftMessageViewHolder -> {
                holder.binding.messageText.text = message.text
                holder.binding.messageTime.text = time
                holder.binding.translationIndicator.visibility = if (message.originalText != null) View.VISIBLE else View.GONE
                setupLongPressMenu(holder.itemView, message, position)
            }
        }
    }

    private fun applyStatusColor(holder: RightMessageViewHolder, message: Message) {
        val context = holder.itemView.context
        val statusColorRes = when (message.status) {
            MessageStatus.SEEN.name -> R.color.status_seen_bg
            MessageStatus.DELIVERED.name -> R.color.status_delivered_bg
            else -> R.color.status_sent_bg
        }
        val targetColor = ContextCompat.getColor(context, statusColorRes)

        val background = holder.binding.messageContainer.background.mutate() as GradientDrawable
        val startColor = background.color?.defaultColor

        if (startColor != targetColor) {
            holder.colorAnimator?.cancel()
            val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), startColor, targetColor)
            colorAnimation.duration = 500
            colorAnimation.addUpdateListener { animator ->
                background.setColor(animator.animatedValue as Int)
            }
            holder.colorAnimator = colorAnimation
            colorAnimation.start()
        }
    }

    private fun setupLongPressMenu(itemView: View, message: Message, position: Int) {
        itemView.setOnLongClickListener { view ->
            val popup = PopupMenu(view.context, view)
            popup.inflate(R.menu.message_context_menu)

            val isTranslated = message.originalText != null
            popup.menu.findItem(R.id.translate_message).isVisible = !isTranslated
            popup.menu.findItem(R.id.show_original).isVisible = isTranslated

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.copy_text -> {
                        val clipboard = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("message", message.text)
                        clipboard.setPrimaryClip(clip)
                        true
                    }
                    R.id.translate_message -> {
                        // --- FIX IS HERE: Changed "translateMessage" to "requestTranslation" ---
                        (view.context as? ChatActivity)?.requestTranslation(message, position)
                        true
                    }
                    R.id.show_original -> {
                        message.text = message.originalText!!
                        message.originalText = null
                        notifyItemChanged(position)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
            true
        }
    }

    override fun getItemCount(): Int = messages.size

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    class RightMessageViewHolder(val binding: ItemMessageRightBinding) : RecyclerView.ViewHolder(binding.root) {
        var colorAnimator: ValueAnimator? = null
    }

    class LeftMessageViewHolder(val binding: ItemMessageLeftBinding) : RecyclerView.ViewHolder(binding.root)
}