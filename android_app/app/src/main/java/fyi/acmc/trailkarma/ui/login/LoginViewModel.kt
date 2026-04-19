package fyi.acmc.trailkarma.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fyi.acmc.trailkarma.db.AppDatabase
import fyi.acmc.trailkarma.models.TrustedContact
import fyi.acmc.trailkarma.models.User
import fyi.acmc.trailkarma.repository.UserRepository
import fyi.acmc.trailkarma.wallet.WalletManager
import kotlinx.coroutines.launch
import java.util.UUID

class LoginViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val repo = UserRepository(app, db.userDao())
    private val walletManager = WalletManager(app)

    fun completeProfileSetup(
        displayName: String,
        realName: String,
        phoneNumber: String,
        defaultRelayPhoneNumber: String,
        contacts: List<TrustedContact>,
        onComplete: () -> Unit = {}
    ) = viewModelScope.launch {
        val existing = repo.currentUser()
        val userId = existing?.userId ?: UUID.randomUUID().toString()
        val wallet = walletManager.ensureWallet(userId)
        val user = User(
            userId = userId,
            displayName = displayName,
            realName = realName.ifBlank { null },
            phoneNumber = phoneNumber,
            defaultRelayPhoneNumber = defaultRelayPhoneNumber,
            walletPublicKey = wallet.publicKeyBase58,
            solanaRegistered = existing?.solanaRegistered ?: false,
            lastWalletSyncAt = existing?.lastWalletSyncAt
        )

        repo.saveUser(user)
        db.trustedContactDao().deleteForUser(userId)
        db.trustedContactDao().insertAll(
            contacts.map {
                it.copy(userId = userId)
            }
        )
        onComplete()
    }
}
