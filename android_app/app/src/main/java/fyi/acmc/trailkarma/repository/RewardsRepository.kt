package fyi.acmc.trailkarma.repository

import android.content.Context
import android.util.Base64
import fyi.acmc.trailkarma.BuildConfig
import fyi.acmc.trailkarma.api.*
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.RelayJobIntent
import fyi.acmc.trailkarma.models.TrailReport
import fyi.acmc.trailkarma.models.User
import fyi.acmc.trailkarma.network.NetworkUtil
import fyi.acmc.trailkarma.solana.SolanaPayloadCodec
import fyi.acmc.trailkarma.util.CryptoUtil
import fyi.acmc.trailkarma.wallet.WalletManager
import java.time.Instant
import java.util.UUID

class RewardsRepository(context: Context, private val db: AppDatabase) {
    private val api = RewardsApiClient.create(BuildConfig.REWARDS_BASE_URL)
    private val walletManager = WalletManager(context)
    private val networkUtil = NetworkUtil(context)
    private val userRepository = UserRepository(context, db.userDao())

    private suspend fun currentUser(): User? = userRepository.currentUser()

    suspend fun ensureLocalWallet(user: User): User {
        val wallet = walletManager.ensureWallet(user.userId)
        if (user.walletPublicKey == wallet.publicKeyBase58) return user
        val updated = user.copy(walletPublicKey = wallet.publicKeyBase58)
        db.userDao().insert(updated)
        return updated
    }

    suspend fun syncCurrentUserRegistration(): WalletStateResponse? {
        if (!networkUtil.isOnlineNow()) return null
        val current = currentUser() ?: return null
        val user = ensureLocalWallet(current)
        val response = api.registerUser(
            RegisterUserRequest(
                appUserId = user.userId,
                displayName = user.displayName,
                walletPublicKey = user.walletPublicKey
            )
        )
        if (!response.isSuccessful) return null
        val body = response.body() ?: return null
        db.userDao().updateWalletRegistration(user.userId, body.walletPublicKey, true, Instant.now().toString())
        return body
    }

    suspend fun fetchWalletState(): WalletStateResponse? {
        if (!networkUtil.isOnlineNow()) return null
        val user = currentUser() ?: return null
        if (user.walletPublicKey.isBlank()) return syncCurrentUserRegistration()
        val response = api.getWallet(user.userId)
        return if (response.isSuccessful) response.body() else null
    }

    suspend fun fetchRewardsActivity(): List<RewardActivityItemResponse> {
        if (!networkUtil.isOnlineNow()) return emptyList()
        val user = currentUser() ?: return emptyList()
        val response = api.getRewardsActivity(user.userId)
        return if (response.isSuccessful) {
            response.body()?.items.orEmpty()
        } else {
            emptyList()
        }
    }

    suspend fun claimRewardsForPendingReports() {
        if (!networkUtil.isOnlineNow()) return
        val walletState = syncCurrentUserRegistration() ?: return
        val reports = db.trailReportDao().getPendingRewardClaims()
        for (report in reports) {
            runCatching {
                val response = api.claimContribution(
                    ClaimContributionRequest(
                        appUserId = walletState.appUserId,
                        reportId = report.reportId,
                        title = report.title,
                        description = report.description,
                        type = report.type.name,
                        lat = report.lat,
                        lng = report.lng,
                        timestamp = report.timestamp,
                        speciesName = report.speciesName,
                        confidence = report.confidence,
                        photoUri = report.photoUri
                    )
                )
                if (response.isSuccessful) {
                    response.body()?.let {
                        db.trailReportDao().markRewardClaimed(report.reportId, it.txSignature, it.verificationTier)
                    }
                } else {
                    if (response.code() in listOf(400, 409)) {
                        db.trailReportDao().markRewardRejected(report.reportId)
                    }
                }
            }
        }
    }

    suspend fun createRelayIntent(destinationLabel: String, payloadReference: String): RelayJobIntent? {
        val baseUser = currentUser() ?: return null
        val user = ensureLocalWallet(baseUser)
        val jobId = CryptoUtil.sha256Hex("${user.userId}:${UUID.randomUUID()}:${System.currentTimeMillis()}")
        val destinationHash = CryptoUtil.sha256Hex(destinationLabel)
        val payloadHash = CryptoUtil.sha256Hex(payloadReference)
        val expiryTs = Instant.now().plusSeconds(6 * 60 * 60).epochSecond
        val rewardAmount = 12
        val nonce = System.currentTimeMillis()
        val signedMessage = SolanaPayloadCodec.relayIntentMessage(
            jobIdHex = jobId,
            senderWalletBase58 = user.walletPublicKey,
            destinationHashHex = destinationHash,
            payloadHashHex = payloadHash,
            expiryTs = expiryTs,
            rewardAmount = rewardAmount,
            nonce = nonce
        )
        val signature = walletManager.sign(user.userId, signedMessage)
        return RelayJobIntent(
            jobId = jobId,
            userId = user.userId,
            senderWallet = user.walletPublicKey,
            destinationHash = destinationHash,
            payloadHash = payloadHash,
            expiryTs = expiryTs,
            rewardAmount = rewardAmount,
            nonce = nonce,
            signedMessageBase64 = Base64.encodeToString(signedMessage, Base64.NO_WRAP),
            signatureBase64 = Base64.encodeToString(signature, Base64.NO_WRAP),
            source = "self",
            status = "pending",
            createdAt = Instant.now().toString()
        ).also { db.relayJobIntentDao().insert(it) }
    }

    suspend fun openPendingRelayJobs() {
        if (!networkUtil.isOnlineNow()) return
        syncCurrentUserRegistration() ?: return
        val pendingJobs = db.relayJobIntentDao().getPendingToOpen()
        for (job in pendingJobs) {
            val response = api.openRelayJob(
                OpenRelayJobRequest(
                    appUserId = job.userId,
                    signedMessageBase64 = job.signedMessageBase64,
                    signatureBase64 = job.signatureBase64,
                    jobIdHex = job.jobId,
                    destinationHashHex = job.destinationHash,
                    payloadHashHex = job.payloadHash,
                    expiryTs = job.expiryTs,
                    rewardAmount = job.rewardAmount,
                    nonce = job.nonce
                )
            )
            if (response.isSuccessful) {
                val tx = response.body()?.txSignature.orEmpty()
                db.relayJobIntentDao().markOpened(job.jobId, "open", tx)
            }
        }
    }

    suspend fun fulfillRelayJob(jobId: String, proofRef: String): Boolean {
        if (!networkUtil.isOnlineNow()) return false
        val user = currentUser() ?: return false
        val response = api.fulfillRelayJob(
            FulfillRelayJobRequest(
                appUserId = user.userId,
                jobIdHex = jobId,
                proofRef = proofRef
            )
        )
        if (!response.isSuccessful) return false
        val tx = response.body()?.txSignature.orEmpty()
        db.relayJobIntentDao().markFulfilled(jobId, proofRef, tx)
        return true
    }

    suspend fun prepareTip(recipientWallet: String, amount: Int): PrepareTipResponse? {
        val user = currentUser() ?: return null
        if (!networkUtil.isOnlineNow()) return null
        val response = api.prepareTip(PrepareTipRequest(user.userId, recipientWallet, amount))
        return if (response.isSuccessful) response.body() else null
    }

    suspend fun submitTip(prepared: PrepareTipResponse): Boolean {
        val user = currentUser() ?: return false
        val signature = walletManager.sign(
            user.userId,
            Base64.decode(prepared.signedMessageBase64, Base64.NO_WRAP)
        )
        val response = api.submitTip(
            SubmitTipRequest(
                appUserId = user.userId,
                recipientWallet = prepared.recipientWallet,
                amount = prepared.amount,
                nonce = prepared.nonce,
                tipIdHex = prepared.tipIdHex,
                signedMessageBase64 = prepared.signedMessageBase64,
                signatureBase64 = Base64.encodeToString(signature, Base64.NO_WRAP)
            )
        )
        return response.isSuccessful
    }
}
