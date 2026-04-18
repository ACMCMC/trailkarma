package fyi.acmc.trailkarma.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.User
import fyi.acmc.trailkarma.repository.UserRepository
import kotlinx.coroutines.launch
import java.util.UUID

class LoginViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = UserRepository(app, AppDatabase.get(app).userDao())

    fun login(displayName: String) = viewModelScope.launch {
        repo.saveUser(User(userId = UUID.randomUUID().toString(), displayName = displayName))
    }
}
