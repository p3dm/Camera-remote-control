package com.example.camerax.server
import java.util.concurrent.ExecutorService
import java.net.ServerSocket
import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class CameraSocketServer (
    private val executor: ExecutorService,
    private val commandHandler: (String) -> Unit,
    private val onPhotoRequested: ((ByteArray) -> Unit) -> Unit
) {
    companion object {
        private const val TAG = "CameraSocketServer"
        private const val SEVER_PORT = 2000
    }

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var currentClientSocket: Socket? = null
    private val hasClient = AtomicBoolean(false)
    private var clientWriter: PrintWriter? = null

    fun start(){
        executor.execute {
            try {
                serverSocket = ServerSocket(SEVER_PORT)
                isRunning = true
                Log.d(TAG, "Server started on port $SEVER_PORT")
                while (isRunning && !Thread.currentThread().isInterrupted) {
                    try {
                        val clientSocket = serverSocket?.accept()

                        if(hasClient.get()){
                            val writer = PrintWriter(
                                BufferedWriter(
                                    OutputStreamWriter(clientSocket?.outputStream)),
                                true)
                            writer.println("SERVER_BUSY")
                        } else {
                            Log.d(TAG, "Client connected: ${clientSocket?.inetAddress}")
                            currentClientSocket = clientSocket
                            hasClient.set(true)
                            handleClient(clientSocket!!)
                        }
                    } catch (e: Exception) {
                        if (!isRunning || serverSocket?.isClosed == true) {
                            Log.d(TAG, "Server stopped")
                            break
                        }
                        Log.e(TAG, "Error: ", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error: SocketServer fail to start ", e)
            }
        }
    }

    fun handleClient(clientSocket: Socket){
        try {
            clientSocket.use { socket ->
                val reader = BufferedReader(InputStreamReader(socket.inputStream))

                 clientWriter = PrintWriter(
                    BufferedWriter(OutputStreamWriter(socket.outputStream)),
                    true
                )
                clientWriter?.println("CONNECTED_TO_SERVER")

                while (isRunning && !clientSocket.isClosed && hasClient.get()) {
                    val command = reader.readLine()
                    if (command == null) {
                        // Client đã ngắt kết nối
                        Log.d(TAG, "Client disconnected: ${clientSocket.inetAddress}")
                        break
                    }else{
                        if (command == "TAKE_PHOTO") {
                            commandHandler(command)
                            onPhotoRequested { imageBytes ->
                                Log.d(TAG, "Image captured, size: ${imageBytes.size} bytes")
                                sendImage(imageBytes)
                                 clientWriter?.println("RECEIVE IMAGE")
                            }
                        } else {
                            commandHandler(command)
                            clientWriter?.println("COMMAND_EXECUTED: $command")
                        }
                    }
                }
            }
        }catch(e: Exception){
            Log.e(TAG, "Error: Handling client", e)
        }finally {
            hasClient.set(false)
            currentClientSocket = null
            clientWriter?.close()
            clientWriter = null
            Log.d(TAG, "Client session ended, ready for new connections")
        }
    }

    fun sendImage(imageBytes: ByteArray){
        if(!hasClient.get() || currentClientSocket == null){
            Log.d(TAG, "No client connected")
            return
        }
        try{
            currentClientSocket?.let{ socket ->
                val outputStream = socket.getOutputStream()

                // Remove the space after IMAGE: to match client parsing
                clientWriter?.println("IMAGE:${imageBytes.size}")
                clientWriter?.flush()

                Thread.sleep(100)

                outputStream.write(imageBytes)
                outputStream.flush()

                Log.d(TAG, "Image sent successfully: ${imageBytes.size} bytes")
            }
        }catch (e: Exception){
            Log.e(TAG, "Error sending image", e)
        }
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress ?: "Unknown"
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "Unable to get IP"
    }

    fun stop(){
        isRunning = false
        currentClientSocket?.let{socket ->
            if(!socket.isClosed){
                try{
                    val writer = PrintWriter(
                        BufferedWriter(OutputStreamWriter(socket.outputStream)),
                        true
                    )
                    writer.println("SERVER_SHUTDOWN")
                    Thread.sleep(100)
                    serverSocket?.close()
                    serverSocket = null

                }catch(e: Exception){
                    e.printStackTrace()
                }

            }
        }
        currentClientSocket = null
        hasClient.set(false)
        serverSocket?.close()
        serverSocket = null
    }
}






