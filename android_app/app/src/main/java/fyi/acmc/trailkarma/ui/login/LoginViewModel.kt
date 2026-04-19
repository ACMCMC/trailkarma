package fyi.acmc.trailkarma.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.User
import fyi.acmc.trailkarma.repository.UserRepository
import fyi.acmc.trailkarma.wallet.WalletManager
import kotlinx.coroutines.launch
import java.util.UUID

class LoginViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = UserRepository(app, AppDatabase.get(app).userDao())
    private val walletManager = WalletManager(app)

    fun login(displayName: String, onComplete: () -> Unit = {}) = viewModelScope.launch {
        val userId = UUID.randomUUID().toString()
        val wallet = walletManager.ensureWallet(userId)
        repo.saveUser(
            User(
                userId = userId,
                displayName = displayName,
                walletPublicKey = wallet.publicKeyBase58
            )
        )
        onComplete()
    }
}
