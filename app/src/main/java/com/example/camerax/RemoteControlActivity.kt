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
import com.example.camerax.config.RelayConfig
import com.example.camerax.client.CameraControlSocket

class RemoteControlActivity : AppCompatActivity(), CameraControlSocket.CameraControllerListener{

    private var controllerWebSocket: CameraControlSocket? = null
    // UI Components
    private lateinit var editTextPin: EditText
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
            Log.d(TAG, "Saving image to gallery: ${imageBytes.size} bytes")

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CameraX")
            }

            val uri = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            uri?.let {
                contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(imageBytes)
                    outputStream.flush()
                }

                Log.d(TAG, "Image saved successfully: $uri")

            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image", e)
            runOnUiThread {
                Toast.makeText(
                    this,
                    "❌ Failed to save image: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setControlsEnabled(enabled: Boolean) {

        runOnUiThread {
            buttonTakePhoto.isEnabled = enabled
            buttonRecordToggle.isEnabled = enabled
            buttonSwitchCamera.isEnabled = enabled

            val alpha = if (enabled) 1.0f else 0.5f
            buttonTakePhoto.alpha = alpha
            buttonRecordToggle.alpha = alpha
            buttonSwitchCamera.alpha = alpha

        }
    }

    private fun initViews() {
        editTextPin = findViewById(R.id.editTextServerIp)
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
                disconnect()
            } else {
                connect()
            }
        }

        buttonTakePhoto.setOnClickListener {
            controllerWebSocket?.takePhoto()
            updateStatus("📷 Taking photo...")
        }

        buttonRecordToggle.setOnClickListener {
            if (!isRecording) {
                controllerWebSocket?.startRecording()
                isRecording = true
                buttonRecordToggle.setImageResource(R.drawable.video_button)
                updateStatus("🎥 Recording...")
            } else {
                controllerWebSocket?.stopRecording()
                isRecording = false
                buttonRecordToggle.setImageResource(R.drawable.recording_button)
                updateStatus("⏹ Recording stopped")
            }
        }

        buttonSwitchCamera.setOnClickListener {
            controllerWebSocket?.switchCamera()
            updateStatus("🔄 Switching camera...")
        }

        buttonViewPhoto.setOnClickListener {
            openGallery()
        }
    }
    private fun connect() {
        val pin = editTextPin.text.toString().trim()

        if (pin.length != 4 || !pin.all { it.isDigit() }) {
            Toast.makeText(this, "PIN must be 4 digits", Toast.LENGTH_SHORT).show()
            return
        }

        controllerWebSocket = CameraControlSocket(
            RelayConfig.RELAY_SERVER_URL,
            pin,
            this
        )

        controllerWebSocket?.connect()
        updateStatus("⏳ Connecting to PIN: $pin...")
        editTextPin.isEnabled = false
        buttonConnect.isEnabled = false
    }

    private fun disconnect() {
        controllerWebSocket?.disconnect()
        controllerWebSocket?.shutdown()
        controllerWebSocket = null
        isConnected = false

        editTextPin.isEnabled = true
        buttonConnect.isEnabled = true
        buttonConnect.text = "Connect"
        setControlsEnabled(false)
        updateStatus("Disconnected")
    }

    private fun updateStatus(status: String) {
        runOnUiThread {
            textViewStatus.text = status
        }
    }

    override fun onConnected() {
        runOnUiThread {
            isConnected = true
            buttonConnect.text = "Disconnect"
            buttonConnect.isEnabled = true
            setControlsEnabled(true)
            updateStatus("✅ Connected successfully!")
            Toast.makeText(this, "Connected to camera", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDisconnected() {
        runOnUiThread {
            isConnected = false
            buttonConnect.text = "Connect"
            buttonConnect.isEnabled = true
            setControlsEnabled(false)
            updateStatus("❌ Disconnected")
            Toast.makeText(this, "Disconnected from camera", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onServerDisconnected() {
        runOnUiThread {
            Toast.makeText(this, "Camera server disconnected", Toast.LENGTH_LONG).show()
            disconnect()
        }
    }

    override fun onResponseReceived(response: String) {
        runOnUiThread {
            when (response) {
                "PHOTO_TAKEN" -> {
                    updateStatus("✅ Photo captured! Receiving image...")
                }
                "RECORDING_STARTED" -> {
                    isRecording = true
                    buttonRecordToggle.setImageResource(R.drawable.recording_button)
                    updateStatus("✅ Recording started")
                }
                "RECORDING_STOPPED" -> {
                    isRecording = false
                    buttonRecordToggle.setImageResource(R.drawable.video_button)
                    updateStatus("✅ Recording stopped")
                }
                "CAMERA_SWITCHED" -> {
                    updateStatus("✅ Camera switched")
                }
                else -> {
                    if (response.startsWith("ERROR:")) {
                        val error = response.substringAfter("ERROR:")
                        updateStatus("❌ Error: $error")
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                    } else {
                        updateStatus("📨 $response")
                    }
                }
            }
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
                if (isConnected) {
                    AlertDialog.Builder(this)
                        .setTitle("Disconnect?")
                        .setMessage("Do you want to disconnect from the camera?")
                        .setPositiveButton("Yes") { _, _ ->
                            disconnect()
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

    override fun onImageReceived(imageBytes: ByteArray) {
        Log.d(TAG, "Image received: ${imageBytes.size} bytes")
        runOnUiThread {
            updateStatus("📷 Image received! Saving...")
            saveImageToGallery(imageBytes)
        }
    }

    override fun onConnectionError(error: String) {
        runOnUiThread {
            updateStatus("❌ Error: $error")
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()

            editTextPin.isEnabled = true
            buttonConnect.isEnabled = true
            buttonConnect.text = "Connect"
            setControlsEnabled(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        controllerWebSocket?.shutdown()
    }

    companion object {
        private const val TAG = "RemoteControlActivity"
    }

}