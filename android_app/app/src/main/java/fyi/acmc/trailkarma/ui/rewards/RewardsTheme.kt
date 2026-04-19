package fyi.acmc.trailkarma.ui.rewards

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object RewardsPalette {
    val Mist = Color(0xFFF5F0E6)
    val Sand = Color(0xFFE6D7BC)
    val Clay = Color(0xFFC56A4A)
    val Pine = Color(0xFF355844)
    val Forest = Color(0xFF1F3A2E)
    val Moss = Color(0xFF6A8E4A)
    val Sky = Color(0xFF3D8DCC)
    val Gold = Color(0xFFE7A64F)
    val Ink = Color(0xFF1E1D1A)
    val Stone = Color(0xFF6D6558)
    val Card = Color(0xFFFFFBF4)
}

private val rewardsColorScheme: ColorScheme = lightColorScheme(
    primary = RewardsPalette.Forest,
    onPrimary = Color.White,
    primaryContainer = RewardsPalette.Sand,
    onPrimaryContainer = RewardsPalette.Ink,
    secondary = RewardsPalette.Sky,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEBFA),
    onSecondaryContainer = RewardsPalette.Ink,
    tertiary = RewardsPalette.Gold,
    onTertiary = RewardsPalette.Ink,
    background = RewardsPalette.Mist,
    onBackground = RewardsPalette.Ink,
    surface = RewardsPalette.Card,
    onSurface = RewardsPalette.Ink,
    surfaceVariant = Color(0xFFF0E7D7),
    onSurfaceVariant = RewardsPalette.Stone,
    outline = Color(0xFFB7AA93)
)

private val rewardsTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 48.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 34.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.6.sp
    )
)

@Composable
fun TrailKarmaRewardsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = rewardsColorScheme,
        typography = rewardsTypography,
        content = content
    )
}
