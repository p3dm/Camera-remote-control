package com.example.camerax.client

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class CameraSocketClient(
    private val serverHost: String,
    private val port: Int,
    private val listener: CameraClientListener,
) {
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader?  = null

    private val executor: ExecutorService = Executors. newFixedThreadPool(3)
    private val scheduledExecutor = Executors.newSingleThreadScheduledExecutor()

    private val isConnected = AtomicBoolean(false)
    private val shouldReconnect = AtomicBoolean(false)

    private val reconnectTask: ScheduledFuture<*>? = null
    companion object {
        private const val TAG = "CameraSocketClient"


    }
    fun connect(){
        if(isConnected.get()){
            Log.d(TAG, "Client already connected")
            return
        }
        executor.execute {
            try{
                val clientSocket = Socket().apply {
                    keepAlive = true
                    tcpNoDelay = true
                    soTimeout = 30000
                    connect(InetSocketAddress(serverHost, port), 10000)
                }
                socket = clientSocket
                writer = PrintWriter(
                    OutputStreamWriter(socket?.getOutputStream(), Charsets.UTF_8),
                    true // auto-flush
                )

                reader = BufferedReader(
                    InputStreamReader(
                        socket?.getInputStream(),
                        Charsets.UTF_8
                    )
                )

                isConnected.set(true)
                shouldReconnect.set(true)
                reconnectTask?.cancel(false)

                Log.i(TAG, "Connected successfully")
                listener.onConnectionChanged(true)

                // Gửi message khởi tạo
                sendCommand("CLIENT_CONNECTED")

                // Start listening for responses (nếu server trả về messages)
                startListening()


            }catch(e: Exception) {
                Log.e(TAG, "Error connecting to server: ${e.message}", e)
                isConnected.set(false)
                listener.onConnectionChanged(false)
                listener.onError("Connection failed: ${e.message}")
            }catch(e: SocketTimeoutException){
                Log.d(TAG, "Connection timed out")
                isConnected.set(false)
                listener.onConnectionChanged(false)
                listener.onError("Connection failed: ${e.message}")
            }
        }
    }

    fun disconnect(){
        if(!isConnected.get()){
            Log.d(TAG, "Không có kết nối nào")
            return
        }
        executor.execute {
            try{
                isConnected.set(false)

                writer?.close()
                reader?.close()
                socket?.close()

                Log.i(TAG, "Ngắt kết nối thành công")
                listener.onConnectionChanged(false)
            }catch (e: Exception){
                Log.e(TAG, "Lỗi ngắt kết nối: ${e.message}", e)
            }
        }
    }

    fun sendCommand(command: String){
        if(!isConnected.get()){
            Log.d(TAG,"Không có kết nối nào")
            return
        }

        executor.execute {
            try{
                writer?.println(command)
                Log. d(TAG, "Command sent: $command")
            } catch(e: Exception){
                Log.e(TAG, "Failed to send command:  $command", e)
                handleConnectionError(e)
            }
        }
    }
    private fun startListening() {
        executor.execute {
            try {
                while (isConnected.get() && !Thread.currentThread().isInterrupted) {
                    val message = reader?.readLine()

                    if (message == null) {
                        Log. w(TAG, "Server closed connection")
                        handleConnectionError(SocketException("Connection closed by server"))
                        break
                    }

                    Log.d(TAG, "Message received: $message")
                    handleServerMessage(message)
                    if(!isConnected.get()){
                        break
                    }
                }
            } catch (e: SocketException) {
                if (isConnected.get()) {
                    Log.e(TAG, "Socket error while listening", e)
                    handleConnectionError(e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error while listening", e)
                handleConnectionError(e)
            } finally{
                disconnect()
            }
        }
    }

    private fun handleServerMessage(message: String){
        when{
            message == "SERVER_SHUTDOWN" -> {
                Log.d(TAG, "Server is shutting down")
                listener.onStatusUpdate("Server has stopped")
                listener.onError("Server stopped - connection closed")
            }
            message.startsWith("Error") ->{
                val error = message.substringAfter("Error")
                listener.onError(error)
            }
            message == "PHOTO_TAKEN" -> {
                listener.onStatusUpdate("Photo captured successfully")
            }
            message == "RECORDING_STARTED" -> {
                listener.onStatusUpdate("Video recording started")
            }
            message == "RECORDING_STOPPED" -> {
                listener.onStatusUpdate("Video recording stopped")
            }
            else -> {
                listener.onMessageReceived(message)
            }
        }
    }
    private fun handleConnectionError(e: Exception){
        if(isConnected.get()) {
            isConnected.set(false)
            listener.onConnectionChanged(false)
            listener.onError("Connection error: ${e.message}")
        }
    }

    //Camera Command
    fun takePhoto() = sendCommand("TAKE_PHOTO")
    fun stopVideoRecording() = sendCommand("RECORD")
    fun startVideoRecording() = sendCommand("RECORD")
    fun switchCamera() = sendCommand("SWITCH_CAMERA")

    fun shutdown() {
        disconnect()
        executor.shutdown()
        scheduledExecutor.shutdown()

        try {
            if (! executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor. shutdownNow()
            }
            if (!scheduledExecutor.awaitTermination(5, TimeUnit. SECONDS)) {
                scheduledExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted while waiting for executor to shutdown", e)
            executor.shutdownNow()
            scheduledExecutor.shutdownNow()
        }
    }

    // Listener interface
    interface CameraClientListener {
        fun onConnectionChanged(isConnected: Boolean)
        fun onMessageReceived(message: String)
        fun onStatusUpdate(status:  String)
        fun onError(error: String)
    }


}