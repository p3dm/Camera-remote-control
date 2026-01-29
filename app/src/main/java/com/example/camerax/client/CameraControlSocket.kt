package com.example.camerax.client

import android.util.Log
import com.example.camerax.shared.CameraWebSocket

class CameraControlSocket (
    serverUrl: String,
    pin: String,
    private val listener: CameraControllerListener
): CameraWebSocket(serverUrl, pin, "CLIENT"){
    companion object {
        private const val TAG = "CameraControllerWebSocket"
    }

    interface CameraControllerListener {
        fun onConnected()
        fun onDisconnected()
        fun onServerDisconnected()
        fun onResponseReceived(response: String)
        fun onImageReceived(imageBytes: ByteArray)
        fun onConnectionError(error: String)
    }

    override fun onRoomCreated(pin: String) {
        // Not used for controller
    }

    override fun onConnectedToRoom() {
        Log.d(TAG, "Connected to camera server")
        listener.onConnected()
    }

    override fun onClientConnected() {

    }

    override fun onClientDisconnected() {
        listener.onDisconnected()
    }

    override fun onServerDisconnected() {
        Log.d(TAG, "Camera server disconnected")
        listener.onServerDisconnected()
    }

    override fun onCommandReceived(command: String) {
        // Commands come from controller, not to controller
    }

    override fun onResponseReceived(message: String) {
        Log.d(TAG, "Response from server: $message")
        listener.onResponseReceived(message)
    }

    override fun onImageReceived(imageBytes: ByteArray) {
        Log.d(TAG, "Image received from server: ${imageBytes.size} bytes")
        listener.onImageReceived(imageBytes)
    }

    override fun onDisconnected() {
        Log.d(TAG, "Controller disconnected")
        listener.onDisconnected()
    }

    override fun onConnectionError(error: String) {
        Log.e(TAG, "Controller error: $error")
        listener.onConnectionError(error)
    }

    // Controller-specific methods (camera commands)
    fun takePhoto() = sendCommand("TAKE_PHOTO")
    fun startRecording() = sendCommand("RECORD")
    fun stopRecording() = sendCommand("STOP_RECORD")
    fun switchCamera() = sendCommand("FLIP_CAMERA")

}