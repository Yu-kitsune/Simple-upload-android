package com.example.ai

import androidx.recyclerview.widget.RecyclerView
import com.example.ai.databinding.ItemBinding

class ItemHolder(private val binding: ItemBinding) : RecyclerView.ViewHolder(binding.root) {
    fun bind(item: Item) {
        binding.itemImage.setImageBitmap(item.image)
        binding.itemTitle.text = item.title
        binding.itemDescription.text = item.description
        binding.itemMetadata.text = item.time
    }
}