package fyi.acmc.trailkarma.ble

import android.content.Context
import fyi.acmc.trailkarma.db.AppDatabase

object BleRepositoryHolder {
    private var instance: BleRepository? = null

    fun getInstance(context: Context): BleRepository {
        if (instance == null) {
            synchronized(this) {
                if (instance == null) {
                    val db = AppDatabase.get(context)
                    instance = BleRepository(
                        context = context,
                        relayPacketDao = db.relayPacketDao(),
                        trailReportDao = db.trailReportDao(),
                        relayJobIntentDao = db.relayJobIntentDao(),
                        relayInboxMessageDao = db.relayInboxMessageDao()
                    )
                }
            }
        }
        return instance!!
    }
}
