package fyi.acmc.trailkarma.ui.camera

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import fyi.acmc.trailkarma.BuildConfig
import fyi.acmc.trailkarma.ble.BleRepositoryHolder
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.BiodiversityContribution
import fyi.acmc.trailkarma.models.CloudSyncState
import fyi.acmc.trailkarma.models.InferenceState
import fyi.acmc.trailkarma.models.KarmaStatus
import fyi.acmc.trailkarma.models.PhotoSyncState
import fyi.acmc.trailkarma.models.ReportSource
import fyi.acmc.trailkarma.models.ReportType
import fyi.acmc.trailkarma.models.TrailReport
import fyi.acmc.trailkarma.models.User
import fyi.acmc.trailkarma.repository.DatabricksSyncRepository
import fyi.acmc.trailkarma.repository.UserRepository
import fyi.acmc.trailkarma.sync.SyncWorker
import fyi.acmc.trailkarma.ui.feedback.FeedbackTone
import fyi.acmc.trailkarma.ui.feedback.TrailFeedbackBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.resume

data class PhotoVerificationUiState(
    val photoPath: String? = null,
    val observationId: String? = null,
    val claimedLabel: String = "",
    val currentUser: User? = null,
    val isVerifying: Boolean = false,
    val result: SpeciesPhotoVerificationResult? = null,
    val errorMessage: String? = null,
    val reportCreated: Boolean = false,
    val usedDatabricksMirror: Boolean = false
)

data class SpeciesPhotoVerificationResult(
    val claimedLabel: String,
    val finalLabel: String,
    val finalTaxonomicLevel: String,
    val matchedClaim: Boolean,
    val animalPresent: Boolean,
    val confidence: Float,
    val confidenceBand: String,
    val explanation: String,
    val verificationStatus: String,
    val uniquenessChecked: Boolean,
    val isUniqueSpecies: Boolean,
    val rewardAmount: Int,
    val collectibleStatus: String,
    val collectibleId: String? = null,
    val collectibleName: String? = null,
    val collectibleImageUri: String? = null,
    val model: String? = null
)

data class CameraLocationSnapshot(
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracyMeters: Float? = null,
    val source: String = "missing"
)

class CameraViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val bleRepo = BleRepositoryHolder.getInstance(app)
    private val userRepo = UserRepository(app, db.userDao())
    private val fusedLocation = LocationServices.getFusedLocationProviderClient(app)
    private val databricksRepo = DatabricksSyncRepository(app, db)
    private val http = OkHttpClient.Builder().build()

    private val _uiState = MutableStateFlow(PhotoVerificationUiState())
    val uiState: StateFlow<PhotoVerificationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val user = userRepo.ensureLocalUser()
            _uiState.value = _uiState.value.copy(currentUser = user)
        }
    }

    fun preparePhotoFile(): File {
        val observationId = UUID.randomUUID().toString()
        val file = File(getApplication<Application>().filesDir, "captures/photos/$observationId.jpg").apply {
            parentFile?.mkdirs()
        }
        _uiState.value = _uiState.value.copy(
            observationId = observationId,
            photoPath = file.absolutePath,
            result = null,
            errorMessage = null,
            reportCreated = false,
            usedDatabricksMirror = false
        )
        return file
    }

    fun setClaimedLabel(value: String) {
        _uiState.value = _uiState.value.copy(claimedLabel = value, errorMessage = null)
    }

    fun confirmPreparedPhotoCapture(success: Boolean) {
        if (!success) {
            _uiState.value = _uiState.value.copy(photoPath = null, observationId = null)
        } else {
            _uiState.value = _uiState.value.copy(result = null, errorMessage = null, reportCreated = false)
        }
    }

    fun importSelectedPhoto(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val file = preparePhotoFile()
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("Unable to read the selected photo.")
                }
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    photoPath = null,
                    observationId = null,
                    errorMessage = error.message ?: "Unable to import the selected image."
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun verifyPhotoClaim() {
        val state = _uiState.value
        val photoPath = state.photoPath
        val observationId = state.observationId
        val claimedLabel = state.claimedLabel.trim()

        if (photoPath.isNullOrBlank() || observationId.isNullOrBlank()) {
            _uiState.value = state.copy(errorMessage = "Take or choose a photo first.")
            return
        }
        if (claimedLabel.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Enter the species label you want to verify.")
            return
        }
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Gemini is not configured in this build.")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isVerifying = true, errorMessage = null, result = null, reportCreated = false)
            runCatching {
                val user = _uiState.value.currentUser ?: userRepo.ensureLocalUser()
                val location = awaitLocationSnapshot()
                val knownAlready = databricksRepo.isVerifiedSpeciesKnown(claimedLabel)
                val response = withContext(Dispatchers.IO) {
                    verifyWithGemini(
                        imageFile = File(photoPath),
                        claimedLabel = claimedLabel,
                        observationId = observationId
                    )
                }
                val finalResult = response.toFinalResult(claimedLabel = claimedLabel, knownAlready = knownAlready)
                val mirrored = saveVerificationOutcome(
                    user = user,
                    observationId = observationId,
                    timestamp = Instant.now().toString(),
                    photoPath = photoPath,
                    claimedLabel = claimedLabel,
                    location = location,
                    result = finalResult
                )
                Triple(finalResult, mirrored, location.lat != null && location.lon != null && finalResult.matchedClaim)
            }.onSuccess { (result, mirrored, reportCreated) ->
                _uiState.value = _uiState.value.copy(
                    isVerifying = false,
                    result = result,
                    errorMessage = null,
                    reportCreated = reportCreated,
                    usedDatabricksMirror = mirrored
                )
                val message = when {
                    result.matchedClaim && reportCreated ->
                        "${result.finalLabel} verified. ${result.rewardAmount} KARMA and collectible state saved."
                    result.matchedClaim ->
                        "${result.finalLabel} verified. Saved locally, but location was missing so no species trail report was created."
                    else ->
                        "Photo checked. The claimed species could not be verified from the image."
                }
                TrailFeedbackBus.emit(message, if (result.matchedClaim) FeedbackTone.Success else FeedbackTone.Info)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isVerifying = false,
                    errorMessage = error.message ?: "Photo verification failed."
                )
            }
        }
    }

    private suspend fun saveVerificationOutcome(
        user: User,
        observationId: String,
        timestamp: String,
        photoPath: String,
        claimedLabel: String,
        location: CameraLocationSnapshot,
        result: SpeciesPhotoVerificationResult
    ): Boolean {
        val contribution = BiodiversityContribution(
            id = UUID.randomUUID().toString(),
            type = "biodiversity_photo_verification",
            observationId = observationId,
            userId = user.userId,
            observerDisplayName = user.displayName,
            observerWalletPublicKey = user.walletPublicKey.ifBlank { null },
            createdAt = timestamp,
            claimedLabel = claimedLabel,
            lat = location.lat,
            lon = location.lon,
            locationAccuracyMeters = location.accuracyMeters,
            locationSource = location.source,
            audioUri = null,
            photoUri = photoPath,
            finalLabel = result.finalLabel,
            finalTaxonomicLevel = result.finalTaxonomicLevel,
            confidence = result.confidence,
            confidenceBand = result.confidenceBand,
            explanation = result.explanation,
            verificationStatus = result.verificationStatus,
            relayable = false,
            karmaStatus = if (result.matchedClaim) KarmaStatus.awarded else KarmaStatus.none,
            inferenceState = InferenceState.CLASSIFIED_LOCAL,
            cloudSyncState = if (databricksRepo.isConfigured()) CloudSyncState.SYNCED else CloudSyncState.NOT_SYNCED,
            photoSyncState = PhotoSyncState.SYNCED,
            safeForRewarding = result.matchedClaim,
            savedLocally = true,
            synced = databricksRepo.isConfigured(),
            modelMetadataJson = JSONObject().put("model", result.model ?: BuildConfig.GEMINI_MODEL).toString(),
            classificationSource = "gemini_android",
            localModelVersion = result.model,
            verificationTxSignature = if (result.matchedClaim) "photo-verify-${observationId.take(8)}" else null,
            verifiedAt = if (result.matchedClaim) timestamp else null,
            collectibleStatus = result.collectibleStatus,
            collectibleId = result.collectibleId,
            collectibleName = result.collectibleName ?: result.finalLabel,
            collectibleImageUri = result.collectibleImageUri,
            rewardPointsAwarded = result.rewardAmount,
            dataShareStatus = if (databricksRepo.isConfigured()) {
                if (location.lat == null || location.lon == null) "mirrored_cloud_missing_location" else "mirrored_cloud"
            } else {
                "local_only"
            }
        )
        db.biodiversityContributionDao().insert(contribution)

        val mirrored = if (databricksRepo.isConfigured() && result.matchedClaim) {
            databricksRepo.mirrorBiodiversityContribution(contribution)
        } else {
            false
        }

        if (result.matchedClaim && location.lat != null && location.lon != null) {
            val mirroredReportId = "photo-$observationId"
            db.trailReportDao().insert(
                TrailReport(
                    reportId = mirroredReportId,
                    userId = user.userId,
                    type = ReportType.species,
                    title = "Photo-verified species: ${result.finalLabel}",
                    description = buildString {
                        append("Claimed label \"")
                        append(claimedLabel)
                        append("\" was verified from a submitted photo. ")
                        append(result.explanation)
                    },
                    lat = location.lat,
                    lng = location.lon,
                    timestamp = timestamp,
                    speciesName = result.finalLabel,
                    confidence = result.confidence,
                    source = ReportSource.self,
                    synced = false,
                    verificationStatus = "pending",
                    photoUri = photoPath,
                    highConfidenceBonus = result.rewardAmount >= 13
                )
            )
            bleRepo.onNewLocalReportCreated(mirroredReportId)
            SyncWorker.schedule(getApplication())
        }

        return mirrored
    }

    private fun verifyWithGemini(
        imageFile: File,
        claimedLabel: String,
        observationId: String
    ): GeminiDecisionResponse {
        val prompt = """
            You are verifying a biodiversity photo submission for a hiking app.
            Observation ID: $observationId
            Claimed species label: $claimedLabel

            Return strict JSON with these fields only:
            matchedClaim (boolean),
            animalPresent (boolean),
            detectedLabel (string or null),
            detectedTaxonomicLevel (species|genus|family|unknown),
            confidence (number between 0 and 1),
            confidenceBand (low|medium|medium-high|high),
            explanation (string).

            Set matchedClaim=true only when the claimed species is clearly visible in the image.
            If the claimed label is wrong, set detectedLabel to the best visible animal label you can justify.
        """.trimIndent()

        val requestJson = JSONObject()
            .put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray()
                    .put(JSONObject().put("text", prompt))
                    .put(
                        JSONObject().put(
                            "inline_data",
                            JSONObject()
                                .put("mime_type", "image/jpeg")
                                .put(
                                    "data",
                                    encodeGeminiImage(imageFile)
                                )
                        )
                    )
                )
            ))
            .put(
                "generationConfig",
                JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("temperature", 0.1)
            )

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/${BuildConfig.GEMINI_MODEL}:generateContent?key=${BuildConfig.GEMINI_API_KEY}")
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Gemini verification failed with HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val root = JSONObject(body)
            val text = root.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
            return GeminiDecisionResponse(JSONObject(extractJsonObject(text)))
        }
    }

    private fun encodeGeminiImage(imageFile: File): String {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(imageFile.absolutePath, bounds)

        var sampleSize = 1
        val maxDimension = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxDimension / sampleSize > 1600) {
            sampleSize *= 2
        }

        val bitmap = BitmapFactory.decodeFile(
            imageFile.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: error("Unable to decode the selected image.")

        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
            output.toByteArray()
        }
        bitmap.recycle()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    @SuppressLint("MissingPermission")
    suspend fun awaitLocationSnapshot(): CameraLocationSnapshot = suspendCancellableCoroutine { continuation ->
        fusedLocation.lastLocation
            .addOnSuccessListener { location ->
                continuation.resume(
                    if (location != null) {
                        CameraLocationSnapshot(
                            lat = location.latitude,
                            lon = location.longitude,
                            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                            source = "fused_last_known"
                        )
                    } else {
                        CameraLocationSnapshot(source = "missing")
                    }
                )
            }
            .addOnFailureListener { continuation.resume(CameraLocationSnapshot(source = "missing")) }
    }
}

private data class GeminiDecisionResponse(
    val matchedClaim: Boolean,
    val animalPresent: Boolean,
    val detectedLabel: String?,
    val detectedTaxonomicLevel: String,
    val confidence: Float,
    val confidenceBand: String,
    val explanation: String
) {
    constructor(json: JSONObject) : this(
        matchedClaim = json.optBoolean("matchedClaim", false),
        animalPresent = json.optBoolean("animalPresent", false),
        detectedLabel = json.optString("detectedLabel").takeIf { it.isNotBlank() },
        detectedTaxonomicLevel = json.optString("detectedTaxonomicLevel", "unknown"),
        confidence = json.optDouble("confidence", 0.0).toFloat(),
        confidenceBand = json.optString("confidenceBand", "low"),
        explanation = json.optString("explanation", "No explanation returned.")
    )
}

private fun GeminiDecisionResponse.toFinalResult(
    claimedLabel: String,
    knownAlready: Boolean?
): SpeciesPhotoVerificationResult {
    val uniquenessChecked = knownAlready != null
    val finalLabel = if (matchedClaim) claimedLabel else (detectedLabel ?: claimedLabel)
    val isUnique = matchedClaim && knownAlready == false
    val rewardAmount = when {
        !matchedClaim -> 0
        isUnique -> 13
        else -> 8
    }
    val collectibleStatus = when {
        !matchedClaim -> "not_eligible"
        !uniquenessChecked -> "pending_uniqueness_check"
        isUnique -> "verified"
        else -> "duplicate_species"
    }
    return SpeciesPhotoVerificationResult(
        claimedLabel = claimedLabel,
        finalLabel = finalLabel,
        finalTaxonomicLevel = if (matchedClaim) "species" else detectedTaxonomicLevel,
        matchedClaim = matchedClaim,
        animalPresent = animalPresent,
        confidence = confidence.coerceIn(0f, 1f),
        confidenceBand = confidenceBand,
        explanation = explanation,
        verificationStatus = if (matchedClaim) "verified" else "rejected",
        uniquenessChecked = uniquenessChecked,
        isUniqueSpecies = isUnique,
        rewardAmount = rewardAmount,
        collectibleStatus = collectibleStatus,
        collectibleId = if (isUnique) "species:${slugify(finalLabel)}" else null,
        collectibleName = finalLabel,
        collectibleImageUri = if (isUnique) buildCollectibleGradient(finalLabel) else null,
        model = BuildConfig.GEMINI_MODEL
    )
}

private fun slugify(value: String): String =
    value.trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { UUID.randomUUID().toString() }

private fun buildCollectibleGradient(label: String): String {
    val hash = label.hashCode().toUInt().toString(16).padStart(8, '0')
    val reverse = label.reversed().hashCode().toUInt().toString(16).padStart(8, '0')
    return "gradient:#${hash.take(6).uppercase()}:#${reverse.take(6).uppercase()}"
}

private fun extractJsonObject(text: String): String {
    val trimmed = text.trim()
    val unfenced = if (trimmed.startsWith("```")) {
        trimmed
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    } else {
        trimmed
    }

    if (unfenced.startsWith("{") && unfenced.endsWith("}")) {
        return unfenced
    }

    val start = unfenced.indexOf('{')
    val end = unfenced.lastIndexOf('}')
    if (start >= 0 && end > start) {
        return unfenced.substring(start, end + 1)
    }
    error("Gemini did not return a JSON object.")
}
