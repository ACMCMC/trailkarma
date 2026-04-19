package fyi.acmc.trailkarma.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(onSpeciesIdentified: () -> Unit, vm: CameraViewModel = viewModel()) {
    val uiState by vm.uiState.collectAsState()
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }
    val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        vm.confirmPreparedPhotoCapture(success)
    }
    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.importSelectedPhoto(uri)
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Photo verification", fontWeight = FontWeight.Bold)
                        Text(
                            "Check a typed species claim against a submitted image with Gemini",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onSpeciesIdentified) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFFFF7EC), Color(0xFFF3ECE2), MaterialTheme.colorScheme.background)
                    )
                )
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF8E6E2E))
                        Text("Species proof from a photo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "Take or upload an image, type the species label you believe is present, and the app will verify the claim with Gemini. Verified matches are checked against the Databricks biodiversity ledger, can create a species report, award KARMA, and unlock a collectible when the species is genuinely new.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    PhotoPreview(photoPath = uiState.photoPath)

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                if (hasCameraPermission) {
                                    val file = vm.preparePhotoFile()
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    captureLauncher.launch(uri)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Take photo")
                        }

                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) {
                            Icon(Icons.Default.Collections, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Choose image")
                        }
                    }

                    OutlinedTextField(
                        value = uiState.claimedLabel,
                        onValueChange = vm::setClaimedLabel,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Claimed species label") },
                        placeholder = { Text("Example: mule deer") },
                        singleLine = true
                    )

                    uiState.errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }

                    Button(
                        onClick = vm::verifyPhotoClaim,
                        enabled = !uiState.isVerifying && !uiState.photoPath.isNullOrBlank() && uiState.claimedLabel.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF375A2D)),
                        contentPadding = PaddingValues(vertical = 16.dp)
                    ) {
                        if (uiState.isVerifying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null)
                        }
                        Spacer(Modifier.size(10.dp))
                        Text(if (uiState.isVerifying) "Verifying photo..." else "Verify species claim")
                    }
                }
            }

            uiState.result?.let { result ->
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (result.matchedClaim) Icons.Default.CheckCircle else Icons.Default.Pets,
                                contentDescription = null,
                                tint = if (result.matchedClaim) Color(0xFF2E7D32) else Color(0xFFB26A00)
                            )
                            Text(
                                if (result.matchedClaim) "Claim verified" else "Claim not verified",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        VerificationFact(label = "Claimed label", value = result.claimedLabel)
                        VerificationFact(label = "Verifier label", value = result.finalLabel)
                        VerificationFact(label = "Confidence", value = "${(result.confidence * 100).toInt()}% • ${result.confidenceBand}")
                        VerificationFact(
                            label = "Species uniqueness",
                            value = when {
                                !result.matchedClaim -> "Not applicable"
                                !result.uniquenessChecked -> "Databricks lookup unavailable"
                                result.isUniqueSpecies -> "New to the biodiversity ledger"
                                else -> "Already seen before"
                            }
                        )
                        VerificationFact(
                            label = "Reward outcome",
                            value = if (result.rewardAmount > 0) "${result.rewardAmount} KARMA" else "No KARMA awarded"
                        )
                        Text(result.explanation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        if (result.matchedClaim && !result.uniquenessChecked) {
                            Text(
                                "The photo was verified, but the Databricks biodiversity lookup was unavailable, so the app only awarded the standard verified-species reward and did not mint a unique collectible.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB26A00)
                            )
                        }

                        if (uiState.reportCreated) {
                            Text(
                                "A species trail report was also created, so the normal sync worker can settle the broader rewards flow the same way as the rest of the app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF2E7D32)
                            )
                        } else if (result.matchedClaim) {
                            Text(
                                "The image was verified, but no location fix was available, so only the biodiversity ledger was updated.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB26A00)
                            )
                        }

                        if (uiState.usedDatabricksMirror) {
                            Text(
                                "This verified biodiversity event was also mirrored to Databricks using the same direct SQL pattern used elsewhere in the app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF1565C0)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoPreview(photoPath: String?) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = photoPath) {
        value = photoPath?.let { path -> BitmapFactory.decodeFile(path) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(Color(0xFFF2ECE2), RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Selected photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color(0xFF6E6A62))
                Text("No image selected yet", color = Color(0xFF6E6A62))
            }
        }
    }
}

@Composable
private fun VerificationFact(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
