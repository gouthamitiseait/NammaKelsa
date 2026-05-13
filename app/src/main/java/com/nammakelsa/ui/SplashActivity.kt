package com.nammakelsa.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.nammakelsa.R
import com.nammakelsa.repository.FirebaseRepository
import com.nammakelsa.ui.customer.CustomerSearchActivity
import com.nammakelsa.ui.worker.WorkerDashboardActivity

/**
 * SplashActivity.kt
 *
 * App entry point. Shown for 2 seconds (branding), then routes:
 *  - If user already logged in → appropriate dashboard (Worker/Customer)
 *  - If not logged in → LoginActivity (role selection)
 *
 * PRD Section 4.1: Session must persist across app restarts until explicit logout.
 * This activity handles that by checking FirebaseAuth.currentUser.
 *
 * PRD Worker Flow Step 1: Install App → Open → Select Role: Worker
 * PRD Customer Flow Step 9: Install App → Open → Select Role: Customer
 */
class SplashActivity : AppCompatActivity() {

    private val repository = FirebaseRepository()
    private val SPLASH_DELAY_MS = 2000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Hide system action bar for full-screen splash
        supportActionBar?.hide()

        // Wait for splash duration, then route user
        Handler(Looper.getMainLooper()).postDelayed({
            routeUser()
        }, SPLASH_DELAY_MS)
    }

    /**
     * Route to the correct screen based on authentication state.
     * PRD Section 4.1: Role selection saved in Firestore user profile.
     *
     * Reads the 'role' field from SharedPreferences (saved at login).
     * In production, this can also be verified against Firestore.
     */
    private fun routeUser() {
        val currentUser = repository.getCurrentUser()

        if (currentUser == null) {
            // Not logged in → go to LoginActivity (role selection)
            navigateTo(LoginActivity::class.java)
            return
        }

        // User is logged in → check saved role in SharedPreferences
        val prefs = getSharedPreferences("namma_kelsa_prefs", MODE_PRIVATE)
        val savedRole = prefs.getString("user_role", null)

        when (savedRole) {
            "worker" -> navigateTo(WorkerDashboardActivity::class.java)
            "customer" -> navigateTo(CustomerSearchActivity::class.java)
            else -> {
                // Role unknown — send back to login for re-selection
                navigateTo(LoginActivity::class.java)
            }
        }
    }

    private fun navigateTo(destination: Class<*>) {
        startActivity(Intent(this, destination))
        finish() // Remove splash from back stack
    }
}
