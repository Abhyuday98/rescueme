package com.myapplication.rescueme

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Camera
import android.media.MediaRecorder
import android.opengl.Visibility
import android.os.Bundle
import android.os.Handler
import android.view.SurfaceView
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.myapplication.rescueme.databinding.ActivityHomeBinding

class HomeActivity : AppCompatActivity() {
    private lateinit var binding:ActivityHomeBinding
    private var delay : Long = 10000 // delay for 10s
    private var VIDEO_PATH = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    private fun hasCameraAudioPermissions() : Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    }

    private fun requestCameraAudioPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA), 222)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 222 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVideoRecording()
        }
    }

    fun sendHelp(view: View) {
        if (hasCameraAudioPermissions()) {
            startVideoRecording()
        } else {
            requestCameraAudioPermissions()
        }
    }

    private fun startVideoRecording() {
        VIDEO_PATH = this.filesDir.absolutePath + "/test.mp4"

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
                binding.videoView.visibility = View.VISIBLE
            }, delay)

    }

    // for testing.
    fun clickVideo(view: View) {
        Toast.makeText(this, "Video has been clicked. Video path is $VIDEO_PATH", Toast.LENGTH_LONG).show()
        binding.videoView.setVideoPath(VIDEO_PATH)
        binding.videoView.start()
    }
}