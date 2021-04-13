package com.myapplication.rescueme

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.*
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.myapplication.rescueme.Helper.Companion.fileExist
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class LocationService : Service(), GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private val CHANNEL_ID = "ForegroundService Kotlin"

    // Location variables
    private lateinit var mLocationRequest: LocationRequest
    private  var mGoogleApiClient: GoogleApiClient? = null
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private var latitude = 0.toDouble()
    private var longitude = 0.toDouble()
    private var isStop = false

    fun startService(context: Context, message: String) {
        val startIntent = Intent(context, LocationService::class.java)
        startIntent.putExtra("inputExtra", message)
        ContextCompat.startForegroundService(context, startIntent)
    }
    fun stopService(context: Context) {
        isStop = true
        Log.i("isStop", "$isStop")
        val stopIntent = Intent(context, LocationService::class.java)
        context.stopService(stopIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        mFusedLocationClient.removeLocationUpdates(mLocationCallback)
        Log.i("onDestroy", "onDestroy")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val input = intent?.getStringExtra("inputExtra")
        createNotificationChannel()
        val notificationIntent = Intent(this, HomeActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, 0
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Retrieving your current location.")
            .setContentText(input)
            .setSmallIcon(R.drawable.heroine1)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(1, notification)

        // gets the user's location
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if (mGoogleApiClient == null) {
            buildGoogleApiClient()
        }

        if (isStop) {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback)
            Log.i("mGoogleApiClient onStartCommand", "$isStop")
            stopSelf()
        } else {
            Log.i("mGoogleApiClient onStartCommand", "$isStop")
        }

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

    // Location code
    @Synchronized
    private fun buildGoogleApiClient() {
        mGoogleApiClient = GoogleApiClient.Builder(this)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .addApi(LocationServices.API)
            .build()
        mGoogleApiClient!!.connect()

    }

    override fun onConnected(bundle: Bundle?) {
        Log.i("location connected", "onConnected is called!")

        mLocationRequest = LocationRequest()
        mLocationRequest.setInterval(30000) // 30 seconds interval
        mLocationRequest.setFastestInterval(30000)
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            mFusedLocationClient.requestLocationUpdates(
                mLocationRequest,
                mLocationCallback,
                Looper.myLooper()
            )
        } else {
            Log.i("Permission result", "No longer have permission")
        }
    }

    var mLocationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            Log.i("MapsActivity", "LocationCallBack()")
            for (location in locationResult.getLocations()) {
                latitude = location.latitude
                longitude = location.longitude

                val msg = "Updated Location: ${location.latitude}, ${location.longitude}"
                Log.i("MapsActivity", msg)
                updateRescueDetails()
            }
        }
    }

    override fun onConnectionSuspended(i: Int) {

    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {

    }

    private fun updateRescueDetails() {
        val victimDetails = getVictimDetails()
        val rescuerDetails = getRescuerDetails()

        val victimName = victimDetails[0]
        val victimNum = victimDetails[1]

        val database = Firebase.database
        val myRef = database.getReference("RescueRecords")
        myRef.child(victimNum).removeValue()

        // loop through rescuer hashmap
        for ((id, details) in rescuerDetails) {
            val rescuerName = details[0]
            val rescuerNum = details[1]

            Log.i("rescuer details", "$rescuerName: $rescuerNum")

            writeToDB(latitude, longitude, victimName, victimNum, rescuerName, rescuerNum)
        }
    }

    // get victim name and number in the form of arraylist: [name, number]
    private fun getVictimDetails() : ArrayList<String> {
        if (!fileExist(baseContext, "my_contact.txt")) {
            return ArrayList()
        }

        var name = ""
        var contactNumber = ""

        val scan = Scanner(openFileInput("my_contact.txt"))
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
        if (!fileExist(baseContext,"contacts.txt")) {
            return HashMap()
        }

        val result = HashMap<String, ArrayList<String>>()

        val scan = Scanner(openFileInput("contacts.txt"))
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


}