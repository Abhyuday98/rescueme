package com.myapplication.rescueme

import android.Manifest
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*


class HomeFragment : Fragment(), View.OnClickListener {
    private lateinit var v : View

    // video variables
//    private var delay : Long = 10000 // delay for 10s
    private var delay : Long = 5000 // delay for 5s, testing purposes.
    private var VIDEO_PATH = ""
    private lateinit var videoView : VideoView

    // countdown timer variables
    private lateinit var mCountDownTimer : CountDownTimer
    var mSimpleDateFormat: SimpleDateFormat = SimpleDateFormat("HH:mm:ss")
    private lateinit var timerDisplay: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        v = inflater.inflate(R.layout.fragment_home, container, false)

        val helpBtn = v.findViewById<Button>(R.id.helpBtn)
        helpBtn.setOnClickListener(this)

        val testBtn = v.findViewById<Button>(R.id.testBtn)
        testBtn.setOnClickListener(this)

        val startTimerBtn = v.findViewById<Button>(R.id.startTimer)
        startTimerBtn.setOnClickListener(this)

        val enterButton = v.findViewById<Button>(R.id.enterButton)
        enterButton.setOnClickListener(this)

        mCountDownTimer = createCountDownTimer()

        return v
    }

    private fun hasCameraAudioPermissions() : Boolean {
        return ActivityCompat.checkSelfPermission(activity!!, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(activity!!, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    }

    private fun requestCameraAudioPermissions() {
        ActivityCompat.requestPermissions(
            activity!!, arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA
            ), 222
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 222 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVideoRecording()
        }
    }

    private fun sendHelp() {
        if (hasCameraAudioPermissions()) {
            // create & call a function that gets the user's location
            startVideoRecording()
//            Toast.makeText(activity!!, "has permission!", Toast.LENGTH_SHORT).show()
        } else {
            requestCameraAudioPermissions()
        }
    }

    // Figure out how to record video.
    private fun startVideoRecording() {
        VIDEO_PATH = activity!!.filesDir.absolutePath + "/test.mp4"

        val recorder = MediaRecorder()

        // set audio and video source
        recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
//        recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA)

        // set output format
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

        // set audio and video encoder
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
//        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.MPEG_4_SP)

        // set output file
        recorder.setOutputFile(VIDEO_PATH)

        // set preview display
//        recorder.setPreviewDisplay(binding.surfaceView.holder.surface)
//        recorder.setPreviewDisplay(findViewById<SurfaceView>(R.id.surfaceView).holder.surface)

        recorder.prepare()
        recorder.start()

        val handler = Handler()
        handler.postDelayed(
            {
                recorder.stop()
                recorder.release()
                videoView = activity!!.findViewById<VideoView>(R.id.videoView)
                videoView.visibility = View.VISIBLE
            }, delay
        )

    }

    // for testing.
    private fun clickVideo() {
        Toast.makeText(
            activity!!,
            "Video has been clicked. Video path is $VIDEO_PATH",
            Toast.LENGTH_LONG
        ).show()
        val videoView = activity!!.findViewById<VideoView>(R.id.videoView)
        videoView.setVideoPath(VIDEO_PATH)
        videoView.start()
    }

    // temp trigger via button click. Once we have audio detected, then can shift this function.
    private fun startTimer() {
        mSimpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        if (v != null) {
            val enterPasscodeEditText = v.findViewById<EditText>(R.id.enterPasscodeEditText)
            enterPasscodeEditText.visibility = View.VISIBLE

            val enterButton = v.findViewById<Button>(R.id.enterButton)
            enterButton.visibility = View.VISIBLE

            timerDisplay = v.findViewById(R.id.timerDisplay);
            timerDisplay.visibility = View.VISIBLE

            mCountDownTimer.start();
            // show enter passcode field and enter button

            mCountDownTimer.onFinish()
        } else {
            Log.i("View v", "v is null.")
        }
    }

    // makes timer pause and disappear.
    private fun stopTimer() {
        mSimpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        if (v != null) {
            val enterPasscodeEditText = v.findViewById<EditText>(R.id.enterPasscodeEditText)
            enterPasscodeEditText.visibility = View.GONE

            val enterButton = v.findViewById<Button>(R.id.enterButton)
            enterButton.visibility = View.GONE

            timerDisplay = v.findViewById(R.id.timerDisplay);
            timerDisplay.visibility = View.GONE

            mCountDownTimer.cancel();
        } else {
            Log.i("View v", "v is null.")
        }
    }

    private fun enterPasscode() {
        if (v != null) {
            val enterPasscodeEditText = v.findViewById<EditText>(R.id.enterPasscodeEditText)
            val enterPasscodeString = enterPasscodeEditText.text.toString()
            if (isCorrectPasscode(enterPasscodeString)) {
                stopTimer()
                Toast.makeText(
                    activity!!,
                    "Correct passcode entered. Timer has stopped.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    activity!!,
                    "Incorrect passcode entered. Please try again.",
                    Toast.LENGTH_SHORT
                ).show()
            }

        } else {
            Log.i("View v", "v is null.")
        }
    }

    private fun String.toMD5(): String {
        val bytes = MessageDigest.getInstance("MD5").digest(this.toByteArray())
        return bytes.toHex()
    }

    private fun ByteArray.toHex(): String {
        return joinToString("") { "%02x".format(it) }
    }

    // compares the entered hashed passcode wih the one saved in file
    private fun isCorrectPasscode(passcodeString: String) : Boolean {
        var originalPasscode = ""
        val hashedOldPasscode = passcodeString.toMD5()

        val scan = Scanner(activity!!.openFileInput("passcode.txt"))
        while (scan.hasNextLine()) {
            originalPasscode = scan.nextLine()
        }

        return hashedOldPasscode == originalPasscode
    }

    private fun fileExist(fname: String?): Boolean {
        val file = activity!!.baseContext.getFileStreamPath(fname)
        return file.exists()
    }

    // Get saved time. If for some reason no time is saved, default is 3 minutes.
    private fun getTime() : Long {
        var mMilliseconds = 0.toLong()

        if (fileExist("time.txt")) {
            val scan = Scanner(activity!!.openFileInput("time.txt"))

            while (scan.hasNextLine()) {
                val line = scan.nextLine()
                mMilliseconds = line.toLong()
            }
        } else {
            mMilliseconds = 3 * 60000
        }

        return mMilliseconds
    }

    // returns CountDownTimer object with the milliseconds read from time.txt
    private fun createCountDownTimer() : CountDownTimer {
        val savedTime = getTime()
        var mCountDownTimer: CountDownTimer = object : CountDownTimer(savedTime, 1000) {
            override fun onFinish() {
                timerDisplay.setText(mSimpleDateFormat.format(0))
                Toast.makeText(activity!!, "Time is up", Toast.LENGTH_SHORT).show()
                sendHelp() // call sendHelp() if countdown timer is finished.
            }

            override fun onTick(millisUntilFinished: Long) {
                timerDisplay.setText(mSimpleDateFormat.format(millisUntilFinished))
            }
        }

        Log.i("mCountDownTimer", "$mCountDownTimer")

        return mCountDownTimer
    }

    override fun onClick(v: View?) {
        when (v!!.id) {
            R.id.helpBtn -> sendHelp()
            R.id.testBtn -> clickVideo()
            R.id.startTimer -> startTimer()
            R.id.enterButton -> enterPasscode()
        }
    }

}