package fyi.acmc.trailkarma.demo

import android.content.Context
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.BiodiversityContribution
import fyi.acmc.trailkarma.models.CloudSyncState
import fyi.acmc.trailkarma.models.InferenceState
import fyi.acmc.trailkarma.models.KarmaStatus
import fyi.acmc.trailkarma.models.PhotoSyncState
import fyi.acmc.trailkarma.models.RelayJobIntent
import fyi.acmc.trailkarma.models.ReportSource
import fyi.acmc.trailkarma.models.ReportType
import fyi.acmc.trailkarma.models.TrailReport
import fyi.acmc.trailkarma.models.TrustedContact
import fyi.acmc.trailkarma.models.User
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.absoluteValue

object DemoDataSeeder {
    private const val PREFS_NAME = "trailkarma_demo_seed"
    private const val KEY_SEED_VERSION = "seed_version"
    private const val SEED_VERSION = 3

    suspend fun seedIfNeeded(
        context: Context,
        db: AppDatabase,
        user: User
    ): User {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_SEED_VERSION, 0) >= SEED_VERSION) return user

        val seededUser = if (
            user.displayName.startsWith("Trail Hiker ") &&
            user.realName.isNullOrBlank() &&
            user.phoneNumber.isBlank()
        ) {
            user.copy(
                displayName = "Maya Trail Scout",
                realName = "Maya Solis",
                phoneNumber = "+1 858-555-0147",
                defaultRelayPhoneNumber = "+1 858-555-0147"
            )
        } else {
            user
        }
        db.userDao().insert(seededUser)

        val now = Instant.now()
        val wallet = seededUser.walletPublicKey.takeIf { it.isNotBlank() }
        val contacts = listOf(
            TrustedContact(
                contactId = "demo-contact-primary",
                userId = seededUser.userId,
                displayName = "Noah Basecamp",
                phoneNumber = "+1 858-555-0199",
                relationshipLabel = "Basecamp",
                isDefault = true,
                createdAt = now.minus(4, ChronoUnit.DAYS).toString()
            ),
            TrustedContact(
                contactId = "demo-contact-ranger",
                userId = seededUser.userId,
                displayName = "Ranger Elena",
                phoneNumber = "+1 619-555-0124",
                relationshipLabel = "Trail support",
                isDefault = false,
                createdAt = now.minus(3, ChronoUnit.DAYS).toString()
            )
        )
        db.trustedContactDao().insertAll(contacts)

        val reports = listOf(
            TrailReport(
                reportId = "demo-report-loose-rock",
                userId = seededUser.userId,
                type = ReportType.hazard,
                title = "Loose rock on switchback",
                description = "Flagged a fresh slide on the southern ridge descent.",
                lat = 32.87692,
                lng = -117.24088,
                timestamp = now.minus(38, ChronoUnit.HOURS).toString(),
                source = ReportSource.self,
                synced = true,
                verificationStatus = "claimed",
                verificationTier = "tier_3",
                rewardClaimed = true,
                rewardTxSignature = "demo-hazard-loose-rock"
            ),
            TrailReport(
                reportId = "demo-report-spring-water",
                userId = seededUser.userId,
                type = ReportType.water,
                title = "Spring running clear",
                description = "Water source confirmed near mile 12 with fresh flow.",
                lat = 32.87754,
                lng = -117.23994,
                timestamp = now.minus(31, ChronoUnit.HOURS).toString(),
                source = ReportSource.self,
                synced = true,
                verificationStatus = "claimed",
                verificationTier = "tier_1",
                rewardClaimed = true,
                rewardTxSignature = "demo-water-spring"
            ),
            TrailReport(
                reportId = "demo-report-hawk-nest",
                userId = seededUser.userId,
                type = ReportType.species,
                title = "Hawk nest logged",
                description = "High-confidence species report archived from the ridge overlook.",
                lat = 32.87806,
                lng = -117.23928,
                timestamp = now.minus(28, ChronoUnit.HOURS).toString(),
                speciesName = "Red-tailed Hawk",
                confidence = 0.92f,
                source = ReportSource.self,
                synced = true,
                verificationStatus = "claimed",
                verificationTier = "tier_2",
                rewardClaimed = true,
                rewardTxSignature = "demo-species-hawk",
                highConfidenceBonus = true
            ),
            TrailReport(
                reportId = "demo-report-flooded-footbridge",
                userId = seededUser.userId,
                type = ReportType.hazard,
                title = "Footbridge washed over",
                description = "Created a reroute note for hikers approaching the creek crossing.",
                lat = 32.87918,
                lng = -117.23874,
                timestamp = now.minus(21, ChronoUnit.HOURS).toString(),
                source = ReportSource.self,
                synced = true,
                verificationStatus = "claimed",
                verificationTier = "tier_3",
                rewardClaimed = true,
                rewardTxSignature = "demo-hazard-footbridge"
            )
        )
        for (report in reports) {
            db.trailReportDao().insert(report)
        }

        val relayJobs = listOf(
            RelayJobIntent(
                jobId = "demo-relay-fulfilled-1",
                userId = seededUser.userId,
                senderWallet = wallet ?: "demo-wallet-maya",
                relayType = "voice_outbound",
                recipientName = "Noah Basecamp",
                recipientPhoneNumber = "+1 858-555-0199",
                destinationHash = "demo-destination-hash-1",
                payloadHash = "demo-payload-hash-1",
                messageBody = "Reached camp safely. Passing along the latest water status for section B.",
                contextSummary = "Safe check-in after ridge traverse",
                contextJson = """{"urgency":"normal","mode":"voice"}""",
                expiryTs = now.plus(9, ChronoUnit.HOURS).epochSecond,
                rewardAmount = 12,
                nonce = 1101L,
                signedMessageBase64 = "demo-signature-message-1",
                signatureBase64 = "demo-signature-1",
                status = "fulfilled",
                proofRef = "demo-call-proof-1",
                openedTxSignature = "demo-relay-open-1",
                fulfilledTxSignature = "demo-relay-fulfill-1",
                callSid = "demo-call-sid-1",
                conversationId = "demo-conversation-1",
                transcriptSummary = "Outbound relay delivered with callback captured.",
                createdAt = now.minus(18, ChronoUnit.HOURS).toString(),
                synced = true
            ),
            RelayJobIntent(
                jobId = "demo-relay-fulfilled-2",
                userId = seededUser.userId,
                senderWallet = wallet ?: "demo-wallet-maya",
                relayType = "voice_reply",
                recipientName = "Ranger Elena",
                recipientPhoneNumber = "+1 619-555-0124",
                destinationHash = "demo-destination-hash-2",
                payloadHash = "demo-payload-hash-2",
                messageBody = "Return call requested once a hiker reaches signal near the highway crossing.",
                contextSummary = "Delayed reply routed back through the mesh",
                contextJson = """{"urgency":"normal","mode":"reply"}""",
                expiryTs = now.plus(12, ChronoUnit.HOURS).epochSecond,
                rewardAmount = 12,
                nonce = 1102L,
                signedMessageBase64 = "demo-signature-message-2",
                signatureBase64 = "demo-signature-2",
                status = "fulfilled",
                proofRef = "demo-call-proof-2",
                openedTxSignature = "demo-relay-open-2",
                fulfilledTxSignature = "demo-relay-fulfill-2",
                callSid = "demo-call-sid-2",
                conversationId = "demo-conversation-2",
                transcriptSummary = "Reply captured and queued for the sender.",
                createdAt = now.minus(11, ChronoUnit.HOURS).toString(),
                synced = true
            ),
            RelayJobIntent(
                jobId = "demo-relay-open-3",
                userId = seededUser.userId,
                senderWallet = wallet ?: "demo-wallet-maya",
                relayType = "voice_outbound",
                recipientName = "Trailhead Pickup",
                recipientPhoneNumber = "+1 760-555-0118",
                destinationHash = "demo-destination-hash-3",
                payloadHash = "demo-payload-hash-3",
                messageBody = "Need a pickup update if anyone gets signal near the lot.",
                contextSummary = "Pending ride coordination",
                contextJson = """{"urgency":"medium","mode":"voice"}""",
                expiryTs = now.plus(18, ChronoUnit.HOURS).epochSecond,
                rewardAmount = 12,
                nonce = 1103L,
                signedMessageBase64 = "demo-signature-message-3",
                signatureBase64 = "demo-signature-3",
                status = "open",
                openedTxSignature = "demo-relay-open-3",
                createdAt = now.minus(2, ChronoUnit.HOURS).toString(),
                synced = true
            )
        )
        for (job in relayJobs) {
            db.relayJobIntentDao().insert(job)
        }

        val biodiversity = listOf(
            seededCollectible(
                seededUser = seededUser,
                wallet = wallet,
                observationId = "demo-observation-pacific-wren",
                label = "Pacific Wren",
                explanation = "Dense canyon song matched the local audio profile and was verified for the field guide.",
                lat = 32.87712,
                lon = -117.24058,
                confidence = 0.97f,
                confidenceBand = "high",
                createdAt = now.minus(54, ChronoUnit.HOURS),
                rewardPoints = 13
            ),
            seededCollectible(
                seededUser = seededUser,
                wallet = wallet,
                observationId = "demo-observation-red-tailed-hawk",
                label = "Red-tailed Hawk",
                explanation = "Audio plus a visual confirmation pass produced a high-confidence match.",
                lat = 32.87841,
                lon = -117.23936,
                confidence = 0.94f,
                confidenceBand = "high",
                createdAt = now.minus(46, ChronoUnit.HOURS),
                rewardPoints = 13
            ),
            seededCollectible(
                seededUser = seededUser,
                wallet = wallet,
                observationId = "demo-observation-mule-deer",
                label = "Mule Deer",
                explanation = "Verified hoof-and-call evidence generated a field-guide collectible for the demo account.",
                lat = 32.87924,
                lon = -117.23892,
                confidence = 0.9f,
                confidenceBand = "high",
                createdAt = now.minus(34, ChronoUnit.HOURS),
                rewardPoints = 10
            ),
            seededCollectible(
                seededUser = seededUser,
                wallet = wallet,
                observationId = "demo-observation-stellers-jay",
                label = "Steller's Jay",
                explanation = "Distinctive call pattern was reviewed and approved for the species gallery.",
                lat = 32.87634,
                lon = -117.24142,
                confidence = 0.88f,
                confidenceBand = "medium",
                createdAt = now.minus(27, ChronoUnit.HOURS),
                rewardPoints = 8
            ),
            seededCollectible(
                seededUser = seededUser,
                wallet = wallet,
                observationId = "demo-observation-tree-frog",
                label = "Pacific Tree Frog",
                explanation = "Water-adjacent call was preserved as a biodiversity collectible after verification.",
                lat = 32.87786,
                lon = -117.24108,
                confidence = 0.85f,
                confidenceBand = "medium",
                createdAt = now.minus(19, ChronoUnit.HOURS),
                rewardPoints = 8
            ),
            seededCollectible(
                seededUser = seededUser,
                wallet = wallet,
                observationId = "demo-observation-coyote",
                label = "Coyote",
                explanation = "Trail-edge howl signature was strong enough to archive as a verified card.",
                lat = 32.87892,
                lon = -117.24012,
                confidence = 0.82f,
                confidenceBand = "medium",
                createdAt = now.minus(13, ChronoUnit.HOURS),
                rewardPoints = 8
            ),
            seededPendingContribution(
                seededUser = seededUser,
                wallet = wallet,
                observationId = "demo-observation-owl-pending",
                label = "Great Horned Owl",
                explanation = "Queued for a second verifier before the collectible clears.",
                lat = 32.87952,
                lon = -117.23988,
                confidence = 0.73f,
                confidenceBand = "medium",
                createdAt = now.minus(90, ChronoUnit.MINUTES)
            )
        )
        for (contribution in biodiversity) {
            db.biodiversityContributionDao().insert(contribution)
        }

        prefs.edit().putInt(KEY_SEED_VERSION, SEED_VERSION).apply()
        return seededUser
    }

    private fun seededCollectible(
        seededUser: User,
        wallet: String?,
        observationId: String,
        label: String,
        explanation: String,
        lat: Double,
        lon: Double,
        confidence: Float,
        confidenceBand: String,
        createdAt: Instant,
        rewardPoints: Int
    ): BiodiversityContribution = BiodiversityContribution(
        id = observationId,
        observationId = observationId,
        userId = seededUser.userId,
        observerDisplayName = seededUser.displayName,
        observerWalletPublicKey = wallet,
        createdAt = createdAt.toString(),
        claimedLabel = label,
        lat = lat,
        lon = lon,
        locationAccuracyMeters = 6.5f,
        locationSource = "seeded_demo",
        finalLabel = label,
        finalTaxonomicLevel = "species",
        confidence = confidence,
        confidenceBand = confidenceBand,
        explanation = explanation,
        verificationStatus = "verified",
        relayable = true,
        karmaStatus = KarmaStatus.awarded,
        inferenceState = InferenceState.CLASSIFIED_LOCAL,
        cloudSyncState = CloudSyncState.SYNCED,
        photoSyncState = PhotoSyncState.SYNCED,
        safeForRewarding = true,
        savedLocally = true,
        synced = true,
        modelMetadataJson = """{"source":"demo_seed","version":"v3"}""",
        classificationSource = "demo_seed",
        localModelVersion = "demo-v3",
        verificationTxSignature = "demo-bio-${observationId.takeLast(6)}",
        verifiedAt = createdAt.plus(18, ChronoUnit.MINUTES).toString(),
        collectibleStatus = "verified",
        collectibleId = "species:${slugify(label)}",
        collectibleName = label,
        collectibleImageUri = buildCollectibleGradient(label),
        rewardPointsAwarded = rewardPoints,
        dataShareStatus = "shared_demo",
        sharedWithOrgAt = createdAt.plus(25, ChronoUnit.MINUTES).toString()
    )

    private fun seededPendingContribution(
        seededUser: User,
        wallet: String?,
        observationId: String,
        label: String,
        explanation: String,
        lat: Double,
        lon: Double,
        confidence: Float,
        confidenceBand: String,
        createdAt: Instant
    ): BiodiversityContribution = BiodiversityContribution(
        id = observationId,
        observationId = observationId,
        userId = seededUser.userId,
        observerDisplayName = seededUser.displayName,
        observerWalletPublicKey = wallet,
        createdAt = createdAt.toString(),
        claimedLabel = label,
        lat = lat,
        lon = lon,
        locationAccuracyMeters = 8.2f,
        locationSource = "seeded_demo",
        finalLabel = label,
        finalTaxonomicLevel = "species",
        confidence = confidence,
        confidenceBand = confidenceBand,
        explanation = explanation,
        verificationStatus = "pending",
        relayable = true,
        karmaStatus = KarmaStatus.pending,
        inferenceState = InferenceState.CLASSIFIED_LOCAL,
        cloudSyncState = CloudSyncState.SYNCED,
        photoSyncState = PhotoSyncState.NONE,
        safeForRewarding = true,
        savedLocally = true,
        synced = true,
        modelMetadataJson = """{"source":"demo_seed","version":"v3"}""",
        classificationSource = "demo_seed",
        localModelVersion = "demo-v3",
        collectibleStatus = "pending_verification",
        collectibleName = label,
        collectibleImageUri = buildCollectibleGradient(label),
        dataShareStatus = "ready_for_review"
    )

    private fun slugify(value: String): String = value
        .trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { UUID.randomUUID().toString().take(8) }

    private fun buildCollectibleGradient(label: String): String {
        val hash = label.hashCode().absoluteValue
        val hueA = hash % 360
        val hueB = (hueA + 56) % 360
        val accentA = android.graphics.Color.HSVToColor(floatArrayOf(hueA.toFloat(), 0.48f, 0.88f))
        val accentB = android.graphics.Color.HSVToColor(floatArrayOf(hueB.toFloat(), 0.56f, 0.94f))
        return "gradient:${toHex(accentA)}:${toHex(accentB)}"
    }

    private fun toHex(color: Int): String = String.format("#%06X", 0xFFFFFF and color)
}
