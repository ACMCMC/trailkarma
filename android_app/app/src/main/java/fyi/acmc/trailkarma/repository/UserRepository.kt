package fyi.acmc.trailkarma.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fyi.acmc.trailkarma.db.UserDao
import fyi.acmc.trailkarma.models.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("user_prefs")
private val KEY_USER_ID = stringPreferencesKey("user_id")

class UserRepository(private val context: Context, private val dao: UserDao) {
    val currentUserId: Flow<String?> = context.dataStore.data.map { it[KEY_USER_ID] }

    suspend fun saveUser(user: User) {
        dao.insert(user)
        context.dataStore.edit { it[KEY_USER_ID] = user.userId }
    }
}
