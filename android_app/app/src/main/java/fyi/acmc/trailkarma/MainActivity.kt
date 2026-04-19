package fyi.acmc.trailkarma

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import fyi.acmc.trailkarma.ble.BleService
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.BiodiversityContribution
import fyi.acmc.trailkarma.models.CloudSyncState
import fyi.acmc.trailkarma.models.InferenceState
import fyi.acmc.trailkarma.models.KarmaStatus
import fyi.acmc.trailkarma.models.PhotoSyncState
import fyi.acmc.trailkarma.models.RelayJobIntent
import fyi.acmc.trailkarma.location.LocationService
import fyi.acmc.trailkarma.models.ReportSource
import fyi.acmc.trailkarma.models.ReportType
import fyi.acmc.trailkarma.models.TrailReport
import fyi.acmc.trailkarma.models.TrustedContact
import fyi.acmc.trailkarma.repository.UserRepository
import fyi.acmc.trailkarma.repository.DatabricksSyncRepository
import fyi.acmc.trailkarma.sync.BiodiversityLocalInferenceWorker
import fyi.acmc.trailkarma.sync.BiodiversitySyncWorker
import fyi.acmc.trailkarma.sync.SyncWorker
import fyi.acmc.trailkarma.ui.design.TrailKarmaAppTheme
import fyi.acmc.trailkarma.ui.navigation.Routes
import fyi.acmc.trailkarma.ui.navigation.TrailKarmaNavGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import fyi.acmc.trailkarma.BuildConfig
import java.time.Instant
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startService(Intent(this, LocationService::class.java))
        }
        // Start BLE mesh service — runs as long as app is alive, even backgrounded
        if (granted[Manifest.permission.BLUETOOTH_SCAN] == true &&
            granted[Manifest.permission.BLUETOOTH_ADVERTISE] == true) {
            BleService.start(this)
        }
        SyncWorker.schedule(this)
        BiodiversityLocalInferenceWorker.schedulePending(this)
        BiodiversitySyncWorker.schedule(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val db = AppDatabase.get(applicationContext)
            seedDatabaseIfEmpty(db)
            val syncRepo = DatabricksSyncRepository(applicationContext, db)

            if (BuildConfig.DATABRICKS_URL.isNotEmpty() &&
                BuildConfig.DATABRICKS_TOKEN.isNotEmpty() &&
                BuildConfig.DATABRICKS_WAREHOUSE.isNotEmpty()
            ) {
                syncRepo.setDatabricksConfig(
                    url = BuildConfig.DATABRICKS_URL,
                    token = BuildConfig.DATABRICKS_TOKEN,
                    warehouse = BuildConfig.DATABRICKS_WAREHOUSE
                )

                if (syncRepo.isOnline()) {
                    runCatching {
                        syncRepo.syncReports()
                        syncRepo.syncLocations()
                        syncRepo.syncRelayPackets()
                        syncRepo.pullReportsFromCloud()
                        syncRepo.pullTrailsFromCloud()
                    }
                }
            }
        }

        requestPermissions()

        setContent {
            TrailKarmaAppTheme {
                val navController = rememberNavController()
                var startDest by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    val db = AppDatabase.get(applicationContext)
                    val repo = UserRepository(applicationContext, db.userDao())
                    repo.ensureLocalUser()
                    startDest = Routes.MAP
                }

                startDest?.let {
                    TrailKarmaNavGraph(navController = navController, startDestination = it)
                }
            }
        }
    }

    private fun requestPermissions() {
        permissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
        ))
    }

    private suspend fun seedDatabaseIfEmpty(db: AppDatabase) {
        val repo = UserRepository(applicationContext, db.userDao())
        val user = repo.ensureLocalUser()
        seedDemoReports(db, user.userId)
        seedDemoContacts(db, user.userId)
        seedDemoRelayJobs(db, user.userId, user.walletPublicKey)
        seedDemoBiodiversity(db, user.userId, user.displayName, user.walletPublicKey.ifBlank { null })
    }

    private suspend fun seedDemoReports(db: AppDatabase, userId: String) {
        if (db.trailReportDao().getAll().first().isNotEmpty()) return
        val now = Instant.now()

        db.trailReportDao().insert(
            TrailReport(
                reportId = "mock-hazard-claimed",
                userId = userId,
                type = ReportType.hazard,
                title = "Rockslide ahead",
                description = "Section near mile 24 has debris and needs a cautious detour.",
                lat = 32.88,
                lng = -117.24,
                timestamp = now.minusSeconds(10_800).toString(),
                source = ReportSource.self,
                synced = true,
                verificationStatus = "claimed",
                verificationTier = "Tier 1",
                rewardClaimed = true,
                rewardTxSignature = "demo-hazard-verify-24"
            )
        )
        db.trailReportDao().insert(
            TrailReport(
                reportId = "mock-water-claimed",
                userId = userId,
                type = ReportType.water,
                title = "Water source confirmed",
                description = "Spring is flowing and clear enough to filter at the lower switchback.",
                lat = 32.89,
                lng = -117.23,
                timestamp = now.minusSeconds(6_600).toString(),
                source = ReportSource.self,
                synced = true,
                verificationStatus = "claimed",
                verificationTier = "Tier 3",
                rewardClaimed = true,
                rewardTxSignature = "demo-water-verify-18"
            )
        )
        db.trailReportDao().insert(
            TrailReport(
                reportId = "mock-species-pending",
                userId = userId,
                type = ReportType.species,
                title = "Birdsong at Torrey pines",
                description = "Saved for biodiversity verification and collectible review.",
                lat = 32.885,
                lng = -117.249,
                timestamp = now.minusSeconds(2_700).toString(),
                source = ReportSource.self,
                speciesName = "Northern flicker",
                confidence = 0.79f,
                synced = false,
                verificationStatus = "pending",
                highConfidenceBonus = false
            )
        )
    }

    private suspend fun seedDemoContacts(db: AppDatabase, userId: String) {
        if (db.trustedContactDao().getForUser(userId).first().isNotEmpty()) return
        db.trustedContactDao().insert(
            TrustedContact(
                userId = userId,
                displayName = "Basecamp Contact",
                phoneNumber = "+15550001111",
                relationshipLabel = "Demo relay destination",
                isDefault = true,
                createdAt = Instant.now().minusSeconds(12_000).toString()
            )
        )
    }

    private suspend fun seedDemoRelayJobs(db: AppDatabase, userId: String, walletPublicKey: String) {
        if (db.relayJobIntentDao().getAll().first().isNotEmpty()) return
        val senderWallet = walletPublicKey.ifBlank { "demo-wallet-pending" }
        val now = Instant.now()

        db.relayJobIntentDao().insert(
            RelayJobIntent(
                jobId = "demo-relay-open",
                userId = userId,
                senderWallet = senderWallet,
                recipientName = "Jordan at basecamp",
                recipientPhoneNumber = "+15550001111",
                destinationHash = "demo-destination-open",
                payloadHash = "demo-payload-open",
                messageBody = "Delayed check-in from the trail. Ask them to wait by the trailhead.",
                contextSummary = "Queued outbound relay call with callback details and last known location.",
                contextJson = """{"share_location":true,"share_real_name":true}""",
                expiryTs = now.plusSeconds(14_400).epochSecond,
                rewardAmount = 12,
                nonce = 1L,
                signedMessageBase64 = "demo-signed-open",
                signatureBase64 = "demo-signature-open",
                status = "open",
                openedTxSignature = "demo-open-tx-001",
                callSid = "demo-call-sid-open",
                createdAt = now.minusSeconds(2_400).toString(),
                synced = true
            )
        )

        db.relayJobIntentDao().insert(
            RelayJobIntent(
                jobId = "demo-relay-fulfilled",
                userId = userId,
                senderWallet = senderWallet,
                recipientName = "Sam emergency contact",
                recipientPhoneNumber = "+15550002222",
                destinationHash = "demo-destination-fulfilled",
                payloadHash = "demo-payload-fulfilled",
                messageBody = "Made contact and passed along the hiker update.",
                contextSummary = "Completed relay mission with returned voice reply.",
                contextJson = """{"share_location":false,"share_real_name":true}""",
                expiryTs = now.plusSeconds(7_200).epochSecond,
                rewardAmount = 12,
                nonce = 2L,
                signedMessageBase64 = "demo-signed-fulfilled",
                signatureBase64 = "demo-signature-fulfilled",
                status = "fulfilled",
                proofRef = "demo-proof-ref",
                openedTxSignature = "demo-open-tx-002",
                fulfilledTxSignature = "demo-fulfill-tx-002",
                callSid = "demo-call-sid-fulfilled",
                conversationId = "demo-conversation-fulfilled",
                transcriptSummary = "Recipient confirmed the message and left a short reply.",
                replyJobId = "demo-reply-job-002",
                createdAt = now.minusSeconds(9_000).toString(),
                synced = true
            )
        )
    }

    private suspend fun seedDemoBiodiversity(
        db: AppDatabase,
        userId: String,
        displayName: String,
        walletPublicKey: String?
    ) {
        if (db.biodiversityContributionDao().getAll().first().isNotEmpty()) return
        val now = Instant.now()
        val gradientA = "gradient:#7A5C3A:#F0C77D"
        val gradientB = "gradient:#4D7660:#C2E4CF"

        val seeded = listOf(
            BiodiversityContribution(
                id = UUID.randomUUID().toString(),
                observationId = "demo-bio-stellers-jay",
                userId = userId,
                observerDisplayName = displayName,
                observerWalletPublicKey = walletPublicKey,
                createdAt = now.minusSeconds(18_000).toString(),
                lat = 32.8874,
                lon = -117.2526,
                locationAccuracyMeters = 12f,
                locationSource = "demo_seed",
                audioUri = "demo://audio/stellers_jay.wav",
                photoUri = "demo://photo/stellers_jay.jpg",
                topKJson = """[{"label":"Steller's jay","score":0.91}]""",
                finalLabel = "Steller's jay",
                finalTaxonomicLevel = "species",
                confidence = 0.91f,
                confidenceBand = "high",
                explanation = "The crest-heavy jay call pattern was clear enough for species-level verification.",
                verificationStatus = "verified",
                relayable = true,
                karmaStatus = KarmaStatus.awarded,
                inferenceState = InferenceState.CLASSIFIED_LOCAL,
                cloudSyncState = CloudSyncState.SYNCED,
                photoSyncState = PhotoSyncState.SYNCED,
                safeForRewarding = true,
                savedLocally = true,
                synced = true,
                modelMetadataJson = """{"source":"demo_seed"}""",
                classificationSource = "local_android",
                localModelVersion = "demo-pack-1",
                verificationTxSignature = "demo-bio-verify-001",
                verifiedAt = now.minusSeconds(17_500).toString(),
                collectibleStatus = "verified",
                collectibleId = "species:stellers-jay",
                collectibleName = "Steller's jay",
                collectibleImageUri = gradientA,
                dataShareStatus = "mirrored_cloud"
            ),
            BiodiversityContribution(
                id = UUID.randomUUID().toString(),
                observationId = "demo-bio-california-towhee",
                userId = userId,
                observerDisplayName = displayName,
                observerWalletPublicKey = walletPublicKey,
                createdAt = now.minusSeconds(14_400).toString(),
                lat = 32.8814,
                lon = -117.2362,
                locationAccuracyMeters = 10f,
                locationSource = "demo_seed",
                audioUri = "demo://audio/california_towhee.wav",
                topKJson = """[{"label":"California towhee","score":0.88}]""",
                finalLabel = "California towhee",
                finalTaxonomicLevel = "species",
                confidence = 0.88f,
                confidenceBand = "medium-high",
                explanation = "Dense brush chatter and repeated chip notes matched California towhee.",
                verificationStatus = "verified",
                relayable = true,
                karmaStatus = KarmaStatus.awarded,
                inferenceState = InferenceState.CLASSIFIED_LOCAL,
                cloudSyncState = CloudSyncState.SYNCED,
                photoSyncState = PhotoSyncState.NONE,
                safeForRewarding = true,
                savedLocally = true,
                synced = true,
                modelMetadataJson = """{"source":"demo_seed"}""",
                classificationSource = "local_android",
                localModelVersion = "demo-pack-1",
                verificationTxSignature = "demo-bio-verify-002",
                verifiedAt = now.minusSeconds(13_900).toString(),
                collectibleStatus = "verified",
                collectibleId = "species:california-towhee",
                collectibleName = "California towhee",
                collectibleImageUri = gradientB,
                dataShareStatus = "mirrored_cloud"
            ),
            BiodiversityContribution(
                id = UUID.randomUUID().toString(),
                observationId = "demo-bio-flicker-pending",
                userId = userId,
                observerDisplayName = displayName,
                observerWalletPublicKey = walletPublicKey,
                createdAt = now.minusSeconds(4_200).toString(),
                lat = 32.8842,
                lon = -117.2415,
                locationAccuracyMeters = 14f,
                locationSource = "demo_seed",
                audioUri = "demo://audio/northern_flicker.wav",
                topKJson = """[{"label":"Northern flicker","score":0.79}]""",
                finalLabel = "Northern flicker",
                finalTaxonomicLevel = "species",
                confidence = 0.79f,
                confidenceBand = "medium-high",
                explanation = "The clip is strong enough to queue for verification and a potential field-guide collectible.",
                verificationStatus = "provisional",
                relayable = true,
                karmaStatus = KarmaStatus.pending,
                inferenceState = InferenceState.CLASSIFIED_LOCAL,
                cloudSyncState = CloudSyncState.SYNC_QUEUED,
                photoSyncState = PhotoSyncState.LOCAL_ONLY,
                safeForRewarding = true,
                savedLocally = true,
                synced = false,
                modelMetadataJson = """{"source":"demo_seed"}""",
                classificationSource = "local_android",
                localModelVersion = "demo-pack-1",
                collectibleStatus = "pending_verification",
                collectibleName = "Northern flicker",
                dataShareStatus = "ready_local"
            )
        )

        seeded.forEach { db.biodiversityContributionDao().insert(it) }
    }
}
