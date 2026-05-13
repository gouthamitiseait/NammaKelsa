package com.nammakelsa.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * Worker.kt — Data Model
 * Mirrors the Firestore collection: /workers/{userId}
 *
 * Fields match PRD Section 8.2 — Firestore Data Model exactly.
 */
data class Worker(

    @DocumentId
    val userId: String = "",

    /** Full name of the worker */
    val name: String = "",

    /**
     * List of skill strings.
     * e.g. ["Painter", "Waterproofing", "Texture Painting"]
     * Primary skill is skills[0].
     */
    val skills: List<String> = emptyList(),

    /** Daily wage in INR */
    val dailyRate: Int = 0,

    /** Area name e.g. "Koramangala, Bangalore" */
    val location: String = "",

    /** Registered phone number — shown only when CALL button is tapped */
    val phoneNumber: String = "",

    /** Firebase Storage URL of profile photo */
    val profilePhotoUrl: String = "",

    /** List of up to 3 Firebase Storage URLs for work gallery */
    val galleryUrls: List<String> = emptyList(),

    /** AI-generated or manually entered descriptions for each gallery photo */
    val galleryDescriptions: List<String> = emptyList(),

    /**
     * True if worker is available for work today.
     * Auto-resets to false at midnight via Firebase Scheduled Function
     * or checked on app launch in onCreate().
     */
    val available: Boolean = false,

    /** Last profile update timestamp */
    @ServerTimestamp
    val lastUpdated: Timestamp? = null

) {
    /**
     * Returns the primary skill (first in list).
     * Used for GenAI badge suggestions and search matching.
     */
    fun primarySkill(): String = skills.firstOrNull() ?: ""

    /**
     * Checks if worker matches a given skill filter.
     * Used in CustomerSearchActivity filtering logic.
     */
    fun matchesSkill(skillFilter: String): Boolean {
        if (skillFilter.isBlank() || skillFilter.equals("All", ignoreCase = true)) return true
        return skills.any { it.equals(skillFilter, ignoreCase = true) }
    }

    /**
     * Returns formatted daily rate string.
     * e.g. "₹800/day"
     */
    fun formattedRate(): String = "₹$dailyRate/day"

    /**
     * Returns display-safe short location (city only).
     * e.g. "Koramangala, Bangalore" → "Koramangala"
     */
    fun shortLocation(): String = location.split(",").firstOrNull()?.trim() ?: location
}
