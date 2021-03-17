package com.myapplication.rescueme

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.*


class HomeFragment : Fragment(), View.OnClickListener {
    private lateinit var v : View

    private var delay : Long = 10000 // delay for 10s
    private var VIDEO_PATH = ""
    private lateinit var videoView : VideoView

//    private var mMilliseconds = 0.toLong()
    private var mMilliseconds : Long = 600000

    var mCountDownTimer: CountDownTimer = object : CountDownTimer(mMilliseconds, 1000) {
        override fun onFinish() {
            timerDisplay.setText(mSimpleDateFormat.format(0))
        }

        override fun onTick(millisUntilFinished: Long) {
            timerDisplay.setText(mSimpleDateFormat.format(millisUntilFinished))
        }
    }

    var mSimpleDateFormat: SimpleDateFormat = SimpleDateFormat("HH:mm:ss")
    private lateinit var timerDisplay: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        v = inflater.inflate(R.layout.fragment_home, container, false)

        val helpBtn = v.findViewById<Button>(R.id.helpBtn)
        helpBtn.setOnClickListener(this)

        val testBtn = v.findViewById<Button>(R.id.testBtn)
        testBtn.setOnClickListener(this)

        val startTimerBtn = v.findViewById<Button>(R.id.startTimer)
        startTimerBtn.setOnClickListener(this)

        val endTimerBtn = v.findViewById<Button>(R.id.stopTimer)
        endTimerBtn.setOnClickListener(this)

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

    // temp trigger via button click. Once we have audio detected, then can shift this function.
    private fun startTimer() {
        mSimpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        if (v != null) {
            timerDisplay = v.findViewById(R.id.timerDisplay);
            timerDisplay.visibility = View.VISIBLE

            mMilliseconds = getTime()

            Toast.makeText(activity!!, "$mMilliseconds", Toast.LENGTH_SHORT).show()
            mCountDownTimer.start();
        } else {
            Log.i("View v", "v is null.")
        }
    }

    private fun stopTimer() {
        mSimpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        if (v != null) {
            timerDisplay = v.findViewById(R.id.timerDisplay);
//            mMilliseconds = getTime()
            mCountDownTimer.cancel();
        } else {
            Log.i("View v", "v is null.")
        }
    }

    private fun sendHelp() {
        if (hasCameraAudioPermissions()) {
            startVideoRecording()
//            Toast.makeText(activity!!, "has permission!", Toast.LENGTH_SHORT).show()
        } else {
            requestCameraAudioPermissions()
        }
    }

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

    private fun fileExist(fname: String?): Boolean {
        val file = activity!!.baseContext.getFileStreamPath(fname)
        return file.exists()
    }

    // Get saved time. If for some reason no time is saved, default is 3 minutes.
    private fun getTime() : Long {
        if (fileExist("time.txt")) {
            val scan = Scanner(activity!!.openFileInput("time.txt"))

            while (scan.hasNextLine()) {
                val line = scan.nextLine()
                mMilliseconds = line.toLong()
            }
        } else {
            Toast.makeText(activity!!, "No time setting saved. Default time will be 3 minutes.", Toast.LENGTH_SHORT).show()
            mMilliseconds = 3 * 60000
        }

        return mMilliseconds
    }

    override fun onClick(v: View?) {
        when (v!!.id) {
            R.id.helpBtn -> sendHelp()
            R.id.testBtn -> clickVideo()
            R.id.startTimer -> startTimer()
            R.id.stopTimer -> stopTimer()
        }
    }

}