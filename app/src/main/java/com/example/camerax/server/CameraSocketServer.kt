package com.example.camerax.server
import android.util.Log
import com.example.camerax.MainActivity
import com.example.camerax.shared.CameraWebSocket

class CameraSocketServer(
    serverUrl: String,
    pin: String,
    private val listener: MainActivity
) : CameraWebSocket(serverUrl, pin, "SERVER") {

    companion object {
        private const val TAG = "CameraServer"
    }

    interface CameraServerListener {
        fun onServerStarted(pin: String)
        fun onClientConnected()
        fun onClientDisconnected()
        fun onCommandReceived(command: String)
        fun onServerError(error: String)
        fun onServerDisconnected()
    }

    override fun onRoomCreated(pin: String) {
        Log.d(TAG, "Server room created with PIN: $pin")
        listener.onServerStarted(pin)
    }

    override fun onConnectedToRoom() {
        // Not used for server
    }

    override fun onClientConnected() {
        Log.d(TAG, "Client connected to server")
        listener.onClientConnected()
    }

    override fun onClientDisconnected() {
        Log.d(TAG, "Client disconnected from server")
        listener.onClientDisconnected()
    }

    override fun onServerDisconnected() {
        listener.onServerDisconnected()
    }

    override fun onCommandReceived(command: String) {
        Log.d(TAG, "Command from client: $command")
        listener.onCommandReceived(command)
    }

    override fun onResponseReceived(message: String) {
        // Responses go to client, not server
    }

    override fun onImageReceived(imageBytes: ByteArray) {
    }

    override fun onDisconnected() {
        Log.d(TAG, "Server disconnected")
        listener.onServerDisconnected()
    }

    override fun onConnectionError(error: String) {
        Log.e(TAG, "Server error: $error")
        listener.onServerError(error)
    }

    // Server-specific methods
    fun notifyPhotoTaken() = sendResponse("PHOTO_TAKEN")
    fun notifyRecordingStarted() = sendResponse("RECORDING_STARTED")
    fun notifyRecordingStopped() = sendResponse("RECORDING_STOPPED")
    fun notifyCameraSwitched() = sendResponse("CAMERA_SWITCHED")
    fun notifyError(error: String) = sendResponse("ERROR: $error")
    fun sendPhotoToClient(imageBytes: ByteArray) {
        Log.d(TAG, "Sending photo to client: ${imageBytes.size} bytes")
        sendImage(imageBytes)
    }
}