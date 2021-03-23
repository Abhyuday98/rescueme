package com.myapplication.rescueme

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

class MyService : Service(), LifecycleOwner {
    val TAG = "MyService"
    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ShowLog("onStartCommand")

        val runnable = Runnable {
            // transfer to service start
            var soundClassifier: SoundClassifier = SoundClassifier(this)
                    .also {
                it.lifecycleOwner = this
            }
            soundClassifier.start()
            Log.i("sound", "Hello")

            var labelName = soundClassifier.labelList[1] // e.g. "No"
            soundClassifier.probabilities.observe(this) { resultMap ->
                var probability = resultMap[labelName] // e.g. 0.7
                Log.i("sound", "$labelName -> ${probability.toString()}")
            }
            // transfer to service end


            for (i in 1..10) {
                Log.i("sound", "Service doing something. ${i.toString()}")
                ShowLog("Service doing something.$i")
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

    override fun getLifecycle(): Lifecycle {

//        TODO("Not yet implemented")
    }
}