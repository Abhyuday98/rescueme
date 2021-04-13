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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.myapplication.rescueme.ar.PlaceNode
import com.myapplication.rescueme.ar.PlacesArFragment
import com.myapplication.rescueme.model.Geometry
import com.myapplication.rescueme.model.GeometryLocation
import com.myapplication.rescueme.model.Place
import com.myapplication.rescueme.model.getPositionVector
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.io.IOException
import kotlin.collections.HashMap

class RescueActivity : AppCompatActivity(), SensorEventListener {
    private val TAG = "LocationActivity"
    private var myNum: String = ""
    private var myName: String = ""

    private lateinit var arFragment: PlacesArFragment
    private lateinit var mapFragment: SupportMapFragment

    // Location
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var dbRef: DatabaseReference
    private lateinit var dbEventListener: ValueEventListener

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

        setUp()
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

    override fun onStop() {
        super.onStop()
        dbRef.removeEventListener(dbEventListener);
    }

    private fun setUp() {
        val extras = intent.extras
        if (extras != null) {
            var victimName = ""
            victimName = extras.getString("victimName")!!
            val victimNum = extras.getString("victimNum")!!
            val rescuerNum = extras.getString("rescuerNum")!!

            val database = Firebase.database
            dbRef = database.getReference("RescueRecords").child(victimNum).child(rescuerNum)
            dbEventListener = object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val new = dataSnapshot.value
                    if (new is HashMap<*,*>) {
                        val lat = new["Lat"] as Double
                        val lng = new["Lng"] as Double
                        updateWantedLoc(victimName, lat, lng)
                    }
                }
                override fun onCancelled(databaseError: DatabaseError) {
                    Log.i("record", "Error updating location!")
                }
            }

            dbRef.addValueEventListener(dbEventListener)

        }
    }

    private fun updateWantedLoc(name: String, lat: Double, lng: Double){
        wantedLoc = Place("wantedLoc", "", name, Geometry(GeometryLocation(lat, lng)))
        Toast.makeText(this, "Location added! Tap a plane to load location.", Toast.LENGTH_LONG).show()
        setUpAr()
        setUpMaps()
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
                }

            } else {
                Log.i("MapsActivity", "Geocoding Ok but no address")
            }
        } catch (e: IOException) {
            Log.e("MapsActivity", "Geocoding issue"+ e.localizedMessage)
        }

        return addressText
    }

}

val Location.latLng: LatLng
    get() = LatLng(this.latitude, this.longitude)
