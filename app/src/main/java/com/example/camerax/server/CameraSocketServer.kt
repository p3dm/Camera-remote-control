package com.example.camerax.server
import java.util.concurrent.ExecutorService
import java.net.ServerSocket
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

class CameraSocketServer (
    private val executor: ExecutorService,
    private val commandHandler: (String) -> Unit
) {
    companion object {
        private const val TAG = "CameraSocketServer"
        private const val SEVER_PORT = 2000
    }
    fun start(){
        executor.execute {
            try {
                val serverSocket = ServerSocket(SEVER_PORT)
                Log.d(TAG, "Server started on port $SEVER_PORT")
                while (!Thread.currentThread().isInterrupted) {
                    try {
                        serverSocket.accept().use { clientSocket ->
                            Log.d(TAG, "Client connected: ${clientSocket.inetAddress}")

                            val reader = BufferedReader(InputStreamReader(clientSocket.inputStream))
                            val command = reader.readLine()

                            if (command != null) {
                                Log.d(TAG, "Yêu cầu nhận được: $command")
                                commandHandler(command)
                            } else {
                                Log.d(TAG, "Yêu cầu không tồn tại")
                            }
                        }
                    } catch (e: Exception) {
                        if (serverSocket.isClosed) {
                            Log.d(TAG, "Server stopped")
                            break
                        }
                        Log.e(TAG, "Error: ", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "SocketServer fail to start, error: ", e)
            }
        }
    }
}

 

