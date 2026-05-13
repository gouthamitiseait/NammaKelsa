package com.nammakelsa.ui.worker

import android.app.Activity
import com.bumptech.glide.Glide
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nammakelsa.R
import com.nammakelsa.model.Worker
import com.nammakelsa.repository.FirebaseRepository
import com.nammakelsa.utils.GenAIHelper
import kotlinx.coroutines.launch

/**
 * WorkGalleryActivity.kt
 *
 * PRD Section 4.3 — Work Gallery Module:
 *  - Worker uploads up to 3 photos of recent completed work
 *  - Photos stored in Firebase Storage under /galleries/{userId}/
 *  - Displayed as a horizontal scrollable gallery on the Worker's public profile
 *  - Worker can replace existing gallery photos at any time
 *
 * PRD F-08 — GenAI Skill Description Generator:
 *  - When worker uploads a gallery photo, "Auto-Describe" button appears
 *  - Pressing it sends the image to Gemini Vision API
 *  - Returns a 1-2 sentence professional description
 *  - Worker can accept the description or edit it before saving
 *
 * PRD Worker Flow Steps 3-4:
 *  Upload 3 Work Gallery Photos → [Optional] Use GenAI to auto-generate descriptions
 */
class WorkGalleryActivity : AppCompatActivity() {

    // ── Gallery Slot Views (3 slots as per PRD) ────────────────
    // Each slot has: ImageView, Upload button, EditText description, AI button
    private lateinit var slots: List<GallerySlot>
    private lateinit var btnSaveGallery: Button
    private lateinit var progressBar: ProgressBar

    // ── State ──────────────────────────────────────────────────
    private val repository = FirebaseRepository()
    private val genAI = GenAIHelper()
    private var currentWorker: Worker? = null

    // Currently active slot waiting for photo pick result
    private var activeSlotIndex: Int = 0

    // ── Photo picker ───────────────────────────────────────────
    private val photoPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data ?: return@registerForActivityResult
                val slot = slots[activeSlotIndex]
                slot.selectedUri = uri
                slot.imageView.setImageURI(uri)
                slot.imageView.visibility = View.VISIBLE
                slot.btnUpload.text = "Change Photo"

                // Show AI Auto-Describe button after photo is selected
                slot.btnAutoDescribe.visibility = View.VISIBLE
                showToast("Photo selected! Tap '✨ Auto-Describe' for AI description.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_work_gallery)

        supportActionBar?.apply {
            title = "Work Gallery"
            setDisplayHomeAsUpEnabled(true)
        }

        initGallerySlots()
        setupSaveButton()
        loadExistingGallery()
    }

    // ── SLOT SETUP ────────────────────────────────────────────

    /**
     * Data class representing one gallery slot (photo + description).
     * PRD: Up to 3 photos with individual descriptions.
     */
    data class GallerySlot(
        val index: Int,
        val imageView: ImageView,
        val btnUpload: Button,
        val etDescription: EditText,
        val btnAutoDescribe: Button,
        val progressBarAI: ProgressBar,
        var selectedUri: Uri? = null,
        var bitmap: Bitmap? = null
    )

    /**
     * Initialize all 3 gallery slots from layout.
     * Each slot is independently uploadable.
     */
    private fun initGallerySlots() {
        slots = listOf(
            GallerySlot(
                index = 0,
                imageView = findViewById(R.id.ivGallery1),
                btnUpload = findViewById(R.id.btnUpload1),
                etDescription = findViewById(R.id.etDesc1),
                btnAutoDescribe = findViewById(R.id.btnAutoDescribe1),
                progressBarAI = findViewById(R.id.progressAI1)
            ),
            GallerySlot(
                index = 1,
                imageView = findViewById(R.id.ivGallery2),
                btnUpload = findViewById(R.id.btnUpload2),
                etDescription = findViewById(R.id.etDesc2),
                btnAutoDescribe = findViewById(R.id.btnAutoDescribe2),
                progressBarAI = findViewById(R.id.progressAI2)
            ),
            GallerySlot(
                index = 2,
                imageView = findViewById(R.id.ivGallery3),
                btnUpload = findViewById(R.id.btnUpload3),
                etDescription = findViewById(R.id.etDesc3),
                btnAutoDescribe = findViewById(R.id.btnAutoDescribe3),
                progressBarAI = findViewById(R.id.progressAI3)
            )
        )

        progressBar = findViewById(R.id.progressBar)
        btnSaveGallery = findViewById(R.id.btnSaveGallery)

        // Initially hide AI buttons until photos are selected
        slots.forEach { slot ->
            slot.btnAutoDescribe.visibility = View.GONE
            slot.progressBarAI.visibility = View.GONE

            // Upload button → open photo picker for this slot
            slot.btnUpload.setOnClickListener {
                activeSlotIndex = slot.index
                openPhotoPicker()
            }

            // AI Auto-Describe button → call Gemini Vision API
            slot.btnAutoDescribe.setOnClickListener {
                autoDescribePhoto(slot)
            }
        }
    }

    // ── LOAD EXISTING GALLERY ─────────────────────────────────

    /**
     * Pre-fill gallery with previously uploaded photos and descriptions.
     * PRD Section 4.3: Worker can replace existing gallery photos at any time.
     */
    private fun loadExistingGallery() {
        lifecycleScope.launch {
            val result = repository.getCurrentWorkerProfile()
            result.onSuccess { worker ->
                currentWorker = worker
                if (worker != null) {
                    worker.galleryUrls.forEachIndexed { index, url ->
                        if (index < slots.size && url.isNotBlank()) {
                            // In production: use Glide to load image from URL
                            Glide.with(this@WorkGalleryActivity)
                                .load(url)
                                .placeholder(android.R.drawable.ic_menu_gallery)
                                .error(android.R.drawable.ic_delete)
                                .into(slots[index].imageView)
                            slots[index].btnUpload.text = "Change Photo"
                            slots[index].btnAutoDescribe.visibility = View.VISIBLE
                        }
                    }
                    worker.galleryDescriptions.forEachIndexed { index, desc ->
                        if (index < slots.size && desc.isNotBlank()) {
                            slots[index].etDescription.setText(desc)
                        }
                    }
                }
            }
        }
    }

    // ── PHOTO PICKER ─────────────────────────────────────────

    /**
     * Open the device photo gallery picker.
     * PRD Section 4.3: Photos stored in Firebase Storage under /galleries/{userId}/
     */
    private fun openPhotoPicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
        }
        photoPickerLauncher.launch(intent)
    }

    // ── F-08: AI AUTO-DESCRIBE ────────────────────────────────

    /**
     * Auto-generate a professional description for the selected gallery photo.
     * PRD Section 5.1:
     *  - Sends image to Gemini Vision API
     *  - Returns 1-2 sentence professional description
     *  - Worker can accept the description or edit it before saving
     *
     * PRD Section 5.4:
     *  - Loading indicator must be shown during all GenAI API calls
     *  - Graceful error handling: if API fails, show message + allow manual entry
     */
    private fun autoDescribePhoto(slot: GallerySlot) {
        val uri = slot.selectedUri
        if (uri == null) {
            showToast("Please select a photo first")
            return
        }

        val worker = currentWorker
        if (worker == null) {
            showToast("Profile not loaded. Please wait.")
            return
        }

        // PRD: Show loading indicator during GenAI call
        slot.progressBarAI.visibility = View.VISIBLE
        slot.btnAutoDescribe.isEnabled = false
        slot.btnAutoDescribe.text = "Asking Gemini Vision…"

        lifecycleScope.launch {
            // Convert URI to Bitmap for Gemini Vision API
            val bitmap = try {
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            } catch (e: Exception) {
                null
            }

            if (bitmap == null) {
                slot.progressBarAI.visibility = View.GONE
                slot.btnAutoDescribe.isEnabled = true
                slot.btnAutoDescribe.text = "✨ Auto-Describe"
                showToast("Could not read image. Please try again.")
                return@launch
            }

            val result = genAI.generateSkillDescription(bitmap, worker.skills)

            // Hide loading, re-enable button
            slot.progressBarAI.visibility = View.GONE
            slot.btnAutoDescribe.isEnabled = true
            slot.btnAutoDescribe.text = "✨ Auto-Describe"

            result.onSuccess { description ->
                // PRD: Worker can accept or edit before saving
                slot.etDescription.setText(description)
                slot.etDescription.setSelection(description.length) // Cursor at end
                showToast("AI description generated! Edit if needed, then Save Gallery.")
            }

            // PRD Section 5.4: Graceful error handling — allow manual entry
            result.onFailure { e ->
                showToast("AI description failed. You can write a description manually.")
            }
        }
    }

    // ── SAVE GALLERY ─────────────────────────────────────────

    /**
     * Upload all selected photos and descriptions to Firebase.
     * PRD Section 4.3: Photos stored in Firebase Storage under /galleries/{userId}/
     * PRD Section 4.3: Worker can replace existing gallery photos.
     */
    private fun setupSaveButton() {
        btnSaveGallery.setOnClickListener { saveGallery() }
    }

    private fun saveGallery() {
        val hasAnyPhoto = slots.any { it.selectedUri != null }
        if (!hasAnyPhoto) {
            showToast("Please select at least one photo")
            return
        }

        progressBar.visibility = View.VISIBLE
        btnSaveGallery.isEnabled = false

        lifecycleScope.launch {
            var successCount = 0
            var failCount = 0

            slots.forEach { slot ->
                val uri = slot.selectedUri
                if (uri != null) {
                    // Upload photo to Firebase Storage
                    val uploadResult = repository.uploadGalleryPhoto(uri, slot.index)
                    if (uploadResult.isSuccess) {
                        successCount++
                    } else {
                        failCount++
                    }
                }

                // Save description to Firestore (even if no new photo)
                val description = slot.etDescription.text.toString().trim()
                if (description.isNotBlank()) {
                    repository.updateGalleryDescription(slot.index, description)
                }
            }

            progressBar.visibility = View.GONE
            btnSaveGallery.isEnabled = true

            when {
                failCount == 0 -> {
                    showToast("Gallery saved successfully! ✅")
                    finish()
                }
                successCount > 0 -> {
                    showToast("$successCount photo(s) saved. $failCount failed — check connection.")
                }
                else -> {
                    showToast("Upload failed. Check internet connection.")
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
