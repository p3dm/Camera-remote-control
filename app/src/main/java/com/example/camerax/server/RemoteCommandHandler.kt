package com.example.camerax.server

import android.util.Log
import com.example.camerax.databinding.ActivityMainBinding
import com.example.camerax.server.camera.CameraController

class RemoteCommandHandler(
    private val viewBinding: ActivityMainBinding,
    private val cameraController: CameraController
) {

    fun handleRemoteCommand(command: String) {
        when (command.uppercase()) {
            "TAKE_PHOTO" -> {
                cameraController.switchToPhotoMode()
                Log.d(TAG, "Executing takePhoto() via remote command.")
                // gọi trực tiếp logic chụp
                viewBinding.captureButton.performClick()

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