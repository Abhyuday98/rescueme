package com.myapplication.rescueme

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues.TAG
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*


class HomeFragment : Fragment(), View.OnClickListener {
    private lateinit var v : View

    // video variables
    private var delay : Long = 10000 // delay for 10s
    private var VIDEO_PATH = ""
    private lateinit var videoView : VideoView

    // countdown timer variables
    private lateinit var mCountDownTimer : CountDownTimer
    var mSimpleDateFormat: SimpleDateFormat = SimpleDateFormat("HH:mm:ss")
    private lateinit var timerDisplay: TextView
    private var isTimerRunning = false

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // onbackpressed logic
        activity?.onBackPressedDispatcher?.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isTimerRunning == true) {
                    showAlertDialog()
                }
            }
        })

        v = inflater.inflate(R.layout.fragment_home, container, false)

        startCameraSession()
//        val surfaceReadyCallback = object: SurfaceHolder.Callback {
//            override fun surfaceChanged(p0: SurfaceHolder?, p1: Int, p2: Int, p3: Int) { }
//            override fun surfaceDestroyed(p0: SurfaceHolder?) { }
//
//            override fun surfaceCreated(p0: SurfaceHolder?) {
//                startCameraSession()
//            }
//        }
//
//        val surfaceView = v.findViewById<SurfaceView>(R.id.surfaceView)
//        surfaceView.holder.addCallback(surfaceReadyCallback)

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

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
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

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun sendHelp() {
        if (hasCameraAudioPermissions()) {
            // create & call a function that gets the user's location
            startVideoRecording()
            Toast.makeText(activity!!, "Recording video...", Toast.LENGTH_SHORT).show()
        } else {
            requestCameraAudioPermissions()
        }
    }

    // Figure out how to record video.

    val MEDIA_TYPE_IMAGE = 1
    val MEDIA_TYPE_VIDEO = 2

    /** Create a file Uri for saving an image or video */
    private fun getOutputMediaFileUri(type: Int): Uri {
        return Uri.fromFile(getOutputMediaFile(type))
    }

    /** Create a File for saving an image or video */
    private fun getOutputMediaFile(type: Int): File? {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        val mediaStorageDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "MyCameraApp"
        )
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        mediaStorageDir.apply {
            if (!exists()) {
                if (!mkdirs()) {
                    Log.d("MyCameraApp", "failed to create directory")
                    return null
                }
            }
        }

        // Create a media file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        return when (type) {
            MEDIA_TYPE_IMAGE -> {
                File("${mediaStorageDir.path}${File.separator}IMG_$timeStamp.jpg")
            }
            MEDIA_TYPE_VIDEO -> {
                File("${mediaStorageDir.path}${File.separator}VID_$timeStamp.mp4")
            }
            else -> null
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun areDimensionsSwapped(displayRotation: Int, cameraCharacteristics: CameraCharacteristics): Boolean {
        var swappedDimensions = false
        when (displayRotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> {
                if (cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) == 90 || cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) == 270) {
                    swappedDimensions = true
                }
            }
            Surface.ROTATION_90, Surface.ROTATION_270 -> {
                if (cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) == 0 || cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) == 180) {
                    swappedDimensions = true
                }
            }
            else -> {
                // invalid display rotation
            }
        }
        return swappedDimensions
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startCameraSession() {
        val surfaceView = v.findViewById<SurfaceView>(R.id.surfaceView)

        val myCameraManager : CameraManager = activity!!.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        if (myCameraManager.cameraIdList.isEmpty()) {
            Toast.makeText(activity!!, "No cameras", Toast.LENGTH_SHORT).show()
            return
        }

        val firstCamera = myCameraManager.cameraIdList[0]

        try {
            myCameraManager.openCamera(firstCamera, object: CameraDevice.StateCallback() {
                override fun onDisconnected(p0: CameraDevice) { }
                override fun onError(p0: CameraDevice, p1: Int) { }

                override fun onOpened(cameraDevice: CameraDevice) {
                    // use the camera
                    val cameraCharacteristics =  myCameraManager.getCameraCharacteristics(cameraDevice.id)

                    cameraCharacteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]?.let { streamConfigurationMap ->
                        streamConfigurationMap.getOutputSizes(ImageFormat.YUV_420_888)?.let { yuvSizes ->
                            val previewSize = yuvSizes.last()

                            val displayRotation = activity!!.windowManager.defaultDisplay.rotation
                            val swappedDimensions = areDimensionsSwapped(displayRotation, cameraCharacteristics)
                            // swap width and height if needed
                            val rotatedPreviewWidth = if (swappedDimensions) previewSize.height else previewSize.width
                            val rotatedPreviewHeight = if (swappedDimensions) previewSize.width else previewSize.height

                            surfaceView.holder.setFixedSize(rotatedPreviewWidth, rotatedPreviewHeight)

                            val previewSurface = surfaceView.holder.surface

                            val captureCallback = object : CameraCaptureSession.StateCallback()
                            {
                                override fun onConfigureFailed(session: CameraCaptureSession) {}

                                override fun onConfigured(session: CameraCaptureSession) {
                                    // session configured
                                    val previewRequestBuilder =   cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                                            .apply {
                                                addTarget(previewSurface)
                                            }
                                    session.setRepeatingRequest(
                                            previewRequestBuilder.build(),
                                            object: CameraCaptureSession.CaptureCallback() {},
                                            Handler { true }
                                    )
                                }
                            }

                            cameraDevice.createCaptureSession(mutableListOf(previewSurface), captureCallback, Handler { true })

                            val surfaceReadyCallback = object: SurfaceHolder.Callback {
                                override fun surfaceChanged(p0: SurfaceHolder?, p1: Int, p2: Int, p3: Int) { }
                                override fun surfaceDestroyed(p0: SurfaceHolder?) { }

                                override fun surfaceCreated(p0: SurfaceHolder?) {
                                    startCameraSession()
                                }
                            }

                            surfaceView.holder.addCallback(surfaceReadyCallback)
                        }
                    }
                }
            }, Handler { true })
        } catch (e : SecurityException) {
            Log.i("security exception", e.message)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startVideoRecording() {
//        VIDEO_PATH = activity!!.filesDir.absolutePath + "/test.mp4"
        try {
            VIDEO_PATH = getOutputMediaFile(MEDIA_TYPE_VIDEO).toString()
        } catch (e: Exception) {
            Log.i("VIDEO_PATH error", e.message)
        }

        val recorder = MediaRecorder()

        // set audio and video source
        recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
        recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA)

        // set profile
        recorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_480P))

        // set output format
//        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

        // set audio and video encoder
//        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
//        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.MPEG_4_SP)

        // set output file
        recorder.setOutputFile(VIDEO_PATH)


        recorder.setPreviewDisplay(v.findViewById<SurfaceView>(R.id.surfaceView).holder.surface)
        recorder.prepare()

        var recordingStarted : Boolean

        try {
            recorder.start()
            recordingStarted = true
            Toast.makeText(activity!!, "Recording video...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.i("recorder start error", e.message)
            recordingStarted = false
            Toast.makeText(activity!!, "Recording did not start.", Toast.LENGTH_SHORT).show()
        }

        // check if recording started before calling recorder.stop(), otherwise will crash.
        if (recordingStarted) {
            val handler = Handler()
            handler.postDelayed(
                    {
                        recorder.stop()
                        recorder.reset()
                        recorder.release()
                        videoView = activity!!.findViewById<VideoView>(R.id.videoView)
                        videoView.visibility = View.VISIBLE
                        Toast.makeText(activity!!, "Video has stopped recording.", Toast.LENGTH_SHORT).show()
                    }, delay
            )
        } else {
            Toast.makeText(activity!!, "recorder.stop() not called since recorder.start() not working.", Toast.LENGTH_SHORT).show()
        }

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
            @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            override fun onFinish() {
                isTimerRunning = false
                timerDisplay.setText(mSimpleDateFormat.format(0))
                sendHelp() // call sendHelp() if countdown timer is finished.
            }

            override fun onTick(millisUntilFinished: Long) {
                isTimerRunning = true
                timerDisplay.setText(mSimpleDateFormat.format(millisUntilFinished))
            }
        }

        Log.i("mCountDownTimer", "$mCountDownTimer")

        return mCountDownTimer
    }

    private fun showAlertDialog() {
        val builder = AlertDialog.Builder(activity!!)
        builder.setTitle("Are you sure you want to exit?")
        builder.setMessage("By exiting while the timer is under countdown, we will send your rescue details immediately just to be safe.")

        builder.setPositiveButton("Yes") { dialog, which ->
            // to add send location & video
            Toast.makeText(activity!!, "Rescue details have been sent to your contacts.", Toast.LENGTH_SHORT).show()
        }

        builder.setNegativeButton("No") { dialog, which ->
            Toast.makeText(activity!!, "Please enter the correct passcode to stop the timer.", Toast.LENGTH_SHORT).show()
        }

        builder.show()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onClick(v: View?) {
        when (v!!.id) {
            R.id.helpBtn -> sendHelp()
            R.id.testBtn -> clickVideo()
            R.id.startTimer -> startTimer()
            R.id.enterButton -> enterPasscode()
        }
    }

}