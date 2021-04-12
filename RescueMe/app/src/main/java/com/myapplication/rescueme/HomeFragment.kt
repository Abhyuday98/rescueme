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
import android.media.MediaCodec
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import android.view.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.myapplication.rescueme.Helper.Companion.fileExist
import com.myapplication.rescueme.Helper.Companion.toMD5
import kotlinx.android.synthetic.main.activity_home.*
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class HomeFragment : Fragment(), View.OnClickListener {
    private lateinit var v: View

    // video variables
    private var duration = 30000 // duration of video in milliseconds
    private var VIDEO_PATH = ""
    private val MEDIA_TYPE_IMAGE = 1
    private val MEDIA_TYPE_VIDEO = 2
    private lateinit var recorder: MediaRecorder
    private var recordingStarted = false

    // countdown timer variables
    private lateinit var mCountDownTimer: CountDownTimer
    var mSimpleDateFormat: SimpleDateFormat = SimpleDateFormat("HH:mm:ss")
    private lateinit var timerDisplay: TextView
    private var isTimerRunning = false


    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        activity!!.title = "Rescue Me"

        activity?.onBackPressedDispatcher?.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isTimerRunning == true) {
                    showAlertDialog()
                }
            }
        })

        requestRequiredPermissions()
        checkGPS()

        v = inflater.inflate(R.layout.fragment_home, container, false)

        val helpBtn = v.findViewById<Button>(R.id.helpBtn)
        helpBtn.setOnClickListener(this)

        val rescuedBtn = v.findViewById<Button>(R.id.rescuedBtn)
        rescuedBtn.setOnClickListener(this)

        val enterButton = v.findViewById<Button>(R.id.enterButton)
        enterButton.setOnClickListener(this)

        mCountDownTimer = createCountDownTimer()

        checkRescue()
        val activity = activity!!
        val action = activity.intent.action
//        Log.i("danger", action)

        if (action != null) {
            if (action == "danger") {
                Log.i("danger", "yes")
                MyService.stopService(activity)

                startTimer()
            } else {
                Log.i("danger", "no")
            }
        } else {
            Log.i("danger", "action is null")
        }

        return v
    }

    private fun displayRescuedFeatures() {
        val rescuePasscodeEditText = v.findViewById<EditText>(R.id.rescuePasscodeEditText)
        rescuePasscodeEditText.visibility = View.VISIBLE

        val rescuedBtn = v.findViewById<Button>(R.id.rescuedBtn)
        rescuedBtn.visibility = View.VISIBLE

        val helpBtn = v.findViewById<Button>(R.id.helpBtn)
        helpBtn.visibility = View.GONE

        val infoTv = v.findViewById<TextView>(R.id.infoTv)
        infoTv.text = "Have you been rescued?"
    }

    private fun checkRescue() {
        val database = Firebase.database
        val myRef = database.getReference("RescueRecords")

        //read records
        myRef.get().addOnSuccessListener {
            var userNumber = ""

            var data = it.value
            val scan = Scanner(activity!!.openFileInput("my_contact.txt"))
            while(scan.hasNextLine()) {
                var userDet = scan.nextLine().split("\t")
                userNumber = userDet[1]
                Log.i("userNumber", userNumber)
            }

            Log.i("firebase", "Got value ${it.value}")
            if (data != null && data is HashMap<*,*>) {
                for (victim in data.keys) {
                    Log.i("userNumber", "victim: $victim")
                    Log.i("comparison", "victim:<$victim>, user:<$userNumber>")
                    if (victim == userNumber) {
                        displayRescuedFeatures()
                        break
                    }
                }
            } else {
                Toast.makeText(activity, "Error getting data this", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener{
            Log.e("firebase", "Error getting data", it)
        }
    }

    // verify rescue
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun verifyRescue() {
        val rescuePasscodeEditText = v.findViewById<EditText>(R.id.rescuePasscodeEditText)
        val rescuePasscodeString = rescuePasscodeEditText.text.toString()
        if (isCorrectPasscode(rescuePasscodeString)) {
            Toast.makeText(
                activity!!,
                "You have been rescued!",
                Toast.LENGTH_SHORT
            ).show()
            endRescue()
        } else {
            Toast.makeText(
                activity!!,
                "Incorrect passcode entered. Please try again.",
                Toast.LENGTH_SHORT
            ).show()
        }
        rescuePasscodeEditText.text = null
    }

    // stop rescue process
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun endRescue() {
        val victimNum = getVictimDetails()[1]

        val rescuePasscodeEditText = v.findViewById<EditText>(R.id.rescuePasscodeEditText)
        rescuePasscodeEditText.visibility = View.GONE

        val rescuedBtn = v.findViewById<Button>(R.id.rescuedBtn)
        rescuedBtn.visibility = View.GONE

        val helpBtn = v.findViewById<Button>(R.id.helpBtn)
        helpBtn.visibility = View.VISIBLE

        val infoTv = v.findViewById<TextView>(R.id.infoTv)
        infoTv.text = "If you need immediate help, click the Help button."

        // stop location
        LocationService.stopService(activity!!) // Figure out how to stop?

        // delete victim details from firebase
        val database = Firebase.database
        val victimRefDatabase = database.getReference("RescueRecords").child(victimNum)
        victimRefDatabase.removeValue()

        // delete victim's video from cloud storage
        val storageRef = FirebaseStorage.getInstance().reference
        val victimRefStorage = storageRef.child(victimNum)
        victimRefStorage.delete().addOnSuccessListener {
            Log.i("Delete video", "video deleted successfully")
        }.addOnFailureListener {
            Log.i("Delete video error", "${it.message}")
        }

//        val it = Intent(activity!!, MainActivity::class.java)
//        it.action = "start"
//        startActivity(it)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
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
                && ActivityCompat.checkSelfPermission(
            activity!!,
            Manifest.permission.FOREGROUND_SERVICE
        ) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(
            activity!!,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(activity!!, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun requestRequiredPermissions() {
        ActivityCompat.requestPermissions(
            activity!!, arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.INTERNET,
                Manifest.permission.FOREGROUND_SERVICE,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                Manifest.permission.SEND_SMS
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

    @RequiresApi(Build.VERSION_CODES.Q)
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
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startHelp() {
        Log.i("hasRequiredPermissions", "${hasRequiredPermissions()}")
        if (hasRequiredPermissions()) {
            startVideoRecording()
            Log.i("video", "video started")
        } else {
            requestRequiredPermissions()
            Log.i("video", "video failed to start")
        }
    }

    private fun sendHelp() {
        uploadVideo()

        // send to rescue contacts (commented off to prevent sending sms when testing)
        val rescuerDetails = getRescuerDetails()
//        for ((id, details) in rescuerDetails) {
//            val rescuerNum = details[1]
//            sendSMS(rescuerNum, "Please rescue me!")
//        }

        // goes to LocationService to update details and location continuously.
        LocationService.startService(activity!!, "Retrieving location...")

        Toast.makeText(activity!!, "Rescue details successfully sent!", Toast.LENGTH_SHORT).show()
    }

    private fun sendSMS(phoneNumber: String, message: String) {
        Log.i("SMS", "Sending SMS")
        val smsManager = SmsManager.getDefault() as SmsManager
        smsManager.sendTextMessage(phoneNumber, null, message, null, null)
    }

    // get victim name and number in the form of arraylist: [name, number]
    private fun getVictimDetails() : ArrayList<String> {
        if (!fileExist(activity!!.baseContext, "my_contact.txt")) {
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
        if (!fileExist(activity!!.baseContext, "contacts.txt")) {
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
            Log.i("Upload msg", "Video sent successfully!")
        }
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
    private fun startVideoRecording() {
        val surfaceView = v.findViewById<SurfaceView>(R.id.surfaceView)
        surfaceView.visibility = View.VISIBLE

        val myCameraManager: CameraManager = activity!!.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        if (myCameraManager.cameraIdList.isEmpty()) {
            Log.i("cameraIdList", "cameraIdList is empty.")
            return
        }

        val firstCamera = myCameraManager.cameraIdList[0]

        try {
            myCameraManager.openCamera(firstCamera, object : CameraDevice.StateCallback() {
                override fun onDisconnected(p0: CameraDevice) {}
                override fun onError(p0: CameraDevice, p1: Int) {}

                override fun onOpened(cameraDevice: CameraDevice) {
                    val cameraCharacteristics = myCameraManager.getCameraCharacteristics(
                        cameraDevice.id
                    )

                    val sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    Log.i("orientation sensor", "$sensorOrientation")

                    cameraCharacteristics[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]?.let { streamConfigurationMap ->
                        streamConfigurationMap.getOutputSizes(ImageFormat.YUV_420_888)
                            ?.let {
                                try {
                                    VIDEO_PATH = getOutputMediaFile(MEDIA_TYPE_VIDEO).toString()
                                } catch (e: Exception) {
                                    Log.i("VIDEO_PATH error", e.message)
                                }

                                val mSurface = surfaceView.holder.surface
                                val previewSurface = MediaCodec.createPersistentInputSurface()

                                recorder = MediaRecorder()

                                // set audio and video source
                                recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
                                recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)

                                // set profile
                                recorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_720P))

                                // set output file
                                recorder.setOutputFile(VIDEO_PATH)

                                recorder.setMaxDuration(duration)

                                recorder.setInputSurface(previewSurface)
                                recorder.setOrientationHint(90)

                                recorder.prepare()

                                // if max duration reached, stop recording.
                                recorder.setOnInfoListener(object : MediaRecorder.OnInfoListener {
                                    override fun onInfo(mr: MediaRecorder?, what: Int, extra: Int) {
                                        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                                            stopVideoRecording()
                                        }
                                    }
                                })

                                val captureCallback =
                                    object : CameraCaptureSession.StateCallback() {
                                        override fun onConfigureFailed(session: CameraCaptureSession) {}

                                        override fun onConfigured(session: CameraCaptureSession) {
                                            try {
                                                recorder.start()
                                                recordingStarted = true
                                                Toast.makeText(activity!!, "Recording video...", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                Log.i("recorder start error", e.message)
                                                recordingStarted = false
                                            }

                                            val previewRequestBuilder =
                                                cameraDevice.createCaptureRequest(
                                                    CameraDevice.TEMPLATE_PREVIEW
                                                )
                                                    .apply {
                                                        addTarget(previewSurface)
                                                        addTarget(mSurface)
                                                    }
                                            session.setRepeatingRequest(
                                                previewRequestBuilder.build(),
                                                object : CameraCaptureSession.CaptureCallback() {},
                                                Handler { true }
                                            )
                                        }
                                    }

                                cameraDevice.createCaptureSession(mutableListOf(mSurface, previewSurface), captureCallback, Handler { true })
                            }
                    }
                }
            }, Handler { true })
        } catch (e: SecurityException) {
            Log.i("security exception", e.message)
        }
    }

    // stops video recording
    private fun stopVideoRecording() {
        try {
            recorder.stop()
            Toast.makeText(activity!!, "Video has stopped recording.", Toast.LENGTH_SHORT).show()
        } catch (e : java.lang.Exception){
            Log.i("recorder error", e.message)
        }

        recorder.reset()
        recorder.release()
        recordingStarted = false

        val surfaceView = v.findViewById<SurfaceView>(R.id.surfaceView)
        surfaceView.visibility = View.GONE
    }

    // temp trigger via button click. Once we have audio detected, then can shift this function.
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startTimer() {
        startHelp()
        mSimpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        val infoTv = v.findViewById<TextView>(R.id.infoTv)
        infoTv.text = "You have asked for help. If this is a false alarm, please enter the correct passcode."

        val helpBtn = v.findViewById<Button>(R.id.helpBtn)
        helpBtn.visibility = View.GONE

        val enterPasscodeEditText = v.findViewById<EditText>(R.id.enterPasscodeEditText)
        enterPasscodeEditText.visibility = View.VISIBLE

        val enterButton = v.findViewById<Button>(R.id.enterButton)
        enterButton.visibility = View.VISIBLE

        timerDisplay = v.findViewById(R.id.timerDisplay);
        timerDisplay.visibility = View.VISIBLE

        mCountDownTimer.start();
    }

    // makes timer pause and disappear.
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun stopTimer() {
        isTimerRunning = false
        val mDrawerLayout = activity!!.drawer_layout
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);

        mSimpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        val infoTv = v.findViewById<TextView>(R.id.infoTv)
        infoTv.text = "If you need immediate help, click the Help button."

        val helpBtn = v.findViewById<Button>(R.id.helpBtn)
        helpBtn.visibility = View.VISIBLE

        val enterPasscodeEditText = v.findViewById<EditText>(R.id.enterPasscodeEditText)
        enterPasscodeEditText.visibility = View.GONE

        val enterButton = v.findViewById<Button>(R.id.enterButton)
        enterButton.visibility = View.GONE

        timerDisplay = v.findViewById(R.id.timerDisplay);
        timerDisplay.visibility = View.GONE

        mCountDownTimer.cancel();
        Log.i("danger", "start the classifier")

        (activity as HomeActivity).startClassifier()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun enterPasscode() {
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

    // Get saved time. If for some reason no time is saved, default is 3 minutes.
    private fun getTime(): Long {
        var mMilliseconds = 0.toLong()

        if (fileExist(activity!!.baseContext, "time.txt")) {
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

                val mDrawerLayout = activity!!.drawer_layout
                mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
            }

            override fun onTick(millisUntilFinished: Long) {
                isTimerRunning = true
                timerDisplay.setText(mSimpleDateFormat.format(millisUntilFinished))

                val mDrawerLayout = activity!!.drawer_layout
                mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            }
        }

        Log.i("mCountDownTimer", "$mCountDownTimer")

        return mCountDownTimer
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun sendRescueDetails() {
        if (recordingStarted) {
            stopVideoRecording()
        }
        stopTimer()
        sendHelp()
        displayRescuedFeatures()
        Toast.makeText(
            activity!!,
            "Rescue details have been sent to your contacts.",
            Toast.LENGTH_SHORT
        ).show()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun showAlertDialog() {
        val builder = AlertDialog.Builder(activity!!)
        builder.setTitle("Are you sure you want to exit?")
        builder.setMessage("By exiting while the timer is under countdown, we will send your rescue details immediately just to be safe.")

        builder.setPositiveButton("Yes") { dialog, which ->
            sendRescueDetails()
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

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onPause() {
        super.onPause()

        if (isTimerRunning) {
            sendRescueDetails()
        }

        Log.i("lifecycle", "onPause, $isTimerRunning")
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onStop() {
        super.onStop()

        if (isTimerRunning) {
            sendRescueDetails()
        }

        Log.i("lifecycle", "onStop, $isTimerRunning")
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onDestroy() {
        super.onDestroy()

        if (isTimerRunning) {
            sendRescueDetails()
        }

        Log.i("lifecycle", "onDestroy, $isTimerRunning")
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onClick(v: View?) {
        when (v!!.id) {
            R.id.helpBtn -> startTimer()
            R.id.enterButton -> enterPasscode()
            R.id.rescuedBtn -> verifyRescue()
        }
    }

}