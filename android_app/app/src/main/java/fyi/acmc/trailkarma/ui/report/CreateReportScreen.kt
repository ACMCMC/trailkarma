package fyi.acmc.trailkarma.ui.report

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fyi.acmc.trailkarma.models.ReportType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateReportScreen(onReportSaved: () -> Unit, vm: CreateReportViewModel = viewModel()) {
    var type by remember { mutableStateOf(ReportType.hazard) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var speciesName by remember { mutableStateOf("") }
    var typeExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Report") },
                navigationIcon = {
                    IconButton(onClick = onReportSaved) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                OutlinedTextField(
                    value = type.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Report Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                    ReportType.entries.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t.name) },
                            onClick = { type = t; typeExpanded = false }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )

            OutlinedTextField(
                value = description, onValueChange = { description = it },
                label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), minLines = 3
            )

            if (type == ReportType.species) {
                OutlinedTextField(
                    value = speciesName, onValueChange = { speciesName = it },
                    label = { Text("Species Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            }

            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        vm.save(type, title, description, speciesName.takeIf { it.isNotBlank() })
                        onReportSaved()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Report Offline")
            }
        }
    }
}
