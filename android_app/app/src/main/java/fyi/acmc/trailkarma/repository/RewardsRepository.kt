package fyi.acmc.trailkarma.repository

import android.content.Context
import android.util.Base64
import fyi.acmc.trailkarma.BuildConfig
import fyi.acmc.trailkarma.api.*
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.RelayJobIntent
import fyi.acmc.trailkarma.models.RelayInboxMessage
import fyi.acmc.trailkarma.models.RelayPacket
import fyi.acmc.trailkarma.models.TrailReport
import fyi.acmc.trailkarma.models.TrustedContact
import fyi.acmc.trailkarma.models.User
import fyi.acmc.trailkarma.network.NetworkUtil
import fyi.acmc.trailkarma.solana.SolanaPayloadCodec
import fyi.acmc.trailkarma.util.CryptoUtil
import fyi.acmc.trailkarma.wallet.WalletManager
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first

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
        api.upsertProfile(
            UpsertProfileRequest(
                appUserId = user.userId,
                displayName = user.displayName,
                walletPublicKey = user.walletPublicKey,
                realName = user.realName,
                phoneNumber = user.phoneNumber.ifBlank { null },
                defaultRelayPhoneNumber = user.defaultRelayPhoneNumber.ifBlank { null }
            )
        )
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

    suspend fun fetchTrustedContacts(): List<TrustedContact> {
        val user = currentUser() ?: return emptyList()
        return db.trustedContactDao().getForUser(user.userId).first()
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

    suspend fun createVoiceRelayIntent(
        recipientName: String,
        recipientPhoneNumber: String,
        messageBody: String,
        shareLocation: Boolean,
        shareRealName: Boolean,
        shareCallbackNumber: Boolean
    ): RelayJobIntent? {
        val baseUser = currentUser() ?: return null
        val user = ensureLocalWallet(baseUser)
        val jobId = CryptoUtil.sha256Hex("${user.userId}:${UUID.randomUUID()}:${System.currentTimeMillis()}")
        val destinationHash = CryptoUtil.sha256Hex(recipientPhoneNumber)
        val contextJson = buildVoiceRelayContext(user, shareLocation, shareRealName, shareCallbackNumber)
        val payloadHash = CryptoUtil.sha256Hex("$messageBody|$contextJson")
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
        val summary = buildRelaySummary(user, messageBody, shareLocation, shareRealName, shareCallbackNumber)

        val intent = RelayJobIntent(
            jobId = jobId,
            userId = user.userId,
            senderWallet = user.walletPublicKey,
            relayType = "voice_outbound",
            recipientName = recipientName,
            recipientPhoneNumber = recipientPhoneNumber,
            destinationHash = destinationHash,
            payloadHash = payloadHash,
            messageBody = messageBody,
            contextSummary = summary,
            contextJson = contextJson,
            expiryTs = expiryTs,
            rewardAmount = rewardAmount,
            nonce = nonce,
            signedMessageBase64 = Base64.encodeToString(signedMessage, Base64.NO_WRAP),
            signatureBase64 = Base64.encodeToString(signature, Base64.NO_WRAP),
            source = "self",
            status = "queued_offline",
            createdAt = Instant.now().toString()
        )

        db.relayJobIntentDao().insert(intent)
        db.relayPacketDao().insert(
            RelayPacket(
                packetId = "voice:$jobId",
                payloadJson = JSONObject()
                    .put("type", "voice_relay_intent")
                    .put("job_id", jobId)
                    .put("user_id", user.userId)
                    .put("sender_wallet", user.walletPublicKey)
                    .put("recipient_name", recipientName)
                    .put("recipient_phone_number", recipientPhoneNumber)
                    .put("destination_hash", destinationHash)
                    .put("payload_hash", payloadHash)
                    .put("message_body", messageBody)
                    .put("context_summary", summary)
                    .put("context_json", JSONObject(contextJson))
                    .put("expiry_ts", expiryTs)
                    .put("reward_amount", rewardAmount)
                    .put("nonce", nonce)
                    .put("signed_message_base64", Base64.encodeToString(signedMessage, Base64.NO_WRAP))
                    .put("signature_base64", Base64.encodeToString(signature, Base64.NO_WRAP))
                    .toString(),
                receivedAt = Instant.now().toString(),
                senderDevice = "self",
                hopCount = 0
            )
        )
        return intent
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

    suspend fun openPendingVoiceRelayJobs() {
        if (!networkUtil.isOnlineNow()) return
        val carrier = syncCurrentUserRegistration() ?: return
        val pendingJobs = db.relayJobIntentDao().getVoiceJobsToSync()
        for (job in pendingJobs) {
            val response = api.openVoiceRelayJob(
                OpenVoiceRelayJobRequest(
                    appUserId = carrier.appUserId,
                    senderWalletPublicKey = job.senderWallet,
                    signedMessageBase64 = job.signedMessageBase64,
                    signatureBase64 = job.signatureBase64,
                    jobIdHex = job.jobId,
                    destinationHashHex = job.destinationHash,
                    payloadHashHex = job.payloadHash,
                    expiryTs = job.expiryTs,
                    rewardAmount = job.rewardAmount,
                    nonce = job.nonce,
                    recipientName = job.recipientName,
                    recipientPhoneNumber = job.recipientPhoneNumber,
                    messageBody = job.messageBody,
                    contextSummary = job.contextSummary,
                    contextJson = job.contextJson
                )
            )
            if (response.isSuccessful) {
                response.body()?.let {
                    db.relayJobIntentDao().updateVoiceRelayStatus(
                        jobId = job.jobId,
                        status = it.status,
                        openedTxSignature = it.openedTxSignature,
                        fulfilledTxSignature = it.fulfilledTxSignature,
                        callSid = it.callSid,
                        conversationId = it.conversationId,
                        transcriptSummary = it.transcriptSummary,
                        replyJobId = it.replyJobId
                    )
                }
            }
        }
    }

    suspend fun syncRelayInbox() {
        if (!networkUtil.isOnlineNow()) return
        val user = currentUser() ?: return
        val response = api.getRelayInbox(user.userId)
        if (!response.isSuccessful) return
        val items = response.body()?.items.orEmpty()
        db.relayInboxMessageDao().insertAll(
            items.map {
                RelayInboxMessage(
                    replyId = it.replyId,
                    originalJobId = it.originalJobId,
                    userId = user.userId,
                    senderLabel = it.senderLabel,
                    senderPhoneNumber = it.senderPhoneNumber,
                    messageSummary = it.messageSummary,
                    messageBody = it.messageBody,
                    contextJson = it.contextJson,
                    createdAt = it.createdAt,
                    status = it.status
                )
            }
        )
        val pendingAck = db.relayInboxMessageDao().getPendingAcknowledgements(user.userId)
        for (message in pendingAck) {
            val ackResponse = api.acknowledgeRelayInbox(message.replyId)
            if (ackResponse.isSuccessful) {
                db.relayInboxMessageDao().markAcknowledged(message.replyId)
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

    private suspend fun buildVoiceRelayContext(
        user: User,
        shareLocation: Boolean,
        shareRealName: Boolean,
        shareCallbackNumber: Boolean
    ): String {
        val current = db.locationUpdateDao().getLatest().first()
        val lat = current?.lat
        val lng = current?.lng
        return JSONObject()
            .put("trail_name", user.displayName)
            .put("real_name", if (shareRealName) user.realName else JSONObject.NULL)
            .put("callback_number", if (shareCallbackNumber) user.phoneNumber else JSONObject.NULL)
            .put("location", if (shareLocation && lat != null && lng != null) {
                JSONObject().put("lat", lat).put("lng", lng)
            } else JSONObject.NULL)
            .put("timestamp", Instant.now().toString())
            .toString()
    }

    private fun buildRelaySummary(
        user: User,
        messageBody: String,
        shareLocation: Boolean,
        shareRealName: Boolean,
        shareCallbackNumber: Boolean
    ): String {
        val identity = if (shareRealName && !user.realName.isNullOrBlank()) {
            "${user.displayName} (${"%s".format(user.realName)})"
        } else {
            user.displayName
        }
        val locationSnippet = if (shareLocation) " They shared their last known trail position." else ""
        val callbackSnippet = if (shareCallbackNumber && user.phoneNumber.isNotBlank()) " Their callback number is available if you want to reply." else ""
        return "$identity asked TrailKarma to relay this message: \"$messageBody\".$locationSnippet$callbackSnippet"
    }
}
