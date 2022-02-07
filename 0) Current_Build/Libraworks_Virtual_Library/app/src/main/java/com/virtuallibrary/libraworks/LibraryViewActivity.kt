package com.virtuallibrary.libraworks

/*
AUTHORS: GRANT SAYLOR, KYLE SMITH, ANTHONY TRAN, JIAYI XU
PROJECT START DATE: 10/2020
DESCRIPTION: VIRTUAL LIBRARY APPLICATION
 */

//region Imports

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

//endregion

//Class for the Library Screen
class LibraryViewActivity : AppCompatActivity() {

    private lateinit var bookListener: ValueEventListener
    private lateinit var libSnapshot: (MutableIterable<DataSnapshot>)
    private val getCurrentUserInstance = FirebaseAuth.getInstance().currentUser
    private val currentUserID = getCurrentUserInstance?.uid         // Get current user ID

    // Get reference from Firebase
    private val database = FirebaseDatabase.getInstance()
    private val sendToFirebaseRef = database.getReference("Libraries")
    private val booksRef = database.getReference("books")

    // Initialize arrays for book images, available copies, book title, and book ISBN
    // To present the covers and other information for each library circulation
    private val bookimage = ArrayList<String>()
    private val bookavail = ArrayList<String>()
    private val booktitle = ArrayList<String>()
    private val bookIsbn = ArrayList<String>()

    // Declare variables for library id and library name
    private lateinit var libraryId: String
    private lateinit var libraryName: String
    lateinit var gridView: GridView

    @SuppressLint("WrongViewCast")
    override fun onCreate(savedInstanceState: Bundle?) {

        // Use bundle to read from Map Activity
        val bundle1: Bundle? = intent.extras
        libraryId = bundle1?.getString("library_id").toString()
        libraryName = bundle1?.getString("library_name").toString()

        //Hide title bar
        try {
            this.supportActionBar!!.hide()
        } catch (e: NullPointerException) {
        }

        //Call superclass and correct screen
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_libraryview)

        // Check internet connection
        if (!checkConnectivity(applicationContext)) {
            fun onInfoWindowClick() {
                val intent = Intent(this, NetworkConnectionActivity::class.java).apply {
                }
                startActivity(intent)

            }
            onInfoWindowClick()
            Toast.makeText(applicationContext, "No Connection  ", Toast.LENGTH_SHORT).show()
        }

        // Present covers function
        readBookEventListener()


        val initCheckout = findViewById<ImageButton>(R.id.button5)

        //Button listener for check out button
        initCheckout.setOnClickListener {

            //Take to the Barcode screen
            fun onInfoWindowClick() {
                val intent = Intent(this, BarcodeActivity::class.java).apply {
                }

                // Put "library_id" and "library_name" into next activity
                val bundle = Bundle()
                setResult(Activity.RESULT_OK, intent)
                bundle.putString("library_id", libraryId)
                bundle.putString("library_name", libraryName)
                intent.putExtras(bundle)
                startActivity(intent)
            }
            onInfoWindowClick()
        }

        // Delete library: If this library does not belong to current account user, means current account user does have permission, so cannot delete library.
        // Otherwise, can delete this library.
        val userID = bundle1?.getString("user_id").toString()
        val deleteLibraryButton: Button = findViewById(R.id.buttonDelete)

        deleteLibraryButton.setOnClickListener {

            if (currentUserID == userID) {
                showConfirmationPopup(deleteLibraryButton)
            } else {
                Toast.makeText(
                    this,
                    "You May Not Delete A Library That Doesn't Belong To You",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // To check system network
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

    // After user delete the library from screen,
    // it will also delete from Firebase,
    // and pop up the text to tell user "Library Has Been Removed from the Map and Database"
    private fun showConfirmationPopup(view: View) {
        val popup = PopupMenu(this, view)
        popup.inflate(R.menu.popup_confirm_lib_delete)
        val bundle1: Bundle? = intent.extras
        val libraryId = bundle1?.getString("library_id").toString()

        popup.setOnMenuItemClickListener { item: MenuItem? ->

            when (item!!.itemId) {

                R.id.Title -> {
                    sendToFirebaseRef.child(libraryId).removeValue()        // Delete from Firebase
                    Toast.makeText(
                        this,
                        "Library Has Been Removed from the Map and Database",
                        Toast.LENGTH_LONG
                    ).show()

                    // After delete the library, will take user back to the title screen
                    fun onInfoWindowClick() {
                        val intent = Intent(this, TitleScreenActivity::class.java).apply {
                        }
                        startActivity(intent)
                    }
                    onInfoWindowClick()
                }

                R.id.Author -> {
                    Toast.makeText(this, "Action Cancelled", Toast.LENGTH_LONG).show()
                }
            }

            true
        }

        popup.show()
    }


    // Read all information from firebase (Libraries: circulation) that will present in LibraryView activity
    private fun readBookEventListener() {
        bookListener = object : ValueEventListener {

            @SuppressLint("ResourceType")
            override fun onDataChange(dataSnapshot: DataSnapshot) {

                // Read user ID from Map Activity
                val bundle1: Bundle? = intent.extras
                val libID = bundle1?.getString("library_id").toString()
                val lib = dataSnapshot.children

                lib.forEach { it ->
                    libSnapshot = dataSnapshot.child(libID).child("circulation").children
                    if (libID != "null") {
                        if (it.key == libID) {

                            // Show current library name on top of the screen
                            val libName = it.child("library_name").value as String
                            val libTitle: TextView = findViewById<EditText>(R.id.textView3)
                            libTitle.text = libName

                            // Read available copies and cover image from Firebase, then add each into arrays
                            libSnapshot.forEach {
                                val availableCopies = it.child("available_copies").value as? Long
                                val imageCode = it.child("cover_image_code").value as String
                                bookimage.add(imageCode)
                                bookavail.add(availableCopies.toString())
                                bookIsbn.add(it.key.toString())         // it.key.toString() => ISBN
                            }

                        }
                        // Book title is not in the "circulation", so use "readBookTitle" to get book titles
                        readBookTitle(bookIsbn)
                    }
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                // Getting BookOrganizationAdapter failed, log a message
//                Log.w(TAG, "loadBook:onCancelled", databaseError.toException())
            }
        }
        sendToFirebaseRef.addValueEventListener(bookListener)
    }


    // To get title of the book, need to go to the different firebase reference ("books")
    // Compare with each book ISBN, if the ISBN matched, then add the book title into the "bookTitle" array
    private fun readBookTitle(isbnArray: ArrayList<String>) {
        booksRef.get().addOnSuccessListener {
            isbnArray.forEach { isbn ->
                // Find the book title from "books" reference
                if (it.hasChild(isbn)) {
                    booktitle.add(it.child(isbn).child("title").value.toString())
                }
            }
            showBooks(bookimage, bookavail, booktitle)
        }.addOnFailureListener {
            Log.e("firebase", "Error getting data", it)
        }

    }

    // Present covers, available copies, and titles.
    @SuppressLint("ResourceType")
    private fun showBooks(
        bookimage: ArrayList<String>,
        bookaval: ArrayList<String>,
        booktitle: ArrayList<String>
    ) {
        gridView = findViewById(R.id.scrollableCheckoutLogView)
        val mainAdapter = LibraryViewActivityAdapter(
            this,
            bookaval,
            bookimage,
            booktitle
        ) // Use adapter to iterate array list
        gridView.adapter = mainAdapter
    }

    // Remove post value event listener
    override fun onPause() {
        sendToFirebaseRef.removeEventListener(bookListener)
        super.onPause()
    }

    //Autogenerated stuff
    public override fun onRestart() {
        super.onRestart()
        finish()
        startActivity(intent)
    }


}

//endregion
//