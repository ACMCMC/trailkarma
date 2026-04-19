package fyi.acmc.trailkarma.ui.history

import android.app.Application
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.TrailReport
import fyi.acmc.trailkarma.repository.ReportRepository

class ReportHistoryViewModel(app: Application) : AndroidViewModel(app) {
    val reports = ReportRepository(AppDatabase.get(app).trailReportDao()).allReports
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportHistoryScreen(vm: ReportHistoryViewModel = viewModel()) {
    val reports by vm.reports.collectAsState(initial = emptyList())

    Scaffold(topBar = { TopAppBar(title = { Text("My Reports") }) }) { padding ->
        if (reports.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No reports yet")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                items(reports) { report -> ReportItem(report) }
            }
        }
    }
}

@Composable
private fun ReportItem(report: TrailReport) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(report.title, style = MaterialTheme.typography.titleSmall)
                Text("${report.type.name} • ${report.timestamp.take(10)}", style = MaterialTheme.typography.bodySmall)
            }
            if (report.synced) {
                Badge(containerColor = Color(0xFF4CAF50)) {
                    Text("synced", fontSize = 9.sp)
                }
            } else {
                // Pending cloud upload — show animated spinner
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFFFF9800)
                )
            }
        }
    }
}
