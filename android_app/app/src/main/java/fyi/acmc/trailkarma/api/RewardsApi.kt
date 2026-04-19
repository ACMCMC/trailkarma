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
data class WalletStateResponse(
    @Json(name = "appUserId") val appUserId: String,
    @Json(name = "displayName") val displayName: String,
    @Json(name = "walletPublicKey") val walletPublicKey: String,
    @Json(name = "karmaBalance") val karmaBalance: String,
    val badges: List<String> = emptyList()
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

    @GET("/v1/users/{appUserId}/wallet")
    suspend fun getWallet(@Path("appUserId") appUserId: String): Response<WalletStateResponse>

    @POST("/v1/contributions/claim")
    suspend fun claimContribution(@Body body: ClaimContributionRequest): Response<ClaimContributionResponse>

    @POST("/v1/relay-jobs/open")
    suspend fun openRelayJob(@Body body: OpenRelayJobRequest): Response<TxSignatureResponse>

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
