package fyi.acmc.trailkarma.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.repository.UserRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val userRepo = UserRepository(app, db.userDao())

    fun updateDisplayName(displayName: String) {
        viewModelScope.launch {
            val user = userRepo.ensureLocalUser()
            db.userDao().updateDisplayName(user.userId, displayName)
        }
    }

    suspend fun getCurrentDisplayName(): String {
        val user = userRepo.ensureLocalUser()
        return user.displayName
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel()) {
    var displayName by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val name = vm.getCurrentDisplayName()
        displayName = name
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(modifier = Modifier.padding(16.dp)) {
            Text("Hiker Name", modifier = Modifier.padding(bottom = 8.dp))
            Text("This name is broadcast to nearby hikers for identification.", modifier = Modifier.padding(bottom = 12.dp))

            if (!loading) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display name") },
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Button(
                    onClick = {
                        vm.updateDisplayName(displayName)
                        onBack()
                    }
                ) {
                    Text("Save")
                }
            }
        }
    }
}
