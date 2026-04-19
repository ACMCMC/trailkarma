package fyi.acmc.trailkarma.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class RegisterUserRequest(
    @Json(name = "appUserId") val appUserId: String,
    @Json(name = "displayName") val displayName: String,
    @Json(name = "walletPublicKey") val walletPublicKey: String
)

@JsonClass(generateAdapter = true)
data class UpsertProfileRequest(
    @Json(name = "appUserId") val appUserId: String,
    @Json(name = "displayName") val displayName: String,
    @Json(name = "walletPublicKey") val walletPublicKey: String,
    @Json(name = "realName") val realName: String? = null,
    @Json(name = "phoneNumber") val phoneNumber: String? = null,
    @Json(name = "defaultRelayPhoneNumber") val defaultRelayPhoneNumber: String? = null
)

@JsonClass(generateAdapter = true)
data class UserProfileResponse(
    @Json(name = "appUserId") val appUserId: String,
    @Json(name = "displayName") val displayName: String,
    @Json(name = "walletPublicKey") val walletPublicKey: String,
    @Json(name = "realName") val realName: String? = null,
    @Json(name = "phoneNumber") val phoneNumber: String? = null,
    @Json(name = "defaultRelayPhoneNumber") val defaultRelayPhoneNumber: String? = null
)

@JsonClass(generateAdapter = true)
data class WalletStateResponse(
    @Json(name = "appUserId") val appUserId: String,
    @Json(name = "displayName") val displayName: String,
    @Json(name = "walletPublicKey") val walletPublicKey: String,
    @Json(name = "karmaBalance") val karmaBalance: String,
    val badges: List<String> = emptyList(),
    @Json(name = "badgeDetails") val badgeDetails: List<BadgeStatusResponse> = emptyList(),
    @Json(name = "rewardStats") val rewardStats: RewardStatsResponse? = null
)

@JsonClass(generateAdapter = true)
data class BadgeStatusResponse(
    val code: String,
    val label: String,
    val description: String,
    val category: String,
    @Json(name = "accentHex") val accentHex: String,
    val earned: Boolean,
    @Json(name = "currentCount") val currentCount: Int,
    @Json(name = "targetCount") val targetCount: Int,
    val mint: String
)

@JsonClass(generateAdapter = true)
data class RewardStatsResponse(
    @Json(name = "totalKarmaEarned") val totalKarmaEarned: Int,
    @Json(name = "verifiedContributionCount") val verifiedContributionCount: Int,
    @Json(name = "hazardCount") val hazardCount: Int,
    @Json(name = "waterCount") val waterCount: Int,
    @Json(name = "speciesCount") val speciesCount: Int,
    @Json(name = "relayCount") val relayCount: Int,
    @Json(name = "badgeCount") val badgeCount: Int
)

@JsonClass(generateAdapter = true)
data class RewardsActivityResponse(
    val items: List<RewardActivityItemResponse> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RewardActivityItemResponse(
    val id: String,
    val kind: String,
    val title: String,
    val subtitle: String,
    @Json(name = "occurredAt") val occurredAt: String,
    @Json(name = "karmaDelta") val karmaDelta: Int? = null,
    @Json(name = "badgeLabel") val badgeLabel: String? = null,
    @Json(name = "txSignature") val txSignature: String? = null,
    val status: String? = null
)

@JsonClass(generateAdapter = true)
data class ClaimContributionRequest(
    @Json(name = "appUserId") val appUserId: String,
    @Json(name = "reportId") val reportId: String,
    val title: String,
    val description: String,
    val type: String,
    val lat: Double,
    val lng: Double,
    val timestamp: String,
    @Json(name = "speciesName") val speciesName: String? = null,
    val confidence: Float? = null,
    @Json(name = "photoUri") val photoUri: String? = null
)

@JsonClass(generateAdapter = true)
data class ClaimContributionResponse(
    @Json(name = "txSignature") val txSignature: String,
    @Json(name = "rewardAmount") val rewardAmount: Int,
    @Json(name = "verificationTier") val verificationTier: String
)

@JsonClass(generateAdapter = true)
data class TxSignatureResponse(
    @Json(name = "txSignature") val txSignature: String,
    val status: String? = null
)

@JsonClass(generateAdapter = true)
data class OpenRelayJobRequest(
    @Json(name = "appUserId") val appUserId: String,
    @Json(name = "signedMessageBase64") val signedMessageBase64: String,
    @Json(name = "signatureBase64") val signatureBase64: String,
    @Json(name = "jobIdHex") val jobIdHex: String,
    @Json(name = "destinationHashHex") val destinationHashHex: String,
    @Json(name = "payloadHashHex") val payloadHashHex: String,
    @Json(name = "expiryTs") val expiryTs: Long,
    @Json(name = "rewardAmount") val rewardAmount: Int,
    val nonce: Long
)

@JsonClass(generateAdapter = true)
data class FulfillRelayJobRequest(
    @Json(name = "appUserId") val appUserId: String,
    @Json(name = "jobIdHex") val jobIdHex: String,
    @Json(name = "proofRef") val proofRef: String
)

@JsonClass(generateAdapter = true)
data class OpenVoiceRelayJobRequest(
    @Json(name = "appUserId") val appUserId: String,
    @Json(name = "senderWalletPublicKey") val senderWalletPublicKey: String,
    @Json(name = "signedMessageBase64") val signedMessageBase64: String,
    @Json(name = "signatureBase64") val signatureBase64: String,
    @Json(name = "jobIdHex") val jobIdHex: String,
    @Json(name = "destinationHashHex") val destinationHashHex: String,
    @Json(name = "payloadHashHex") val payloadHashHex: String,
    @Json(name = "expiryTs") val expiryTs: Long,
    @Json(name = "rewardAmount") val rewardAmount: Int,
    val nonce: Long,
    @Json(name = "recipientName") val recipientName: String,
    @Json(name = "recipientPhoneNumber") val recipientPhoneNumber: String,
    @Json(name = "messageBody") val messageBody: String,
    @Json(name = "contextSummary") val contextSummary: String,
    @Json(name = "contextJson") val contextJson: String
)

@JsonClass(generateAdapter = true)
data class VoiceRelayJobResponse(
    @Json(name = "jobId") val jobId: String,
    val status: String,
    @Json(name = "openedTxSignature") val openedTxSignature: String? = null,
    @Json(name = "fulfilledTxSignature") val fulfilledTxSignature: String? = null,
    @Json(name = "callSid") val callSid: String? = null,
    @Json(name = "conversationId") val conversationId: String? = null,
    @Json(name = "transcriptSummary") val transcriptSummary: String? = null,
    @Json(name = "replyJobId") val replyJobId: String? = null
)

@JsonClass(generateAdapter = true)
data class VoiceRelayJobsResponse(
    val items: List<VoiceRelayJobResponse> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RelayInboxItemResponse(
    @Json(name = "replyId") val replyId: String,
    @Json(name = "originalJobId") val originalJobId: String,
    @Json(name = "senderLabel") val senderLabel: String,
    @Json(name = "senderPhoneNumber") val senderPhoneNumber: String,
    @Json(name = "messageSummary") val messageSummary: String,
    @Json(name = "messageBody") val messageBody: String,
    @Json(name = "contextJson") val contextJson: String = "{}",
    @Json(name = "createdAt") val createdAt: String,
    val status: String
)

@JsonClass(generateAdapter = true)
data class RelayInboxResponse(
    val items: List<RelayInboxItemResponse> = emptyList()
)

@JsonClass(generateAdapter = true)
data class MeshRelayReplyItemResponse(
    @Json(name = "replyId") val replyId: String,
    @Json(name = "originalJobId") val originalJobId: String,
    @Json(name = "targetUserId") val targetUserId: String,
    @Json(name = "senderLabel") val senderLabel: String,
    @Json(name = "senderPhoneNumber") val senderPhoneNumber: String,
    @Json(name = "messageSummary") val messageSummary: String,
    @Json(name = "messageBody") val messageBody: String,
    @Json(name = "contextJson") val contextJson: String = "{}",
    @Json(name = "createdAt") val createdAt: String,
    val status: String
)

@JsonClass(generateAdapter = true)
data class MeshRelayReplyResponse(
    val items: List<MeshRelayReplyItemResponse> = emptyList()
)

@JsonClass(generateAdapter = true)
data class PrepareTipRequest(
    @Json(name = "appUserId") val appUserId: String,
    @Json(name = "recipientWallet") val recipientWallet: String,
    val amount: Int
)

@JsonClass(generateAdapter = true)
data class PrepareTipResponse(
    @Json(name = "tipIdHex") val tipIdHex: String,
    val nonce: Long,
    val amount: Int,
    @Json(name = "senderWallet") val senderWallet: String,
    @Json(name = "recipientWallet") val recipientWallet: String,
    @Json(name = "signedMessageBase64") val signedMessageBase64: String
)

@JsonClass(generateAdapter = true)
data class SubmitTipRequest(
    @Json(name = "appUserId") val appUserId: String,
    @Json(name = "recipientWallet") val recipientWallet: String,
    val amount: Int,
    val nonce: Long,
    @Json(name = "tipIdHex") val tipIdHex: String,
    @Json(name = "signedMessageBase64") val signedMessageBase64: String,
    @Json(name = "signatureBase64") val signatureBase64: String
)

interface RewardsApi {
    @POST("/v1/users/register")
    suspend fun registerUser(@Body body: RegisterUserRequest): Response<WalletStateResponse>

    @POST("/v1/profile/upsert")
    suspend fun upsertProfile(@Body body: UpsertProfileRequest): Response<UserProfileResponse>

    @GET("/v1/profile/{appUserId}")
    suspend fun getProfile(@Path("appUserId") appUserId: String): Response<UserProfileResponse>

    @GET("/v1/users/{appUserId}/wallet")
    suspend fun getWallet(@Path("appUserId") appUserId: String): Response<WalletStateResponse>

    @GET("/v1/users/{appUserId}/rewards/activity")
    suspend fun getRewardsActivity(@Path("appUserId") appUserId: String): Response<RewardsActivityResponse>

    @POST("/v1/contributions/claim")
    suspend fun claimContribution(@Body body: ClaimContributionRequest): Response<ClaimContributionResponse>

    @POST("/v1/relay-jobs/open")
    suspend fun openRelayJob(@Body body: OpenRelayJobRequest): Response<TxSignatureResponse>

    @POST("/v1/voice-relay/jobs/open")
    suspend fun openVoiceRelayJob(@Body body: OpenVoiceRelayJobRequest): Response<VoiceRelayJobResponse>

    @GET("/v1/voice-relay/jobs/{appUserId}")
    suspend fun getVoiceRelayJobs(@Path("appUserId") appUserId: String): Response<VoiceRelayJobsResponse>

    @GET("/v1/voice-relay/inbox/{appUserId}")
    suspend fun getRelayInbox(@Path("appUserId") appUserId: String): Response<RelayInboxResponse>

    @GET("/v1/voice-relay/mesh/{appUserId}")
    suspend fun getMeshRelayReplies(@Path("appUserId") appUserId: String): Response<MeshRelayReplyResponse>

    @POST("/v1/voice-relay/inbox/{replyId}/ack")
    suspend fun acknowledgeRelayInbox(@Path("replyId") replyId: String): Response<TxSignatureResponse>

    @POST("/v1/relay-jobs/fulfill")
    suspend fun fulfillRelayJob(@Body body: FulfillRelayJobRequest): Response<TxSignatureResponse>

    @POST("/v1/karma/tip/prepare")
    suspend fun prepareTip(@Body body: PrepareTipRequest): Response<PrepareTipResponse>

    @POST("/v1/karma/tip/submit")
    suspend fun submitTip(@Body body: SubmitTipRequest): Response<TxSignatureResponse>
}

object RewardsApiClient {
    fun create(baseUrl: String): RewardsApi = Retrofit.Builder()
        .baseUrl(baseUrl.ensureTrailingSlash())
        .client(
            OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
                .build()
        )
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create(RewardsApi::class.java)

    private fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"
}
