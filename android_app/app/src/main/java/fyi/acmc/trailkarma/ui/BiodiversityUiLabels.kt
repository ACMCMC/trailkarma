package fyi.acmc.trailkarma.ui

fun biodiversitySourceLabel(source: String?): String? = when (source) {
    null -> null
    "local_tflite_perch" -> "on-device Perch"
    "heuristic_fallback" -> "heuristic fallback"
    "backend_acoustic" -> "backend acoustic"
    else -> source.replace('_', ' ')
}
