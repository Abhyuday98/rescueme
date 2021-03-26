package com.myapplication.rescueme

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.LifecycleService

class SoundClassifierActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sound_classifier)

        intent = Intent(this, MyService::class.java)
        startService(intent)
        // transfer to service start
//        var soundClassifier: SoundClassifier = SoundClassifier(this).also {
//            it.lifecycleOwner = this
//        }
//        soundClassifier.start()
//        Log.i("sound", "Hello")
//
//        var labelName = soundClassifier.labelList[1] // e.g. "No"
//        soundClassifier.probabilities.observe(this) { resultMap ->
//            var probability = resultMap[labelName] // e.g. 0.7
//            Log.i("sound", "$labelName -> ${probability.toString()}")
//        }

//        var soundClassifier2 = SoundClassifier2(this)
//        soundClassifier2.start()
//        var labelName = soundClassifier2.labelList[1] // e.g. "No"
//        soundClassifier2.probabilities.observe(this) { resultMap ->
//            var probability = resultMap[labelName] // e.g. 0.7
//            Log.i("sound", "$labelName -> ${probability.toString()}")
//        }

        // transfer to service end

    }
}