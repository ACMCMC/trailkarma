package fyi.acmc.trailkarma.ui.camera

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SpeciesIdentification(
    val speciesName: String,
    val confidence: Float,
    val imageUri: String? = null
)

class CameraViewModel : ViewModel() {
    private val _identification = MutableStateFlow<SpeciesIdentification?>(null)
    val identification: StateFlow<SpeciesIdentification?> = _identification

    fun captureAndIdentify(imageUri: String) {
        // TODO: Integrate TFLite model for species identification
        // For now, this is a placeholder
    }

    fun clearIdentification() {
        _identification.value = null
    }
}
