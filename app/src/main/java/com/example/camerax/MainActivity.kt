package com.example.camerax

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.camerax.databinding.ActivityMainBinding
import com.example.camerax.server.CameraSocketServer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.example.camerax.server.camera.CameraController
import com.example.camerax.server.permisson.PermissionHelper
import com.example.camerax.server.RemoteCommandHandler

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraController: CameraController
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var remoteCommandHandler: RemoteCommandHandler
    private var cameraSocketServer: CameraSocketServer? = null
    private var isServerRunning = false

    private val activityResultLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        // Handle Permission granted/rejected
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

        setupUI()

        remoteCommandHandler = RemoteCommandHandler(viewBinding,cameraController)

        if (allPermissionsGranted()) {
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
        // Cập nhật text của menu item dựa vào trạng thái server
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
        if(cameraSocketServer == null){
            cameraSocketServer = CameraSocketServer(cameraExecutor){command ->
                runOnUiThread{
                    remoteCommandHandler.handleRemoteCommand(command)
                }
            }
            cameraSocketServer?.start()

            isServerRunning = true
            invalidateOptionsMenu() // Refresh menu

            showServerInfoDialog()
        }
    }

    private fun stopServer(){
        cameraSocketServer?.stop()
        cameraSocketServer = null
        isServerRunning = false
        invalidateOptionsMenu() // Refresh menu
    }

    private fun showServerInfoDialog() {
        val ipAddress = cameraSocketServer?.getLocalIpAddress() ?: "Unknown"

        AlertDialog.Builder(this)
            .setTitle("Server Running")
            .setMessage(
                "Server is now listening on:\n\n" +
                        "IP: $ipAddress\n" +
                        "Port: 2000\n\n" +
                        "Share this IP with clients to connect."
            )
            .setPositiveButton("Copy IP") { _, _ ->
                copyToClipboard(ipAddress)
            }
            .setNegativeButton("OK", null)
            .show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Server IP", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "IP copied to clipboard", Toast.LENGTH_SHORT).show()
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

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startSocketServer(){
        if(cameraSocketServer == null){
            cameraSocketServer = CameraSocketServer(cameraExecutor) { command ->
                runOnUiThread {
                    remoteCommandHandler.handleRemoteCommand(command)
                }
            }
            cameraSocketServer?.start()
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
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


