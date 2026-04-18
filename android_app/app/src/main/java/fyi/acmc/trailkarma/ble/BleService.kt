package fyi.acmc.trailkarma.ble

import android.app.Service
import android.content.Intent
import android.os.IBinder

class BleService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    // BLE GATT server / advertiser wired here in Phase 4
}
