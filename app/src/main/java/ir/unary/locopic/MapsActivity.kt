package ir.unary.locopic

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Toast
import com.google.android.gms.location.*
import com.google.android.gms.location.places.*
import com.google.android.gms.location.places.ui.PlacePicker
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.gms.tasks.Task
import com.google.maps.android.SphericalUtil
import kotlinx.android.synthetic.main.activity_maps.*


class MapsActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnCameraMoveListener, GoogleMap.OnCameraIdleListener, View.OnClickListener, GoogleMap.OnPolygonClickListener, GoogleMap.OnPolylineClickListener {

    private var PLACE_PICKER_REQUEST = 1002
    private val LOCATION_SETTINGS_REQUEST: Int = 12500
    private lateinit var mMap: GoogleMap
    private var placeFlag = false
    private var updateFlag = false
    private var mHeading: Double = -180.0
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var mPlaceDetectionClient: PlaceDetectionClient
    private lateinit var mPlaceGeoDataClient: GeoDataClient
    private lateinit var currentLocation: LatLng
    private lateinit var cameraPosition: CameraPosition
    lateinit var marker: Marker
    lateinit var mLocationRequest: LocationRequest
    lateinit var mLocationCallback: LocationCallback
    lateinit var mSettingsClient: SettingsClient
    lateinit var mLocationSettingsRequest: LocationSettingsRequest
    private val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 10000
    private val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS: Long = UPDATE_INTERVAL_IN_MILLISECONDS / 2
    private val patternsArray = ArrayList<PatternItem>()
    private val pointsArray = ArrayList<LatLng>()
    private val markerArray = ArrayList<Marker>()
    private val polygonsArray = ArrayList<Polygon>()

    enum class Directions {
        North, NorthWest, West, SouthWest, South, SouthEast, East, NorthEast;
    }

    override fun onPause() {
        super.onPause()
        try {
            stopLocationUpdate()
        } catch (e: Exception) {
            Log.e("ERROR", e.message + " ")
        }
    }

    override fun onResume() {
        super.onResume()
        if (updateFlag) startLocationUpdate()
        updateUpdateButtonUI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        /*if (savedInstanceState != null) {
            cameraPosition = savedInstanceState.getParcelable("cameraPosition")
            currentLocation = savedInstanceState.getParcelable("lastLocation")
        }*/


        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mPlaceDetectionClient = Places.getPlaceDetectionClient(this)
        mPlaceGeoDataClient = Places.getGeoDataClient(this)
        mSettingsClient = LocationServices.getSettingsClient(this)
        btn_nearbyPlaces.setOnClickListener(this)
        btn_updateLocation.setOnClickListener(this)
        btn_drawPoly.setOnClickListener(this)
        btn_addPoint.setOnClickListener(this)
        btn_deletePoint.setOnClickListener(this)
        btn_computeDistance.setOnClickListener(this)
        btn_computeOfsset.setOnClickListener(this)
        btn_degree.setOnClickListener(this)
        addPatterns()
    }

    fun ConvertDiretionToDegree(enum: Directions): Double {
        when (enum) {
            Directions.North -> return -180.0
            Directions.NorthWest -> return -135.0
            Directions.West -> return -90.0
            Directions.SouthWest -> return -45.0
            Directions.South -> return 0.0
            Directions.SouthEast -> return 45.0
            Directions.East -> return 90.0
            Directions.NorthEast -> return 135.0
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdate() {
        createLocationRequest()
        createLocationCallback()
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, null)
    }

    private fun createLocationRequest() {
        mLocationRequest = LocationRequest.create()
        mLocationRequest.interval = UPDATE_INTERVAL_IN_MILLISECONDS
        mLocationRequest.fastestInterval = FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }

    private fun createLocationCallback() {
        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult ?: return
                for (location in locationResult.locations) {
                    // todo something
                }
                val x = LatLng(locationResult.lastLocation.latitude, locationResult.lastLocation.longitude)
                currentLocation = x
                updateCameraPosition()
                Log.e("Location Update", "location updated!")
            }
        }
    }

    private fun stopLocationUpdate() {
        mFusedLocationClient.removeLocationUpdates(mLocationCallback).addOnSuccessListener(this, {
            Log.e("Location Update", "location update request was removed")
        })
    }

    private fun updateCameraPosition() {
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation,
                cameraPosition.zoom))
        marker.position = currentLocation
    }

    override fun attachBaseContext(newBase: Context) {
        //val language = PrefsUtil.getStringPreference(this@MainActivity, "lang")
        super.attachBaseContext(MyContextWrapper.wrap(newBase, "fa"))
    }


    override fun onClick(p0: View?) {
        when (p0?.id) {
            btn_nearbyPlaces.id -> {
                if (placeFlag) {
                    openPlacePicker()
                } else {
                    getDetectedPlaces()
                }
                placeFlag = !placeFlag
            }
            btn_updateLocation.id -> {
                if (!updateFlag) {
                    startLocationUpdate()
                } else {
                    stopLocationUpdate()
                }
                updateFlag = !updateFlag
                updateUpdateButtonUI()
            }
            btn_addPoint.id -> {
                addPoint()
                val i = pointsArray.size
                btn_addPoint.text = "add point + $i";
            }
            btn_drawPoly.id -> drawPolygon()
            btn_deletePoint.id -> deletePoint()
            btn_computeDistance.id -> drawLine()
            btn_computeOfsset.id -> computeOffset()
            btn_degree.id -> changeHeading()
        }
    }

    private fun changeHeading() {

    }

    private fun computeDistance(points: List<LatLng>?): Double {
        return SphericalUtil.computeDistanceBetween(points?.get(0), points?.get(1))
    }

    private fun computeArea(points: MutableList<LatLng>) = SphericalUtil.computeArea(points)

    private fun drawLine() {
        if (pointsArray.size >= 2) {
            val s = pointsArray.size
            val from = pointsArray[s - 1]
            val to = pointsArray[s - 2]
            val line = mMap.addPolyline(PolylineOptions().clickable(true).add(from).add(to))
            line.pattern = patternsArray
            line.color = Color.RED
            line.jointType = JointType.ROUND
            line.isClickable = true
        }
    }

    private fun computeOffset() {

    }

    private fun deletePoint() {
        pointsArray.removeAt(pointsArray.size - 1)
        markerArray[markerArray.size - 1].remove()
    }

    private fun addPoint() {
        pointsArray.add(getCenterPointLatLng())
        val i = pointsArray.size - 1
        markerArray.add(mMap.addMarker(MarkerOptions().position(getCenterPointLatLng()).title("#$i")))
    }

    private fun flushPointsArray() {
        pointsArray.clear()
        btn_addPoint.text = "add point"
    }

    private fun addPatterns() {
        patternsArray.add(Dash(20f))
        patternsArray.add(Gap(10f))
    }

    private fun drawPolygon() {
        val polygon = mMap.addPolygon(PolygonOptions().clickable(true).addAll(pointsArray))
        polygon.strokePattern = patternsArray
        polygon.strokeWidth = 5f
        polygon.strokeColor = Color.BLUE
        polygon.fillColor = Color.GREEN
        polygon.isClickable = true
        polygonsArray.add(polygon)
        flushPointsArray()

    }

    override fun onPolygonClick(poly: Polygon?) {
        if (poly?.fillColor == Color.GREEN) {
            poly.fillColor = Color.RED
        } else {
            poly?.fillColor = Color.GREEN
        }
        try {
            val area = SphericalUtil.computeArea(poly?.points)
            SphericalUtil.computeOffset(getCenterPointLatLng(), 5000.0, 06.0)
            Toast.makeText(this, "poly ID :" + poly?.id, Toast.LENGTH_SHORT).show()
            Log.e("MAP UTILS", "area = $area")
        } catch (e: Exception) {
            Log.e("ERRROR", e.message + "")
        }
    }

    override fun onPolylineClick(line: Polyline?) {
        Toast.makeText(this, "poly ID :" + line?.id, Toast.LENGTH_SHORT).show()
        Log.e("MAP UTILS", "distance = " + computeDistance(line?.points))
    }


    private fun updateUpdateButtonUI() {
        if (updateFlag) {
            btn_updateLocation.setTextColor(ContextCompat.getColor(this, R.color.colorAccent))
        } else {
            btn_updateLocation.setTextColor(Color.BLACK)
        }
    }


    override fun onMapReady(googleMap: GoogleMap) {

        mMap = googleMap
        mMap.setOnCameraMoveListener(this)
        mMap.setOnCameraIdleListener(this)
        mMap.setOnPolygonClickListener(this)
        mMap.setOnPolylineClickListener(this)
        //showMyLocationButton()
        checkLocationSettingIsEnabled()
    }


    override fun onCameraIdle() {
        Log.e("Camera", "Idle")
        updateCenterPoint()
    }

    override fun onCameraMove() {
        Log.e("Camera", "Move")
        updateCenterPoint()
    }

    private fun updateCenterPoint() {
        val centerPoint = getCenterPointLatLng()
        tv_centerPoint.text = ""
        tv_centerPoint.append(centerPoint.latitude.toString() + "\n")
        tv_centerPoint.append(centerPoint.longitude.toString())
        cameraPosition = mMap.cameraPosition
    }

    @SuppressLint("MissingPermission")
    private fun showMyLocationButton() {
        mMap.isMyLocationEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = true
    }


    @SuppressLint("MissingPermission", "RestrictedApi")
    private fun getDetectedPlaces() {
        mPlaceDetectionClient.getCurrentPlace(null).addOnSuccessListener(this, { placeLikelihoodBufferResponse ->
            if (placeLikelihoodBufferResponse != null) {
                Log.e("MapActivity", "PLACE DETECTION CONNECTION SUCCESSFUL")
            }
        }).addOnCompleteListener { task: Task<PlaceLikelihoodBufferResponse> ->
            if (task.isSuccessful && task.result != null) {
                val places = task.result
                if (places.count() != 0) {
                    val count = places.count()
                    val placeName = ArrayList<String>(count)
                    val placeAddress = ArrayList<String>(count)
                    val placesAttribute = ArrayList<String>(count)
                    val placesLatLng = ArrayList<LatLng>(count)
                    var i = 0
                    for (place: PlaceLikelihood in places) {
                        placeName.add(place.place.name.toString())
                        Log.e("Place Name : ", placeName[i])
                        placeAddress.add(place.place.address.toString())
                        Log.e("Place Address:", placeAddress[i])
                        if (place.place.attributions != null) {
                            placesAttribute.add(place.place.attributions.toString())
                        } else {
                            placesAttribute.add("")
                        }
                        Log.e("Place Attribute:", placesAttribute[i])
                        placesLatLng.add(place.place.latLng)
                        Log.e("Place LatLng:", placesLatLng[i].toString())
                        i++
                        if (i > count - 1) {
                            break
                        }
                    }
                    places.release()
                    openPlacesDialog(placeName, placeAddress, placesAttribute, placesLatLng)
                }
            }
        }.addOnFailureListener(this, { exception ->
            Log.e("MapActivity", exception.localizedMessage + "\n" + exception.cause + "kkkkkkkkkkkkk")
        })
    }


    private fun openPlacesDialog(mLikelyPlaceNames: ArrayList<String>, mLikelyPlaceAddresses: ArrayList<String>
                                 , mLikelyPlaceAttributions: ArrayList<String>, mLikelyPlaceLatLngs: ArrayList<LatLng>) {
        // Ask the user to choose the place where they are now.
        val listener = DialogInterface.OnClickListener { _, which ->
            // The "which" argument contains the position of the selected item.
            val markerLatLng = mLikelyPlaceLatLngs[which]
            var markerSnippet = mLikelyPlaceAddresses[which]
            markerSnippet = markerSnippet + "\n" + mLikelyPlaceAttributions[which]
            // Add a marker for the selected place, with an info window
            // showing information about that place.

            marker.title = mLikelyPlaceNames[which]
            marker.position = markerLatLng
            marker.snippet = markerSnippet

            // Position the map's camera at the location of the marker.
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(markerLatLng,
                    8.0f))
        }

        val a = arrayOfNulls<String>(mLikelyPlaceNames.size)
        mLikelyPlaceNames.toArray(a)

        // Display the dialog.
        AlertDialog.Builder(this)
                .setTitle("انتخاب مکان")
                .setItems(a, listener)
                .show()
    }


    private fun checkLocationSettingIsEnabled() {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        var gpsEnabled = false
        var networkEnabled = false

        try {
            gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (ex: Exception) {
        }

        try {
            networkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (ex: Exception) {
        }

        if (!gpsEnabled && !networkEnabled) {
            val dialog = AlertDialog.Builder(this)
            dialog.setMessage("Location is not enabled")
            dialog.setPositiveButton("setting", { _, _ ->
                val myIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivityForResult(myIntent, LOCATION_SETTINGS_REQUEST)
            })
            dialog.setNegativeButton("cancel", { _, _ ->
                // TODO Auto-generated method stub
            })
            dialog.show()
        } else {
            updateFusedLoc()
        }
    }

    private fun checkLocationSetting() {
        val mLocationSettingsRequestBuilder = LocationSettingsRequest.Builder()
        mLocationSettingsRequestBuilder.addLocationRequest(mLocationRequest)
        mLocationSettingsRequest = mLocationSettingsRequestBuilder.build()
        mSettingsClient.checkLocationSettings(mLocationSettingsRequest)
                .addOnSuccessListener(this, {
                    // todo settings are available ...
                }).addOnFailureListener(this, {
                    // todo settings are not available . create request for enable location setting
                })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == LOCATION_SETTINGS_REQUEST) {
            checkLocationSettingIsEnabled()
        }
        if (requestCode == PLACE_PICKER_REQUEST) {
            if (resultCode == RESULT_OK) {
                val place = PlacePicker.getPlace(this, data)
                marker.title = place.name.toString()
                marker.position = place.latLng
                // marker.snippet = place.address.toString() + "\n" + place.attributions.toString()
                // Position the map's camera at the location of the marker.
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(place.latLng,
                        8f))
            }
        }
    }


    @SuppressLint("MissingPermission")
    private fun updateFusedLoc() {
        Log.e("HERE", "1")
        mFusedLocationClient.lastLocation
                .addOnSuccessListener(this) { location ->
                    if (location != null) {
                        Log.e("Here", "sdfsdf")
                        currentLocation = LatLng(location.latitude, location.longitude)
                        marker = mMap.addMarker(MarkerOptions().position(currentLocation).title("My Location"))
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 8.0f))
                    }
                }.addOnFailureListener(this) { exception ->
                    Log.e("FusedLoc", exception.message + " :s")
                }
    }


    private fun openPlacePicker() {
        val builder = PlacePicker.IntentBuilder()
        builder.setLatLngBounds(mMap.projection.visibleRegion.latLngBounds)
        startActivityForResult(builder.build(this), PLACE_PICKER_REQUEST)
    }

    private fun getCenterPointLatLng(): LatLng {
        return mMap.projection.visibleRegion.latLngBounds.center
    }

}
