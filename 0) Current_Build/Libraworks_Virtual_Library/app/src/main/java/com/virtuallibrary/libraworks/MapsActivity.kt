package com.virtuallibrary.libraworks

/*
AUTHORS: GRANT SAYLOR, KYLE SMITH, ANTHONY TRAN, JIAYI XU
PROJECT START DATE: 10/2020 - 6/2021
DESCRIPTION: VIRTUAL LIBRARY APPLICATION
 */

//region Imports
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.google.maps.android.ui.IconGenerator
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.concurrent.timerTask

//endregion

//region Class
//Class for the map screen
class MapsActivity : AppCompatActivity(), GoogleMap.OnInfoWindowClickListener,
    GoogleMap.OnInfoWindowLongClickListener, OnMapReadyCallback, GoogleMap.OnMarkerDragListener,
    GoogleMap.OnMarkerClickListener {

    //region Initializations
    private lateinit var mapGeneration: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var lastLocation: Location
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest
    private lateinit var markerListener: ValueEventListener
    private lateinit var clusterManager: ClusterManager<MyItem>
    private var locationUpdateState = false
    private var originalLongitude = 0.0
    private var originalLatitude = 0.0
    private var searchLongitude: Double = 0.0
    private var searchLatitude: Double = 0.0
    private lateinit var addANewLibraryToMapMarker: Marker
    private val getFirebaseInstance = FirebaseDatabase.getInstance()
    private val sendToFirebaseRef = getFirebaseInstance.getReference("Libraries")
    private val usersRef = getFirebaseInstance.getReference("users")
    private val getCurrentUserInstance = FirebaseAuth.getInstance().currentUser
    val currentUserID = getCurrentUserInstance?.uid
    private val markerArray = ArrayList<Set<Any>>()
    private val favoriteList = HashMap<String, String>()
    private var favHashmap = HashMap<String, String>()
    var name: String = ""
    private var libCount = 0
    //endregion

    //region Methods

    //Starts the Location engine
    private fun startLocationUpdates() {
        //Accesses the exact location of the user
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            null
        )
    }

    //Starts the location requests
    private fun createLocationRequest() {
        locationRequest = LocationRequest()
        locationRequest.interval = 10000
        locationRequest.fastestInterval = 5000
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
        val client = LocationServices.getSettingsClient(this)
        val task = client.checkLocationSettings(builder.build())
        task.addOnSuccessListener {
            locationUpdateState = true
            startLocationUpdates()
        }
        task.addOnFailureListener { e ->
            if (e is ResolvableApiException) {
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    e.startResolutionForResult(
                        this@MapsActivity,
                        REQUEST_CHECK_SETTINGS
                    )
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                }
            }
        }
    }

    //Autogenerated stuff
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CHECK_SETTINGS) {
            if (resultCode == Activity.RESULT_OK) {
                locationUpdateState = true
                startLocationUpdates()
            }
        }
    }

    //Autogenerated stuff
    override fun onPause() {
        println("onPause is running")

        //Remove event listener
        usersRef.removeEventListener(markerListener)
        super.onPause()

        //Remove location updates
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    //Autogenerated stuff
    public override fun onResume() {
        //Resume location updates
        super.onResume()
        if (!locationUpdateState) {
            startLocationUpdates()
        }
    }

    //Autogenerated stuff
    public override fun onRestart() {
        //Resume location updates
        super.onRestart()
        if (!locationUpdateState) {
            startLocationUpdates()
        }
        finish()
        startActivity(intent)
    }

    //Autogenerated stuff
    companion object {
        const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val REQUEST_CHECK_SETTINGS = 2
    }

    //Sets the map fragment up
    private fun setUpMap() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }
        setUpClusterer()
        mapGeneration.isMyLocationEnabled = true

        fusedLocationClient.lastLocation.addOnSuccessListener(this) { location ->
            // Got last known location. In some rare situations this can be null.
            if (location != null) {
                lastLocation = location
                var currentLatLng = LatLng(location.latitude, location.longitude)

                val restrictBoundsOfMapToGeoLocation = LatLngBounds(
                    LatLng(location.latitude - 0.1, location.longitude - 0.1),// SW bounds
                    LatLng(location.latitude + 0.1, location.longitude + 0.1)  // NE bounds
                )
                //using search intent bundle info
                if (searchLatitude != 0.0 && searchLongitude != 0.0) {
                    currentLatLng = LatLng(searchLatitude, searchLongitude)
                }
                mapGeneration.setLatLngBoundsForCameraTarget(restrictBoundsOfMapToGeoLocation)
                //Add the ability to add more markers
                addMoreMarkers()
                mapGeneration.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
            }
        }
    }

    //This allows the map to repopulate
    private fun addMarkerEventListener(markerReference: DatabaseReference) {
        //Create a listener
        markerListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                //All of this stuff is for the database
                val markerObject = dataSnapshot.children
                markerObject.forEach {
                    val pins = it.key.toString()
                    it.children
                    val longitude1 = it.child("longitude").value
                    println(longitude1)
                    val latitude1 = it.child("latitude").value
                    val creationistID = it.child("user_id").value
                    val totalBooks = it.child("total_books").value
                    name = it.child("library_name").value as String
                    println(latitude1)
                    val markerSet: Set<Any> = setOf(
                        latitude1 as Double,
                        longitude1 as Double,
                        pins,
                        name,
                        creationistID as String,
                        totalBooks as Long
                    )
                    favoriteList[pins] = name
                    println(markerSet)
                    markerArray.add(markerSet)
                }
                regenerateMarkers()
                addFavoriteEventListener(usersRef)
            }

            override fun onCancelled(databaseError: DatabaseError) {
            }
        }
        markerReference.addListenerForSingleValueEvent(markerListener)
    }

    //This will regenerate all the markers for the user
    private fun regenerateMarkers() {
        for (element in markerArray) {
            val regenName = element.elementAt(3)
            val totalBooksFromDatabase = element.elementAt(5)

            for (i in 0..0) {
                val offset = i / 60.0
                var lat = element.elementAt(0) as Double
                var lng = element.elementAt(1) as Double
                lat += offset
                lng += offset
                val offsetItem =
                    MyItem(
                        lat,
                        lng,
                        "$regenName",
                        "Total Books: $totalBooksFromDatabase || Tap To Enter"
                    )
                MarkerClusterRenderer(applicationContext, mapGeneration, clusterManager)
                clusterManager.renderer = MarkerClusterRenderer(this, mapGeneration, clusterManager)
                clusterManager.addItem(offsetItem)
            }
        }
    }


    private fun addFavoriteEventListener(userReference: DatabaseReference) {
        //Create a listener
        markerListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {

                if (dataSnapshot.child("$currentUserID")
                        .hasChild("favorite_libraries")
                ) {// if the user has a library favorite collection
                    val favoriteLibrarySnapshot =
                        dataSnapshot.child("$currentUserID").child("favorite_libraries").children
                    favoriteLibrarySnapshot.forEach {
                        it.key
                        val libraryId = it.value
                        favHashmap[libraryId as String] = favoriteList[libraryId]!!
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
            }
        }
        userReference.addListenerForSingleValueEvent(markerListener)
    }

    //Autogenerated stuff
    override fun onCreate(savedInstanceState: Bundle?) {
        val bundle1: Bundle? = intent.extras
        //Hide the title bar
        try {
            this.supportActionBar!!.hide()
        } catch (e: NullPointerException) {
        }
        //Call the super and set the screen
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_buttons)
        if (bundle1?.getDouble("longitude") != null) {
            searchLongitude = bundle1.getDouble("longitude")
        }
        if (bundle1?.getDouble("latitude") != null) {
            searchLatitude = bundle1.getDouble("latitude")
        }
        println("oncreate is running")
        if (!checkConnectivity(applicationContext)) {
            fun onInfoWindowClick() {
                val intent = Intent(this, NetworkConnectionActivity::class.java).apply {
                }
                startActivity(intent)
            }
            onInfoWindowClick()
            Toast.makeText(applicationContext, "No Connection  ", Toast.LENGTH_SHORT).show()
        }

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                super.onLocationResult(p0)
                lastLocation = p0.lastLocation
            }
        }
        createLocationRequest()
    }

    private fun checkConnectivity(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: NetworkInfo? = connectivityManager.activeNetworkInfo
        return if (activeNetwork?.isConnected != null) {
            activeNetwork.isConnected
        } else {
            false
        }
    }

    private fun favLibZoom(title: CharSequence) {
        var headerLatitude = 0.0
        var headerLongitude = 0.0
        for (element in markerArray) {
            if (element.elementAt(3) == title) {
                //do stuff
                headerLatitude = element.elementAt(0) as Double
                headerLongitude = element.elementAt(1) as Double
            }
        }
        val currentLatLng = LatLng(headerLatitude, headerLongitude)
        mapGeneration.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 18f))
    }

    private fun showPopup(view: View) {
        val popup = PopupMenu(this, view)
        popup.inflate(R.menu.popup_map_favorites)
        //Rename menu items based on index
        val menuOpts = popup.menu
        var i = 0
        favHashmap.forEach {
            menuOpts.getItem(i).title = it.value
            i++
        }

        //Use click listener to travel to another activity of fav library
        popup.setOnMenuItemClickListener { item: MenuItem? ->
            when (item!!.itemId) {
                R.id.header1 -> {
                    favLibZoom(item.title)
                }
                R.id.header2 -> {
                    favLibZoom(item.title)
                }
                R.id.header3 -> {
                    favLibZoom(item.title)
                }
                R.id.header4 -> {
                    favLibZoom(item.title)
                }
                R.id.header5 -> {
                    favLibZoom(item.title)
                }
            }
            true
        }
        popup.show()
    }

    private fun showMapConfirmationPopup(view: View) {
        val popup = PopupMenu(this, view)
        popup.inflate(R.menu.popup_add_to_map)
        val addLibraryName = findViewById<EditText>(R.id.editLibName)
        val libraryName = addLibraryName.text
        val totalBooksDummyContent: Long = 0
        val addLibraryButton = findViewById<Button>(R.id.buttonAddLibrary)
        popup.setOnMenuItemClickListener { item: MenuItem? ->
            when (item!!.itemId) {
                R.id.add_to_map -> {
                    libCount++
                    if (libCount == 6) {
                        Toast.makeText(
                            this,
                            "You Have Added The Maximum Amount of Libraries in a Session, Please Slow Down.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        if (libraryName.length > 25) {
                            Toast.makeText(
                                this,
                                "Name Too Long, Max Length 25 Characters",
                                Toast.LENGTH_LONG
                            ).show()
                        } else if (libraryName.toString() == "" || libraryName.toString() == "Name Your Library") {
                            Toast.makeText(
                                this,
                                "Invalid Name, please try again",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                this,
                                "Please Move $libraryName to the Desired Location, Press and Hold to Move",
                                Toast.LENGTH_LONG
                            ).show()
                            if (ActivityCompat.checkSelfPermission(
                                    this,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                                    this,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                            }
                            fusedLocationClient.lastLocation.addOnSuccessListener(this) { location ->
                                // Got last known location. In some rare situations this can be null.
                                if (location != null) {
                                    lastLocation = location
                                    //Brand new library icon
                                    addANewLibraryToMapMarker = mapGeneration.addMarker(
                                        MarkerOptions()
                                            .position(LatLng(location.latitude, location.longitude))
                                            .draggable(true)
                                            .title(libraryName.toString())
                                            .snippet(totalBooksDummyContent.toString())
                                            .icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_user_construct))
                                    )
                                }
                            }
                        }
                    }
                }

                R.id.confirm_library -> {
                    //Prevent invalid names
                    if (libraryName.toString() == "" || libraryName.toString() == "Name Your Library") {
                        Toast.makeText(this, "Invalid Name, please try again", Toast.LENGTH_LONG)
                            .show()
                    } else {
                        Toast.makeText(
                            this,
                            "$libraryName Added. Regenerating Map",
                            Toast.LENGTH_LONG
                        ).show()
                        //Prevent abuse of library adding function
                        addLibraryButton.isClickable = false
                        //Timer to prevent abuse of adding libraries
                        Timer().schedule(timerTask {
                            addLibraryButton.isClickable = true
                        }, 1000 * 30)
                        onMarkerDrag(addANewLibraryToMapMarker)
                        onMarkerDragEnd(addANewLibraryToMapMarker)
                        val key = sendToFirebaseRef.push().key
                        //Set the values of the library
                        if (key != null) {
                            sendToFirebaseRef.child(key).child("latitude")
                                .setValue(originalLatitude)
                            sendToFirebaseRef.child(key).child("longitude")
                                .setValue(originalLongitude)
                            sendToFirebaseRef.child(key).child("total_books")
                                .setValue(totalBooksDummyContent)
                            sendToFirebaseRef.child(key).child("library_name")
                                .setValue(libraryName.toString())
                            sendToFirebaseRef.child(key).child("user_id")
                                .setValue(currentUserID.toString())
                        }
                        //Regenerate the activity to populate the new library
                        fun onInfoWindowClick() {
                            val intent = Intent(this, MapsActivity::class.java).apply {
                            }
                            finish()
                            startActivity(intent)
                        }
                        onInfoWindowClick()
                    }
                }
            }
            true
        }
        popup.show()
    }

    //Ability to add more markers to the map
    private fun addMoreMarkers() {
        //Store the library name for future use
        val addLibraryName = findViewById<EditText>(R.id.editLibName)
        val favoriteLibrariesButton = findViewById<Button>(R.id.button12)
        addLibraryName.text
        //Allows access to the add library button once you've set a name
        addLibraryName.setOnClickListener {
            val addLibraryButton = findViewById<Button>(R.id.buttonAddLibrary)
            addLibraryButton.setOnClickListener {
                showMapConfirmationPopup(addLibraryButton)
            }
        }
        favoriteLibrariesButton.setOnClickListener {
            showPopup(favoriteLibrariesButton)
        }
    }

    //Overridden methods to keep track of the location of the marker
    override fun onMarkerDragStart(p0: Marker?) {
    }

    //Overridden methods to keep track of the location of the marker
    override fun onMarkerDrag(p0: Marker) {
        originalLatitude = p0.position.latitude
        originalLongitude = p0.position.longitude
    }

    //Overridden methods to keep track of the location of the marker
    override fun onMarkerDragEnd(p0: Marker) {
        originalLatitude = p0.position.latitude
        originalLongitude = p0.position.longitude
    }

    //Overridden methods to keep track of the location of the marker
    override fun onMarkerClick(marker: Marker): Boolean {
        // Retrieve the data from the marker.
        val clickCount = marker.tag as? Int
        // Check if a click count was set, then display the click count.
        clickCount?.let {
            val newClickCount = it + 1
            marker.tag = newClickCount
            Toast.makeText(
                this,
                "${marker.title} has been clicked $newClickCount times.",
                Toast.LENGTH_SHORT
            ).show()
        }
        // Return false to indicate that we have not consumed the event and that we wish
        // for the default behavior to occur (which is for the camera to move such that the
        // marker is centered and for the marker's info window to open, if it has one).
        return false
    }

    //Use to enter library
    override fun onInfoWindowClick(marker: Marker) {
        //Store the position of the library
        val getMarkerLatLang = marker.position
        //Get all the info of the library marker and bundle it to the library screen
        for (element in markerArray) {
            if (getMarkerLatLang == LatLng(
                    element.elementAt(0) as Double,
                    element.elementAt(1) as Double
                )
            ) {
                //Create the bundle
                val bundle = Bundle()
                //Set the bundle info
                bundle.putString("library_id", element.elementAt(2) as String)
                bundle.putString("user_id", element.elementAt(4) as String)
                bundle.putString("library_name", element.elementAt(3) as String)
                //Progress to the next library screen
                val intent = Intent(this@MapsActivity, LibraryViewActivity::class.java)
                intent.putExtras(bundle)
                startActivity(intent)
            }
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @SuppressLint("PotentialBehaviorOverride")
    override fun onMapReady(googleMap: GoogleMap) {
        mapGeneration = googleMap
        mapGeneration.uiSettings.isZoomControlsEnabled = true
        mapGeneration.setOnMarkerClickListener(this)
        mapGeneration.setOnMarkerDragListener(this)
        mapGeneration.setOnInfoWindowClickListener(this)
        mapGeneration.setOnInfoWindowLongClickListener(this)
        addMarkerEventListener(sendToFirebaseRef)
        setUpMap()
    }

    //Add items to the favorites list on long press
    override fun onInfoWindowLongClick(marker: Marker?) {
        //Store the lat-lang of the marker
        val getMarkerLatLang = marker?.position
        //Store the library ID number
        var libraryId: String
        for (element in markerArray) {
            //If the marker matches the current library
            if (getMarkerLatLang == LatLng(
                    element.elementAt(0) as Double,
                    element.elementAt(1) as Double
                )
            ) {
                //Set the name corresponding to that of the library marker title
                val libraryName = marker.title
                libraryId = element.elementAt(2) as String
                print("before null check")
                //Remove from the list
                if (libraryName != null) {
                    print("before removal")
                    if (favHashmap.containsKey(libraryId)) {
                        print("if it contains key")
                        favHashmap.remove(libraryId)
                        print("after removal")
                        Toast.makeText(
                            this, "Removed From Favorites",
                            Toast.LENGTH_SHORT
                        ).show()
                        if (currentUserID != null) {
                            usersRef.child(currentUserID).child("favorite_libraries").removeValue()
                            var index = 0
                            favHashmap.forEach {
                                usersRef.child(currentUserID).child("favorite_libraries")
                                    .child(index.toString())
                                    .setValue(it.key)
                                index++
                            }
                        }
                    } else {
                        //Prevent list overflow by limiting favorite list size
                        if (favHashmap.size > 4) {
                            Toast.makeText(
                                this, "Too Many Favorites",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            favHashmap[libraryId] = libraryName
                        }
                    }
                }
            }
        }

        //If the user is logged in
        if (currentUserID != null) {
            var index = 0
            favHashmap.forEach {
                usersRef.child(currentUserID).child("favorite_libraries").child(index.toString())
                    .setValue(it.key)
                index++
            }
        }
        Toast.makeText(
            this, "Adding to Favorites",
            Toast.LENGTH_SHORT
        ).show()
    }
    //endregion


    //Get and set the parameters for each cluster marker (position, title, snippet)
    inner class MyItem(
        lat: Double,
        lng: Double,
        private val title: String,
        private val snippet: String
    ) : ClusterItem {
        private val position: LatLng = LatLng(lat, lng)

        override fun getPosition(): LatLng {
            return position
        }

        override fun getTitle(): String {
            return title
        }

        override fun getSnippet(): String {
            return snippet
        }

    }

    //Populate the map with clusters
    @SuppressLint("PotentialBehaviorOverride")
    private fun setUpClusterer() {
        // Initialize the manager with the context and the map.
        //Allows tracking of the map so the clusters can render
        clusterManager = ClusterManager(applicationContext, mapGeneration)
        mapGeneration.setOnCameraIdleListener(clusterManager)
        mapGeneration.setOnMarkerClickListener(clusterManager)
    }

    //Inner class to render the clustering of markers
    inner class MarkerClusterRenderer(
        context: Context,
        map: GoogleMap,
        clusterManager: ClusterManager<MyItem>?
    ) :
        DefaultClusterRenderer<MyItem>(context, map, clusterManager) {
        private val markerDimension = 48  // 2
        private var iconGenerator: IconGenerator? = null
        private var markerImageView: ImageView? = null

        //Generate the marker clusters
        init {
            iconGenerator = IconGenerator(context)
            markerImageView = ImageView(context)
            markerImageView!!.layoutParams =
                ViewGroup.LayoutParams(markerDimension, markerDimension)
            //Change this to change the cluster color
            markerImageView!!.setBackgroundColor(getColor(R.color.colorPrimary))
            iconGenerator!!.setContentView(markerImageView)
        }

        //Set marker icons before cluster creation
        override fun onBeforeClusterItemRendered(item: MyItem, markerOptions: MarkerOptions) {
            super.onBeforeClusterItemRendered(item, markerOptions)
            //Select the icon you want
            markerOptions.icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_user_location))
        }
    }
}