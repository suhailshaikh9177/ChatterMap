package com.example.locatorchat.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.locatorchat.databinding.ItemLanguageBinding
import java.util.*

// Data class to hold language information
data class Language(val code: String, val name: String)

class LanguageAdapter(
    private var allLanguages: List<Language>,
    private val onLanguageSelected: (Language) -> Unit
) : RecyclerView.Adapter<LanguageAdapter.LanguageViewHolder>() {

    private var filteredLanguages: MutableList<Language> = allLanguages.toMutableList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageViewHolder {
        val binding = ItemLanguageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LanguageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LanguageViewHolder, position: Int) {
        holder.bind(filteredLanguages[position])
    }

    override fun getItemCount(): Int = filteredLanguages.size

    fun filter(query: String) {
        filteredLanguages.clear()
        if (query.isEmpty()) {
            filteredLanguages.addAll(allLanguages)
        } else {
            val lowerCaseQuery = query.lowercase(Locale.getDefault())
            for (language in allLanguages) {
                if (language.name.lowercase(Locale.getDefault()).contains(lowerCaseQuery)) {
                    filteredLanguages.add(language)
                }
            }
        }
        notifyDataSetChanged()
    }

    inner class LanguageViewHolder(private val binding: ItemLanguageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(language: Language) {
            binding.languageNameText.text = language.name
            itemView.setOnClickListener {
                onLanguageSelected(language)
            }
        }
    }
}