package com.nammakelsa.ui.worker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.nammakelsa.R
import com.nammakelsa.model.Worker
import com.nammakelsa.repository.FirebaseRepository
import com.nammakelsa.utils.GenAIHelper
import com.nammakelsa.utils.LocationUtils
import kotlinx.coroutines.launch

/**
 * WorkerProfileSetupActivity.kt
 *
 * Worker profile creation and editing screen.
 *
 * PRD Section 4.2 — Worker Profile Module:
 *  - Upload profile photo from camera or gallery
 *  - Fill: Full Name, Skill Type, Daily Rate (INR), Location (city/area)
 *  - Skill Type via ChipGroup — supports multi-skill selection
 *  - Profile data saved to Firestore under /workers/{userId}
 *  - Profile editable at any time from Worker Dashboard
 *
 * PRD F-10 — Badge Suggestion (GenAI):
 *  - "Suggest Badges" button shown after name and skill are filled
 *  - GenAI analyses primary skill → suggests 2-3 related sub-skill badges
 *  - Worker can accept or dismiss individual badge suggestions
 *
 * PRD Worker Flow Steps 2-3:
 *  Register → Create Profile (Name, Skill, Rate, Location) → Upload Profile Photo
 */
class WorkerProfileSetupActivity : AppCompatActivity() {

    // ── Views ──────────────────────────────────────────────────
    private lateinit var ivProfilePhoto: ImageView
    private lateinit var btnChangePhoto: Button
    private lateinit var etFullName: EditText
    private lateinit var etDailyRate: EditText
    private lateinit var etLocation: EditText
    private lateinit var etPhoneNumber: EditText
    private lateinit var chipGroupSkills: ChipGroup
    private lateinit var btnSuggestBadges: Button
    private lateinit var chipGroupSuggestedBadges: ChipGroup
    private lateinit var layoutSuggestedBadges: View
    private lateinit var btnDetectLocation: Button
    private lateinit var btnSaveProfile: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var progressBarAI: ProgressBar

    // ── State ──────────────────────────────────────────────────
    private val repository = FirebaseRepository()
    private val genAI = GenAIHelper()
    private var selectedPhotoUri: Uri? = null
    private val selectedSkills = mutableSetOf<String>()
    private val acceptedBadges = mutableSetOf<String>()

    // Skill options matching PRD ChipGroup options
    private val availableSkills = listOf(
        "Painter", "Plumber", "Electrician", "Tiler",
        "Carpenter", "Welder", "Gardener"
    )

    // ── Photo picker ───────────────────────────────────────────
    private val photoPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) {
                    selectedPhotoUri = uri
                    ivProfilePhoto.setImageURI(uri)
                    showToast("Profile photo selected")
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_worker_profile_setup)

        supportActionBar?.apply {
            title = "My Profile"
            setDisplayHomeAsUpEnabled(true)
        }

        initViews()
        buildSkillChips()
        setupClickListeners()
        loadExistingProfile()
    }

    private fun initViews() {
        ivProfilePhoto = findViewById(R.id.ivProfilePhoto)
        btnChangePhoto = findViewById(R.id.btnChangePhoto)
        etFullName = findViewById(R.id.etFullName)
        etDailyRate = findViewById(R.id.etDailyRate)
        etLocation = findViewById(R.id.etLocation)
        etPhoneNumber = findViewById(R.id.etPhoneNumber)
        chipGroupSkills = findViewById(R.id.chipGroupSkills)
        btnSuggestBadges = findViewById(R.id.btnSuggestBadges)
        chipGroupSuggestedBadges = findViewById(R.id.chipGroupSuggestedBadges)
        layoutSuggestedBadges = findViewById(R.id.layoutSuggestedBadges)
        btnDetectLocation = findViewById(R.id.btnDetectLocation)
        btnSaveProfile = findViewById(R.id.btnSaveProfile)
        progressBar = findViewById(R.id.progressBar)
        progressBarAI = findViewById(R.id.progressBarAI)

        layoutSuggestedBadges.visibility = View.GONE
    }

    // ── SKILL CHIPS (PRD: ChipGroup, multi-skill) ─────────────

    /**
     * Build the skill ChipGroup dynamically.
     * PRD Section 4.2: Skill Type via ChipGroup — multi-skill selection supported.
     * PRD Section 4.5: Filter options match: Painter, Plumber, Electrician, Tiler, Gardener, Carpenter, Welder
     */
    private fun buildSkillChips() {
        chipGroupSkills.removeAllViews()

        availableSkills.forEach { skill ->
            val chip = Chip(this).apply {
                text = skill
                isCheckable = true
                isCheckedIconVisible = true
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedSkills.add(skill)
                    else selectedSkills.remove(skill)
                }
            }
            chipGroupSkills.addView(chip)
        }
    }

    // ── LOAD EXISTING PROFILE ─────────────────────────────────

    /**
     * Pre-fill form if worker already has a profile (edit mode).
     * PRD Section 4.2: Profile must be editable at any time from Worker Dashboard.
     */
    private fun loadExistingProfile() {
        lifecycleScope.launch {
            val result = repository.getCurrentWorkerProfile()
            result.onSuccess { worker ->
                if (worker != null) prefillForm(worker)
            }
        }
    }

    private fun prefillForm(worker: Worker) {
        etFullName.setText(worker.name)
        etDailyRate.setText(worker.dailyRate.toString())
        etLocation.setText(worker.location)
        etPhoneNumber.setText(worker.phoneNumber)

        // Re-check chips for saved skills
        for (i in 0 until chipGroupSkills.childCount) {
            val chip = chipGroupSkills.getChildAt(i) as? Chip ?: continue
            if (worker.skills.contains(chip.text.toString())) {
                chip.isChecked = true
                selectedSkills.add(chip.text.toString())
            }
        }
    }

    // ── CLICK LISTENERS ───────────────────────────────────────

    private fun setupClickListeners() {

        // Profile photo selection
        btnChangePhoto.setOnClickListener { openPhotoPicker() }
        ivProfilePhoto.setOnClickListener { openPhotoPicker() }

        // PRD F-10: Suggest Badges button
        btnSuggestBadges.setOnClickListener { suggestBadgesWithAI() }

        // PRD: GPS location detection (with manual fallback per PRD Section 13 Risks)
        btnDetectLocation.setOnClickListener { detectCurrentLocation() }

        // Save profile to Firestore
        btnSaveProfile.setOnClickListener { saveProfile() }
    }

    /**
     * Open camera or gallery photo picker.
     * PRD Section 4.2: Upload profile photo from camera or gallery.
     */
    private fun openPhotoPicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
        }
        photoPickerLauncher.launch(intent)
    }

    // ── F-10: AI BADGE SUGGESTION ─────────────────────────────

    /**
     * Trigger GenAI badge suggestion for the worker's primary skill.
     * PRD Section 5.3:
     *  - Worker fills name and skill → "Suggest Badges" button appears
     *  - GenAI analyses primary skill → suggests 2-3 sub-skill badges
     *  - Worker can accept or dismiss individual badge suggestions
     *
     * Example: Painter → [Waterproofing] [Texture Painting] [Wall Putty]
     */
    private fun suggestBadgesWithAI() {
        if (selectedSkills.isEmpty()) {
            showToast("Please select at least one skill first")
            return
        }

        // PRD Section 5.4: Show loading indicator during GenAI calls
        progressBarAI.visibility = View.VISIBLE
        btnSuggestBadges.isEnabled = false
        btnSuggestBadges.text = "Asking Gemini AI…"

        val primarySkill = selectedSkills.first()

        lifecycleScope.launch {
            val result = genAI.suggestSkillBadges(primarySkill)

            progressBarAI.visibility = View.GONE
            btnSuggestBadges.isEnabled = true
            btnSuggestBadges.text = "✨ Suggest Skill Badges"

            result.onSuccess { badges ->
                showSuggestedBadges(badges)
                showToast("AI suggested ${badges.size} skill badges!")
            }

            // PRD Section 5.4: Graceful error handling — allow manual entry if API fails
            result.onFailure { e ->
                showToast("AI suggestion failed. You can add badges manually.")
                layoutSuggestedBadges.visibility = View.GONE
            }
        }
    }

    /**
     * Render suggested badge chips.
     * Each chip can be: accepted (added to profile) or dismissed (removed).
     */
    private fun showSuggestedBadges(badges: List<String>) {
        chipGroupSuggestedBadges.removeAllViews()
        acceptedBadges.clear()

        badges.forEach { badge ->
            val chip = Chip(this).apply {
                text = badge
                isCheckable = true
                isCloseIconVisible = true
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) acceptedBadges.add(badge)
                    else acceptedBadges.remove(badge)
                }
                setOnCloseIconClickListener {
                    // Dismiss this badge suggestion
                    chipGroupSuggestedBadges.removeView(this)
                    acceptedBadges.remove(badge)
                }
            }
            chipGroupSuggestedBadges.addView(chip)
        }

        layoutSuggestedBadges.visibility = View.VISIBLE
    }

    // ── LOCATION DETECTION ────────────────────────────────────

    /**
     * Detect current GPS location and fill the location field.
     * PRD: Uses Android FusedLocationProvider API via LocationUtils.
     * PRD Section 13 (Risks): GPS unavailable fallback — manual city/area entry.
     */
    private fun detectCurrentLocation() {
        if (!LocationUtils.hasLocationPermission(this)) {
            requestPermissions(
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
            return
        }

        showToast("Detecting your location…")
        lifecycleScope.launch {
            val location = LocationUtils.getCurrentLocation(this@WorkerProfileSetupActivity)
            if (location != null) {
                // In production: reverse geocode lat/lon to area name
                // For now, show coordinates as placeholder
                etLocation.setText("Lat: ${location.latitude}, Lon: ${location.longitude}")
                showToast("Location detected! Please edit to area name.")
            } else {
                showToast("Could not detect location. Please enter manually.")
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION &&
            grantResults.isNotEmpty() &&
            grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            detectCurrentLocation()
        }
    }

    // ── SAVE PROFILE ─────────────────────────────────────────

    /**
     * Validate inputs and save worker profile to Firestore.
     * PRD Section 4.2: Data saved under /workers/{userId}.
     * PRD Section 4.2: Includes accepted AI-suggested badges in skills list.
     */
    private fun saveProfile() {
        val name = etFullName.text.toString().trim()
        val rateText = etDailyRate.text.toString().trim()
        val location = etLocation.text.toString().trim()
        val phone = etPhoneNumber.text.toString().trim()

        // Validation
        if (name.isEmpty()) { showToast("Please enter your full name"); return }
        if (rateText.isEmpty()) { showToast("Please enter your daily rate"); return }
        val rate = rateText.toIntOrNull()
        if (rate == null || rate < 100) { showToast("Enter a valid daily rate (minimum ₹100)"); return }
        if (selectedSkills.isEmpty()) { showToast("Please select at least one skill"); return }
        if (location.isEmpty()) { showToast("Please enter your location"); return }
        if (phone.isEmpty()) { showToast("Please enter your phone number"); return }

        // Merge accepted AI badges into skills list
        val allSkills = (selectedSkills + acceptedBadges).toList()

        val worker = Worker(
            userId = repository.getCurrentUserId() ?: "",
            name = name,
            skills = allSkills,
            dailyRate = rate,
            location = location,
            phoneNumber = phone
            // profilePhotoUrl updated separately after upload
        )

        progressBar.visibility = View.VISIBLE
        btnSaveProfile.isEnabled = false

        lifecycleScope.launch {
            // Save profile text data
            val saveResult = repository.saveWorkerProfile(worker)

            // If a photo was selected, upload it too
            if (selectedPhotoUri != null) {
                repository.uploadProfilePhoto(selectedPhotoUri!!)
            }

            progressBar.visibility = View.GONE
            btnSaveProfile.isEnabled = true

            saveResult.onSuccess {
                showToast("Profile saved successfully! ✅")
                finish() // Return to dashboard
            }

            saveResult.onFailure { e ->
                showToast("Failed to save profile: ${e.message}")
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 1001
    }
}
