package com.example.camerax.shared

import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.compareTo

abstract class CameraWebSocket(
    private val serverUrl: String,
    private val pin: String,
    private val deviceType: String
) {
    private var webSocket : WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private val client = OkHttpClient().newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    companion object {
        private const val TAG = "CameraSocket"
    }
    fun connect(){
        if (isConnected.get()) {
            Log.d(TAG, "Already connected")
            return
        }

        Log.d(TAG, "Connecting to: $serverUrl with PIN: $pin as $deviceType")

        val request = Request.Builder()
            .url(serverUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, " WebSocket opened")
                isConnected.set(true)
                joinRoom()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Message: $text")
                handleMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleMessage(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, " WebSocket closing: $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, " WebSocket closed: $reason")
                isConnected.set(false)
                onDisconnected()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, " WebSocket error: ${t.message}", t)
                isConnected.set(false)
                onConnectionError(t.message ?: "Connection failed")
            }
        })
    }

    private fun joinRoom() {
        val message = JSONObject().apply {
            put("type", "CREATE_ROOM")
            put("pin", pin)
            put("deviceType", deviceType)
        }
        sendRawMessage(message.toString())
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.getString("type")

            when (type) {
                "ROOM_CREATED" -> {
                    Log.d(TAG, "✅ Room created")
                    isConnected.set(true)
                    onRoomCreated(json.optString("pin", pin))
                }

                "CONNECTED" -> {
                    Log.d(TAG, "Connected to room")
                    isConnected.set(true)
                    onConnectedToRoom()
                }

                "CLIENT_CONNECTED" -> {
                    Log.d(TAG, "Client connected")
                    onClientConnected()
                }

                "CLIENT_DISCONNECTED" -> {
                    Log.d(TAG, "⚠Client disconnected")
                    onClientDisconnected()
                }

                "SERVER_DISCONNECTED" -> {
                    Log.d(TAG, "⚠Server disconnected")
                    onServerDisconnected()
                }

                "IMAGE" -> {
                    val base64Image = json.getString("data")
                    val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
                    Log.d(TAG, " Image received: ${imageBytes.size} bytes")
                    onImageReceived(imageBytes)
                }

                "COMMAND" -> {
                    val command = json.getString("command")
                    Log.d(TAG, " Command: $command")
                    onCommandReceived(command)
                }

                "RESPONSE" -> {
                    val responseMsg = json.getString("message")
                    if (responseMsg.length > 100) {
                        try {
                            val imageBytes = Base64.decode(responseMsg, Base64.DEFAULT)
                            Log.d(TAG, "Image received: ${imageBytes.size} bytes")
                            onImageReceived(imageBytes)
                        } catch (e: IllegalArgumentException) {
                            Log.d(TAG, "Response: $responseMsg")
                            onResponseReceived(responseMsg)
                        }
                    } else {
                        // Regular text response
                        Log.d(TAG, "Response: $responseMsg")
                        onResponseReceived(responseMsg)
                    }
                }

                "ERROR" -> {
                    val errorMsg = json.getString("message")
                    Log.e(TAG, " Server error: $errorMsg")
                    onConnectionError(errorMsg)
                }

                else -> {
                    Log.w(TAG, " Unknown message type: $type")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, " Error parsing message: $text", e)
        }
    }

    fun sendCommand(command: String) {
        if (!isConnected.get()) {
            Log.w(TAG, "️ Not connected, cannot send command")
            return
        }

        val message = JSONObject().apply {
            put("type", "COMMAND")
            put("command", command)
        }
        sendRawMessage(message.toString())
        Log.d(TAG, " Command sent: $command")
    }

    fun sendResponse(response: String) {
        if (!isConnected.get()) {
            Log.w(TAG, "⚠️ Not connected, cannot send response")
            return
        }

        val message = JSONObject().apply {
            put("type", "RESPONSE")
            put("message", response)
        }
        sendRawMessage(message.toString())
        Log.d(TAG, " Response sent: $response")
    }

    fun sendImage(imageBytes: ByteArray) {
        if (!isConnected.get()) {
            Log.w(TAG, "️ Not connected, cannot send image")
            return
        }

        try {
            // Encode image to Base64
            val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

            val message = JSONObject().apply {
                put("type", "RESPONSE")
                put("message", base64Image)
            }

            // Send via WebSocket
            webSocket?.send(message.toString())
            Log.d(TAG, " Image sent: ${imageBytes.size} bytes")
        } catch (e: Exception) {
            Log.e(TAG, " Error sending image", e)
        }
    }

    protected fun sendRawMessage(message: String) {
        webSocket?.send(message)
    }
    fun disconnect(){
        if(!isConnected.get()){
            Log.d(TAG, "Không có kết nối nào")
            return
        } else {
            webSocket?.close(1000, "User disconnect")
            isConnected.set(false)
        }

    }
    fun shutdown() {
        disconnect()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    abstract fun onRoomCreated(pin: String)
    abstract fun onConnectedToRoom()
    abstract fun onClientConnected()
    abstract fun onClientDisconnected()
    abstract fun onServerDisconnected()
    abstract fun onCommandReceived(command: String)
    abstract fun onResponseReceived(message: String)
    abstract fun onImageReceived(imageBytes: ByteArray)
    abstract fun onDisconnected()
    abstract fun onConnectionError(error: String)

}