package com.example.camerax

import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageAnalysis
import com.example.camerax.client.CameraSocketClient
import kotlin.text.insert


class RemoteControlActivity : AppCompatActivity(), CameraSocketClient.CameraClientListener {

    private var client: CameraSocketClient? = null

    // UI Components
    private lateinit var editTextServerIp: EditText
    private lateinit var textViewStatus: TextView
    private lateinit var buttonConnect: Button
    private lateinit var buttonTakePhoto: ImageButton
    private lateinit var buttonRecordToggle: ImageButton
    private lateinit var buttonSwitchCamera: ImageButton
    private lateinit var buttonViewPhoto: ImageButton

    private var isConnected = false
    private var isRecording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote_control)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Remote Control"

        initViews()
        setupListeners()
        setControlsEnabled(false)
    }

    private fun saveImageToGallery(imageBytes: ByteArray) {
        try {
            val contentResolver = contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CameraX")
            }

            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(imageBytes)
                }
                Toast.makeText(this, "Photo saved to gallery", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("RemoteControl", "Error saving image", e)

        }
    }

    private fun setControlsEnabled(enabled: Boolean) {

        runOnUiThread {
            buttonTakePhoto.isEnabled = enabled
            buttonRecordToggle.isEnabled = enabled
            buttonSwitchCamera.isEnabled = enabled
            buttonViewPhoto.isEnabled = enabled

            val alpha = if (enabled) 1.0f else 0.5f
            buttonTakePhoto.alpha = alpha
            buttonRecordToggle.alpha = alpha
            buttonSwitchCamera.alpha = alpha
            buttonViewPhoto.alpha = alpha
        }
    }

    private fun initViews() {
        editTextServerIp = findViewById(R.id.editTextServerIp)
        textViewStatus = findViewById(R.id.textViewStatus)
        buttonConnect = findViewById(R.id.buttonConnect)
        buttonTakePhoto = findViewById(R.id.buttonTakePhoto)
        buttonRecordToggle = findViewById(R.id.buttonRecordToggle)
        buttonSwitchCamera = findViewById(R.id.buttonSwitchCamera)
        buttonViewPhoto = findViewById(R.id.buttonViewPhoto)
    }

    private fun setupListeners() {
        buttonConnect.setOnClickListener {
            if (isConnected) {
                // Đang kết nối -> Ngắt kết nối
                client?.disconnect()
            } else {
                // Chưa kết nối -> Kết nối
                val serverIp = editTextServerIp.text.toString().trim()
                if (serverIp.isNotEmpty()) {
                    client = CameraSocketClient(serverIp, 2000, this)
                    client?.connect()
                    updateStatus("⏳ Connecting to $serverIp:2000...")
                } else {
                    Toast.makeText(this, "Please enter server IP", Toast.LENGTH_SHORT).show()
                }
            }
        }

        buttonTakePhoto.setOnClickListener {
            client?.takePhoto()
            updateStatus("📷 Taking photo...")
            flashScreen()
        }

        buttonRecordToggle.setOnClickListener {
            if (isRecording) {
                // Đang quay -> Dừng quay
                client?.stopVideoRecording()
                updateStatus("⏹️ Stopping recording...")
                isRecording = false
                // Đổi về icon video_button (màu đỏ tròn)
                buttonRecordToggle.setBackgroundResource(R.drawable.video_button)
                buttonRecordToggle.contentDescription = "Start Recording"
            } else {
                // Không quay -> Bắt đầu quay
                client?.startVideoRecording()
                updateStatus("🎥 Starting recording...")
                isRecording = true
                // Đổi sang icon recording_button (vuông đỏ trong tròn)
                buttonRecordToggle.setBackgroundResource(R.drawable.recording_button)
                buttonRecordToggle.contentDescription = "Stop Recording"
            }
        }

        buttonSwitchCamera.setOnClickListener {
            client?.flipCamera()
            updateStatus("🔄 Switching camera...")
        }

        buttonViewPhoto.setOnClickListener {
            // Mở gallery hoặc hiển thị ảnh vừa chụp
            openGallery()
        }
    }

    private fun flashScreen() {
        // Hiệu ứng flash khi chụp ảnh
        window.decorView.animate()
            .alpha(0.5f)
            .setDuration(100)
            .withEndAction {
                window.decorView.animate().alpha(1f).setDuration(100).start()
            }
            .start()
    }

    private fun updateStatus(status: String) {
        runOnUiThread {
            textViewStatus.text = status
        }
    }

    // CameraClientListener implementation
    override fun onConnectionChanged(isConnected: Boolean) {
        if (this.isConnected == isConnected) return
        runOnUiThread {
            this.isConnected = isConnected

            // Cập nhật nút Connect/Disconnect
            if (isConnected) {
                buttonConnect.text = "Disconnect"
                setControlsEnabled(true)
                editTextServerIp.isEnabled = false
            } else {
                buttonConnect.text = "Connect"
                editTextServerIp.isEnabled = true
                setControlsEnabled(true)
                // Reset recording state when disconnected
                isRecording = false
                buttonRecordToggle.setBackgroundResource(R.drawable.video_button)
                buttonRecordToggle.contentDescription = "Start Recording"
            }

            // Enable/disable control buttons
            val controlButtons = listOf(
                buttonTakePhoto, buttonRecordToggle, buttonSwitchCamera
            )
            controlButtons.forEach { it.isEnabled = isConnected }

            // Cập nhật status
            val status = if (isConnected) "✅ Connected successfully!" else "❌ Disconnected"
            updateStatus(status)

            Toast.makeText(this, status, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.type = "image/*"
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Xử lý back button
                if (isConnected) {
                    AlertDialog.Builder(this)
                        .setTitle("Disconnect?")
                        .setMessage("Do you want to disconnect from server?")
                        .setPositiveButton("Yes") { _, _ ->
                            client?.disconnect()
                            finish()
                        }
                        .setNegativeButton("No", null)
                        .show()
                } else {
                    finish()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onMessageReceived(message: String) {
        updateStatus("📨 $message")
    }

    override fun onStatusUpdate(status: String) {
        updateStatus("ℹ️ $status")
    }

    override fun onError(error: String) {
        runOnUiThread {
            updateStatus("⚠️ Error: $error")
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        }
    }

    override fun onImageReceived(imageBytes: ByteArray) {
        runOnUiThread {
            saveImageToGallery(imageBytes)
            updateStatus("📷 Image received and saved!")
        }
    }

    override fun onDestroy() {
        client?.shutdown()
        super.onDestroy()
    }
}