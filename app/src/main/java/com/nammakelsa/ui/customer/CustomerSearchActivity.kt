package com.nammakelsa.ui.customer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import com.google.android.material.textfield.TextInputEditText
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.firestore.ListenerRegistration
import com.nammakelsa.R
import com.nammakelsa.model.Worker
import com.nammakelsa.repository.FirebaseRepository
import com.nammakelsa.utils.GenAIHelper
import com.nammakelsa.utils.LocationUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * CustomerSearchActivity.kt
 *
 * Main screen for the Customer role.
 *
 * PRD Section 4.5 — Customer Search Module:
 *  - ChipGroup for skill filter selection
 *  - Filter options: Painter, Plumber, Electrician, Tiler, Gardener, Carpenter, Welder
 *  - Results list shows only workers with available: true, filtered by skill
 *  - Each list item: Profile photo, Name, Skill tags, Daily rate, Distance
 *  - Distance calculated using worker's location + customer's current GPS
 *  - Results sorted by distance (nearest first) by default
 *
 * PRD F-09 — Natural Language Customer Search:
 *  - Customer types a free-text query instead of chip filters
 *  - Input sent to Gemini text API to extract relevant skill category
 *  - Example: "someone to fix my leaking pipe" → "Plumber"
 *  - App applies resolved category as skill filter automatically
 *
 * PRD Customer Flow Steps 11-12:
 *  Select skill chip OR type free-text → Browse available workers sorted by distance
 */
class CustomerSearchActivity : AppCompatActivity() {

    // ── Views ──────────────────────────────────────────────────
    private lateinit var etSearchQuery: TextInputEditText
    private lateinit var btnNLSearch: Button
    private lateinit var tvNLResult: TextView
    private lateinit var chipGroupSkills: ChipGroup
    private lateinit var rvWorkers: RecyclerView
    private lateinit var progressBarAI: ProgressBar
    private lateinit var tvEmptyState: TextView
    private lateinit var tvResultCount: TextView
    private lateinit var layoutNLResult: View
    private lateinit var btnLogout: Button

    // ── State ──────────────────────────────────────────────────
    private val repository = FirebaseRepository()
    private val genAI = GenAIHelper()
    private var workerAdapter: WorkerListAdapter? = null
    private var firestoreListener: ListenerRegistration? = null
    private var selectedSkillFilter: String = "All"
    private var customerLat: Double = 0.0
    private var customerLon: Double = 0.0
    private var nlSearchJob: Job? = null

    // Skill filter options — PRD Section 4.5
    private val skillFilters = listOf("All", "Painter", "Plumber", "Electrician",
        "Tiler", "Carpenter", "Welder", "Gardener")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_customer_search)
        supportActionBar?.hide()

        initViews()
        buildSkillChips()
        setupRecyclerView()
        setupSearchBar()
        setupClickListeners()
        requestLocationAndLoadWorkers()
    }

    private fun initViews() {
        etSearchQuery    = findViewById(R.id.etSearchQuery)
        btnNLSearch      = findViewById(R.id.btnNLSearch)
        tvNLResult       = findViewById(R.id.tvNLResult)
        chipGroupSkills  = findViewById(R.id.chipGroupSkills)
        rvWorkers        = findViewById(R.id.rvWorkers)
        progressBarAI    = findViewById(R.id.progressBarAI)
        tvEmptyState     = findViewById(R.id.tvEmptyState)
        tvResultCount    = findViewById(R.id.tvResultCount)
        layoutNLResult   = findViewById(R.id.layoutNLResult)
        btnLogout = findViewById(R.id.btnLogout)

        layoutNLResult.visibility = View.GONE
    }

    // ── SKILL CHIPS ───────────────────────────────────────────

    /**
     * Build the skill filter ChipGroup.
     * PRD Section 4.5: Filter options as chips, single-select.
     * Selecting "All" shows every available worker.
     */
    private fun buildSkillChips() {
        chipGroupSkills.removeAllViews()
        chipGroupSkills.isSingleSelection = true

        skillFilters.forEach { skill ->
            val chip = Chip(this).apply {
                text = skill
                isCheckable = true
                isChecked = (skill == "All")
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedSkillFilter = skill
                        // Clear NL search result when chip is manually tapped
                        layoutNLResult.visibility = View.GONE
                        loadWorkers(skill)
                    }
                }
            }
            chipGroupSkills.addView(chip)
        }
    }

    /**
     * Programmatically select a skill chip (used after NL search resolves a skill).
     */
    private fun selectChipBySkill(skill: String) {
        for (i in 0 until chipGroupSkills.childCount) {
            val chip = chipGroupSkills.getChildAt(i) as? Chip ?: continue
            if (chip.text.toString() == skill) {
                chip.isChecked = true
                break
            }
        }
    }

    // ── RECYCLER VIEW ─────────────────────────────────────────

    private fun setupRecyclerView() {
        workerAdapter = WorkerListAdapter(
            workers = emptyList(),
            customerLat = customerLat,
            customerLon = customerLon
        ) { worker ->
            openWorkerDetail(worker)
        }
        rvWorkers.layoutManager = LinearLayoutManager(this)
        rvWorkers.adapter = workerAdapter
    }

    // ── F-09: NATURAL LANGUAGE SEARCH ─────────────────────────

    /**
     * Setup the free-text search bar with debounced AI resolution.
     * PRD Section 5.2: Free-text query → Gemini extracts skill category.
     *
     * Debouncing: waits 800ms after user stops typing before calling Gemini.
     * This avoids API calls on every keystroke.
     */
    private fun setupSearchBar() {
        etSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // Cancel previous debounce job
                nlSearchJob?.cancel()
                val query = s?.toString()?.trim() ?: return
                if (query.length < 3) return // Don't search for very short inputs

                // Debounce — wait 800ms before calling AI
                nlSearchJob = lifecycleScope.launch {
                    delay(800)
                    resolveNLQuery(query)
                }
            }
        })

        // Also handle explicit search button tap
        btnNLSearch.setOnClickListener {
            val query = etSearchQuery.text.toString().trim()
            if (query.isBlank()) {
                Toast.makeText(this, "Type what you're looking for", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            nlSearchJob?.cancel()
            resolveNLQuery(query)
        }
    }

    /**
     * Send user's free-text query to Gemini to extract skill category.
     *
     * PRD Section 5.2:
     *   "someone to fix my leaking pipe" → "Plumber"
     *   "need painting for my bedroom walls" → "Painter"
     *
     * PRD Section 5.4: Show loading indicator, graceful error fallback.
     */
    private fun resolveNLQuery(query: String) {
        // PRD: Show loading indicator during GenAI call
        progressBarAI.visibility = View.VISIBLE
        btnNLSearch.isEnabled = false

        lifecycleScope.launch {
            val result = genAI.resolveSearchQuery(query)

            progressBarAI.visibility = View.GONE
            btnNLSearch.isEnabled = true

            result.onSuccess { resolvedSkill ->
                if (resolvedSkill != null) {
                    // Show the resolved skill to user with clear feedback
                    tvNLResult.text = "🤖 Gemini understood: \"$query\" → $resolvedSkill"
                    layoutNLResult.visibility = View.VISIBLE

                    // Apply as chip filter
                    selectedSkillFilter = resolvedSkill
                    selectChipBySkill(resolvedSkill)
                    loadWorkers(resolvedSkill)
                } else {
                    // Could not resolve — show all workers
                    tvNLResult.text = "🤖 Could not identify a skill. Showing all workers."
                    layoutNLResult.visibility = View.VISIBLE
                    loadWorkers("All")
                }
            }

            // PRD Section 5.4: Graceful error — fall back to showing all workers
            result.onFailure {
                layoutNLResult.visibility = View.GONE
                Toast.makeText(
                    this@CustomerSearchActivity,
                    "AI search unavailable. Use the skill chips above.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ── LOAD WORKERS ──────────────────────────────────────────

    /**
     * Request GPS location then load available workers.
     * Location is used for distance calculation and sorting.
     */
    private fun requestLocationAndLoadWorkers() {
        if (!LocationUtils.hasLocationPermission(this)) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
        } else {
            fetchLocationAndLoad()
        }
    }

    private fun fetchLocationAndLoad() {
        lifecycleScope.launch {
            val location = LocationUtils.getCurrentLocation(this@CustomerSearchActivity)
            if (location != null) {
                customerLat = location.latitude
                customerLon = location.longitude
                workerAdapter?.updateCustomerLocation(customerLat, customerLon)
            }
            // Load workers regardless — distance shows "Unknown" if no GPS
            startRealtimeWorkerListener(selectedSkillFilter)
        }
    }

    /**
     * Set up a real-time Firestore listener for available workers.
     * PRD Section 4.4: Customer search must only show workers with available: true
     * PRD Non-Functional 6.1: Availability change must reflect within 2 seconds.
     *
     * Uses addSnapshotListener for live updates instead of one-time get().
     */
    private fun startRealtimeWorkerListener(skillFilter: String) {
        // Remove previous listener to avoid duplicates
        firestoreListener?.remove()

        showLoading(true)

        firestoreListener = repository.listenToAvailableWorkers(
            skillFilter = if (skillFilter == "All") null else skillFilter,
            onUpdate = { workers ->
                showLoading(false)
                val sorted = LocationUtils.sortWorkersByDistance(
                    workers, customerLat, customerLon
                )
                val workerList = sorted.map { it.first }
                updateWorkerList(workerList)
            },
            onError = { e ->
                showLoading(false)
                Toast.makeText(this, "Error loading workers: ${e.message}",
                    Toast.LENGTH_SHORT).show()
            }
        )
    }

    /**
     * One-time load (fallback if real-time listener not set up yet).
     */
    private fun loadWorkers(skillFilter: String) {
        startRealtimeWorkerListener(skillFilter)
    }

    /**
     * Update the RecyclerView with filtered, distance-sorted workers.
     * PRD Section 4.5: Each item shows profile photo, name, skill tags,
     * daily rate, and distance from customer.
     */
    private fun updateWorkerList(workers: List<Worker>) {
        workerAdapter?.updateWorkers(workers, customerLat, customerLon)

        val filterLabel = if (selectedSkillFilter == "All") "all skills" else selectedSkillFilter
        tvResultCount.text = "${workers.size} worker(s) available nearby · $filterLabel"

        if (workers.isEmpty()) {
            tvEmptyState.visibility = View.VISIBLE
            tvEmptyState.text = "No $filterLabel workers available right now.\nTry a different skill or check back later."
            rvWorkers.visibility = View.GONE
        } else {
            tvEmptyState.visibility = View.GONE
            rvWorkers.visibility = View.VISIBLE
        }
    }

    // ── NAVIGATION ────────────────────────────────────────────

    private fun setupClickListeners() {

        // Dismiss NL result banner
        findViewById<View>(R.id.btnDismissNL)?.setOnClickListener {
            layoutNLResult.visibility = View.GONE
            etSearchQuery.text?.clear()
        }

        // Logout button
        btnLogout.setOnClickListener {

            repository.signOut()

            Toast.makeText(
                this,
                "Logged out successfully",
                Toast.LENGTH_SHORT
            ).show()

            val intent = Intent(this, com.nammakelsa.ui.LoginActivity::class.java)

            intent.flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK

            startActivity(intent)
            finish()
        }
    }

    /**
     * Navigate to WorkerDetailActivity when a worker card is tapped.
     * PRD Customer Flow Step 13: Tap a worker card → View full profile.
     */
    private fun openWorkerDetail(worker: Worker) {
        val intent = Intent(this, WorkerDetailActivity::class.java).apply {
            putExtra(WorkerDetailActivity.EXTRA_WORKER_ID, worker.userId)
            putExtra(WorkerDetailActivity.EXTRA_CUSTOMER_LAT, customerLat)
            putExtra(WorkerDetailActivity.EXTRA_CUSTOMER_LON, customerLon)
        }
        startActivity(intent)
    }

    // ── PERMISSIONS ───────────────────────────────────────────

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchLocationAndLoad()
            } else {
                // PRD Section 13: GPS unavailable fallback — load without distance
                Toast.makeText(this,
                    "Location permission denied. Distance will not be shown.",
                    Toast.LENGTH_SHORT).show()
                startRealtimeWorkerListener(selectedSkillFilter)
            }
        }
    }

    // ── LIFECYCLE ─────────────────────────────────────────────

    override fun onDestroy() {
        super.onDestroy()
        // Remove Firestore listener to avoid memory leaks
        firestoreListener?.remove()
        nlSearchJob?.cancel()
    }

    // ── HELPERS ───────────────────────────────────────────────

    private fun showLoading(show: Boolean) {
        progressBarAI.visibility =
            if (show) View.VISIBLE else View.GONE
    }
    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 1002
    }
}
