package com.myapplication.rescueme

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Camera
import android.media.MediaRecorder
import android.opengl.Visibility
import android.os.Bundle
import android.os.Handler
import android.view.MenuItem
import android.view.SurfaceView
import android.view.View
import android.widget.Toast
import android.widget.Toolbar
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.myapplication.rescueme.databinding.ActivityHomeBinding
import java.util.*

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.FloatBuffer
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.ceil
import kotlin.math.sin

class HomeActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var binding:ActivityHomeBinding
    private lateinit var drawer : DrawerLayout

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = binding.toolbar
        setSupportActionBar(toolbar)

        drawer = binding.drawerLayout
        val navigationView : NavigationView = findViewById(R.id.nav_view) //somehow binding cannot find that id nav_view
        navigationView.setNavigationItemSelectedListener(this)

        val toggle : ActionBarDrawerToggle = ActionBarDrawerToggle(this, drawer, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer.addDrawerListener(toggle)
        toggle.syncState()

        // savedInstanceState null condition means load Home fragment on create if no rotation
        // E.g. if contacts fragment is open, screen rotates stays at contacts fragment, will not load home fragment.
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.fragment_container, HomeFragment()).commit()
            navigationView.setCheckedItem(R.id.home)
        }

        requestRequiredPermissions()
        MyService.startService(this, "Listening to you to keep you safe!")

        var soundClassifier2 = SoundClassifier2(this)
        soundClassifier2.start(this)
        var labelName = soundClassifier2.labelList[1] // e.g. "No"
        soundClassifier2.probabilities.observe(this) { resultMap ->
            var probability = resultMap[labelName] // e.g. 0.7
            Log.i("sound", "$labelName -> ${probability.toString()}")
        }

    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun requestRequiredPermissions() {
        ActivityCompat.requestPermissions(
            this, arrayOf(
                Manifest.permission.FOREGROUND_SERVICE,
            ), 222
        )
    }


    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.home -> {
                supportFragmentManager.beginTransaction().replace(R.id.fragment_container, HomeFragment()).commit()
            }
            R.id.contacts_setting -> {
                supportFragmentManager.beginTransaction().replace(R.id.fragment_container, ContactsFragment()).commit()
            }
            R.id.timer_setting -> {
                supportFragmentManager.beginTransaction().replace(R.id.fragment_container, TimerFragment()).commit()
            }
            R.id.passcode_setting -> {
                supportFragmentManager.beginTransaction().replace(R.id.fragment_container, PasscodeFragment()).commit()
            }
            R.id.rescue_tab -> {
                supportFragmentManager.beginTransaction().replace(R.id.fragment_container, RescueFragment()).commit()
            }
            R.id.mycontact_setting -> {
                supportFragmentManager.beginTransaction().replace(R.id.fragment_container, MyContactFragment()).commit()
            }
        }

        drawer.closeDrawer(GravityCompat.START)
        return true
    }

    // so that when user press back, close navigation drawer if open instead of exiting activity first
    override fun onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
    inner class SoundClassifier2(context: Context) {
        private val TAG = "SoundClassifier"
        private val NANOS_IN_MILLIS = 1_000_000.toDouble()

        inner class Options {
            /** Path of the converted model label file, relative to the assets/ directory.  */
            val metadataPath: String = "labels.txt"

            /** Path of the converted .tflite file, relative to the assets/ directory.  */
            val modelPath: String = "sound_classifier.tflite"

            /** The required audio sample rate in Hz.  */
            val sampleRate: Int = 44_100

            /** How many milliseconds to sleep between successive audio sample pulls.  */
            val audioPullPeriod: Long = 50L

            /** Number of warm up runs to do after loading the TFLite model.  */
            val warmupRuns: Int = 3

            /** Number of points in average to reduce noise. */
            val pointsInAverage: Int = 10

            /** Overlap factor of recognition period */
            var overlapFactor: Float = 0.8f

            /** Probability value above which a class is labeled as active (i.e., detected) the display.  */
            var probabilityThreshold: Float = 0.3f
        }

        val options = Options()

        val isRecording: Boolean
            get() = recordingThread?.isAlive == true

        /** As the result of sound classification, this value emits map of probabilities */
        val probabilities: LiveData<Map<String, Float>>
            get() = _probabilities
        private val _probabilities = MutableLiveData<Map<String, Float>>()

        private val recordingBufferLock: ReentrantLock = ReentrantLock()

        var isClosed: Boolean = true
            private set



        /** Overlap factor of recognition period */
        var overlapFactor: Float
            get() = options.overlapFactor
            set(value) {
                options.overlapFactor = value.also {
                    recognitionPeriod = (1000L * (1 - value)).toLong()
                }
            }

        /** Probability value above which a class is labeled as active (i.e., detected) the display.  */
        var probabilityThreshold: Float
            get() = options.probabilityThreshold
            set(value) {
                options.probabilityThreshold = value
            }

        /** Paused by user */
        var isPaused: Boolean = false
            set(value) {
                field = value
//                if (value) stop() else start()
            }

        /** Names of the model's output classes.  */
        lateinit var labelList: List<String>
            private set

        /** How many milliseconds between consecutive model inference calls.  */
        private var recognitionPeriod = (1000L * (1 - overlapFactor)).toLong()

        /** The TFLite interpreter instance.  */
        private lateinit var interpreter: Interpreter

        /** Audio length (in # of PCM samples) required by the TFLite model.  */
        private var modelInputLength = 0

        /** Number of output classes of the TFLite model.  */
        private var modelNumClasses = 0

        /** Used to hold the real-time probabilities predicted by the model for the output classes.  */
        private lateinit var predictionProbs: FloatArray

        /** Latest prediction latency in milliseconds.  */
        private var latestPredictionLatencyMs = 0f

        private var recordingThread: Thread? = null
        private var recognitionThread: Thread? = null

        private var recordingOffset = 0
        private lateinit var recordingBuffer: ShortArray

        /** Buffer that holds audio PCM sample that are fed to the TFLite model for inference.  */
        private lateinit var inputBuffer: FloatBuffer

        init {
            loadLabels(context)
            setupInterpreter(context)
            warmUpModel()
            startAudioRecord(context)
        }

//    override fun onResume(owner: LifecycleOwner) = start()
//
//    override fun onPause(owner: LifecycleOwner) = stop()

        /**
         * Starts sound classification, which triggers running of
         * `recordingThread` and `recognitionThread`.
         */
        fun start(context: Context) {
            if (!isPaused) {
                startAudioRecord(context)
            }
        }

        /**
         * Stops sound classification, which triggers interruption of
         * `recordingThread` and `recognitionThread`.
         */
        fun stop() {
            if (isClosed || !isRecording) return
            recordingThread?.interrupt()
            recognitionThread?.interrupt()

            _probabilities.postValue(labelList.associateWith { 0f })
        }

        fun close() {
            stop()

            if (isClosed) return
            interpreter.close()

            isClosed = true
        }

        /** Retrieve labels from "labels.txt" file */
        private fun loadLabels(context: Context) {
            try {
                val reader = BufferedReader(InputStreamReader(context.assets.open(options.metadataPath)))
                val wordList = mutableListOf<String>()
                reader.useLines { lines ->
                    lines.forEach {
                        wordList.add(it.split(" ").last())
                    }
                }
                labelList = wordList.map { it.toTitleCase() }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to read model ${options.metadataPath}: ${e.message}")
            }
        }

        private fun setupInterpreter(context: Context) {
            interpreter = try {
                val tfliteBuffer = FileUtil.loadMappedFile(context, options.modelPath)
                Log.i(TAG, "Done creating TFLite buffer from ${options.modelPath}")
                Interpreter(tfliteBuffer, Interpreter.Options())
            } catch (e: IOException) {
                Log.e(TAG, "Failed to load TFLite model - ${e.message}")
                return
            }

            // Inspect input and output specs.
            val inputShape = interpreter.getInputTensor(0).shape()
            Log.i(TAG, "TFLite model input shape: ${inputShape.contentToString()}")
            modelInputLength = inputShape[1]

            val outputShape = interpreter.getOutputTensor(0).shape()
            Log.i(TAG, "TFLite output shape: ${outputShape.contentToString()}")
            modelNumClasses = outputShape[1]
            if (modelNumClasses != labelList.size) {
                Log.e(
                    TAG,
                    "Mismatch between metadata number of classes (${labelList.size})" +
                            " and model output length ($modelNumClasses)"
                )
            }
            // Fill the array with NaNs initially.
            predictionProbs = FloatArray(modelNumClasses) { Float.NaN }

            inputBuffer = FloatBuffer.allocate(modelInputLength)
        }

        private fun warmUpModel() {
            generateDummyAudioInput(inputBuffer)
            for (n in 0 until options.warmupRuns) {
                val t0 = SystemClock.elapsedRealtimeNanos()

                // Create input and output buffers.
                val outputBuffer = FloatBuffer.allocate(modelNumClasses)
                inputBuffer.rewind()
                outputBuffer.rewind()
                interpreter.run(inputBuffer, outputBuffer)

                Log.i(
                    TAG,
                    "Switches: Done calling interpreter.run(): %s (%.6f ms)".format(
                        outputBuffer.array().contentToString(),
                        (SystemClock.elapsedRealtimeNanos() - t0) / NANOS_IN_MILLIS
                    )
                )
            }
        }

        private fun generateDummyAudioInput(inputBuffer: FloatBuffer) {
            val twoPiTimesFreq = 2 * Math.PI.toFloat() * 1000f
            for (i in 0 until modelInputLength) {
                val x = i.toFloat() / (modelInputLength - 1)
                inputBuffer.put(i, sin(twoPiTimesFreq * x.toDouble()).toFloat())
            }
        }

        /** Start a thread to pull audio samples in continuously.  */
        @Synchronized
        private fun startAudioRecord(context: Context) {
            if (isRecording) return
            recordingThread = AudioRecordingThread(context).apply {
                start()
            }
            isClosed = false
        }

        /** Start a thread that runs model inference (i.e., recognition) at a regular interval.  */
        private fun startRecognition(context: Context) {
            recognitionThread = RecognitionThread(context).apply {
                start()
            }
        }

        /** Runnable class to run a thread for audio recording */
        private inner class AudioRecordingThread(context: Context) : Thread() {
            var con: Context = context
            override fun run() {

                var bufferSize = AudioRecord.getMinBufferSize(
                    options.sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                    bufferSize = options.sampleRate * 2
                    Log.w(TAG, "bufferSize has error or bad value")
                }
                Log.i(TAG, "bufferSize = $bufferSize")
                val record = AudioRecord(
                    // including MIC, UNPROCESSED, and CAMCORDER.
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    options.sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord failed to initialize")
                    return
                }
                Log.i(TAG, "Successfully initialized AudioRecord")
                val bufferSamples = bufferSize / 2
                val audioBuffer = ShortArray(bufferSamples)
                val recordingBufferSamples =
                    ceil(modelInputLength.toFloat() / bufferSamples.toDouble())
                        .toInt() * bufferSamples
                Log.i(TAG, "recordingBufferSamples = $recordingBufferSamples")
                recordingOffset = 0
                recordingBuffer = ShortArray(recordingBufferSamples)
                record.startRecording()
                Log.i(TAG, "Successfully started AudioRecord recording")

                // Start recognition (model inference) thread.
                startRecognition(con)

                while (!isInterrupted) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(options.audioPullPeriod)
                    } catch (e: InterruptedException) {
                        Log.w(TAG, "Sleep interrupted in audio recording thread.")
                        break
                    }
                    when (record.read(audioBuffer, 0, audioBuffer.size)) {
                        AudioRecord.ERROR_INVALID_OPERATION -> {
                            Log.w(TAG, "AudioRecord.ERROR_INVALID_OPERATION")
                        }
                        AudioRecord.ERROR_BAD_VALUE -> {
                            Log.w(TAG, "AudioRecord.ERROR_BAD_VALUE")
                        }
                        AudioRecord.ERROR_DEAD_OBJECT -> {
                            Log.w(TAG, "AudioRecord.ERROR_DEAD_OBJECT")
                        }
                        AudioRecord.ERROR -> {
                            Log.w(TAG, "AudioRecord.ERROR")
                        }
                        bufferSamples -> {
                            // We apply locks here to avoid two separate threads (the recording and
                            // recognition threads) reading and writing from the recordingBuffer at the same
                            // time, which can cause the recognition thread to read garbled audio snippets.
                            recordingBufferLock.withLock {
                                audioBuffer.copyInto(
                                    recordingBuffer,
                                    recordingOffset,
                                    0,
                                    bufferSamples
                                )
                                recordingOffset =
                                    (recordingOffset + bufferSamples) % recordingBufferSamples
                            }
                        }
                    }
                }
            }
        }

        private inner class RecognitionThread(context: Context) : Thread() {
            var con: Context = context
            @RequiresApi(Build.VERSION_CODES.O)
            override fun run() {
                if (modelInputLength <= 0 || modelNumClasses <= 0) {
                    Log.e(TAG, "Switches: Cannot start recognition because model is unavailable.")
                    return
                }
                val outputBuffer = FloatBuffer.allocate(modelNumClasses)
                while (!isInterrupted) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(recognitionPeriod)
                    } catch (e: InterruptedException) {
                        Log.w(TAG, "Sleep interrupted in recognition thread.")
                        break
                    }
                    var samplesAreAllZero = true

                    recordingBufferLock.withLock {
                        var j = (recordingOffset - modelInputLength) % modelInputLength
                        if (j < 0) {
                            j += modelInputLength
                        }

                        for (i in 0 until modelInputLength) {
                            val s = if (i >= options.pointsInAverage && j >= options.pointsInAverage) {
                                ((j - options.pointsInAverage + 1)..j).map { recordingBuffer[it % modelInputLength] }
                                    .average()
                            } else {
                                recordingBuffer[j % modelInputLength]
                            }
                            j += 1

                            if (samplesAreAllZero && s.toInt() != 0) {
                                samplesAreAllZero = false
                            }
                            inputBuffer.put(i, s.toFloat())
                        }
                    }
                    if (samplesAreAllZero) {
                        Log.w(TAG, "No audio input: All audio samples are zero!")
                        continue
                    }
                    val t0 = SystemClock.elapsedRealtimeNanos()
                    inputBuffer.rewind()
                    outputBuffer.rewind()
                    interpreter.run(inputBuffer, outputBuffer)
                    outputBuffer.rewind()
                    outputBuffer.get(predictionProbs) // Copy data to predictionProbs.

                    val probList = predictionProbs.map {
                        if (it > probabilityThreshold) it else 0f
                    }
//                Log.i("sound", labelList.toString())
//                Log.i("sound", probList.toString())
//                if (probList[1]>90){
                    val detected = probList[1]>0.95
                    if(detected){
//                        var channel_id = "1212"
//                        var channel_name = "1234"
//                        var notification_id = 1223
                        Log.i(
                            "sound",
                            "RescueMe | " + probList[1].toString() + " | " + detected.toString()
                        )
//                        val channel = NotificationChannel(
//                            channel_id,
//                            channel_name,
//                            NotificationManager.IMPORTANCE_HIGH
//                        )
//                        val builder = Notification.Builder(con, channel_id)
//                            .setContentTitle("Danger Alert!")
//                            .setContentText("Click to open!")
//                            .setSmallIcon(R.drawable.heroine1)
//                            .setChannelId(channel_id)
//                        val notification = builder.build()
//
//                        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//                        manager.createNotificationChannel(channel)
////                        manager.notify(notification_id, notification)
//                        Log.i("Notification", "Notif Sent")
//                        val intent = Intent(con, MainActivity::class.java)
//                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
//                        startActivity(intent)

                        val intent = Intent(con, MainActivity::class.java)
//                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // You need this if starting
//                        intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                        //  the activity from a service
                        //  the activity from a service
                        intent.action = Intent.ACTION_MAIN
                        intent.addCategory(Intent.CATEGORY_LAUNCHER)
                        startActivity(intent)
                        close()

//                        val handler = Handler(Looper.getMainLooper())
//                        handler.post(Runnable {
//                            val intent = Intent(SoundClassifierActivity::class.java, RescueActivity::class.java)
//                            this@CurrentActivity.startActivity(intent)
//                        })


//                        Toast.makeText(applicationContext,"this is toast message",Toast.LENGTH_SHORT).show()



                    }
//                }

//                val intent = Intent(this, MyService::class.java)
//                startService(intent)

                    // Do something and then stop thread

                    //
                    _probabilities.postValue(labelList.zip(probList).toMap())
                    latestPredictionLatencyMs = ((SystemClock.elapsedRealtimeNanos() - t0) / 1e6).toFloat()
                }
            }
        }
    }

    private fun String.toTitleCase() =
        splitToSequence("_")
            .map { it.capitalize(Locale.ROOT) }
            .joinToString(" ")
            .trim()

}