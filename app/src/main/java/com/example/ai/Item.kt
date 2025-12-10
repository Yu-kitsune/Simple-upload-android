package com.example.ai

import android.graphics.Bitmap
data class Item(
    val image: Bitmap,
    val title: String,
    val percent: String,
    val description: String,
    val time: String
)