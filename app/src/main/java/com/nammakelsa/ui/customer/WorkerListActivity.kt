package com.nammakelsa.ui.customer

import android.content.Intent
import android.os.Bundle
import com.bumptech.glide.Glide
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.nammakelsa.R
import com.nammakelsa.model.Worker
import com.nammakelsa.utils.LocationUtils

/**
 * WorkerListActivity.kt
 *
 * Shows the full list of available workers for a given skill filter.
 * Can be navigated to from CustomerSearchActivity for a dedicated full-screen list.
 *
 * PRD Section 4.5 — Customer Search Module:
 *  Each list item displays:
 *   - Profile photo
 *   - Name
 *   - Skill tags (chips)
 *   - Daily rate
 *   - Distance from customer
 */
class WorkerListActivity : AppCompatActivity() {

    private lateinit var rvWorkers: RecyclerView
    private lateinit var tvTitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_worker_list)

        supportActionBar?.apply {
            title = intent.getStringExtra(EXTRA_SKILL_FILTER) ?: "Available Workers"
            setDisplayHomeAsUpEnabled(true)
        }

        rvWorkers = findViewById(R.id.rvWorkers)
        rvWorkers.layoutManager = LinearLayoutManager(this)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        const val EXTRA_SKILL_FILTER = "skill_filter"
        const val EXTRA_CUSTOMER_LAT = "customer_lat"
        const val EXTRA_CUSTOMER_LON = "customer_lon"
    }
}

// =============================================================
// WorkerListAdapter.kt
// RecyclerView Adapter for the worker search results list.
// =============================================================

/**
 * WorkerListAdapter
 *
 * Adapter for displaying workers in CustomerSearchActivity's RecyclerView.
 *
 * PRD Section 4.5 — Each list item shows:
 *  - Profile photo (circular)
 *  - Worker name
 *  - Skill chips
 *  - Daily rate (₹X/day)
 *  - Distance from customer (X km away)
 *  - Availability dot (green dot — always visible since list only has available workers)
 *
 * PRD Section 6.4 (Security):
 *  Worker phone number is NOT shown in the list.
 *  It is only revealed when the CALL button is tapped in WorkerDetailActivity.
 */
class WorkerListAdapter(
    private var workers: List<Worker>,
    private var customerLat: Double,
    private var customerLon: Double,
    private val onWorkerClick: (Worker) -> Unit
) : RecyclerView.Adapter<WorkerListAdapter.WorkerViewHolder>() {

    // ── VIEW HOLDER ───────────────────────────────────────────

    inner class WorkerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivProfilePhoto: ImageView   = itemView.findViewById(R.id.ivWorkerPhoto)
        val tvName: TextView            = itemView.findViewById(R.id.tvWorkerName)
        val chipGroupSkills: ChipGroup  = itemView.findViewById(R.id.chipGroupWorkerSkills)
        val tvRate: TextView            = itemView.findViewById(R.id.tvWorkerRate)
        val tvDistance: TextView        = itemView.findViewById(R.id.tvWorkerDistance)
        val tvLocation: TextView        = itemView.findViewById(R.id.tvWorkerLocation)
        val ivAvailabilityDot: View     = itemView.findViewById(R.id.viewAvailabilityDot)
    }

    // ── ADAPTER OVERRIDES ─────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_worker_card, parent, false)
        return WorkerViewHolder(view)
    }

    override fun getItemCount(): Int = workers.size

    /**
     * Bind worker data to the card view.
     * PRD Section 6.4: Phone number NOT shown here — only in WorkerDetailActivity.
     */
    override fun onBindViewHolder(holder: WorkerViewHolder, position: Int) {
        val worker = workers[position]

        // Name
        holder.tvName.text = worker.name

        // Rate — "₹800/day"
        holder.tvRate.text = worker.formattedRate()

        // Short location — "Koramangala"
        holder.tvLocation.text = worker.shortLocation()

        // Skills as chips (show max 3 to avoid overflow)
        holder.chipGroupSkills.removeAllViews()
        worker.skills.take(3).forEach { skill ->
            val chip = Chip(holder.itemView.context).apply {
                text = skill
                isCheckable = false
                isClickable = false
                textSize = 10f
            }
            holder.chipGroupSkills.addView(chip)
        }
        // If worker has more than 3 skills, show "+N more" chip
        if (worker.skills.size > 3) {
            val moreChip = Chip(holder.itemView.context).apply {
                text = "+${worker.skills.size - 3}"
                isCheckable = false
                isClickable = false
                textSize = 10f
            }
            holder.chipGroupSkills.addView(moreChip)
        }

        // Distance calculation
        val workerCoords = LocationUtils.getCityCoordinates(worker.location)
        if (workerCoords != null && (customerLat != 0.0 || customerLon != 0.0)) {
            val distance = LocationUtils.calculateDistanceKm(
                customerLat, customerLon,
                workerCoords.first, workerCoords.second
            )
            holder.tvDistance.text = LocationUtils.formatDistance(distance)
        } else {
            holder.tvDistance.text = "Distance unknown"
        }

        if (worker.profilePhotoUrl.isNotBlank()) {

            Glide.with(holder.itemView.context)
                .load(worker.profilePhotoUrl)
                .placeholder(android.R.drawable.ic_menu_camera)
                .error(android.R.drawable.ic_delete)
                .into(holder.ivProfilePhoto)

        }
        // Availability dot — always green since this list only shows available workers
        holder.ivAvailabilityDot.setBackgroundResource(R.drawable.dot_available)

        // Card tap → open worker detail
        holder.itemView.setOnClickListener { onWorkerClick(worker) }
    }

    // ── LIST UPDATE ───────────────────────────────────────────

    /**
     * Update the worker list with DiffUtil for smooth animations.
     * Called when Firestore real-time listener pushes new data.
     */
    fun updateWorkers(newWorkers: List<Worker>, lat: Double, lon: Double) {
        customerLat = lat
        customerLon = lon

        val diffResult = DiffUtil.calculateDiff(WorkerDiffCallback(workers, newWorkers))
        workers = newWorkers
        diffResult.dispatchUpdatesTo(this)
    }

    /**
     * Update customer location without reloading the full list.
     * Used when GPS fix arrives after initial load.
     */
    fun updateCustomerLocation(lat: Double, lon: Double) {
        customerLat = lat
        customerLon = lon
        notifyDataSetChanged()
    }

    // ── DIFF CALLBACK ─────────────────────────────────────────

    private class WorkerDiffCallback(
        private val oldList: List<Worker>,
        private val newList: List<Worker>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean =
            oldList[oldPos].userId == newList[newPos].userId

        override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean =
            oldList[oldPos] == newList[newPos]
    }
}
