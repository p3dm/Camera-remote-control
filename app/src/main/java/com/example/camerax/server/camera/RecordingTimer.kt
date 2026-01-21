package com.example.camerax.server.camera

import android.os.Handler
import android.os.Looper
import java.util.Locale

class RecordingTimer(private val onTimeUpdate: (String)-> Unit) {

    private var startTime: Long = 0
    private var isRunning = false
    private val handler = Handler(Looper.getMainLooper())

    private val updateTask = object : Runnable{
        override fun run() {
            if(isRunning){
                val elapsed = System.currentTimeMillis() - startTime
                onTimeUpdate(formatTime(elapsed))
                handler.postDelayed(this, 1000 )
            }
        }
    }

    fun start(){
        if(!isRunning){
            startTime = System.currentTimeMillis()
            isRunning = true
            handler.post(updateTask)
        }
    }

    fun stop(){
        isRunning = false
        handler.removeCallbacks(updateTask)
        onTimeUpdate("00:00")
    }

    private fun formatTime(time: Long): String{
        val seconds = (time / 1000) % 60
        val minute = (time/ (1000*60)) %60
        return String.format(Locale.getDefault(), "%02d:%02d", minute, seconds)
    }
}