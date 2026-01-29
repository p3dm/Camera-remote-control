package com.example.camerax.server

import android.util.Log
import com.example.camerax.databinding.ActivityMainBinding
import com.example.camerax.server.camera.CameraController

class RemoteCommandHandler(
    private val viewBinding: ActivityMainBinding,
    private val cameraController: CameraController,
    private val onPhotoCaptured: ((ByteArray) -> Unit)? = null
) {

    fun handleRemoteCommand(command: String) {
        Log.d(TAG, "handleRemoteCommand called with: $command")
        when (command.uppercase()) {
            "TAKE_PHOTO" -> {
                cameraController.switchToPhotoMode()
                Log.d(TAG, "Executing takePhoto() via remote command.")
                // Chụp ảnh với callback để gửi ảnh về client
                cameraController.takePhoto { imageBytes ->
                    Log.d(TAG, "Photo captured, ${imageBytes.size} bytes, calling onPhotoCaptured")
                    onPhotoCaptured?.invoke(imageBytes)
                    Log.d(TAG, "onPhotoCaptured invoked")
                }
            }

            "RECORD" -> {
                Log.d(TAG, "Executing captureVideo() via remote command.")
                cameraController.switchToVideoMode()
                viewBinding.captureButton.performClick()
            }

            "FLIP_CAMERA" -> {
                Log.d(TAG, "Executing flipCamera() via remote command.")
                cameraController.flipCamera()
            }

            else -> {
                Log.w(TAG, "Unknown command received: $command")
            }
        }
    }

    companion object {
        private const val TAG = "RemoteCommandHandler"
    }
}