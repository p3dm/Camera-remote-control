package com.example.camerax.server
import java.util.concurrent.ExecutorService
import java.net.ServerSocket
import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PrintStream
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class CameraSocketServer (
    private val executor: ExecutorService,
    private val commandHandler: (String) -> Unit
) {
    companion object {
        private const val TAG = "CameraSocketServer"
        private const val SEVER_PORT = 2000
    }

    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    private var currentClientSocket: Socket? = null
    private val hasClient = AtomicBoolean(false)

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
                val command = reader.readLine()

                val writer = PrintWriter(
                    BufferedWriter(OutputStreamWriter(socket.outputStream)),
                    true
                )
                while (isRunning && !clientSocket.isClosed && hasClient.get()) {
                    val command = reader.readLine()

                    if (command == null) {
                        // Client đã ngắt kết nối
                        Log.d(TAG, "Client disconnected: ${clientSocket.inetAddress}")
                        break
                    }
                }
            }
        }catch(e: Exception){
            Log.e(TAG, "Error: Handling client", e)
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






