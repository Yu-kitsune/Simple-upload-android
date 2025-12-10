package com.example.ai

import com.google.gson.annotations.SerializedName

data class UploadResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("message")
    val message: String,

    @SerializedName("url")
    val url: String? = null,

    @SerializedName("filename")
    val filename: String? = null
)