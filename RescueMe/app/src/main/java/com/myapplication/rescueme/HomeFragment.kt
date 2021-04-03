package com.myapplication.rescueme

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Context.LOCATION_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.Fragment
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener
import com.google.android.gms.location.*
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


class HomeFragment : Fragment(), View.OnClickListener, ConnectionCallbacks, OnConnectionFailedListener {
    private lateinit var v: View

    // video variables
    private var duration = 10000 // duration of video in milliseconds
    private var VIDEO_PATH = ""
    private lateinit var videoView: VideoView
    private val MEDIA_TYPE_IMAGE = 1
    private val MEDIA_TYPE_VIDEO = 2
    private lateinit var recorder: MediaRecorder
    private var recordingStarted = false

    // countdown timer variables
    private lateinit var mCountDownTimer: CountDownTimer
    var mSimpleDateFormat: SimpleDateFormat = SimpleDateFormat("HH:mm:ss")
    private lateinit var timerDisplay: TextView
    private var isTimerRunning = false

    // location variables
    private lateinit var mLocationRequest: LocationRequest
    private  var mGoogleApiClient: GoogleApiClient? = null
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private var latitude = 0.toDouble()
    private var longitude = 0.toDouble()


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

        requestRequiredPermissions()
        checkGPS()

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(activity!!)

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

    private fun hasRequiredPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(activity!!, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(activity!!, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(
            activity!!,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(
            activity!!,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(activity!!, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED

    }

    private fun requestRequiredPermissions() {
        ActivityCompat.requestPermissions(
            activity!!, arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.INTERNET
            ), 222
        )
    }

    private fun checkGPS() {
        val manager = activity!!.getSystemService(LOCATION_SERVICE) as LocationManager?
        if (!manager!!.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            buildAlertMessageNoGps()
        }
    }

    private fun buildAlertMessageNoGps() {
        val builder = AlertDialog.Builder(activity!!)
        builder.setMessage("Your location is off. Please enable it for the app to work properly.")
            .setCancelable(false)
            .setPositiveButton(
                "Ok"
            ) { dialog, id -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            .setNegativeButton(
                "Cancel"
            ) { dialog, id -> dialog.cancel() }
        val alert = builder.create()
        alert.show()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 222 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startHelp()
        }
    }

    // Starts getting location, recording video
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startHelp() {
        if (hasRequiredPermissions()) {
            // gets the user's location
            if (mGoogleApiClient == null) {
                buildGoogleApiClient()
            }
            startCameraSession()
        } else {
            requestRequiredPermissions()
        }
    }

    private fun sendHelp() {
        val victimDetails = getVictimDetails()
        val rescuerDetails = getRescuerDetails()

        val victimName = victimDetails[0]
        val victimNum = victimDetails[1]

        // loop through rescuer hashmap
        for ((id, details) in rescuerDetails) {
            val rescuerName = details[0]
            val rescuerNum = details[1]

            Log.i("rescuer details", "$rescuerName: $rescuerNum")

            writeToDB(latitude, longitude, victimName, victimNum, rescuerName, rescuerNum)
        }

        uploadVideo()

        Toast.makeText(activity!!, "Rescue details successfully sent!", Toast.LENGTH_SHORT).show()
    }

    // get victim name and number in the form of arraylist: [name, number]
    private fun getVictimDetails() : ArrayList<String> {
        if (!fileExist("my_contact.txt")) {
            return ArrayList()
        }

        var name = ""
        var contactNumber = ""

        val scan = Scanner(activity!!.openFileInput("my_contact.txt"))
        while (scan.hasNextLine()) {
            val line = scan.nextLine()
            val pieces = line.split("\t")

            name = pieces[0]
            contactNumber = pieces[1]
        }

        val result = arrayListOf(name, contactNumber)
        return result
    }

    // get rescuer details in the form of hashmap: id -> [name, number]
    private fun getRescuerDetails() : HashMap<String, ArrayList<String>> {
        if (!fileExist("contacts.txt")) {
            return HashMap()
        }

        val result = HashMap<String, ArrayList<String>>()

        val scan = Scanner(activity!!.openFileInput("contacts.txt"))
        while (scan.hasNextLine()) {
            val line = scan.nextLine()
            val pieces = line.split("\t")

            val id = pieces[0]
            val name = pieces[1]
            val contactNumber = pieces[2]

            result[id] = arrayListOf(name, contactNumber)
        }

        return result
    }

    // Sends victim name, victim contact, rescuer name, rescuer contact, lat, lng to Firebase
    private fun writeToDB(
        lat: Double,
        lng: Double,
        victimName: String,
        victimNum: String,
        rescuerName: String,
        rescuerNum: String
    ) {
        val database = Firebase.database
        val myRef = database.getReference("RescueRecords")
        val timeStamp = ServerValue.TIMESTAMP

        var newRecord: HashMap<String, Any> = hashMapOf(
            "Created" to timeStamp,
            "Lat" to lat,
            "Lng" to lng,
            "RescuerName" to rescuerName,
            "RescuerNum" to rescuerNum,
            "VictimName" to victimName,
            "VictimNum" to victimNum
        )

        val victimRef = myRef.child(victimNum)
        val rescuerRef = victimRef.child(rescuerNum)
        rescuerRef.setValue(newRecord)
    }

    private fun uploadVideo() {
        // Create a storage reference from our app
        val storageRef = FirebaseStorage.getInstance().reference

        // Create a reference to victim's number
        val victimNum = getVictimDetails()[1]
        val victimRef = storageRef.child(victimNum)

        var file = Uri.fromFile(File(VIDEO_PATH))
        val uploadTask = victimRef.putFile(file)

        // Register observers to listen for when the download is done or if it fails
        uploadTask.addOnFailureListener {
            // Handle unsuccessful uploads
            Log.i("Upload msg", "Video not sent. ${it.message}")
        }.addOnSuccessListener { taskSnapshot ->
            // taskSnapshot.metadata contains file metadata such as size, content-type, etc.
            // ...
            Log.i("Upload msg", "Video sent successfully!")
        }
    }

    override fun onPause() {
        super.onPause()

        //stop location updates when Activity is no longer active
        mFusedLocationClient.removeLocationUpdates(mLocationCallback)
    }

    @Synchronized
    private fun buildGoogleApiClient() {
        mGoogleApiClient = GoogleApiClient.Builder(activity!!)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .addApi(LocationServices.API)
            .build()
        mGoogleApiClient!!.connect()

    }

    override fun onConnected(bundle: Bundle?) {
        mLocationRequest = LocationRequest()
        mLocationRequest.setInterval(3000) // three second interval
        mLocationRequest.setFastestInterval(3000)
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        if (ContextCompat.checkSelfPermission(activity!!, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            mFusedLocationClient.requestLocationUpdates(
                mLocationRequest,
                mLocationCallback,
                Looper.myLooper()
            )
//            Toast.makeText(activity, "CONNECTED!", Toast.LENGTH_SHORT).show() // for testing
        }
    }

    var mLocationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            Log.i("MapsActivity", "LocationCallBack()")
            for (location in locationResult.getLocations()) {
                latitude = location.latitude
                longitude = location.longitude

                val msg = "Updated Location: ${location.latitude}, ${location.longitude}"
                Log.i(
                    "MapsActivity",
                    "<Latitude, Longitude> " + "<" + location.latitude + "," + location.longitude + ">"
                )
            }
        }
    }

    override fun onConnectionSuspended(i: Int) {

    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {

    }

    /** Create a file Uri for saving an image or video */
    private fun getOutputMediaFileUri(type: Int): Uri {
        return Uri.fromFile(getOutputMediaFile(type))
    }

    /** Create a File for saving an image or video */
    private fun getOutputMediaFile(type: Int): File? {
        val mediaStorageDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "RescueMe"
        )

        // Create the storage directory if it does not exist
        mediaStorageDir.apply {
            if (!exists()) {
                if (!mkdirs()) {
                    Log.d("RescueMe", "failed to create directory")
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
    private fun startCameraSession() {
        val surfaceView = v.findViewById<SurfaceView>(R.id.surfaceView)
        surfaceView.visibility = View.VISIBLE

        val myCameraManager: CameraManager = activity!!.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        if (myCameraManager.cameraIdList.isEmpty()) {
            Toast.makeText(activity!!, "No cameras", Toast.LENGTH_SHORT).show()
            return
        }

        val firstCamera = myCameraManager.cameraIdList[0]

        try {
            myCameraManager.openCamera(firstCamera, object : CameraDevice.StateCallback() {
                override fun onDisconnected(p0: CameraDevice) {}
                override fun onError(p0: CameraDevice, p1: Int) {}

                override fun onOpened(cameraDevice: CameraDevice) {
                    // use the camera
                    startVideoRecording()
                    val cameraCharacteristics = myCameraManager.getCameraCharacteristics(
                        cameraDevice.id
                    )

                    cameraCharacteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]?.let { streamConfigurationMap ->
                        streamConfigurationMap.getOutputSizes(ImageFormat.YUV_420_888)
                            ?.let { yuvSizes ->
                                val previewSize = yuvSizes.last()

                                val displayRotation =
                                    activity!!.windowManager.defaultDisplay.rotation
                                val swappedDimensions = areDimensionsSwapped(
                                    displayRotation,
                                    cameraCharacteristics
                                )
                                // swap width and height if needed
                                val rotatedPreviewWidth =
                                    if (swappedDimensions) previewSize.height else previewSize.width
                                val rotatedPreviewHeight =
                                    if (swappedDimensions) previewSize.width else previewSize.height

                                surfaceView.holder.setFixedSize(
                                    rotatedPreviewWidth,
                                    rotatedPreviewHeight
                                )

                                val previewSurface = surfaceView.holder.surface

                                val captureCallback =
                                    object : CameraCaptureSession.StateCallback() {
                                        override fun onConfigureFailed(session: CameraCaptureSession) {}

                                        override fun onConfigured(session: CameraCaptureSession) {
                                            // session configured
                                            val previewRequestBuilder =
                                                cameraDevice.createCaptureRequest(
                                                    CameraDevice.TEMPLATE_PREVIEW
                                                )
                                                    .apply {
                                                        addTarget(previewSurface)
                                                    }
                                            session.setRepeatingRequest(
                                                previewRequestBuilder.build(),
                                                object : CameraCaptureSession.CaptureCallback() {},
                                                Handler { true }
                                            )
                                        }
                                    }
                            }
                    }
                }
            }, Handler { true })
        } catch (e: SecurityException) {
            Log.i("security exception", e.message)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun areDimensionsSwapped(
        displayRotation: Int,
        cameraCharacteristics: CameraCharacteristics
    ): Boolean {
        var swappedDimensions = false
        when (displayRotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> {
                if (cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) == 90 || cameraCharacteristics.get(
                        CameraCharacteristics.SENSOR_ORIENTATION
                    ) == 270
                ) {
                    swappedDimensions = true
                }
            }
            Surface.ROTATION_90, Surface.ROTATION_270 -> {
                if (cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) == 0 || cameraCharacteristics.get(
                        CameraCharacteristics.SENSOR_ORIENTATION
                    ) == 180
                ) {
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
    private fun startVideoRecording() {
        try {
            VIDEO_PATH = getOutputMediaFile(MEDIA_TYPE_VIDEO).toString()
        } catch (e: Exception) {
            Log.i("VIDEO_PATH error", e.message)
        }

        recorder = MediaRecorder()

        // set audio and video source
        recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
        recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA)

        // set profile
        recorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_480P))

        // set output file
        recorder.setOutputFile(VIDEO_PATH)

        recorder.setMaxDuration(duration)

        recorder.setPreviewDisplay(v.findViewById<SurfaceView>(R.id.surfaceView).holder.surface)
        recorder.prepare()

        try {
            recorder.start()
            recordingStarted = true
            Toast.makeText(activity!!, "Recording video...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.i("recorder start error", e.message)
            recordingStarted = false
            Toast.makeText(activity!!, "Recording did not start.", Toast.LENGTH_SHORT).show()
        }

        // if max duration reached, stop recording.
        recorder.setOnInfoListener(object : MediaRecorder.OnInfoListener {
            override fun onInfo(mr: MediaRecorder?, what: Int, extra: Int) {
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    stopVideoRecording()
                }
            }
        })
    }

    // stops video recording
    private fun stopVideoRecording() {
        recorder.stop()
        recorder.reset()
        recorder.release()
        videoView = activity!!.findViewById<VideoView>(R.id.videoView)
        videoView.visibility = View.VISIBLE
        Toast.makeText(activity!!, "Video has stopped recording.", Toast.LENGTH_SHORT).show()
        recordingStarted = false

        val surfaceView = v.findViewById<SurfaceView>(R.id.surfaceView)
        surfaceView.visibility = View.GONE
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
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun startTimer() {
        startHelp()
        Toast.makeText(activity!!, "Recording video...", Toast.LENGTH_SHORT).show()
        mSimpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        if (v != null) {
            val enterPasscodeEditText = v.findViewById<EditText>(R.id.enterPasscodeEditText)
            enterPasscodeEditText.visibility = View.VISIBLE

            val enterButton = v.findViewById<Button>(R.id.enterButton)
            enterButton.visibility = View.VISIBLE

            timerDisplay = v.findViewById(R.id.timerDisplay);
            timerDisplay.visibility = View.VISIBLE

            mCountDownTimer.start();
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
                // check if recording started before calling stopVideoRecording(), otherwise will crash.
                if (recordingStarted) {
                    stopVideoRecording()
                }
                stopTimer()
                Toast.makeText(
                    activity!!,
                    "Correct passcode entered. Timer has stopped.",
                    Toast.LENGTH_SHORT
                ).show()

                // Delete video if not used.
                val file = File(VIDEO_PATH)
                val deleted = file.delete()
                if (deleted) {
                    Log.i("Delete", "$VIDEO_PATH is successfully deleted")
                }
            } else {
                Toast.makeText(
                    activity!!,
                    "Incorrect passcode entered. Please try again.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            enterPasscodeEditText.text = null
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
    private fun isCorrectPasscode(passcodeString: String): Boolean {
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
    private fun getTime(): Long {
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
    private fun createCountDownTimer(): CountDownTimer {
        val savedTime = getTime()
        val mCountDownTimer: CountDownTimer = object : CountDownTimer(savedTime, 1000) {
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
            // to allow user to close activity
            Toast.makeText(
                activity!!,
                "Rescue details have been sent to your contacts.",
                Toast.LENGTH_SHORT
            ).show()
        }

        builder.setNegativeButton("No") { dialog, which ->
            Toast.makeText(
                activity!!,
                "Please enter the correct passcode to stop the timer.",
                Toast.LENGTH_SHORT
            ).show()
        }

        builder.show()
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onClick(v: View?) {
        when (v!!.id) {
            R.id.helpBtn -> startHelp()
//            R.id.helpBtn -> sendHelp()
            R.id.testBtn -> clickVideo()
            R.id.startTimer -> startTimer()
            R.id.enterButton -> enterPasscode()
        }
    }

}