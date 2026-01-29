package com.example.camerax

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.camerax.config.RelayConfig
import com.example.camerax.databinding.ActivityMainBinding
import com.example.camerax.server.CameraSocketServer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.example.camerax.server.camera.CameraController
import com.example.camerax.server.permisson.PermissionHelper
import com.example.camerax.server.RemoteCommandHandler


class MainActivity : AppCompatActivity(),CameraSocketServer.CameraServerListener {
    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraController: CameraController
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var remoteCommandHandler: RemoteCommandHandler
    private var serverWebSocket: CameraSocketServer? = null
    private var isServerRunning = false
    private var currentPin: String = ""

    private val activityResultLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        var permissionGranted = permissionHelper.handlePermissionResult(permissions)
        if (!permissionGranted) {
            Toast.makeText(
                baseContext,
                "Permission request denied",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            cameraController.startCamera()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        setSupportActionBar(viewBinding.toolbar)

        cameraExecutor = Executors.newSingleThreadExecutor()

        permissionHelper = PermissionHelper(
            activity = this,
            launcher = activityResultLauncher
        )

        cameraController = CameraController(
            activity = this,
            viewBinding = viewBinding,
            cameraExecutor = cameraExecutor
        )

        remoteCommandHandler = RemoteCommandHandler(
            viewBinding = viewBinding,
            cameraController = cameraController,
            onPhotoCaptured = { imageBytes ->
                Log.d(TAG, "Photo captured: ${imageBytes.size} bytes, sending to client...")
                // Gửi ảnh đến client qua WebSocket
                serverWebSocket?.sendPhotoToClient(imageBytes)
                Log.d(TAG, "Photo sent to client")
            })

        setupUI()
        if (permissionHelper.allPermissionsGranted()) {
            cameraController.startCamera()
        } else {
            permissionHelper.requestPermissions()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_server)?.title =
            if (isServerRunning) "Stop Server" else "Become Server"
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_server -> {
                toggleServerMode()
                true
            }
            R.id.action_remote_control -> {
                openRemoteControl()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun toggleServerMode(){
        if(isServerRunning){
            stopServer()
        }else{
            startServer()
        }
    }

    private fun startServer(){
        if (serverWebSocket != null) {
            Toast.makeText(this, "Server already running", Toast.LENGTH_SHORT).show()
            return
        }

        // Generate random 4-digit PIN
        currentPin = 5361.toString()

        serverWebSocket = CameraSocketServer(
            RelayConfig.RELAY_SERVER_URL,
            currentPin,
            this
        )

        serverWebSocket?.connect()
        Toast.makeText(this, "Starting server...", Toast.LENGTH_SHORT).show()
    }

    private fun stopServer(){
        serverWebSocket?.disconnect()
        serverWebSocket?.shutdown()
        serverWebSocket = null
        isServerRunning = false
        invalidateOptionsMenu()
        Toast.makeText(this, "Server stopped", Toast.LENGTH_SHORT).show()
    }

    private fun showServerInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle("Server is running")
            .setMessage(
                "Server is connected!\n\n" +
                        "PIN: $currentPin\n\n" +
                        "Use this PIN with the controller device to connect remotely."
            )
            .setPositiveButton("Copy PIN") { _, _ ->
                copyToClipboard(currentPin)
            }
            .setNegativeButton("OK", null)
            .show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Server PIN", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "PIN copied: $text", Toast.LENGTH_SHORT).show()
    }

    private fun openRemoteControl() {
        val intent = Intent(this, RemoteControlActivity::class.java)
        startActivity(intent)
    }

    private fun setupUI() {
        viewBinding.modeSelectorGroup.setOnCheckedChangeListener { _, _ ->
            cameraController.updateCaptureButtonUI()

        }
        viewBinding.photoViewButton.setOnClickListener {
            cameraController.openGallery()
        }

        viewBinding.captureButton.setOnClickListener {
            cameraController.onCaptureButtonClicked(it)
        }

        viewBinding.flipCameraButton.setOnClickListener {
            cameraController.flipCamera()
        }

        cameraController.updateCaptureButtonUI()
    }

    override fun onServerStarted(pin: String) {
        runOnUiThread {
            isServerRunning = true
            invalidateOptionsMenu()
            showServerInfoDialog()
            Toast.makeText(this, "Server started with PIN: $pin", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onClientConnected() {
        runOnUiThread {
            Toast.makeText(this, "Controller connected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onClientDisconnected() {
        runOnUiThread {
            Toast.makeText(this, "Controller disconnected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCommandReceived(command: String) {
        runOnUiThread {
            handleRemoteCommand(command)
        }
    }

    private fun handleRemoteCommand(command: String) {
        Log.d(TAG, "Handling command: $command")
        remoteCommandHandler.handleRemoteCommand(command)

        // Gửi response về client (không phải ảnh, chỉ là confirmation)
        when (command) {
            "TAKE_PHOTO" -> {
                serverWebSocket?.notifyPhotoTaken()
            }
            "RECORD" -> {
                serverWebSocket?.notifyRecordingStarted()
            }
            "STOP_RECORD" -> {
                serverWebSocket?.notifyRecordingStopped()
            }
            "SWITCH_CAMERA" -> {
                serverWebSocket?.notifyCameraSwitched()
            }
        }
    }

    override fun onServerError(error: String) {
        runOnUiThread {
            Toast.makeText(this, "❌ Error: $error", Toast.LENGTH_LONG).show()
            stopServer()
        }
    }

    override fun onServerDisconnected() {
        runOnUiThread {
            isServerRunning = false
            invalidateOptionsMenu()
            Toast.makeText(this, "Server disconnected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
        cameraExecutor.shutdown()
    }

    companion object {
        var TAG = "MainActivity"
        val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

}


