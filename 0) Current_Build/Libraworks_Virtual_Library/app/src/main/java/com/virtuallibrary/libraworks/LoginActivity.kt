package com.virtuallibrary.libraworks

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.ErrorCodes
import com.firebase.ui.auth.IdpResponse
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    // Request code for sign in
    private val rcSignIn = 123
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            // Already signed in
            startActivity(Intent(this@LoginActivity, TitleScreenActivity::class.java))
            finish()
        } else {
            // Not signed in. Start the login flow.
            privacyAndTerms()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // RC_SIGN_IN is the request code you passed when starting the sign in flow.
        if (requestCode == rcSignIn) {
            val response = IdpResponse.fromResultIntent(data)
            if (resultCode == Activity.RESULT_OK) {
                // Successfully signed in
                Toast.makeText(applicationContext, "Successfully Signed In ", Toast.LENGTH_SHORT)
                    .show()
                startActivity(Intent(this@LoginActivity, TitleScreenActivity::class.java))
                finish()
                return
            } else {
                // Sign in failed
                if (response == null) {
                    // User pressed back button
                    Log.e("Login", "Login canceled by User")
                    return
                }
                if (response.error!!.errorCode == ErrorCodes.NO_NETWORK) {
                    Log.e("Login", "No Internet Connection")
                    return
                }
                if (response.error!!.errorCode == ErrorCodes.UNKNOWN_ERROR) {
                    Log.e("Login", "Unknown Error")
                    return
                }
            }
            Log.e("Login", "Unknown sign in response")
        }
    }

    private fun privacyAndTerms() {
        val providers = emptyList<AuthUI.IdpConfig>()
        startActivityForResult(
            AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAvailableProviders(providers)
                .setTosAndPrivacyPolicyUrls(
                    "https://github.com/grantsaylor/libraworksToS/blob/main/lvlterms",
                    "https://github.com/grantsaylor/libraworksToS/blob/main/lvltos"
                )
                .build(),
            rcSignIn
        )
    }


}