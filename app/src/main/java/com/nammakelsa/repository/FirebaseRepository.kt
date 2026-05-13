package com.nammakelsa.repository



import android.net.Uri

import android.util.Log

import com.google.firebase.auth.FirebaseAuth

import com.google.firebase.auth.FirebaseUser

import com.google.firebase.auth.PhoneAuthCredential

import com.google.firebase.firestore.FirebaseFirestore

import com.google.firebase.firestore.Query

import com.google.firebase.storage.FirebaseStorage

import com.nammakelsa.model.Worker

import kotlinx.coroutines.tasks.await



/**

 * FirebaseRepository.kt

 *

 * Single source of truth for all Firebase operations.

 * PRD Section 6.5 — No direct Firestore calls in UI code.

 * All Auth, Firestore, and Storage interactions go through here.

 *

 * Follows Repository Pattern — UI activities call these functions only.

 */

class FirebaseRepository {



    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val storage: FirebaseStorage = FirebaseStorage.getInstance()



    companion object {

        private const val TAG = "FirebaseRepository"

        private const val WORKERS_COLLECTION = "workers"

        private const val GALLERIES_PATH = "galleries"

        private const val PROFILES_PATH = "profiles"

    }



// =========================================================

// SECTION 1 — AUTHENTICATION (F-01)

// PRD: Firebase Authentication — phone OTP / email+password

// =========================================================



    /** Returns the currently signed-in Firebase user, or null */

    fun getCurrentUser(): FirebaseUser? = auth.currentUser



    /** Returns the UID of the currently signed-in user */

    fun getCurrentUserId(): String? = auth.currentUser?.uid



    /** Returns true if a user is currently logged in */

    fun isLoggedIn(): Boolean = auth.currentUser != null



    /**

     * Sign in with phone OTP credential.

     * Called after user enters OTP from Firebase Phone Auth.

     * @param credential PhoneAuthCredential from PhoneAuthProvider

     * @return Result<FirebaseUser> — Success or Failure with exception

     */

    suspend fun signInWithPhoneCredential(credential: PhoneAuthCredential): Result<FirebaseUser> {

        return try {

            val result = auth.signInWithCredential(credential).await()

            val user = result.user ?: throw Exception("Authentication failed — user is null")

            Log.d(TAG, "Phone sign-in success: ${user.uid}")

            Result.success(user)

        } catch (e: Exception) {

            Log.e(TAG, "Phone sign-in failed", e)

            Result.failure(e)

        }

    }



    /**

     * Sign in with email and password.

     * @return Result<FirebaseUser>

     */

    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> {

        return try {

            val result = auth.signInWithEmailAndPassword(email, password).await()

            val user = result.user ?: throw Exception("Sign-in failed")

            Result.success(user)

        } catch (e: Exception) {

            Log.e(TAG, "Email sign-in failed", e)

            Result.failure(e)

        }

    }



    /**

     * Register a new user with email and password.

     * @return Result<FirebaseUser>

     */

    suspend fun registerWithEmail(email: String, password: String): Result<FirebaseUser> {

        return try {

            val result = auth.createUserWithEmailAndPassword(email, password).await()

            val user = result.user ?: throw Exception("Registration failed")

            Result.success(user)

        } catch (e: Exception) {

            Log.e(TAG, "Email registration failed", e)

            Result.failure(e)

        }

    }



    /** Sign out current user */

    fun signOut() {

        auth.signOut()

        Log.d(TAG, "User signed out")

    }



// =========================================================

// SECTION 2 — WORKER PROFILE (F-02)

// PRD: /workers/{userId} — Firestore document

// =========================================================



    /**

     * Save or update a worker profile in Firestore.

     * Document ID = Firebase Auth UID.

     * @param worker Worker data object to save

     * @return Result<Unit>

     */

    suspend fun saveWorkerProfile(worker: Worker): Result<Unit> {

        val uid = getCurrentUserId() ?: return Result.failure(Exception("Not authenticated"))

        return try {

            db.collection(WORKERS_COLLECTION)

                .document(uid)

                .set(worker)

                .await()

            Log.d(TAG, "Worker profile saved: $uid")

            Result.success(Unit)

        } catch (e: Exception) {

            Log.e(TAG, "Failed to save worker profile", e)

            Result.failure(e)

        }

    }



    /**

     * Fetch the current logged-in worker's profile from Firestore.

     * @return Result<Worker?>

     */

    suspend fun getCurrentWorkerProfile(): Result<Worker?> {

        val uid = getCurrentUserId() ?: return Result.failure(Exception("Not authenticated"))

        return getWorkerById(uid)

    }



    /**

     * Fetch any worker's profile by their userId.

     * Used in WorkerDetailActivity.

     * @param userId Firebase Auth UID

     * @return Result<Worker?>

     */

    suspend fun getWorkerById(userId: String): Result<Worker?> {

        return try {

            val snapshot = db.collection(WORKERS_COLLECTION)

                .document(userId)

                .get()

                .await()

            val worker = snapshot.toObject(Worker::class.java)

            Result.success(worker)

        } catch (e: Exception) {

            Log.e(TAG, "Failed to fetch worker $userId", e)

            Result.failure(e)

        }

    }



    /**

     * Update a specific field on the worker's Firestore document.

     * Used for partial updates (e.g. availability toggle only).

     * @param field Firestore field name

     * @param value New value

     */

    suspend fun updateWorkerField(field: String, value: Any): Result<Unit> {

        val uid = getCurrentUserId() ?: return Result.failure(Exception("Not authenticated"))

        return try {

            db.collection(WORKERS_COLLECTION)

                .document(uid)

                .update(field, value)

                .await()

            Log.d(TAG, "Updated field '$field' for worker $uid")

            Result.success(Unit)

        } catch (e: Exception) {

            Log.e(TAG, "Failed to update field '$field'", e)

            Result.failure(e)

        }

    }



// =========================================================

// SECTION 3 — AVAILABILITY TOGGLE (F-04)

// PRD: Real-time Firestore sync — available: true/false

// Auto-reset at midnight checked in onCreate()

// =========================================================



    /**

     * Update the worker's availability status in real-time.

     * PRD requirement: must reflect in Customer search within 2 seconds.

     * @param available true = available for work today

     */

    suspend fun updateAvailability(available: Boolean): Result<Unit> {

        return updateWorkerField("available", available)

    }



    /**

     * Check and reset availability if it's a new day.

     * Called in WorkerDashboardActivity.onCreate() per PRD spec.

     * Compares lastUpdated date with today's date.

     */

    suspend fun resetAvailabilityIfNewDay(): Result<Unit> {

        val uid = getCurrentUserId() ?: return Result.failure(Exception("Not authenticated"))

        return try {

            val snapshot = db.collection(WORKERS_COLLECTION)

                .document(uid)

                .get()

                .await()

            val worker = snapshot.toObject(Worker::class.java)

            val lastUpdated = worker?.lastUpdated?.toDate()

            val today = java.util.Calendar.getInstance()



            if (lastUpdated != null) {

                val lastCal = java.util.Calendar.getInstance()

                lastCal.time = lastUpdated

                val isNewDay = today.get(java.util.Calendar.DAY_OF_YEAR) !=

                        lastCal.get(java.util.Calendar.DAY_OF_YEAR) ||

                        today.get(java.util.Calendar.YEAR) != lastCal.get(java.util.Calendar.YEAR)



                if (isNewDay && worker.available) {

                    Log.d(TAG, "New day detected — resetting availability to false")

                    updateAvailability(false)

                }

            }

            Result.success(Unit)

        } catch (e: Exception) {

            Log.e(TAG, "Failed to check/reset availability", e)

            Result.failure(e)

        }

    }



// =========================================================

// SECTION 4 — CUSTOMER SEARCH (F-05)

// PRD: Filter by skill + distance, available workers only

// =========================================================



    /**

     * Fetch all workers who are available today.

     * Optionally filtered by skill type.

     * Results sorted by distance on the client side (Firestore

     * doesn't support geo-distance sorting natively without GeoFirestore).

     *

     * @param skillFilter Skill to filter by. Null or empty = all skills.

     * @return Result<List<Worker>>

     */

    suspend fun getAvailableWorkers(skillFilter: String? = null): Result<List<Worker>> {

        return try {

            var query: Query = db.collection(WORKERS_COLLECTION)

                .whereEqualTo("available", true)



// Note: Firestore array-contains for skill filtering

            if (!skillFilter.isNullOrBlank() && skillFilter != "All") {

                query = query.whereArrayContains("skills", skillFilter)

            }



            val snapshot = query.get().await()

            val workers = snapshot.toObjects(Worker::class.java)

            Log.d(TAG, "Fetched ${workers.size} available workers (filter: $skillFilter)")

            Result.success(workers)

        } catch (e: Exception) {

            Log.e(TAG, "Failed to fetch available workers", e)

            Result.failure(e)

        }

    }



    /**

     * Listen to available workers in real-time using Firestore snapshot listener.

     * More efficient than polling — updates CustomerSearchActivity live.

     * @param skillFilter Optional skill filter

     * @param onUpdate Callback invoked whenever data changes

     * @return ListenerRegistration (call .remove() in onDestroy to stop listening)

     */

    fun listenToAvailableWorkers(

        skillFilter: String? = null,

        onUpdate: (List<Worker>) -> Unit,

        onError: (Exception) -> Unit

    ): com.google.firebase.firestore.ListenerRegistration {

        var query: Query = db.collection(WORKERS_COLLECTION)

            .whereEqualTo("available", true)



        if (!skillFilter.isNullOrBlank() && skillFilter != "All") {

            query = query.whereArrayContains("skills", skillFilter)

        }



        return query.addSnapshotListener { snapshot, error ->

            if (error != null) {

                Log.e(TAG, "Real-time listener error", error)

                onError(error)

                return@addSnapshotListener

            }

            val workers = snapshot?.toObjects(Worker::class.java) ?: emptyList()

            onUpdate(workers)

        }

    }



// =========================================================

// SECTION 5 — WORK GALLERY (F-03)

// PRD: Up to 3 photos in Firebase Storage /galleries/{userId}/

// =========================================================



    /**

     * Upload a gallery photo to Firebase Storage.

     * Path: /galleries/{userId}/{slot}.jpg (slot = 0, 1, or 2)

     * After upload, saves the download URL to Firestore.

     *

     * @param imageUri Local URI of the selected image

     * @param slot Gallery slot index (0, 1, or 2)

     * @return Result<String> — Download URL on success

     */

    /**

     * Upload a gallery photo to Firebase Storage.

     * Path: galleries/{userId}/photo_{slot}.jpg

     */

    suspend fun uploadGalleryPhoto(imageUri: Uri, slot: Int): Result<String> {



        val uid = getCurrentUserId()

            ?: return Result.failure(Exception("User not authenticated"))



        return try {



// Correct Firebase Storage path

            val storageRef = FirebaseStorage.getInstance()

                .reference

                .child("galleries")

                .child(uid)

                .child("photo_${slot}.jpg")



            Log.d(TAG, "Uploading gallery photo to: ${storageRef.path}")



// Upload image

            val uploadTask = storageRef.putFile(imageUri).await()



// Ensure upload succeeded

            if (uploadTask.metadata == null) {

                throw Exception("Upload failed: metadata is null")

            }



// Get download URL

            val downloadUrl = storageRef.downloadUrl.await().toString()



            Log.d(TAG, "Gallery photo uploaded successfully: $downloadUrl")



// Fetch current worker document

            val snapshot = db.collection(WORKERS_COLLECTION)

                .document(uid)

                .get()

                .await()



            val worker = snapshot.toObject(Worker::class.java)



// Ensure gallery list always has 3 slots

            val currentUrls = worker?.galleryUrls?.toMutableList()

                ?: mutableListOf("", "", "")



            while (currentUrls.size < 3) {

                currentUrls.add("")

            }



// Update slot URL

            currentUrls[slot] = downloadUrl



// Save updated gallery URLs

            db.collection(WORKERS_COLLECTION)

                .document(uid)

                .update("galleryUrls", currentUrls)

                .await()



            Log.d(TAG, "Firestore galleryUrls updated")



            Result.success(downloadUrl)



        } catch (e: Exception) {



            Log.e(TAG, "Failed to upload gallery photo", e)



            Result.failure(

                Exception(

                    "Gallery upload failed: ${e.message}"

                )

            )

        }

    }



    /**

     * Update gallery photo description in Firestore.

     * Called after GenAI auto-describe or manual edit.

     * @param slot Gallery slot index (0, 1, or 2)

     * @param description Text description to save

     */

    suspend fun updateGalleryDescription(slot: Int, description: String): Result<Unit> {

        val uid = getCurrentUserId() ?: return Result.failure(Exception("Not authenticated"))

        return try {

            val snapshot = db.collection(WORKERS_COLLECTION).document(uid).get().await()

            val worker = snapshot.toObject(Worker::class.java)

            val descriptions = worker?.galleryDescriptions?.toMutableList()

                ?: mutableListOf("", "", "")

            while (descriptions.size <= slot) descriptions.add("")

            descriptions[slot] = description



            db.collection(WORKERS_COLLECTION)

                .document(uid)

                .update("galleryDescriptions", descriptions)

                .await()



            Log.d(TAG, "Gallery description updated for slot $slot")

            Result.success(Unit)

        } catch (e: Exception) {

            Log.e(TAG, "Failed to update gallery description", e)

            Result.failure(e)

        }

    }



    /**

     * Upload profile photo to Firebase Storage.

     * Path: /profiles/{userId}/profile.jpg

     * @param imageUri Local URI of the selected photo

     * @return Result<String> — Download URL

     */

    suspend fun uploadProfilePhoto(imageUri: Uri): Result<String> {

        val uid = getCurrentUserId() ?: return Result.failure(Exception("Not authenticated"))

        return try {

            val storageRef = storage.reference

                .child("$PROFILES_PATH/$uid/profile.jpg")



            storageRef.putFile(imageUri).await()

            val downloadUrl = storageRef.downloadUrl.await().toString()



            db.collection(WORKERS_COLLECTION)

                .document(uid)

                .update("profilePhotoUrl", downloadUrl)

                .await()



            Log.d(TAG, "Profile photo uploaded: $downloadUrl")

            Result.success(downloadUrl)

        } catch (e: Exception) {

            Log.e(TAG, "Failed to upload profile photo", e)

            Result.failure(e)

        }

    }



// =========================================================

// SECTION 6 — FIRESTORE OFFLINE PERSISTENCE (F-11)

// PRD: Core profile data viewable without internet

// Enable once in Application class — not called here directly

// =========================================================



    /**

     * Enable Firestore offline persistence.

     * Call this ONCE from NammaKelsaApplication (Application class),

     * before any Firestore usage.

     * PRD F-11: Offline support for core profile data.

     */

    fun enableOfflinePersistence() {

// Called once in Application class:

// FirebaseFirestore.getInstance().firestoreSettings =

// FirebaseFirestoreSettings.Builder()

// .setPersistenceEnabled(true)

// .build()

        Log.d(TAG, "Offline persistence should be enabled in Application class")

    }

}