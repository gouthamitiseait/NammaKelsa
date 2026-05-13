package com.nammakelsa

import android.app.Application
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

/**
 * NammaKelsaApplication.kt
 *
 * Custom Application class. Called once when the app process starts.
 * Registered in AndroidManifest.xml: android:name=".NammaKelsaApplication"
 *
 * Responsibilities:
 *  1. Enable Firestore offline persistence — PRD F-11
 *  2. Any other one-time app-level initialization
 *
 * PRD F-11 — Offline Support:
 *  "Firestore offline persistence enabled — core profile data
 *   must be viewable without internet."
 *
 * PRD Section 6.3 — Reliability:
 *  "App must not crash on low-memory devices (tested on 2 GB RAM)"
 */
class NammaKelsaApplication : Application() {

    companion object {
        private const val TAG = "NammaKelsaApp"
    }

    override fun onCreate() {
        super.onCreate()
        initFirestoreOfflinePersistence()
    }

    /**
     * Enable Firestore offline persistence.
     *
     * PRD F-11: Core profile data must be viewable without internet.
     * This caches Firestore reads locally on the device so that
     * worker profiles and search results are available when offline.
     *
     * Must be called BEFORE any Firestore operations.
     * Must be called only ONCE — hence in Application class.
     */
    private fun initFirestoreOfflinePersistence() {
        try {
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)         // Enable local disk cache
                .setCacheSizeBytes(
                    FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED
                )                                    // No cache size limit for profile data
                .build()

            FirebaseFirestore.getInstance().firestoreSettings = settings
            Log.d(TAG, "Firestore offline persistence enabled")

        } catch (e: Exception) {
            // Persistence setup can fail if called multiple times (e.g. in tests)
            Log.w(TAG, "Firestore persistence init warning: ${e.message}")
        }
    }
}
