package fyi.acmc.trailkarma

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.LocationUpdate
import fyi.acmc.trailkarma.models.ReportType
import fyi.acmc.trailkarma.models.TrailReport
import fyi.acmc.trailkarma.models.User
import fyi.acmc.trailkarma.repository.RewardsRepository
import fyi.acmc.trailkarma.repository.UserRepository
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RewardsRepositoryIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = AppDatabase.get(context)
    private val userRepository = UserRepository(context, db.userDao())
    private val rewardsRepository = RewardsRepository(context, db)

    @Test
    fun registrationAndRewardClaimSyncWithBackend() = runBlocking {
        resetDatabase()
        val user = createFreshUser("Integration Alice")
        val reportId = "android-int-${System.currentTimeMillis()}"

        db.trailReportDao().insert(
            TrailReport(
                reportId = reportId,
                userId = user.userId,
                type = ReportType.hazard,
                title = "Loose rocks on ridge",
                description = "The switchback is covered in loose shale.",
                lat = 32.881,
                lng = -117.234,
                timestamp = Instant.now().toString(),
            )
        )

        val walletState = waitForValue { rewardsRepository.syncCurrentUserRegistration() }
        assertNotNull(walletState)
        assertTrue(walletState!!.walletPublicKey.isNotBlank())

        rewardsRepository.claimRewardsForPendingReports()

        val claimedReport = waitForReport(reportId) {
            it.rewardClaimed &&
                it.verificationStatus == "claimed" &&
                !it.rewardTxSignature.isNullOrBlank()
        }
        assertTrue(claimedReport.rewardClaimed)
        assertEquals("claimed", claimedReport.verificationStatus)
        assertFalse(claimedReport.rewardTxSignature.isNullOrBlank())

        val refreshedWallet = waitForValue {
            rewardsRepository.fetchWalletState()?.takeIf {
                (it.karmaBalance.toIntOrNull() ?: 0) >= 10 && it.badges.contains("Trail Scout")
            }
        }
        assertNotNull(refreshedWallet)
        assertTrue((refreshedWallet!!.karmaBalance.toIntOrNull() ?: 0) >= 10)
        assertTrue(refreshedWallet.badges.contains("Trail Scout"))

        val activity = waitForValue {
            rewardsRepository.fetchRewardsActivity().takeIf { entries ->
                entries.any { it.kind == "contribution_reward" || it.kind == "badge_earned" }
            }
        }.orEmpty()
        assertTrue(activity.any { it.kind == "contribution_reward" || it.kind == "badge_earned" })
    }

    @Test
    fun relayIntentCanOpenAndVoiceIntentStaysQueuedOffline() = runBlocking {
        resetDatabase()
        createFreshUser("Integration Relay")

        waitForValue { rewardsRepository.syncCurrentUserRegistration() }

        val relayIntent = rewardsRepository.createRelayIntent(
            destinationLabel = "Integration Contact",
            payloadReference = "Queued check-in for later delivery"
        )
        assertNotNull(relayIntent)

        rewardsRepository.openPendingRelayJobs()

        val openJob = db.relayJobIntentDao().getAll().first().first { it.jobId == relayIntent!!.jobId }
        assertEquals("open", openJob.status)
        assertFalse(openJob.openedTxSignature.isNullOrBlank())

        db.locationUpdateDao().insert(
            LocationUpdate(
                userId = userRepository.currentUser()!!.userId,
                timestamp = Instant.now().toString(),
                lat = 32.882,
                lng = -117.235,
            )
        )

        val voiceIntent = rewardsRepository.createVoiceRelayIntent(
            recipientName = "Offline Contact",
            recipientPhoneNumber = "+15551239999",
            messageBody = "Checking in from the trail once someone regains service.",
            shareLocation = true,
            shareRealName = true,
            shareCallbackNumber = true
        )
        assertNotNull(voiceIntent)
        assertEquals("queued_offline", voiceIntent!!.status)

        val packet = db.relayPacketDao().getById("voice:${voiceIntent.jobId}")
        assertNotNull(packet)
        assertTrue(packet!!.payloadJson.contains("\"type\":\"voice_relay_intent\""))
    }

    private suspend fun createFreshUser(displayName: String): User {
        val user = User(
            userId = UUID.randomUUID().toString(),
            displayName = displayName,
            realName = displayName,
            phoneNumber = "+1555000${(1000..9999).random()}",
            defaultRelayPhoneNumber = "+1555000${(1000..9999).random()}",
        )
        userRepository.saveUser(user)
        return user
    }

    private suspend fun resetDatabase() {
        db.clearAllTables()
    }

    private suspend fun waitForReport(
        reportId: String,
        attempts: Int = 20,
        delayMs: Long = 1000,
        predicate: (TrailReport) -> Boolean
    ): TrailReport {
        repeat(attempts - 1) {
            db.trailReportDao().getById(reportId)?.let {
                if (predicate(it)) return it
            }
            kotlinx.coroutines.delay(delayMs)
        }
        val final = db.trailReportDao().getById(reportId)
        assertNotNull("Timed out waiting for report $reportId", final)
        assertTrue("Timed out waiting for report $reportId to satisfy predicate", predicate(final!!))
        return final
    }

    private suspend fun <T> waitForValue(
        attempts: Int = 12,
        delayMs: Long = 1500,
        block: suspend () -> T?
    ): T? {
        repeat(attempts - 1) {
            block()?.let { return it }
            kotlinx.coroutines.delay(delayMs)
        }
        return block()
    }
}
