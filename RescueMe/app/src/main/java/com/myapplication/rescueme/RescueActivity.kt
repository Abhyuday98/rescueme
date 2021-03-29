package com.myapplication.rescueme

import android.app.ActivityManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.ar.core.Anchor
import com.google.ar.sceneform.AnchorNode
import com.myapplication.rescueme.ar.PlaceNode
import com.myapplication.rescueme.ar.PlacesArFragment
import com.myapplication.rescueme.model.Geometry
import com.myapplication.rescueme.model.GeometryLocation
import com.myapplication.rescueme.model.Place
import com.myapplication.rescueme.model.getPositionVector
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class RescueActivity : AppCompatActivity(), SensorEventListener {
    private val TAG = "LocationActivity"
    private lateinit var myNum: Int

    private lateinit var arFragment: PlacesArFragment
    private lateinit var mapFragment: SupportMapFragment

    // Location
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Sensor
    private lateinit var sensorManager: SensorManager
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var anchorNode: AnchorNode? = null
    private var markers: MutableList<Marker> = emptyList<Marker>().toMutableList()
    private var places: List<Place>? = null
    private var currentLocation: Location? = null
    private var map: GoogleMap? = null
    private var anchor: Anchor? = null
    private var wantedLoc: Place? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isSupportedDevice()) {
            return
        }
        setContentView(R.layout.activity_rescue)

        arFragment = supportFragmentManager.findFragmentById(R.id.ar_fragment) as PlacesArFragment
        mapFragment =
            supportFragmentManager.findFragmentById(R.id.maps_fragment) as SupportMapFragment

        sensorManager = getSystemService()!!
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        writeToDB()
    }


    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    private fun writeToDB() {
        val database = Firebase.database
        val myRef = database.getReference("RescueRecords")

        // update records
//        var myNum = 99999995
//        var newRecord: HashMap<String, Int> = hashMapOf("Created" to 1615994024, "Lat" to 12, "Lng" to 12, "Rescuer" to 99999996, "Victim" to myNum)
//        var updatedRecords = arrayListOf<HashMap<*,*>>(newRecord)
//        myRef.get().addOnSuccessListener {
//            var data = it.value
//            if (data != null && data is ArrayList<*>) {
//                for (record in data) {
//                    if (record is HashMap<*,*>) {
//                        if (record["Victim"] != myNum.toLong()) {
//                            updatedRecords.add(record)
//                        }
//                    }
//                }
//                myRef.setValue(updatedRecords)
//            } else {
//                Toast.makeText(this, "Error updating data", Toast.LENGTH_SHORT).show()
//            }
//
//        }.addOnFailureListener{
//            Log.e("firebase", "Error getting data", it)
//        }


        //read records
        myRef.get().addOnSuccessListener {
            var data = it.value
            val scan = Scanner(openFileInput("my_contact.txt"))
            while(scan.hasNextLine()) {
                myNum = scan.nextLine().substring(3).toInt()
            }

            var isNeedHelp = false
//            Log.i("firebase", "sad ${data!!::class.simpleName}")
            Log.i("firebase", "Got value ${it.value}")
            if (data != null && data is ArrayList<*>) {
                for (record in data) {
                    if (record is HashMap<*,*>) {
                        Log.i("firebase", "record ${record}")
                        Log.i("firebase", "record type ${record["Rescuer"]}")
                        if (record["Rescuer"] == myNum.toLong()) {

                            var victimPhoneNum = record["Victim"].toString()

                            //change to be able to handle multiple locs
                            wantedLoc = Place("wantedLoc", "", victimPhoneNum, Geometry(GeometryLocation(record["Lat"] as Double, record["Lng"] as Double)))
                            isNeedHelp = true
                            Toast.makeText(this, "Location added! Tap a plane to load location.", Toast.LENGTH_LONG).show()

                        }
                    }
                }
                if (!isNeedHelp) {
                    Toast.makeText(this, "All is well! There is no call for help.", Toast.LENGTH_LONG).show()
                }
                setUpAr()
                setUpMaps()
            } else {
                Toast.makeText(this, "Error getting data", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener{
            Log.e("firebase", "Error getting data", it)
        }
    }

    private fun setUpAr() {
        arFragment.setOnTapArPlaneListener { hitResult, _, _ ->
            anchor?.detach()
            // Create anchor
            anchor = hitResult.createAnchor()
            anchorNode = AnchorNode(anchor)
            anchorNode?.setParent(arFragment.arSceneView.scene)
            addPlaces(anchorNode!!)
        }
    }

    private fun addPlaces(anchorNode: AnchorNode) {
        val currentLocation = currentLocation
        if (currentLocation == null) {
            Log.w(TAG, "Location has not been determined yet")
            return
        }

        val places = places
        if (places == null) {
            Log.w(TAG, "No places to put")
            return
        }

        for (place in places) {
            // Add the place in AR
            val placeNode = PlaceNode(this, place)

            placeNode.setParent(anchorNode)
            placeNode.localPosition = place.getPositionVector(orientationAngles[0], currentLocation.latLng)
            placeNode.setOnTapListener { _, _ ->
                showInfoWindow(place)
            }


            val markerOptions = MarkerOptions().position(place.geometry.location.latLng)
            var titleStr = getAddress(place.geometry.location.latLng)
            if (titleStr == "") {
                titleStr = "HERE"
            }
            markerOptions.title(titleStr)
            map?.addMarker(markerOptions)
        }
    }

    private fun showInfoWindow(place: Place) {
        // Show in AR
        val matchingPlaceNode = anchorNode?.children?.filter {
            it is PlaceNode
        }?.first {
            val otherPlace = (it as PlaceNode).place ?: return@first false
            return@first otherPlace == place
        } as? PlaceNode
        matchingPlaceNode?.showInfoWindow()

        // Show as marker
        val matchingMarker = markers.firstOrNull {
            val placeTag = (it.tag as? Place) ?: return@firstOrNull false
            return@firstOrNull placeTag == place
        }
        matchingMarker?.showInfoWindow()
    }


    private fun setUpMaps() {
        mapFragment.getMapAsync { googleMap ->
            googleMap.isMyLocationEnabled = true

            getCurrentLocation {
                val pos = CameraPosition.fromLatLngZoom(it.latLng, 13f)
                googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(pos))
                getNearbyPlaces(it)
            }

            googleMap.setOnMarkerClickListener { marker ->
                val tag = marker.tag
                if (tag !is Place) {
                    return@setOnMarkerClickListener false
                }
                showInfoWindow(tag)
                return@setOnMarkerClickListener true
            }
            map = googleMap

        }
    }

    private fun getCurrentLocation(onSuccess: (Location) -> Unit) {

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            currentLocation = location
            onSuccess(location)
        }.addOnFailureListener {
            Log.e(TAG, "Could not get location")
        }
    }

    private fun getNearbyPlaces(location: Location) {
        var loc = wantedLoc
        if (loc != null) {
            val places = listOf<Place>(loc)
            this@RescueActivity.places = places
        }

    }


    private fun isSupportedDevice(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val openGlVersionString = activityManager.deviceConfigurationInfo.glEsVersion
        if (openGlVersionString.toDouble() < 3.0) {
            Toast.makeText(this, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                .show()
            finish()
            return false
        }
        return true
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) {
            return
        }
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
        }

        // Update rotation matrix, which is needed to update orientation angles.
        SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
    }

    private fun getAddress(latLng: LatLng): String {

        val geocoder = Geocoder(this)
        val addresses: List<Address>?
        val address: Address?
        var addressText = ""

        try {
            // 2
            addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            // 3

            if (null != addresses && !addresses.isEmpty()) {
                address = addresses[0]

                for (i in 0 until address.maxAddressLineIndex + 1) {
                    addressText += if (i == 0) address.getAddressLine(i) else "\n" + address.getAddressLine(i)
                    Log.i("MapsActivity", "geocodefor" + address.getAddressLine(i))
                    Log.i("MapsActivity", "geocodeaddresstext" + addressText)

                }

            } else {
                Log.i("MapsActivity", "geocodeok but no add")
            }
        } catch (e: IOException) {
            Log.e("MapsActivity", "geocodeissue"+ e.localizedMessage)
        }

        return addressText
    }

}

val Location.latLng: LatLng
    get() = LatLng(this.latitude, this.longitude)
