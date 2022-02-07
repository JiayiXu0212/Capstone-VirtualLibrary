package com.virtuallibrary.libraworks

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.squareup.picasso.Picasso

class UserActivity : AppCompatActivity() {


    private val getCurrentUserInstance = FirebaseAuth.getInstance().currentUser
    val currentUserID = getCurrentUserInstance?.uid

    private val getFirebaseInstance = FirebaseDatabase.getInstance()
    private val sendToFirebaseRef = getFirebaseInstance.getReference("users")
    private val checkOutLogRef = sendToFirebaseRef.child(currentUserID!!).child("check_out_log")
    private val booksRef = getFirebaseInstance.getReference("books")
    private var bookISBN: String = ""
    private lateinit var bookCover: String
    private lateinit var userBooksSet: Set<String>
    private lateinit var checkedOutBookISBN: String
    private lateinit var userListener: ValueEventListener
    private var checkOutLogHashMap: HashMap<String, Book> = HashMap()

    private lateinit var gridView: GridView

    //region HelperFunctions
    override fun onCreate(savedInstanceState: Bundle?) {

        //Hide title bar
        try {
            this.supportActionBar!!.hide()
        } catch (e: NullPointerException) {
        }

        readCheckOutLog()
        //Call super and correct screen
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user)

        if (!checkConnectivity(applicationContext)) {
            fun onInfoWindowClick() {
                val intent = Intent(this, NetworkConnectionActivity::class.java).apply {
                }
                startActivity(intent)
            }
            onInfoWindowClick()
            Toast.makeText(applicationContext, "No Connection  ", Toast.LENGTH_SHORT).show()
        }

        println("Before event listeners")
        addUserEventListener()

        val updateProfile = findViewById<Button>(R.id.updateProfileButton)
        val updateCollection = findViewById<Button>(R.id.personalCollectionButton)
        val checkOutLog = findViewById<Button>(R.id.checkedOutBooksButton)

        updateProfile.setOnClickListener {
            updateProfile()
        }

        updateCollection.setOnClickListener {
            fun onInfoWindowClick() {
                val intent = Intent(this, BarcodeActivity::class.java).apply {
                }
                startActivity(intent)
            }
            onInfoWindowClick()
        }

        checkOutLog.setOnClickListener {
            val titleArray: ArrayList<String> = ArrayList()
            val libraryNameArray: ArrayList<String> = ArrayList()
            val checkoutDateArray: ArrayList<String> = ArrayList()
            val coverImageArray: ArrayList<String> = ArrayList()

            println("Size of hashmap: ${checkOutLogHashMap.size}")
            for ((_, value) in checkOutLogHashMap) {
                coverImageArray.add(value.coverId)
                checkoutDateArray.add(value.checkoutDate)
                libraryNameArray.add(value.libraryName)
                titleArray.add(value.title)
                println("This is the info for each book in the checkout log!")
                println(value.title)
                println(value.checkoutDate)
                println(value.libraryName)
                println(value.coverId)
            }
            setContentView(R.layout.activity_fragment_check_out_log)
            showBooks(titleArray, libraryNameArray, checkoutDateArray, coverImageArray)
        }

        // query user database for list of personal books
        // query books database for each ISBN in personal books
        // take cover image from books database, use in Picasso to display cover to user
        // caption cover with 'Display your favorite book'
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

    override fun onPause() {
        // Remove post value event listener
        sendToFirebaseRef.removeEventListener(userListener)
        super.onPause()
    }


    private fun updateProfile() {
        val addUsername = findViewById<EditText>(R.id.username)
        val addHometown = findViewById<EditText>(R.id.hometown)
        val addFavBook = findViewById<EditText>(R.id.favBook)
        val addFavGenre = findViewById<EditText>(R.id.favGenre)

        if (currentUserID != null) {
            sendToFirebaseRef.child(currentUserID).child("username")
                .setValue(addUsername.text.toString())
            sendToFirebaseRef.child(currentUserID).child("hometown")
                .setValue(addHometown.text.toString())
            sendToFirebaseRef.child(currentUserID).child("favorite_book")
                .setValue(addFavBook.text.toString())
            sendToFirebaseRef.child(currentUserID).child("favorite_genre")
                .setValue(addFavGenre.text.toString())
        }
    }

    //This allows the map to repopulate
    private fun addUserEventListener() {
        //Create a listener
        userListener = object : ValueEventListener {
            @SuppressLint("SetTextI18n")
            override fun onDataChange(dataSnapshot: DataSnapshot) {

                //All of this stuff is for the database
                val userObject = dataSnapshot.children
                val persBookCover: ImageView = findViewById(R.id.personalBookCover)

                userObject.forEach {
                    val usernameBox: String
                    val hometownBox: String
                    val bookBox: String
                    val genreBox: String

                    if (it.key.toString() == currentUserID) {
//                        println("afterIf itkey" + it.key.toString())
                        usernameBox = it.child("username").value as String
                        hometownBox = it.child("hometown").value as String
                        bookBox = it.child("favorite_book").value as String
                        genreBox = it.child("favorite_genre").value as String

                        // Iterate through the personal collection of the user
                        if (dataSnapshot.child(currentUserID).child("personal_books").exists()) {
                            val tasks = dataSnapshot.child(currentUserID)
                                .child("personal_books").children.iterator()

                            while (tasks.hasNext()) {
                                val listIndex =
                                    tasks.next() // this variable holds the current ISBN value
                                userBooksSet = setOf(listIndex.key as String)
                                bookISBN = listIndex.key as String
                                println("Current ISBN value is $bookISBN")
                                bookCover = listIndex.child("cover_image_code").value as String
                                println("current cover ID: $bookCover")
                                Picasso.get()
                                    .load("https://covers.openlibrary.org/b/id/$bookCover-L.jpg")
                                    .into(
                                        persBookCover
                                    )
                            }
                        }

                        val usernameReinit: EditText = findViewById(R.id.username)
                        val hometownReinit: EditText = findViewById(R.id.hometown)
                        val bookReinit: EditText = findViewById(R.id.favBook)
                        val genreReinit: EditText = findViewById(R.id.favGenre)
                        val userHeaderReinit: TextView = findViewById(R.id.userProfileHeader)

                        usernameReinit.setText(usernameBox)
                        hometownReinit.setText(hometownBox)
                        bookReinit.setText(bookBox)
                        genreReinit.setText(genreBox)
                        userHeaderReinit.text = "$usernameBox's Profile"
//                        println("userBox$usernameBox")
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
            }
        }
        sendToFirebaseRef.addValueEventListener(userListener)
    }

    private fun readCheckOutLog() {
        checkOutLogRef.get().addOnSuccessListener { it ->
            Log.i("firebase", "Got value ${it.children}")
            val bookObject = it.children
            bookObject.forEach { it2 ->
                checkedOutBookISBN = it2.key as String
                val date = it2.child("date").value.toString()
                val libraryName = it2.child("library_name").value.toString()
                var checkedOutBook = Book()
                if (checkOutLogHashMap.containsKey(checkedOutBookISBN)) {
                    checkedOutBook = checkOutLogHashMap[checkedOutBookISBN]!!
                }
                checkedOutBook.checkoutDate = date
                checkedOutBook.libraryName = libraryName
                checkOutLogHashMap[checkedOutBookISBN] = checkedOutBook
                println("my book = ${checkedOutBook.checkoutDate}")
                println("Building hashmap, size currently: ${checkOutLogHashMap.size}")
            }
            booksRef.get().addOnSuccessListener { it3 ->
                Log.i("firebase", "Got value ${it3.children}")
                println("I'm at the start of the booksRef.get() method!")
                val bookObject = it3.children
                bookObject.forEach { it4 ->
                    println("This is the book ISBN: ${it4.key}")
                    if (checkOutLogHashMap.containsKey(it4.key)) {
                        checkOutLogHashMap[it4.key]!!.title = it4.child("title").value.toString()
                        checkOutLogHashMap[it4.key]!!.coverId =
                            it4.child("cover_image_code").value.toString()
                        println("I'm inside the booksRef.get() method!")
                    }
                }
            }.addOnFailureListener {
                Log.e("firebase", "Error getting data", it)
            }
        }.addOnFailureListener {
            Log.e("firebase", "Error getting data", it)
        }
    }

    @SuppressLint("ResourceType")
    private fun showBooks(
        titleArray: ArrayList<String>,
        libraryNameArray: ArrayList<String>,
        checkoutDateArray: ArrayList<String>,
        coverImageArray: ArrayList<String>
    ) {
        gridView = findViewById(R.id.scrollableCheckoutLogView)
        val mainAdapter = CheckOutLogDisplayAdapter(
            this,
            titleArray,
            libraryNameArray,
            checkoutDateArray,
            coverImageArray
        )
        gridView.adapter = mainAdapter
    }
}