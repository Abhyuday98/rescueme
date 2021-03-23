package com.myapplication.rescueme

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class MyService : Service() {
    val TAG = "MyService"
    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ShowLog("onStartCommand")

        val runnable = Runnable {
            for (i in 1..10) {
                ShowLog("Service doing something." + i.toString())
                Thread.sleep(1000)
            }
        }
        val thread = Thread(runnable)
        thread.start()
        return super.onStartCommand(intent, flags, startId)
    }

    fun ShowLog(message: String) {
        Log.d(TAG, message)
    }
}