package com.virtuallibrary.libraworks

/*
AUTHORS: GRANT SAYLOR, KYLE SMITH, ANTHONY TRAN, JIAYI XU
PROJECT START DATE: 10/2020
DESCRIPTION: VIRTUAL LIBRARY APPLICATION
 */

//region Imports
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.*
import me.xdrop.fuzzywuzzy.FuzzySearch
import kotlin.math.abs


//newly added

//endregion

//Class for the Search screen
class SearchActivity : AppCompatActivity() {

    //database information
    private val database = FirebaseDatabase.getInstance()
    private val booksRef = database.getReference("books")
    private val librariesRef = database.getReference("Libraries")
    private lateinit var bookListener: ValueEventListener
    private lateinit var libListener: ValueEventListener

    //global variables for querying
    private val searchResults = ArrayList<Book>()
    private val bookArray = ArrayList<Book>()

    //global variables to get device location
    private var libraryLocation: HashMap<String, ArrayList<Library>> = HashMap()
    private var deviceLongitude = 0.0
    private var deviceLatitude = 0.0
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    //grid view for search results
    private lateinit var gridView: GridView

    //user input
    private lateinit var searchInput: String

    //barcode information
    private lateinit var librarySnapshot: (MutableIterable<DataSnapshot>)


    //region HelperFunctions
    override fun onCreate(savedInstanceState: Bundle?) {
        //Hide title bar
        try {
            this.supportActionBar!!.hide()
        } catch (e: NullPointerException) {
        }

        //Call super and correct screen
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        if (!checkConnectivity(applicationContext)) {
            fun onInfoWindowClick() {
                val intent = Intent(this, NetworkConnectionActivity::class.java).apply {
                }
                startActivity(intent)
            }
            onInfoWindowClick()
            Toast.makeText(applicationContext, "No Connection  ", Toast.LENGTH_SHORT).show()
        }
        //get device location
        getDeviceLocation()
        //prepopulate all the books for searches
        addBookEventListener(booksRef)
        //finding button and editText
        val searchButton = findViewById<Button>(R.id.searchButton)
        val textBoxClickMe = findViewById<EditText>(R.id.editTextTextPersonName2)
        //setting on click listener
        searchButton.setOnClickListener {
            //getting the user input
            searchInput = textBoxClickMe.text.toString()
            //query for the search result
            searchBook(bookArray)
        }
    }

    // Remove post value event listener
    override fun onPause() {
        booksRef.removeEventListener(bookListener)
        super.onPause()
    }

    private fun checkConnectivity(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: NetworkInfo? = connectivityManager.activeNetworkInfo
        if (activeNetwork?.isConnected != null) {
            return activeNetwork.isConnected
        } else {
            return false
        }
    }

    //Getting Device location for future use
    private fun getDeviceLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                MapsActivity.LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.lastLocation.addOnSuccessListener(this) { location ->
            // Got last known location. In some rare situations this can be null.
            if (location != null) {
                deviceLatitude = location.latitude
                deviceLongitude = location.longitude
            }
        }
    }

    //Function to populate search Results array
    private fun searchBook(bookArray: ArrayList<Book>) {
        //clear search results upon searching new user input
        searchResults.clear()
        val unorganizedBookList = hashMapOf<Book, Int>()
        //using fuzzy search algorithm to determine if a particular book is what the user may or may not be looking for
        bookArray.forEach {
            //fuzzy search by book Title
            if (FuzzySearch.weightedRatio(it.title, searchInput) >= 70) {
                unorganizedBookList[it] = FuzzySearch.weightedRatio(it.title, searchInput)
                //fuzzy search by book Author
            } else if (FuzzySearch.weightedRatio(it.author, searchInput) >= 70) {
                unorganizedBookList[it] = FuzzySearch.weightedRatio(it.author, searchInput)
            }
        }
        //organize book by how close the search input is with the resulting books
        //using the fuzzy search API
        val organizedResult =
            unorganizedBookList.toList().sortedByDescending { (_, value) -> value }.toMap()
        //populate the search results array
        for (entry in organizedResult) {
            searchResults.add(entry.key)
        }
        //generate searchResults onto the gridView
        showBooks(searchResults)
        //asynchronously gather libraries that has books from the search result
        addLibEventListener(librariesRef, searchResults)
    }

    //populate all the books from the database to an array for future use
    private fun addBookEventListener(bookReference: DatabaseReference) {
        bookListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                // Get BookOrganizationAdapter object and use the values to update the UI
                val book = dataSnapshot.children
                // Get the book's ISBN, Title, Cover Image code, and author
                book.forEach {
                    val book = Book()
                    book.author = it.child("author").value.toString()
                    book.title = it.child("title").value.toString()
                    book.coverId = it.child("cover_image_code").value.toString()
                    book.isbn = it.key.toString()
                    bookArray.add(book)
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting BookOrganizationAdapter failed, log a message
//                Log.w(TAG, "loadBook:onCancelled", databaseError.toException())
            }
        }
        bookReference.addValueEventListener(bookListener)
    }

    //This event listener will gather the libraries that has the books from the search array
    private fun addLibEventListener(
        libReference: DatabaseReference,
        resultsArray: ArrayList<Book>
    ) {
        libListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                // Get BookOrganizationAdapter object and use the values to update the UI
                for (book in resultsArray) {
                    val libArray = ArrayList<Library>()
                    val isbnVal = book.isbn
                    if (isbnVal != null) {
                        val library = dataSnapshot.children
                        library.forEach {
                            val libraryId = it.key
                            if (dataSnapshot.child("$libraryId").hasChild("circulation")) {
                                librarySnapshot = dataSnapshot.child("$libraryId")
                                    .child("circulation").children // all books in library circulation are now in the
                                if (dataSnapshot.child("$libraryId").child("circulation").hasChild(
                                        isbnVal
                                    )
                                ) {
                                    //this part of the code will do a geo fence to prevent finding libraries too faraway
                                    val checkingLong = dataSnapshot.child("$libraryId")
                                        .child("longitude").value as Double
                                    val checkingLat = dataSnapshot.child("$libraryId")
                                        .child("latitude").value as Double
                                    // Gathers the library's Name, ID, Long and lang
                                    if (abs(checkingLong - deviceLongitude) <= 0.1 && abs(
                                            checkingLat - deviceLatitude
                                        ) <= 0.1
                                    ) {
                                        val libObj = Library()
                                        libObj.libraryName = dataSnapshot.child("$libraryId")
                                            .child("library_name").value.toString()
                                        libObj.libraryId =
                                            dataSnapshot.child("$libraryId").value.toString()
                                        libObj.longitude = dataSnapshot.child("$libraryId")
                                            .child("longitude").value as Double
                                        libObj.latitude = dataSnapshot.child("$libraryId")
                                            .child("latitude").value as Double
                                        libArray.add(libObj)
                                    }
                                }
                            }
                        }
                    }
                    if (isbnVal != null) {
                        libraryLocation[isbnVal] = libArray
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting BookOrganizationAdapter failed, log a message
//                Log.w(TAG, "loadBook:onCancelled", databaseError.toException())
            }
        }
        libReference.addValueEventListener(libListener)
    }

    //populates the gridView using an adapter
    @SuppressLint("ResourceType")
    private fun showBooks(resultsArray: ArrayList<Book>) {
        gridView = findViewById(R.id.scrollableCheckoutLogView)
        val mainAdapter = ScrollSearchAdapter(this, resultsArray)
        gridView.adapter = mainAdapter
        gridView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->

            val isbnVal = searchResults[position].isbn
            if (isbnVal != null) {
                showPopup(gridView, isbnVal)
            }
        }
    }

    //creates the popups when you click a book to see if a library contains that book.
    private fun showPopup(view: View, ISBN: String) {
        val popup = PopupMenu(this, view)
        popup.inflate(R.menu.popup_search_options)

        //Rename menu items based on index
        val menuOpts = popup.menu
        menuOpts.getItem(0).title = "No Nearby Libraries"
        menuOpts.getItem(1).title = ""
        menuOpts.getItem(2).title = ""
        for (i in 0 until (libraryLocation[ISBN]?.size!!)) {
            if (i == 3) {
                break
            }
            menuOpts.getItem(i).title = libraryLocation[ISBN]?.elementAt(i)?.libraryName
        }
        //Use click listener to travel to another activity of fav library
        popup.setOnMenuItemClickListener { item: MenuItem? ->
            when (item!!.itemId) {
                R.id.lib1 -> {
                    if (menuOpts.getItem(0).title != "No Nearby Libraries") {
                        val bundle = Bundle()
                        //Set the bundle info
                        libraryLocation[ISBN]?.elementAt(0)?.longitude?.let {
                            bundle.putDouble(
                                "longitude",
                                it
                            )
                        }
                        libraryLocation[ISBN]?.elementAt(0)?.latitude?.let {
                            bundle.putDouble(
                                "latitude",
                                it
                            )
                        }
                        //Progress to the next library screen
                        val intent = Intent(this@SearchActivity, MapsActivity::class.java)
                        intent.putExtras(bundle)
                        startActivity(intent)
                    }
                }
                R.id.lib2 -> {
                    if (menuOpts.getItem(1).title != "") {
                        val bundle = Bundle()
                        //Set the bundle info
                        libraryLocation[ISBN]?.elementAt(1)?.longitude?.let {
                            bundle.putDouble(
                                "longitude",
                                it
                            )
                        }
                        libraryLocation[ISBN]?.elementAt(1)?.latitude?.let {
                            bundle.putDouble(
                                "latitude",
                                it
                            )
                        }
                        //Progress to the next library screen
                        val intent = Intent(this@SearchActivity, MapsActivity::class.java)
                        intent.putExtras(bundle)
                        startActivity(intent)
                    }
                }
                R.id.lib3 -> {
                    if (menuOpts.getItem(2).title != "") {
                        val bundle = Bundle()
                        //Set the bundle info
                        libraryLocation[ISBN]?.elementAt(2)?.longitude?.let {
                            bundle.putDouble(
                                "longitude",
                                it
                            )
                        }
                        libraryLocation[ISBN]?.elementAt(2)?.latitude?.let {
                            bundle.putDouble(
                                "latitude",
                                it
                            )
                        }
                        //Progress to the next library screen
                        val intent = Intent(this@SearchActivity, MapsActivity::class.java)
                        intent.putExtras(bundle)
                        startActivity(intent)
                    }
                }
            }
            true
        }
        popup.show()
    }
}