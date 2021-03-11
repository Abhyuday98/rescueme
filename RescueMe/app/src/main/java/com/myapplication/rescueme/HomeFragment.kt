package com.myapplication.rescueme

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.VideoView
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment

class HomeFragment : Fragment() {
    private var delay : Long = 10000 // delay for 10s
    private var VIDEO_PATH = ""
    private lateinit var videoView : VideoView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
//        return super.onCreateView(inflater, container, savedInstanceState)
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

//    private fun hasCameraAudioPermissions() : Boolean {
//        return ActivityCompat.checkSelfPermission(activity!!, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
//                && ActivityCompat.checkSelfPermission(activity!!, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
//
//    }
//
//    private fun requestCameraAudioPermissions() {
//        ActivityCompat.requestPermissions(activity!!, arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA), 222)
//    }
//
//    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        if (requestCode == 222 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//            startVideoRecording()
//        }
//    }
//
//    fun sendHelp(view: View) {
//        if (hasCameraAudioPermissions()) {
////            startVideoRecording()
//            Toast.makeText(activity!!, "has permission!", Toast.LENGTH_SHORT).show()
//        } else {
//            requestCameraAudioPermissions()
//        }
//    }
//
//    private fun startVideoRecording() {
//        VIDEO_PATH = activity!!.filesDir.absolutePath + "/test.mp4"
//
//        val recorder = MediaRecorder()
//
//        // set audio and video source
//        recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
////        recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA)
//
//        // set output format
//        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
//
//        // set audio and video encoder
//        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
////        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.MPEG_4_SP)
//
//        // set output file
//        recorder.setOutputFile(VIDEO_PATH)
//
//        // set preview display
////        recorder.setPreviewDisplay(binding.surfaceView.holder.surface)
////        recorder.setPreviewDisplay(findViewById<SurfaceView>(R.id.surfaceView).holder.surface)
//
//        recorder.prepare()
//        recorder.start()
//
//        val handler = Handler()
//        handler.postDelayed(
//                {
//                    recorder.stop()
//                    recorder.release()
//                    videoView = activity!!.findViewById<VideoView>(R.id.videoView)
//                    videoView.visibility = View.VISIBLE
//                }, delay)
//
//    }
//
//    // for testing.
//    fun clickVideo(view: View) {
//        Toast.makeText(activity!!, "Video has been clicked. Video path is $VIDEO_PATH", Toast.LENGTH_LONG).show()
//        val videoView = activity!!.findViewById<VideoView>(R.id.videoView)
//        videoView.setVideoPath(VIDEO_PATH)
//        videoView.start()
//    }

}