package fyi.acmc.trailkarma.api

import android.os.Build
import java.net.URI

object DebugEndpointResolver {
    fun resolve(baseUrl: String): String {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return normalized
        if (uri.host != "10.0.2.2" || isLikelyEmulator()) {
            return normalized
        }

        val rewritten = URI(
            uri.scheme,
            uri.userInfo,
            "127.0.0.1",
            uri.port,
            uri.path,
            uri.query,
            uri.fragment
        )
        return rewritten.toString().let { if (it.endsWith("/")) it else "$it/" }
    }

    private fun isLikelyEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.orEmpty()
        val model = Build.MODEL.orEmpty()
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val brand = Build.BRAND.orEmpty()
        val device = Build.DEVICE.orEmpty()
        val product = Build.PRODUCT.orEmpty()

        return fingerprint.startsWith("generic") ||
            fingerprint.startsWith("unknown") ||
            model.contains("sdk", ignoreCase = true) ||
            model.contains("emulator", ignoreCase = true) ||
            manufacturer.contains("Genymotion", ignoreCase = true) ||
            (brand.startsWith("generic") && device.startsWith("generic")) ||
            product.contains("sdk", ignoreCase = true) ||
            product.contains("emulator", ignoreCase = true)
    }
}
