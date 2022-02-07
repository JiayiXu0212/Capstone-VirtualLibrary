package com.virtuallibrary.libraworks

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.text.SimpleDateFormat
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.vision.CameraSource
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.Detector.Detections
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.gms.vision.barcode.BarcodeDetector
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.squareup.picasso.Picasso
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


class BarcodeActivity : AppCompatActivity()  {

    private lateinit var cameraView: SurfaceView
    private lateinit var barcodeDetector: BarcodeDetector
    private lateinit var cameraSource: CameraSource
    private var toneGen1: ToneGenerator? = null

    private var isbnTextBox: TextView? = null
    private var titleTextBox: TextView? = null
    private var checkOutTextBox: TextView? = null
    private var scannedBookISBN: String? = null
    private lateinit var jsonText: String
    private lateinit var title: String
    private var hasSubtitle: Boolean = false
    private lateinit var subtitle: String
    private lateinit var coverId: String
    private lateinit var pubDate: String
    private lateinit var authorURL: String
    private lateinit var jsonAuthorText: String
    private lateinit var author: String
    private lateinit var libraryId: String
    private var totalBookCount: Long = 0
    private var checkOutLogArrayList: ArrayList<Book> = ArrayList()
    private lateinit var bookListener: ValueEventListener
    private lateinit var personalCollectionListener: ValueEventListener
    private lateinit var libraryCirculationListener: ValueEventListener

    private val database = FirebaseDatabase.getInstance()
    private val booksRef = database.getReference("books")
    private val librariesRef = database.getReference("Libraries")
    private val usersRef = database.getReference("users")
    private var notInDatabase: Boolean = true
    private lateinit var booksSnapshot: (MutableIterable<DataSnapshot>)
    private lateinit var personalCollectionSnapshot: (MutableIterable<DataSnapshot>)
    private lateinit var checkOutLogSnapshot: (MutableIterable<DataSnapshot>)
    private lateinit var librarySnapshot: (MutableIterable<DataSnapshot>)
    private var checkOut: Boolean = false
    private var personalCollection: HashMap<String, Long> = HashMap()
    private var libraryCirculation: HashMap<String, Long> = HashMap()

    private val errorTAG: String? = null
    private val getCurrentUserInstance = FirebaseAuth.getInstance().currentUser
    private val currentUserID = getCurrentUserInstance?.uid
    var counter = 0


    // initializes the activity
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_barcode)

        // check for internet connection
        if (!checkConnectivity(applicationContext)) {
            fun onInfoWindowClick() {
                val intent = Intent(this, NetworkConnectionActivity::class.java).apply {
                }
                startActivity(intent)
            }
            onInfoWindowClick()
            Toast.makeText(applicationContext, "No Connection  ", Toast.LENGTH_SHORT).show();
        }

        // initialize ui elements
        toneGen1 = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        cameraView = findViewById(R.id.surface_view)
        isbnTextBox = findViewById(R.id.barcode_text)
        titleTextBox = findViewById(R.id.titleAfterScan)
        checkOutTextBox = findViewById(R.id.checkOutQuestion)
        val checkoutButton = findViewById<Button>(R.id.checkoutButton)
        val bookCover = findViewById<ImageView>(R.id.bookCover)
        val checkInOutButton = findViewById<ToggleButton>(R.id.checkInOutToggle)
        val checkInOutButtonBackground = findViewById<Button>(R.id.checkInOutBackgroundButton)
        initialiseDetectorsAndSources()

        // read bundle contents from previous activity
        val bundle: Bundle? = intent.extras
        libraryId = bundle?.getString("library_id").toString()
        val libraryName = bundle?.getString("library_name").toString()

        // if no libraryId found in bundle, user is here from profile screen, so remove certain UI elements
        if (libraryId == "null") {
            checkInOutButton.alpha = 0.0F
            checkInOutButton.isClickable = false
            checkInOutButtonBackground.alpha = 0.0F
            checkInOutButtonBackground.isClickable = false
            checkOutTextBox!!.alpha = 0.0F
        }

        // for emulator only -- scannedBookISBN will be supplied by the phone's camera when scanning a barcode
//        scannedBookISBN = "9781433832161"
//        scannedBookISBN = "9781250178602" // double image code, no subtitle
//        scannedBookISBN = "9780062931061"
//        scannedBookISBN = "9781984878106"
//        scannedBookISBN = "9781400049356"
//        scannedBookISBN = "9781328519030"
//        scannedBookISBN = "9781524763169"
//        scannedBookISBN = "9781524763138"
//        scannedBookISBN = "9780525560715"
//        scannedBookISBN = "9780786852901"
      scannedBookISBN = "9780593465271"

        addBookEventListener()
        addLibraryCirculationEventListener()
        addPersonalCollectionEventListener()

        checkInOutButton.setOnCheckedChangeListener{ _, isChecked ->
            if(isChecked) {
                checkOut = true
                Toast.makeText(this, "Checking OUT", Toast.LENGTH_SHORT).show()
            }
            else {
                checkOut = false
                Toast.makeText(this, "Checking IN", Toast.LENGTH_SHORT).show()
            }
        }

        checkoutButton.setOnClickListener {
            // first check to see if the user is logged in.  If not, redirect to login/signup page.
            val auth = FirebaseAuth.getInstance()
            if (scannedBookISBN != null) { // only operate on valid ISBNs from camera, else do nothing
                if (auth.currentUser == null) {  // Not signed in
                    Toast.makeText(
                        applicationContext,
                        "Please Sign in to Check Out a Title",
                        Toast.LENGTH_SHORT
                    ).show();
                    val intent = Intent(this, LoginActivitySpecial::class.java).apply {
                    }
                    startActivity(intent)
                } else { // User is signed in
                    // remove event listeners to prevent them from triggering while we add to database
                    booksRef.removeEventListener(bookListener)
                    librariesRef.removeEventListener(libraryCirculationListener)
                    usersRef.removeEventListener(personalCollectionListener)

                    titleTextBox!!.alpha = 0.0F

                    Picasso.get().load("https://covers.openlibrary.org/b/id/$coverId-L.jpg").into(
                        bookCover)

                    // If book data is not in our database, fill out that section.
                    if (notInDatabase) {
                        // push book information to firebase
                        booksRef.child(scannedBookISBN!!).child("title").setValue(title)
                        booksRef.child(scannedBookISBN!!).child("cover_image_code").setValue(coverId)
                        booksRef.child(scannedBookISBN!!).child("publish_date").setValue(pubDate)
                        booksRef.child(scannedBookISBN!!).child("author").setValue(author)
                        if (hasSubtitle) {
                            booksRef.child(scannedBookISBN!!).child("subtitle").setValue(subtitle)
                            booksRef.child(scannedBookISBN!!).child("full_title").setValue("$title $subtitle")
                        }
                    }

                    // Check if the user came to this page from library page or user page
                    if (libraryId == "null") { // book will be added to personal collection
                        usersRef.child(currentUserID!!).child("personal_books").child(scannedBookISBN!!).child(
                            "cover_image_code"
                        ).setValue(coverId)
                        if (!personalCollection.containsKey(scannedBookISBN!!)) { // book does not exist in personal collection
                            usersRef.child(currentUserID!!).child("personal_books").child(
                                scannedBookISBN!!
                            ).child("available_copies").setValue(1)
                        } else { // book is in personal collection already
                            var availableCopies: Long = personalCollection[scannedBookISBN!!]!!
                            availableCopies++
                            usersRef.child(currentUserID!!).child("personal_books").child(
                                scannedBookISBN!!
                            ).child("available_copies").setValue(availableCopies)
                        }
                    }
                    else { // book will be added to library
                        librariesRef.child(libraryId!!).child("circulation").child(scannedBookISBN!!).child(
                            "cover_image_code"
                        ).setValue(coverId)
                        if (!libraryCirculation.containsKey(scannedBookISBN!!)) { // this book is not in the library
                            librariesRef.child(libraryId!!).child("circulation").child(scannedBookISBN!!).child(
                                "available_copies"
                            ).setValue(1)
                            totalBookCount++
                        }
                        else { // this book is already in the library circulation
                            var availableCopies: Long = libraryCirculation[scannedBookISBN!!]!!
                            if (checkOut) { // checking out
                                if (availableCopies != null) {
                                    if (availableCopies <= 0) {
                                        // can't check out book, no available copies.  Display error to user (shouldn't be possible in real scenario)
                                        Toast.makeText(
                                            applicationContext,
                                            "Something went wrong, $title isn't available according to our records.  Did you mean to check this title in?",
                                            Toast.LENGTH_LONG
                                        ).show();
                                    } else { // checkout is valid, decrement available copies for the library and make an entry in the user's checkout log
                                        availableCopies--
                                        totalBookCount--
                                        val c: Date = Calendar.getInstance().getTime()
                                        val df = SimpleDateFormat(
                                            "dd-MMM-yyyy",
                                            Locale.getDefault()
                                        )
                                        val formattedDate: String = df.format(c)
                                        usersRef.child(currentUserID!!).child("check_out_log").child(
                                            scannedBookISBN!!
                                        ).child("date").setValue(formattedDate)
                                        usersRef.child(currentUserID!!).child("check_out_log").child(
                                            scannedBookISBN!!
                                        ).child("library_id").setValue(libraryId)
                                        usersRef.child(currentUserID!!).child("check_out_log").child(
                                            scannedBookISBN!!
                                        ).child("library_name").setValue(libraryName)
                                        librariesRef.child(libraryId!!).child("total_books").setValue(
                                            totalBookCount
                                        )
                                    }
                                }
                            }
                            else if(availableCopies != null) { // checking in
                                availableCopies++
                                librariesRef.child(libraryId!!).child("circulation").child(scannedBookISBN!!).child(
                                    "available_copies"
                                ).setValue(availableCopies)
                                totalBookCount++
                                librariesRef.child(libraryId!!).child("total_books").setValue(
                                    totalBookCount
                                )
                                // check the user's check-out-log to see if the book is a return
                                checkOutLogArrayList.forEach {
                                    if (it.isbn == scannedBookISBN) { // if the checked in book is in the user's checkout log
                                        usersRef.child(currentUserID!!).child("check_out_log").child(
                                            scannedBookISBN!!
                                        ).removeValue() // remove the entry from the checkout log
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Toast the user that the transaction was successful
            if (checkOut) {
                Toast.makeText(
                    applicationContext,
                    "Successfully Checked-Out $title",
                    Toast.LENGTH_LONG
                ).show();
            } else if(!checkOut) {
                Toast.makeText(
                    applicationContext,
                    "Successfully Checked-In $title",
                    Toast.LENGTH_LONG
                ).show();
            }

            // This will refresh the activity which fixes issues pertaining to checking in/out multiple books in 1 sitting
            object : CountDownTimer(1500, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    //  countTime.text = counter.toString()
                    counter++
                }
                override fun onFinish() {
                    val intent = intent

                    finish()
                    overridePendingTransition(0,0)
                    val bundle = Bundle()
                    bundle.putString("library_id", libraryId)
                    bundle.putString("library_name", libraryName)
                    intent.putExtras(bundle)
                    startActivity(intent)
                    overridePendingTransition(0,0)
                }
            }.start()
        }
    }

    // checks for internet connection
    fun checkConnectivity(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: NetworkInfo? = connectivityManager.activeNetworkInfo
        if(activeNetwork?.isConnected!=null){
            return activeNetwork.isConnected
        }
        else{
            return false
        }
    }

    // Reads "Books" root of database and pulls info if scanned book ISBN is there
    private fun addBookEventListener() {
        bookListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                booksSnapshot = dataSnapshot.children // all children of the database reference passed to method
                booksSnapshot.forEach { // for each child in the database ref
                    val bookISBN = it.key
                    if (bookISBN == scannedBookISBN) { // if the scanned ISBN is in the database
                        notInDatabase = false
                        title = it.child("title").value as String
                        titleTextBox!!.text = "Scanned Book Title: \n\n$title"
                        titleTextBox!!.alpha = 1.0F
                        coverId = it.child("cover_image_code").value as String
                        println("This is printing because the book is in our database, notInDatabase is: $notInDatabase")
                    }
                    // find way to stop ISBN comparing if we find a match
                }
                if (notInDatabase) {
                    getBookInfo()
                }
            }
            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Book failed, log a message
                Log.w(errorTAG, "loadBook:onCancelled", databaseError.toException())
            }
        }
        booksRef.addValueEventListener(bookListener) // read database once
    }

    //Get the URL and associated JSON text
    private fun getBookInfo() {
        doAsync {
            val url = URL("https://openlibrary.org/isbn/$scannedBookISBN.json")
            val urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.connect()
            try {
                jsonText = urlConnection.inputStream.bufferedReader().readText()
                Log.d("UrlTest", (jsonText as String)!!)
            } finally {
                urlConnection.disconnect()
            }
            uiThread {
            }
            getJsonValues()
        }
    }

    // convert the JSON text from website into a JSON object and grab info from it
    private fun getJsonValues() {
        try {
            // Convert JSON string from webpage into JSON object
            val obj = JSONObject(jsonText)
            title = obj.get("title").toString()
            runOnUiThread {
                titleTextBox!!.text = "Scanned Book Title: $title"
                titleTextBox!!.alpha = 1.0F
            }

            pubDate = obj.getString("publish_date")

            // Get book's cover image code.  If one does not exist, set the code to a generic cover.
            if(obj.has("covers")){
                var coverIdRaw = obj.get("covers").toString().drop(1)
                var delimiter1 = ","
                var delimiter2 = "]"
                coverId = coverIdRaw.split(delimiter1, delimiter2)[0]
            }else{
                coverId = "8231431"
            }

            // if the book has a subtitle
            if (!obj.isNull("subtitle")) {
                subtitle = obj.getString("subtitle")
                hasSubtitle = true
            }

            if (obj.has("authors")) {
                authorURL = obj.getJSONArray("authors")[0].toString().substring(9).replace("\\", "").removeSuffix(
                    "\"}"
                )
                // Have to visit new URL for author info
                doAsync {
                    val url2 = URL("https://openlibrary.org$authorURL.json")
                    val urlConnection2 = url2.openConnection() as HttpURLConnection
                    urlConnection2.connect()
                    try {
                        jsonAuthorText = urlConnection2.inputStream.bufferedReader().readText()
                        Log.d("UrlAuthor", (jsonAuthorText as String)!!)
                        val objAuthor = JSONObject(jsonAuthorText)
                        author = objAuthor.get("name").toString()
                    } finally {
                        urlConnection2.disconnect()
                    }
                    uiThread {
                    }
                }
            } else {
                author = ""
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    // Reads Personal Collection of current user to build a hashmap of all books they have, and how many copies are available.
    private fun addPersonalCollectionEventListener() {
        personalCollectionListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if (dataSnapshot.child("$currentUserID").hasChild("personal_books")) {// if the user has a personal collection
                    personalCollectionSnapshot = dataSnapshot.child("$currentUserID").child("personal_books").children // all books in personal collection are now in the snapshot
                    personalCollectionSnapshot.forEach { // for each book in the collection
                        val bookISBN = it.key
                        val availableCopies = it.child("available_copies").value as Long
                        if (bookISBN != null) {
                            personalCollection[bookISBN] = availableCopies
                        }
                    }
                }
                checkOutLogArrayList.clear()
                if (dataSnapshot.child("$currentUserID").hasChild("check_out_log")) {
                    checkOutLogSnapshot = dataSnapshot.child("$currentUserID").child("check_out_log").children // all books in checkout log are now in the snapshot
                    checkOutLogSnapshot.forEach {
                        var book = Book()
                        book.isbn = it.key.toString()
                        book.libraryId = it.child("library_id").value.toString()
                        book.checkoutDate = it.child("date").value.toString()
                        checkOutLogArrayList.add(book) // checkOutLogArrayList now contains book objects for every book in the user's checkout log
                    }
                }
            }
            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Book failed, log a message
                Log.w(errorTAG, "loadPersonalCollection:onCancelled", databaseError.toException())
            }
        }
        usersRef.addValueEventListener(personalCollectionListener)
    }

    // Reads library database for current library to build a hashmap of all books they have, and how many copies are available.
    private fun addLibraryCirculationEventListener() {
        libraryCirculationListener = object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if(libraryId != "null"){
                    var totalBookTemp = 0.toLong()
                    totalBookCount = dataSnapshot.child("$libraryId").child("total_books").value as Long
                    if (dataSnapshot.child("$libraryId").child("circulation").exists()) {// if library has a circulation
                        librarySnapshot = dataSnapshot.child("$libraryId").child("circulation").children // all books in library circulation are now in the snapshot
                        librarySnapshot.forEach { // for each book in the circulation
                            val bookISBN = it.key

                            if (bookISBN != null && it.child("available_copies").exists()) {
                                val availableCopies = it.child("available_copies").value as Long
                                libraryCirculation[bookISBN] = availableCopies
                                totalBookTemp += availableCopies
                            }
                        }
                        if (totalBookTemp != totalBookCount) {
                            totalBookCount = totalBookTemp
                            librariesRef.child(libraryId!!).child("total_books").setValue(
                                totalBookCount
                            )
                        }
                        println("This is the hashmap of the library circulation: " + libraryCirculation.entries)
                    }
                }
            }
            override fun onCancelled(databaseError: DatabaseError) {
                // Getting Book failed, log a message
                Log.w(errorTAG, "loadBook:onCancelled", databaseError.toException())
            }
        }
        librariesRef.addValueEventListener(libraryCirculationListener) // read database continuously
    }



    //Scan and get barcodeData
    private fun initialiseDetectorsAndSources() {
        barcodeDetector = BarcodeDetector.Builder(this)
            .setBarcodeFormats(Barcode.ALL_FORMATS)
            .build()
        cameraSource = CameraSource.Builder(this, barcodeDetector)
            .setRequestedPreviewSize(1920, 1080)
            .setAutoFocusEnabled(true) //you should add this feature
            .build()
        cameraView!!.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                try {
                    if (ActivityCompat.checkSelfPermission(
                            this@BarcodeActivity,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        cameraSource.start(cameraView!!.holder)
                    } else {
                        ActivityCompat.requestPermissions(
                            this@BarcodeActivity,
                            arrayOf(Manifest.permission.CAMERA),
                            REQUEST_CAMERA_PERMISSION
                        )
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                cameraSource.stop()
            }
        })
        barcodeDetector.setProcessor(object : Detector.Processor<Barcode> {
            override fun release() {
                // Toast.makeText(getApplicationContext(), "To prevent memory leaks barcode scanner has been stopped", Toast.LENGTH_SHORT).show();
            }

            //Gets the barcode data from Google Vision
            override fun receiveDetections(detections: Detections<Barcode>) {
                val barcodes = detections.detectedItems
                if (barcodes.size() != 0) {
//                    binding.barcodeText.post {
                    isbnTextBox!!.post {
                        if (barcodes.valueAt(0).email != null) {
                            isbnTextBox!!.removeCallbacks(null)
                            scannedBookISBN = barcodes.valueAt(0).email.address
                        } else {
                            scannedBookISBN = barcodes.valueAt(0).displayValue
                        }
                        isbnTextBox!!.text = scannedBookISBN

                        addBookEventListener()
                        addLibraryCirculationEventListener()
                        addPersonalCollectionEventListener()
//                        toneGen1!!.startTone(ToneGenerator.TONE_CDMA_PIP, 150)
                    }
                }
            }
        })
    }

    override fun onPause() {
        super.onPause()
        // Remove post value event listener
        booksRef.removeEventListener(bookListener)
        librariesRef.removeEventListener(libraryCirculationListener)
        usersRef.removeEventListener(personalCollectionListener)
        supportActionBar!!.hide()
        cameraSource!!.release()
    }

    override fun onResume() {
        super.onResume()
        supportActionBar!!.hide()
        initialiseDetectorsAndSources()
    }

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 201
    }
}