package fyi.acmc.trailkarma.api

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WalletStateResponse(
    val displayName: String? = null,
    val walletPublicKey: String = "",
    val rewardStats: RewardStats? = null,
    val karmaBalance: String? = "0",
    val badgeDetails: List<BadgeStatusResponse>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class RewardStats(
    val badgeCount: Int = 0,
    val verifiedContributionCount: Int = 0,
    val totalKarmaEarned: Int = 0,
    val speciesCount: Int = 0,
    val hazardCount: Int = 0
)

@JsonClass(generateAdapter = true)
data class BadgeStatusResponse(
    val code: String,
    val label: String,
    val description: String,
    val category: String,
    val accentHex: String,
    val earned: Boolean,
    val currentCount: Int = 0,
    val targetCount: Int = 0,
    val mint: String? = null
)

@JsonClass(generateAdapter = true)
data class RewardActivityItemResponse(
    val id: String,
    val kind: String,
    val title: String,
    val subtitle: String,
    val karmaDelta: Int? = 0,
    val occurredAt: String,
    val txSignature: String? = null,
    val status: String? = null
)
