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
import fyi.acmc.trailkarma.util.EncryptionUtil
import fyi.acmc.trailkarma.wallet.WalletManager
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first

class RewardsRepository(context: Context, private val db: AppDatabase) {
    private val walletManager = WalletManager(context)
    private val networkUtil = NetworkUtil(context)
    private val userRepository = UserRepository(context, db.userDao())

    private suspend fun currentUser(): User? = userRepository.currentUser()
    private suspend fun <T> safeApiCall(block: suspend () -> T): T? = runCatching { block() }.getOrNull()

    suspend fun ensureLocalWallet(user: User): User {
        val wallet = walletManager.ensureWallet(user.userId)
        if (user.walletPublicKey == wallet.publicKeyBase58) return user
        val updated = user.copy(walletPublicKey = wallet.publicKeyBase58)
        db.userDao().insert(updated)
        return updated
    }

    suspend fun syncCurrentUserRegistration(): WalletStateResponse? {
        // Backend dependency removed. Registration is now local-only.
        val current = currentUser() ?: userRepository.ensureLocalUser()
        val user = ensureLocalWallet(current)
        db.userDao().updateWalletRegistration(user.userId, user.walletPublicKey, true, Instant.now().toString())
        return WalletStateResponse(
            displayName = user.displayName,
            walletPublicKey = user.walletPublicKey
        )
    }

    suspend fun fetchWalletState(): WalletStateResponse? {
        // Backend dependency removed. Returning local state only.
        return null 
    }

    suspend fun fetchRewardsActivity(): List<RewardActivityItemResponse> {
        // Backend dependency removed. Returning empty list.
        return emptyList()
    }

    suspend fun fetchTrustedContacts(): List<TrustedContact> {
        val user = currentUser() ?: userRepository.ensureLocalUser()
        return db.trustedContactDao().getForUser(user.userId).first()
    }

    suspend fun claimRewardsForPendingReports() {
        // Backend dependency removed. Rewards are now tracked via Databricks sync.
        // The RewardsRepository no longer handles direct on-chain claiming via the Node.js backend.
    }

    suspend fun createRelayIntent(destinationLabel: String, payloadReference: String): RelayJobIntent? {
        val baseUser = currentUser() ?: userRepository.ensureLocalUser()
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
        val baseUser = currentUser() ?: userRepository.ensureLocalUser()
        val user = ensureLocalWallet(baseUser)
        val jobId = CryptoUtil.sha256Hex("${user.userId}:${UUID.randomUUID()}:${System.currentTimeMillis()}")
        val destinationHash = CryptoUtil.sha256Hex(recipientPhoneNumber)
        val contextJson = buildVoiceRelayContext(user, shareLocation, shareRealName, shareCallbackNumber)
        
        // PRIVACY: Encrypt the sensitive payload (recipient, message, context) for the Backend
        val plainPayload = JSONObject()
            .put("recipientName", recipientName)
            .put("recipientPhoneNumber", recipientPhoneNumber)
            .put("messageBody", messageBody)
            .put("contextJson", contextJson)
            .toString()
        val encryptedBlob = EncryptionUtil.encryptForBackend(plainPayload, BuildConfig.RELAY_ENCRYPTION_PUBLIC_KEY)
        val payloadHash = CryptoUtil.sha256Hex(encryptedBlob)
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
            encryptedBlob = encryptedBlob,
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
                    // Carrier devices only forward this payload blob.
                    .put("encrypted_blob", encryptedBlob)
                    .put("destination_hash", destinationHash)
                    .put("payload_hash", payloadHash)
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
        // Backend dependency removed. Relay jobs are now synced via Databricks.
    }

    suspend fun openPendingVoiceRelayJobs() {
        // Backend dependency removed. Voice relay jobs are now handled via the Oracle/Attestor service independent of direct app calls.
    }

    suspend fun refreshVoiceRelayJobs(): List<Any> {
        // Backend dependency removed.
        return emptyList()
    }

    suspend fun syncRelayInbox(): Int {
        // Backend dependency removed.
        return 0
    }

    suspend fun syncMeshRelayReplies() {
        // Backend dependency removed.
    }

    suspend fun fulfillRelayJob(jobId: String, proofRef: String): Boolean {
        // Backend dependency removed.
        return false
    }

    suspend fun prepareTip(recipientWallet: String, amount: Int): Any? {
        // Backend dependency removed.
        return null
    }

    suspend fun submitTip(prepared: Any): Boolean {
        // Backend dependency removed.
        return false
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
