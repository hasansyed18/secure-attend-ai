package com.example.autoattendance

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logoContainer = findViewById<LinearLayout>(R.id.logoContainer)
        val tvVersion = findViewById<TextView>(R.id.tvVersion)

        val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up)
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)

        logoContainer.startAnimation(slideUp)
        tvVersion.startAnimation(fadeIn)

        FirestoreSeeder.seedIfNeeded()
        com.example.autoattendance.ui.theme.ThemeConfig.load(this)

        Handler(Looper.getMainLooper()).postDelayed({
            val auth = FirebaseAuth.getInstance()
            val userPrefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            val savedRole = userPrefs.getString("role", null)

            if (auth.currentUser != null) {
                Log.d("Routing", "SplashActivity: User logged in, Saved Role: $savedRole")
                if (savedRole != null) {
                    proceedToMain()
                } else {
                    // Reinstall case: role missing in prefs but user logged in.
                    Log.d("Routing", "SplashActivity: Saved role missing, fetching from Firestore")
                    FirebaseFirestore.getInstance()
                        .collection("users").document(auth.currentUser!!.uid).get()
                        .addOnSuccessListener { snapshot ->
                            val role = snapshot.getString("role")
                            Log.d("Routing", "SplashActivity: Firestore role: $role")
                            if (role != null) {
                                userPrefs.edit()
                                    .putString("role", role)
                                    .putString("name", snapshot.getString("name"))
                                    .putString("institutionId", snapshot.getString("institutionId"))
                                    .apply()
                            }
                            proceedToMain()
                        }
                        .addOnFailureListener { e ->
                            Log.e("Routing", "SplashActivity: Firestore fetch failed", e)
                            proceedToMain()
                        }
                }
            } else {
                Log.d("Routing", "SplashActivity: No user logged in")
                proceedToMain()
            }
        }, 2500)
    }

    private fun proceedToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        finish()
    }
}
