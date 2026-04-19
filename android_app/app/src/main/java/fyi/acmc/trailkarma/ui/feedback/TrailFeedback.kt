package fyi.acmc.trailkarma.ui.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import fyi.acmc.trailkarma.ui.rewards.RewardsPalette
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class FeedbackTone {
    Info,
    Success,
    Error,
}

data class FeedbackMessage(
    val text: String,
    val tone: FeedbackTone = FeedbackTone.Info,
)

object TrailFeedbackBus {
    private val _events = MutableSharedFlow<FeedbackMessage>(extraBufferCapacity = 12)
    val events: SharedFlow<FeedbackMessage> = _events.asSharedFlow()

    fun emit(message: String, tone: FeedbackTone = FeedbackTone.Info) {
        _events.tryEmit(FeedbackMessage(message, tone))
    }
}

enum class OperationStateTone {
    Working,
    Success,
    Error,
}

enum class OperationStepState {
    Pending,
    Active,
    Complete,
    Error,
}

data class OperationStepUi(
    val label: String,
    val detail: String? = null,
    val state: OperationStepState = OperationStepState.Pending,
)

data class OperationUiState(
    val title: String,
    val message: String,
    val tone: OperationStateTone,
    val progress: Float? = null,
    val steps: List<OperationStepUi> = emptyList(),
)

@Composable
fun TrailOperationCard(
    state: OperationUiState,
    modifier: Modifier = Modifier,
) {
    val accent = when (state.tone) {
        OperationStateTone.Working -> RewardsPalette.Sky
        OperationStateTone.Success -> RewardsPalette.Forest
        OperationStateTone.Error -> RewardsPalette.Clay
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .background(accent.copy(alpha = 0.12f), CircleShape)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = stateIcon(state.tone),
                        contentDescription = null,
                        tint = accent,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(state.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.progress?.let {
                LinearProgressIndicator(
                    progress = { it.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = accent,
                    trackColor = accent.copy(alpha = 0.14f),
                )
            }

            if (state.steps.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.steps.forEach { step ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(stepColor(step.state).copy(alpha = 0.12f), CircleShape)
                                    .padding(7.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = stepIcon(step.state),
                                    contentDescription = null,
                                    tint = stepColor(step.state),
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(step.label, style = MaterialTheme.typography.bodyMedium)
                                step.detail?.let { detail ->
                                    Text(
                                        detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun stateIcon(tone: OperationStateTone): ImageVector = when (tone) {
    OperationStateTone.Working -> Icons.Default.Sync
    OperationStateTone.Success -> Icons.Default.CheckCircle
    OperationStateTone.Error -> Icons.Default.ErrorOutline
}

@Composable
private fun stepIcon(state: OperationStepState): ImageVector = when (state) {
    OperationStepState.Pending -> Icons.Default.Sync
    OperationStepState.Active -> Icons.Default.Sync
    OperationStepState.Complete -> Icons.Default.CheckCircle
    OperationStepState.Error -> Icons.Default.ErrorOutline
}

@Composable
private fun stepColor(state: OperationStepState): Color = when (state) {
    OperationStepState.Pending -> MaterialTheme.colorScheme.onSurfaceVariant
    OperationStepState.Active -> RewardsPalette.Sky
    OperationStepState.Complete -> RewardsPalette.Forest
    OperationStepState.Error -> RewardsPalette.Clay
}
