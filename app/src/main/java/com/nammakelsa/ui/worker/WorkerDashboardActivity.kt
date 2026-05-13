package com.nammakelsa.ui.worker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.bumptech.glide.Glide
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.nammakelsa.ui.customer.WorkerDetailActivity
import com.nammakelsa.ui.LoginActivity
import com.nammakelsa.utils.GenAIHelper
import androidx.lifecycle.lifecycleScope
import com.nammakelsa.R
import com.nammakelsa.model.Worker
import com.nammakelsa.repository.FirebaseRepository
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth

class WorkerDashboardActivity : AppCompatActivity() {

    // ── Views ──────────────────────────────────────────────────
    private lateinit var tvWorkerName: TextView
    private lateinit var tvSkills: TextView
    private lateinit var ivProfilePhoto: ImageView
    private lateinit var switchAvailability: SwitchCompat
    private lateinit var tvAvailabilityStatus: TextView
    private lateinit var tvJobsCompleted: TextView
    private lateinit var tvDailyRate: TextView
    private lateinit var tvRating: TextView
    private lateinit var btnEditProfile: Button
    private lateinit var btnWorkGallery: Button
    private lateinit var btnPreviewProfile: Button
    private lateinit var btnAiFeatures: Button
    private lateinit var btnSuggestBadges: Button
    private lateinit var chipGroupBadges: ChipGroup
    private val genAI = GenAIHelper()
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutContent: View
    private lateinit var btnLogout: Button

    // ── State ──────────────────────────────────────────────────
    private val repository = FirebaseRepository()
    private var currentWorker: Worker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_worker_dashboard)
        supportActionBar?.hide()

        initViews()
        setupClickListeners()
        loadWorkerProfile()

        lifecycleScope.launch {
            repository.resetAvailabilityIfNewDay()
        }
    }

    private fun initViews() {
        tvWorkerName = findViewById(R.id.tvWorkerName)
        tvSkills = findViewById(R.id.tvSkills)
        ivProfilePhoto = findViewById(R.id.ivProfilePhoto)
        switchAvailability = findViewById(R.id.switchAvailability)
        tvAvailabilityStatus = findViewById(R.id.tvAvailabilityStatus)
        tvJobsCompleted = findViewById(R.id.tvJobsCompleted)
        tvDailyRate = findViewById(R.id.tvDailyRate)
        tvRating = findViewById(R.id.tvRating)
        btnEditProfile = findViewById(R.id.btnEditProfile)
        btnWorkGallery = findViewById(R.id.btnWorkGallery)
        btnPreviewProfile = findViewById(R.id.btnPreviewProfile)
        btnAiFeatures = findViewById(R.id.btnAiFeatures)
        btnSuggestBadges = findViewById(R.id.btnSuggestBadges)
        chipGroupBadges = findViewById(R.id.chipGroupBadges)
        progressBar = findViewById(R.id.progressBar)
        layoutContent = findViewById(R.id.layoutContent)
        btnLogout = findViewById(R.id.btnLogout)
    }

    private fun loadWorkerProfile() {
        showLoading(true)
        lifecycleScope.launch {
            val result = repository.getCurrentWorkerProfile()
            result.onSuccess { worker ->
                showLoading(false)
                if (worker == null) {
                    navigateToProfileSetup()
                    return@onSuccess
                }
                currentWorker = worker
                populateUI(worker)
            }
            result.onFailure { e ->
                showLoading(false)
                showToast("Failed to load profile: ${e.message}")
            }
        }
    }

    private fun populateUI(worker: Worker) {
        tvWorkerName.text = worker.name
        tvSkills.text = worker.skills.joinToString(" · ")
        tvDailyRate.text = worker.formattedRate()

        switchAvailability.setOnCheckedChangeListener(null)
        switchAvailability.isChecked = worker.available
        updateAvailabilityUI(worker.available)

        switchAvailability.setOnCheckedChangeListener { _, isChecked ->
            onAvailabilityToggled(isChecked)
        }

        if (worker.profilePhotoUrl.isNotBlank()) {
            Glide.with(this)
                .load(worker.profilePhotoUrl)
                .placeholder(android.R.drawable.ic_menu_camera)
                .error(android.R.drawable.ic_menu_report_image)
                .into(ivProfilePhoto)
        }
    }

    private fun onAvailabilityToggled(isAvailable: Boolean) {
        updateAvailabilityUI(isAvailable)
        lifecycleScope.launch {
            val result = repository.updateAvailability(isAvailable)
            result.onSuccess {
                val message = if (isAvailable) "✅ You are LIVE!" else "🔕 Offline."
                showToast(message)
            }
            result.onFailure {
                switchAvailability.setOnCheckedChangeListener(null)
                switchAvailability.isChecked = !isAvailable
                switchAvailability.setOnCheckedChangeListener { _, checked -> onAvailabilityToggled(checked) }
                showToast("Update failed.")
            }
        }
    }

    private fun updateAvailabilityUI(isAvailable: Boolean) {
        if (isAvailable) {
            tvAvailabilityStatus.text = "🟢 You are LIVE — Customers can find you!"
            tvAvailabilityStatus.setTextColor(getColor(R.color.colorAvailable))
        } else {
            tvAvailabilityStatus.text = "⚫ Offline — Turn ON to receive work enquiries"
            tvAvailabilityStatus.setTextColor(getColor(R.color.colorUnavailable))
        }
    }

    private fun setupClickListeners() {
        btnEditProfile.setOnClickListener {
            startActivity(Intent(this, WorkerProfileSetupActivity::class.java))
        }

        btnWorkGallery.setOnClickListener {
            startActivity(Intent(this, WorkGalleryActivity::class.java))
        }

        // ── SYNCED PREVIEW LOGIC ──
        btnPreviewProfile.setOnClickListener {
            val workerId = repository.getCurrentUserId()
            if (workerId == null) {
                showToast("Worker ID not found")
                return@setOnClickListener
            }
            val intent = Intent(this, WorkerDetailActivity::class.java)

            // Using the Constant from the Detail Activity for safety
            intent.putExtra(WorkerDetailActivity.EXTRA_WORKER_ID, workerId)

            startActivity(intent)
        }

        btnAiFeatures.setOnClickListener {
            showToast("AI Features:\n• Auto Photos\n• AI Badges\n• Smart Search")
        }

        btnSuggestBadges.setOnClickListener {
            val worker = currentWorker
            if (worker == null) {
                showToast("Profile not loaded")
                return@setOnClickListener
            }

            val primarySkill = worker.skills.firstOrNull()
            if (primarySkill.isNullOrBlank()) {
                showToast("Please add skills first")
                return@setOnClickListener
            }

            showToast("Generating AI badge suggestions...")

            lifecycleScope.launch {
                val result = genAI.suggestSkillBadges(primarySkill)
                result.onSuccess { badges ->
                    chipGroupBadges.removeAllViews()
                    if (badges.isEmpty()) {
                        addManualChip("Pro Worker")
                    } else {
                        badges.forEach { addManualChip(it) }
                    }
                    showToast("AI badges generated!")
                }
                result.onFailure {
                    showToast("Failed to generate badges")
                }
            }
        }

        ivProfilePhoto.setOnClickListener {
            startActivity(Intent(this, WorkerProfileSetupActivity::class.java))
        }

        btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun addManualChip(badgeText: String) {
        val chip = Chip(this).apply {
            text = badgeText
            isCheckable = false
            setChipIconResource(android.R.drawable.ic_menu_info_details)
        }
        chipGroupBadges.addView(chip)
    }

    private fun navigateToProfileSetup() {
        startActivity(Intent(this, WorkerProfileSetupActivity::class.java))
    }

    override fun onResume() {
        super.onResume()
        loadWorkerProfile()
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        layoutContent.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}