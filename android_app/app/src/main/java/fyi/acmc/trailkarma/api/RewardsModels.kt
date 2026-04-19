package fyi.acmc.trailkarma.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WalletStateResponse(
    @Json(name = "appUserId") val appUserId: String = "",
    @Json(name = "displayName") val displayName: String = "",
    @Json(name = "walletPublicKey") val walletPublicKey: String = "",
    @Json(name = "karmaBalance") val karmaBalance: String = "0",
    val badges: List<String> = emptyList(),
    @Json(name = "badgeDetails") val badgeDetails: List<BadgeStatusResponse> = emptyList(),
    @Json(name = "rewardStats") val rewardStats: RewardStats? = null
)

@JsonClass(generateAdapter = true)
data class RewardStats(
    @Json(name = "badgeCount") val badgeCount: Int = 0,
    @Json(name = "verifiedContributionCount") val verifiedContributionCount: Int = 0,
    @Json(name = "totalKarmaEarned") val totalKarmaEarned: Int = 0,
    @Json(name = "speciesCount") val speciesCount: Int = 0,
    @Json(name = "hazardCount") val hazardCount: Int = 0,
    @Json(name = "waterCount") val waterCount: Int = 0,
    @Json(name = "relayCount") val relayCount: Int = 0
)

@JsonClass(generateAdapter = true)
data class BadgeStatusResponse(
    val code: String,
    val label: String,
    val description: String,
    val category: String,
    @Json(name = "accentHex") val accentHex: String,
    val earned: Boolean,
    val currentCount: Int = 0,
    val targetCount: Int = 0,
    val mint: String = ""
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
    val txSignature: String? = null,
    val status: String? = null
)
