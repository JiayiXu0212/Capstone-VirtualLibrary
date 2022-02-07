package com.virtuallibrary.libraworks

/*
AUTHORS: GRANT SAYLOR, KYLE SMITH, ANTHONY TRAN, JIAYI XU
PROJECT START DATE: 10/2020
DESCRIPTION: VIRTUAL LIBRARY APPLICATION
 */

//region Imports
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

//endregion

//Class for the title screen
class NetworkConnectionActivity : AppCompatActivity() {

    //region HelperFunctions
    override fun onCreate(savedInstanceState: Bundle?) {

        //Hide the title screen
        try {
            this.supportActionBar!!.hide()
        } catch (e: NullPointerException) {
        }

        //Call the super and show the right screen
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_no_network)


        val refreshButton = findViewById<Button>(R.id.refreshButton2)
        val refreshButton2 = findViewById<Button>(R.id.buttonWarning)

        refreshButton.setOnClickListener {

            if (checkConnectivity(applicationContext)) {
                //Take to the Barcode screen
                fun onInfoWindowClick() {
                    val intent = Intent(this, TitleScreenActivity::class.java).apply {
                    }
                    finish()
                    overridePendingTransition(0, 0)
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                }
                onInfoWindowClick()
                Toast.makeText(applicationContext, "Connected ", Toast.LENGTH_SHORT).show()
            } else if (!checkConnectivity(applicationContext)) {
                Toast.makeText(applicationContext, "No Connection  ", Toast.LENGTH_SHORT).show()
            }


        }

        refreshButton2.setOnClickListener {

            if (checkConnectivity(applicationContext)) {
                //Take to the Barcode screen
                fun onInfoWindowClick() {
                    val intent = Intent(this, TitleScreenActivity::class.java).apply {
                    }
                    finish()
                    overridePendingTransition(0, 0)
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                }
                onInfoWindowClick()
                Toast.makeText(applicationContext, "Connected ", Toast.LENGTH_SHORT).show()
            } else if (!checkConnectivity(applicationContext)) {
                Toast.makeText(applicationContext, "No Connection  ", Toast.LENGTH_SHORT).show()
            }
        }


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


}