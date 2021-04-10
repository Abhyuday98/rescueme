package com.myapplication.rescueme

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class MyService : Service() {
    val TAG = "MyService"
    private val CHANNEL_ID = "ForegroundService Kotlin"

    companion object {
        fun startService(context: Context, message: String) {
            val startIntent = Intent(context, MyService::class.java)
            startIntent.putExtra("inputExtra", message)
            ContextCompat.startForegroundService(context, startIntent)
        }
        fun stopService(context: Context) {
            val stopIntent = Intent(context, MyService::class.java)
            context.stopService(stopIntent)
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //do heavy work on a background thread
        val input = intent?.getStringExtra("inputExtra")
        createNotificationChannel()
        val notificationIntent = Intent(this, SoundClassifierActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
                this,
                0, notificationIntent, 0
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Rescue Me")
                .setContentText(input)
                .setSmallIcon(R.drawable.heroine1)
                .setContentIntent(pendingIntent)
                .build()
        startForeground(1, notification)
        //stopSelf();
        return START_NOT_STICKY
    }
    override fun onBind(intent: Intent): IBinder? {
        return null
    }
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(NotificationManager::class.java)
            manager!!.createNotificationChannel(serviceChannel)
        }
    }

//    @RequiresApi(Build.VERSION_CODES.O)
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        ShowLog("onStartCommand")


////        val runnable = Runnable {
////            // transfer to service start
//////            var soundClassifier: SoundClassifier = SoundClassifier(this)
//////                    .also {
//////                it.lifecycleOwner = this
//////            }
//////            soundClassifier.start()
//////            Log.i("sound", "Hello")
//////
//////            var labelName = soundClassifier.labelList[1] // e.g. "No"
//////            soundClassifier.probabilities.observe(this) { resultMap ->
//////                var probability = resultMap[labelName] // e.g. 0.7
//////                Log.i("sound", "$labelName -> ${probability.toString()}")
//////            }
////            // transfer to service end
////
////            var soundClassifier2 = SoundClassifier2(this)
////            soundClassifier2.start()
////            for (i in 1..10) {
////                Log.i("sound", "Service doing something. ${i.toString()}")
////                ShowLog("Service doing something.$i")
////                Thread.sleep(1000)
////            }
////        }
////        val thread = Thread(runnable)
////        thread.start()
//        return super.onStartCommand(intent, flags, startId)
//    }

//    fun ShowLog(message: String) {
//        Log.d(TAG, message)
//    }

//    override fun getLifecycle(): Lifecycle {
//
//    }

}