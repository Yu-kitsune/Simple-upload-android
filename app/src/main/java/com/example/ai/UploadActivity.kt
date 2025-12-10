package com.example.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.ai.databinding.ActivityMainBinding
import com.example.ai.ApiConfig
import com.example.ai.databinding.ActivityUploadBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream



class UploadActivity : AppCompatActivity() {
    private lateinit var binding: ActivityUploadBinding
    private var currentImageUri: Uri? = null
    private var currentImageFile: File? = null
    private var isUploading = false

    // Camera launcher
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentImageUri != null) {
            displayImage(currentImageUri!!)
        }
    }

    // Gallery launcher
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            currentImageUri = it
            displayImage(it)
        }
    }

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (cameraGranted) {
            openCamera()
        } else {
            Toast.makeText(this, "Cần cấp quyền Camera", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUploadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtons()
    }

    private fun setupButtons() {
        // Nút mở camera
        binding.btnOpenCamera.setOnClickListener {
            checkCameraPermissionAndOpen()
        }

        // Nút tải ảnh từ thư viện
        binding.btnUpload.setOnClickListener {
            openGallery()
        }

        // Nút xóa ảnh
        binding.btnDeleteImage.setOnClickListener {
            deleteImage()
        }

        // Nút gửi ảnh
        binding.root.findViewById<android.widget.Button>(
            resources.getIdentifier("btnSendImage", "id", packageName)
        )?.setOnClickListener {
            uploadImageToServer()
        }
    }

    private fun checkCameraPermissionAndOpen() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            else -> {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.CAMERA)
                )
            }
        }
    }

    private fun openCamera() {
        val imageFile = File(cacheDir, "camera_${System.currentTimeMillis()}.jpg")
        currentImageFile = imageFile
        currentImageUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            imageFile
        )
        if(currentImageUri == null){
            Toast.makeText(this, "Vui lòng nhập ảnh", Toast.LENGTH_SHORT).show()
        }else{
            cameraLauncher.launch(currentImageUri!!)
        }
    }

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    private fun displayImage(uri: Uri) {
        binding.apply {
            imagePreview.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
            btnDeleteImage.visibility = View.VISIBLE

            Glide.with(this@UploadActivity)
                .load(uri)
                .into(imagePreview)
        }
    }

    private fun deleteImage() {
        binding.apply {
            imagePreview.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
            btnDeleteImage.visibility = View.GONE
            imagePreview.setImageDrawable(null)
        }
        currentImageUri = null
        currentImageFile = null
    }

    private fun uploadImageToServer() {
        if (currentImageUri == null) {
            Toast.makeText(this, "Vui lòng chọn ảnh trước", Toast.LENGTH_SHORT).show()
            return
        }

        if (isUploading) {
            Toast.makeText(this, "Đang tải lên...", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                isUploading = true
                showLoading(true)

                val file = withContext(Dispatchers.IO) {
                    prepareImageFile(currentImageUri!!)
                }

                if (file == null) {
                    Toast.makeText(
                        this@UploadActivity,
                        "Không thể chuẩn bị file ảnh",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val requestBody = file.asRequestBody("image/*".toMediaTypeOrNull())
                val imagePart = MultipartBody.Part.createFormData(
                    "image",
                    file.name,
                    requestBody
                )

                val response = ApiConfig.getApiService().uploadImage(imagePart)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val result = response.body()!!
                        if (result.success) {
                            Toast.makeText(
                                this@UploadActivity,
                                "Tải lên thành công: ${result.message}",
                                Toast.LENGTH_LONG
                            ).show()
                            // Xóa ảnh sau khi upload thành công (tuỳ chọn)
                            // deleteImage()
                        } else {
                            Toast.makeText(
                                this@UploadActivity,
                                "Lỗi: ${result.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            this@UploadActivity,
                            "Lỗi server: ${response.code()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@UploadActivity,
                        "Lỗi: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    e.printStackTrace()
                }
            } finally {
                isUploading = false
                showLoading(false)
            }
        }
    }

    private suspend fun prepareImageFile(uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return@withContext null

            // Đọc ảnh
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // Xoay ảnh nếu cần (cho ảnh từ camera)
            val rotatedBitmap = rotateImageIfRequired(uri, bitmap)

            // Nén và lưu ảnh
            val file = File(cacheDir, "upload_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }

            rotatedBitmap.recycle()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun rotateImageIfRequired(uri: Uri, bitmap: Bitmap): Bitmap {
        try {
            val input = contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(input)
            input.close()

            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            return when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(bitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(bitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(bitmap, 270f)
                else -> bitmap
            }
        } catch (e: Exception) {
            return bitmap
        }
    }

    private fun rotateImage(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun showLoading(show: Boolean) {
        binding.btnOpenCamera.isEnabled = !show
        binding.btnUpload.isEnabled = !show
        binding.root.findViewById<android.widget.Button>(
            resources.getIdentifier("btnSendImage", "id", packageName)
        )?.text = if (show) "Đang tải lên..." else "Gửi ảnh"
    }
}