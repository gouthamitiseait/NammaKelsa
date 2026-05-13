package com.nammakelsa.utils

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import com.nammakelsa.BuildConfig
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * GenAIHelper.kt
 *
 * All Generative AI (Gemini API) calls are isolated in this class.
 * PRD Section 5 — GenAI Integration Requirements
 * PRD Section 6.5 — GenAI code must be in a dedicated GenAIHelper class
 *
 * Features implemented:
 *  F-08 — Skill Description Generator (Gemini Vision API)
 *  F-09 — Natural Language Customer Search (Gemini Text API)
 *  F-10 — Skill Badge Auto-Suggestion (Gemini Text API)
 *
 * API Key: Loaded from BuildConfig (never hardcoded) per PRD Section 5.4
 * Build setup: Add GEMINI_API_KEY to local.properties and reference in build.gradle
 */
class GenAIHelper {

    companion object {
        private const val TAG = "GenAIHelper"

        // PRD Section 5.4: API key must never be hardcoded in source.
        // Set via: local.properties → GEMINI_API_KEY=your_key_here
        // Reference in build.gradle: buildConfigField("String", "GEMINI_API_KEY", ...)
        private val API_KEY: String get() = BuildConfigProxy.geminiApiKey()

        // Gemini model endpoints
        private const val GEMINI_TEXT_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

        private const val GEMINI_VISION_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

        // Supported skill categories — matches ChipGroup options in PRD Section 4.5
        val SKILL_CATEGORIES = listOf(
            "Painter", "Plumber", "Electrician", "Tiler",
            "Carpenter", "Welder", "Gardener"
        )
    }

    // =========================================================
    // F-08 — SKILL DESCRIPTION GENERATOR
    // PRD Section 5.1
    // Sends gallery photo → Gemini Vision → returns 1-2 sentence description
    // =========================================================

    /**
     * Auto-generate a professional skill description from a gallery photo.
     * PRD Section 5.1: Uses Gemini Vision API ().
     *
     * Called when worker taps "Auto-Describe" button on gallery photo.
     * Worker can accept or edit the result before saving.
     *
     * @param bitmap The gallery photo as a Bitmap
     * @param workerSkills The worker's primary skills for context
     * @return Result<String> — 1-2 sentence professional description
     *
     * Example output:
     * "Professional interior wall painting with clean finish and sharp edges,
     *  suited for bedroom or living room settings."
     */
    suspend fun generateSkillDescription(
        bitmap: Bitmap,
        workerSkills: List<String>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Convert bitmap to Base64 for Gemini Vision API
            val base64Image = bitmapToBase64(bitmap)
            val primarySkill = workerSkills.firstOrNull() ?: "skilled work"

            val prompt = """
                You are helping a daily-wage skilled worker in India create a professional 
                description for their work portfolio photo.
                
                The worker's primary skill is: $primarySkill
                Skills: ${workerSkills.joinToString(", ")}
                
                Look at this photo of their completed work and write a 1-2 sentence 
                professional description that:
                - Describes what was done specifically
                - Highlights quality and craftsmanship
                - Sounds professional but simple
                - Is suitable for a labor marketplace profile
                
                Write ONLY the description. No preamble, no quotes.
                Example: "Professional interior wall painting with clean finish and sharp edges, 
                suited for bedroom or living room settings."
            """.trimIndent()

            val requestBody = buildVisionRequestBody(prompt, base64Image)
            val response = callGeminiAPI(GEMINI_VISION_URL, requestBody)
            val description = extractTextFromResponse(response)

            Log.d(TAG, "Skill description generated successfully")
            Result.success(description)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate skill description", e)
            Result.failure(e)
        }
    }

    // =========================================================
    // F-09 — NATURAL LANGUAGE CUSTOMER SEARCH
    // PRD Section 5.2
    // Free-text query → Gemini extracts relevant skill category
    // =========================================================

    /**
     * Resolve a free-text customer search query to a skill category.
     * PRD Section 5.2: Uses Gemini Text API (gemini-1.5-flash).
     *
     * Called in CustomerSearchActivity when user types in free-text search.
     * The resolved skill is then used as the chip filter automatically.
     *
     * @param query Free-text search input from customer
     * @return Result<String?> — Matched skill category, or null if unresolved
     *
     * Example:
     *   Input:  "someone to fix my leaking pipe"
     *   Output: "Plumber"
     *
     *   Input:  "need help painting my bedroom walls"
     *   Output: "Painter"
     */
    suspend fun resolveSearchQuery(query: String): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val categoriesStr = SKILL_CATEGORIES.joinToString(", ")

            val prompt = """
                You are a skill classifier for a labor marketplace app in India.
                
                The user is searching for a worker and typed: "$query"
                
                Available skill categories: $categoriesStr
                
                Identify which ONE skill category best matches the user's need.
                
                Rules:
                - Reply with ONLY the skill category name (exact match from the list above)
                - If no category matches, reply with: NONE
                - Do not add any explanation or punctuation
                
                Examples:
                "someone to fix my leaking pipe" → Plumber
                "need a painter for my bedroom" → Painter  
                "electricity problem in my house" → Electrician
                "wants tiles in bathroom" → Tiler
                "make a wooden shelf" → Carpenter
                "trim my garden plants" → Gardener
                "need welding for iron gate" → Welder
                "I need help" → NONE
            """.trimIndent()

            val requestBody = buildTextRequestBody(prompt)
            val response = callGeminiAPI(GEMINI_TEXT_URL, requestBody)
            val result = extractTextFromResponse(response).trim()

            val resolvedSkill = if (result == "NONE" || !SKILL_CATEGORIES.contains(result)) {
                null
            } else {
                result
            }

            Log.d(TAG, "Query '$query' resolved to skill: $resolvedSkill")
            Result.success(resolvedSkill)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve search query", e)
            Result.failure(e)
        }
    }

    // =========================================================
    // F-10 — SKILL BADGE AUTO-SUGGESTION
    // PRD Section 5.3
    // Primary skill → Gemini → 2-3 related sub-skill badge suggestions
    // =========================================================

    /**
     * Suggest 2-3 sub-skill badges for the worker's profile.
     * PRD Section 5.3: Uses Gemini Text API (gemini-1.5-flash).
     *
     * Called in WorkerProfileSetupActivity when worker taps "Suggest Badges".
     * Worker can accept or dismiss individual badge suggestions.
     *
     * @param primarySkill The worker's primary skill (e.g. "Painter")
     * @return Result<List<String>> — 2-3 suggested sub-skill badges
     *
     * Example:
     *   Input:  "Painter"
     *   Output: ["Waterproofing", "Texture Painting", "Wall Putty"]
     */
    suspend fun suggestSkillBadges(primarySkill: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            try {
                val prompt = """
                You are helping a daily-wage worker in India build their professional profile 
                on a labor marketplace app.
                
                The worker's primary skill is: "$primarySkill"
                
                Suggest exactly 3 specific sub-skills or specialisations that are:
                - Commonly associated with "$primarySkill" work in India
                - In demand among homeowners
                - Simple 1-3 word labels
                
                Reply with ONLY a comma-separated list of 3 sub-skills. No numbering, no explanation.
                
                Examples:
                Painter → Waterproofing, Texture Painting, Wall Putty
                Plumber → Pipe Fitting, Bathroom Setup, Motor Pump
                Electrician → Wiring, Panel Work, Solar Installation
                Tiler → Flooring, Wall Cladding, Mosaic Work
                Carpenter → Furniture, Door Frames, False Ceiling
                Gardener → Landscaping, Terrace Farming, Tree Pruning
                Welder → Fabrication, Gate Making, MS Structure
            """.trimIndent()

                val requestBody = buildTextRequestBody(prompt)
                val response = callGeminiAPI(GEMINI_TEXT_URL, requestBody)
                val rawResult = extractTextFromResponse(response)

                // Parse comma-separated badges
                val badges = rawResult
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .take(3)

                Log.d(TAG, "Badge suggestions for '$primarySkill': $badges")
                Result.success(badges)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to suggest badges for '$primarySkill'", e)
                Result.failure(e)
            }
        }

    // =========================================================
    // PRIVATE — API CALL HELPERS
    // =========================================================

    /**
     * Build the JSON request body for Gemini text-only API.
     */
    private fun buildTextRequestBody(prompt: String): String {
        return JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            // Safety settings — allow helpful content per PRD
            put("safetySettings", JSONArray().apply {
                put(JSONObject().apply {
                    put("category", "HARM_CATEGORY_HARASSMENT")
                    put("threshold", "BLOCK_MEDIUM_AND_ABOVE")
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)       // Lower = more deterministic
                put("maxOutputTokens", 256)
                put("topP", 0.8)
            })
        }.toString()
    }

    /**
     * Build the JSON request body for Gemini Vision API (text + image).
     */
    private fun buildVisionRequestBody(prompt: String, base64Image: String): String {
        return JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        // Text part
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                        // Image part (Base64 encoded)
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", "image/jpeg")
                                put("data", base64Image)
                            })
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.4)
                put("maxOutputTokens", 128)
            })
        }.toString()
    }

    /**
     * Make HTTP POST call to Gemini API endpoint.
     * PRD Section 5.4: Shows loading indicator in UI (caller's responsibility).
     * PRD Section 5.4: Graceful error handling on API failure.
     */
    private fun callGeminiAPI(endpoint: String, requestBody: String): String {
        val url = URL("$endpoint?key=$API_KEY")
        val connection = url.openConnection() as HttpURLConnection

        return try {
            connection.apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 15_000   // 15 seconds
                readTimeout = 30_000      // 30 seconds
            }

            connection.outputStream.use { os ->
                os.write(requestBody.toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText()
                throw Exception("Gemini API error $responseCode: $errorBody")
            }

            connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Extract the text content from Gemini API JSON response.
     * Navigates: candidates[0] → content → parts[0] → text
     */
    private fun extractTextFromResponse(response: String): String {
        val jsonResponse = JSONObject(response)
        val candidates = jsonResponse.getJSONArray("candidates")
        if (candidates.length() == 0) throw Exception("No candidates in Gemini response")

        val content = candidates.getJSONObject(0).getJSONObject("content")
        val parts = content.getJSONArray("parts")
        if (parts.length() == 0) throw Exception("No parts in Gemini response")

        return parts.getJSONObject(0).getString("text").trim()
    }

    /**
     * Convert Bitmap to Base64 string for Gemini Vision API.
     * Compresses to JPEG at 80% quality before encoding.
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}

/**
 * BuildConfigProxy
 * Provides a safe way to access BuildConfig fields.
 * In real project, replace with: BuildConfig.GEMINI_API_KEY
 */
object BuildConfigProxy {
    fun geminiApiKey(): String {
        // In production: return BuildConfig.GEMINI_API_KEY
        // Set in local.properties: GEMINI_API_KEY=AIza...
        // Referenced in build.gradle:
        //   buildConfigField("String", "GEMINI_API_KEY",
        //       "\"${project.properties['GEMINI_API_KEY']}\"")
        return BuildConfig.GEMINI_API_KEY// Replace with BuildConfig.GEMINI_API_KEY in real build
    }
}
