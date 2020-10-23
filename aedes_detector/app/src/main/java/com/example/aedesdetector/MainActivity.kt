package com.example.aedesdetector

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import cafe.adriel.androidaudioconverter.AndroidAudioConverter
import cafe.adriel.androidaudioconverter.callback.ILoadCallback
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val navView: BottomNavigationView = findViewById(R.id.nav_view)

        val navController = findNavController(R.id.nav_host_fragment)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(setOf(
                R.id.navigation_map, R.id.navigation_recorder))
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        AndroidAudioConverter.load(this, object : ILoadCallback {
            override fun onSuccess() {
                Log.d("INFO", "Loaded audio converter lib")
            }

            override fun onFailure(error: Exception) {
                // FFmpeg is not supported by device
                Log.d("INFO", "Failed to load audio converter")
            }
        })
        auth = FirebaseAuth.getInstance()
        auth.signInAnonymously()
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d("DEBUG", "signInAnonymously:success")
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w("DEBUG", "signInAnonymously:failure", task.exception)
                }

                // ...
            }
    }
}
