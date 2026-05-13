package com.nammakelsa.ui.customer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.nammakelsa.R
import com.nammakelsa.model.Worker
import com.nammakelsa.repository.FirebaseRepository
import kotlinx.coroutines.launch

class WorkerDetailActivity : AppCompatActivity() {

    private lateinit var ivProfilePhoto: ImageView
    private lateinit var tvWorkerName: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvDailyRate: TextView
    private lateinit var chipGroupSkills: ChipGroup
    private lateinit var btnCall: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutContent: View

    private val repository = FirebaseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_worker_detail)

        // Using the Constants defined in companion object
        val workerId = intent.getStringExtra(EXTRA_WORKER_ID)

        if (workerId.isNullOrEmpty()) {
            Toast.makeText(this, "Profile not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        loadWorkerProfile(workerId)
    }

    private fun initViews() {
        ivProfilePhoto = findViewById(R.id.ivProfilePhoto)
        tvWorkerName = findViewById(R.id.tvWorkerName)
        tvLocation = findViewById(R.id.tvLocation)
        tvDailyRate = findViewById(R.id.tvDailyRate)
        chipGroupSkills = findViewById(R.id.chipGroupSkills)
        btnCall = findViewById(R.id.btnCall)
        progressBar = findViewById(R.id.progressBar)
        layoutContent = findViewById(R.id.layoutContent)

        layoutContent.visibility = View.GONE
    }

    private fun loadWorkerProfile(id: String) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = repository.getWorkerById(id)
            progressBar.visibility = View.GONE
            result.onSuccess { worker ->
                if (worker != null) populateUI(worker)
                else finish()
            }
        }
    }

    private fun populateUI(worker: Worker) {
        layoutContent.visibility = View.VISIBLE
        tvWorkerName.text = worker.name
        tvLocation.text = "📍 ${worker.location}"
        tvDailyRate.text = worker.formattedRate()

        chipGroupSkills.removeAllViews()
        worker.skills.forEach { skill ->
            val chip = Chip(this).apply { text = skill }
            chipGroupSkills.addView(chip)
        }

        btnCall.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${worker.phoneNumber}"))
            startActivity(intent)
        }
    }

    // THIS BLOCK FIXES THE "UNRESOLVED REFERENCE" ERROR
    companion object {
        const val EXTRA_WORKER_ID = "worker_id"
        const val EXTRA_CUSTOMER_LAT = "customer_lat"
        const val EXTRA_CUSTOMER_LON = "customer_lon"
    }
}