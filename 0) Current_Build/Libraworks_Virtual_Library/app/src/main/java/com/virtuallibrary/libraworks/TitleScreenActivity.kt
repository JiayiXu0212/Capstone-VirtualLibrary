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
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.firebase.ui.auth.AuthUI
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.smarteist.autoimageslider.SliderView
import kotlin.random.Random


//endregion

//Class for the title screen
class TitleScreenActivity : AppCompatActivity() {

    // Connect to Firebase
    private lateinit var bookListener: ValueEventListener
    private val database = FirebaseDatabase.getInstance()
    private val bookRef = database.getReference("books")

    // Initialize different arrays to load random book covers
    private val imageList: ArrayList<String> = ArrayList()
    private val imageList2: ArrayList<String> = ArrayList()
    private val imageList3: ArrayList<String> = ArrayList()
    private val imageList4: ArrayList<String> = ArrayList()
    private val imageList5: ArrayList<String> = ArrayList()

    // To load all book covers
    private val bookimage = ArrayList<String>()

    //region HelperFunctions
    override fun onCreate(savedInstanceState: Bundle?) {

        //Hide the title screen
        try {
            this.supportActionBar!!.hide()
        } catch (e: NullPointerException) {
        }


        //Call the super and show the right screen
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_titlescreen)

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
        // Random show book cover function
        readBookCover()
        //Create the values for the XML file
        val mapButton = findViewById<Button>(R.id.button)
        val userButton = findViewById<Button>(R.id.button4)
        val searchBooksButton = findViewById<Button>(R.id.button3)
        val signOutButton = findViewById<Button>(R.id.buttonWarning)
        val deleteAccountButton = findViewById<Button>(R.id.button10)

        val auth = FirebaseAuth.getInstance()


        if (auth.currentUser == null) {
            // Not signed in
            val intent = Intent(this, TitleNotSignedInActivity::class.java).apply {
            }
            finish()
            startActivity(intent)

        }

        //Send the user to the correct screen
        mapButton.setOnClickListener {

            fun onInfoWindowClick() {
                val intent = Intent(this, MapsActivity::class.java).apply {
                }

                startActivity(intent)
            }
            onInfoWindowClick()

        }

        //Send the user to the correct screen
        userButton.setOnClickListener {
            fun onInfoWindowClick() {
                val intent = Intent(this, UserActivity::class.java).apply {
                }

                startActivity(intent)
            }
            onInfoWindowClick()

        }

        //Send the user to the correct screen
        signOutButton.setOnClickListener {
            fun onInfoWindowClick() {
                val intent = Intent(this, TitleNotSignedInActivity::class.java).apply {
                }
                finish()
                overridePendingTransition(0, 0)
                startActivity(intent)
                overridePendingTransition(0, 0)
            }
            onInfoWindowClick()
            signOut()
            Toast.makeText(applicationContext, "Successfully Signed Out ", Toast.LENGTH_SHORT)
                .show()

        }

        //Send the user to the correct screen
        deleteAccountButton.setOnClickListener {
            showConfirmationPopup(deleteAccountButton)
        }


        //Send the user to the correct screen
        searchBooksButton.setOnClickListener {
            fun onInfoWindowClick() {
                val intent = Intent(this, SearchActivity::class.java).apply {
                }

                startActivity(intent)
            }

            onInfoWindowClick()

        }

    }

    // Remove post value event listener
    override fun onPause() {
        super.onPause()
        bookRef.removeEventListener(bookListener)
    }

    // New function to set images with auto cycle
    private fun setImageInSlider(images: ArrayList<String>, imageSlider: SliderView) {
        val adapter = TitileImageAdapter()
        adapter.renewItems(images)
        imageSlider.setSliderAdapter(adapter)
        imageSlider.isAutoCycle = true
        imageSlider.startAutoCycle()
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

    // Delete user account
    private fun showConfirmationPopup(view: View) {
        val popup = PopupMenu(this, view)
        popup.inflate(R.menu.popup_confirm_lib_delete)
        val bundle1: Bundle? = intent.extras
        bundle1?.getString("library_id").toString()
        popup.setOnMenuItemClickListener { item: MenuItem? ->
            when (item!!.itemId) {

                R.id.Title -> {

                    // After delete current account, will take user to "Not Signed In" screen
                    fun onInfoWindowClick() {
                        val intent = Intent(this, TitleNotSignedInActivity::class.java).apply {
                        }
                        startActivity(intent)
                    }
                    onInfoWindowClick()
                    delete()
                    Toast.makeText(
                        applicationContext,
                        "Successfully Deleted Account ",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                R.id.Author -> {
                    Toast.makeText(this, "Action Cancelled", Toast.LENGTH_LONG).show()
                }
            }
            true
        }

        popup.show()
    }

    //Signs the user out
    private fun signOut() {
        AuthUI.getInstance()
            .signOut(this)
            .addOnCompleteListener {
                // ...
            }
    }

    //Deletes the user account
    private fun delete() {
        // [START auth_fui_delete]
        AuthUI.getInstance()
            .delete(this)
            .addOnCompleteListener {
                // ...
            }
        // [END auth_fui_delete]
    }

    // Read book covers from Firebase
    private fun readBookCover() {
        bookListener = object : ValueEventListener {

            @SuppressLint("ResourceType")
            override fun onDataChange(dataSnapshot: DataSnapshot) {

                val lib = dataSnapshot.children
                lib.forEach {
                    val imageCode = it.child("cover_image_code").value as String

                    // If book does not have image code, insert a new image code for this book
                    if (imageCode != "8231431") {
                        bookimage.add(imageCode)
                    }
                }
                showCover(bookimage)
            }


            override fun onCancelled(databaseError: DatabaseError) {
                // Getting BookOrganizationAdapter failed, log a message
//                Log.w(TAG, "loadBook:onCancelled", databaseError.toException())
            }
        }
        bookRef.addValueEventListener(bookListener)
    }


    // To show covers on the title screen
    private fun showCover(bookimage: ArrayList<String>) {
        // Set SliderView to every grid
        val imageSlider = findViewById<SliderView>(R.id.imageSlider)
        val imageSlider2 = findViewById<SliderView>(R.id.imageSlider5)
        val imageSlider3 = findViewById<SliderView>(R.id.imageSlider6)
        val imageSlider4 = findViewById<SliderView>(R.id.imageSlider8)
        val imageSlider5 = findViewById<SliderView>(R.id.imageSlider10)
        val imageSlider6 = findViewById<SliderView>(R.id.imageSlider11)
        val imageSlider7 = findViewById<SliderView>(R.id.imageSlider13)
        val imageSlider8 = findViewById<SliderView>(R.id.imageSlider9)
        val imageSlider9 = findViewById<SliderView>(R.id.imageSlider4)
        val imageSlider10 = findViewById<SliderView>(R.id.imageSlider7)

        // Pick random covers from "bookimage" array list 20 times
        for (i in 0..20) {
            imageList.add("https://covers.openlibrary.org/b/id/${bookimage[Random.nextInt(bookimage.size - 1)]}-L.jpg")
            imageList2.add("https://covers.openlibrary.org/b/id/${bookimage[Random.nextInt(bookimage.size - 1)]}-L.jpg")
            imageList3.add("https://covers.openlibrary.org/b/id/${bookimage[Random.nextInt(bookimage.size - 1)]}-L.jpg")
            imageList4.add("https://covers.openlibrary.org/b/id/${bookimage[Random.nextInt(bookimage.size - 1)]}-L.jpg")
            imageList5.add("https://covers.openlibrary.org/b/id/${bookimage[Random.nextInt(bookimage.size - 1)]}-L.jpg")
        }

        // Load images to grids
        setImageInSlider(imageList, imageSlider)
        setImageInSlider(imageList3, imageSlider2)
        setImageInSlider(imageList2, imageSlider3)
        setImageInSlider(imageList5, imageSlider4)
        setImageInSlider(imageList4, imageSlider5)
        setImageInSlider(imageList2, imageSlider6)
        setImageInSlider(imageList, imageSlider7)
        setImageInSlider(imageList4, imageSlider8)
        setImageInSlider(imageList5, imageSlider9)
        setImageInSlider(imageList3, imageSlider10)

    }

    //endregion
}