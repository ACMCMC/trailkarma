# Android App - Databricks Sync Setup

The TrailKarma Android app now syncs trail reports and location data to Databricks when online, with offline-first caching.

## How It Works

1. **Offline Mode**: Data is stored locally in Room database.
2. **Online Mode**: When network is available, the app PUSHES unsynced offline data to Databricks and PULLS the latest community data down to the device.
3. **Cache**: The most recent synced data remains available offline.

## Setup Instructions

### 1. Configure Databricks in Your App

Add this to `MainActivity.kt` after checking user login:

```kotlin
import fyi.acmc.trailkarma.repository.DatabricksSyncRepository
import fyi.acmc.trailkarma.db.AppDatabase

// After login is confirmed
val db = AppDatabase.get(this)
val syncRepo = DatabricksSyncRepository(this, db)
syncRepo.setDatabricksConfig(
    url = "https://your-workspace.cloud.databricks.com",
    token = "dapi...",  // Your Databricks personal access token
    warehouse = "warehouse-id"  // Your SQL warehouse ID
)

// Initial sync on app startup
if (syncRepo.isOnline()) {
    syncRepo.syncReports() // Push offline data
    syncRepo.pullReportsFromCloud() // Pull community updates
}
```

### 2. Get Your Databricks Credentials

From your Databricks workspace:

- **URL**: Your workspace URL (e.g., `https://adb-1234567890.cloud.databricks.com`)
- **Token**: 
  - Click your username → Personal settings
  - Click "Access tokens" → Generate new token
  - Copy the token (keep it secret!)
- **Warehouse ID**:
  - Click "SQL" → "Warehouses"
  - Click your warehouse → Copy the ID from the URL bar

### 3. What Gets Synced

When online, the app automatically syncs in both directions:

- **Pushes Trail Reports**: Pushes locally generated reports (type, title, image_url, etc) to Databricks.
- **Pulls Trail Reports**: Pulls the most recent global reports from Databricks so the user can see hazards/water updates from others.
- **Pushes Location Updates**: Pushes GPS pings.
- **Status**: Each record is marked `synced=true` after successful upload

### 4. Sync Behavior

- **Automatic**: WorkManager triggers sync when network becomes available
- **Manual**: Call `syncRepo.syncReports()` and `syncRepo.syncLocations()` from your ViewModel
- **Retry**: If sync fails, WorkManager will retry on next network change
- **Offline**: All data remains accessible locally until sync succeeds
- **BLE Relay**: Encounter data from nearby hikers is stored as `RelayPacket` and synced to the cloud, allowing reports to propagate even from zero-signal areas.

## 📱 User Interface Updates

### Hiker Nickname (Login)
The initial login screen asks for your **"Hiker Nickname (Trail Name)"**. This is your personal alias in the TrailKarma community (e.g., "Strider"). Once logged in, this name is attached to all your reports and sightings.

### Dynamic Trail Selection
After login, you can select which physical trail you are currently hiking (e.g., PCT) directly from the Map Screen. This list is pulled live from Databricks, ensuring you always have the latest trail data and community reports for your specific route.

## 📡 BLE Contact Tracing & Relay
The app uses Bluetooth Low Energy (BLE) to detect other hikers in the vicinity, even in total "dead zones."

- **Broadcasting**: Your phone constantly broadcasts a "TrailKarma Beacon" containing your hiker ID.
- **Scanning**: Your phone scans for nearby beacons. When a contact is made, it logs an "Encounter" with signal strength (RSSI).
- **Data Mesh**: If you encounter a hiker who has "relayed" data, your phone will automatically store it and attempt to upload it to Databricks as soon as you hit a pocket of LTE/5G.


## Code Examples

### In CreateReportViewModel
```kotlin
val syncRepo = DatabricksSyncRepository(context, db)
if (syncRepo.isOnline()) {
    // Can show "syncing..." indicator
    syncRepo.syncReports()
}
```

### In MapViewModel
```kotlin
// Show synced status in UI
reports.collectAsState().value.forEach { report ->
    if (report.synced) {
        // Show green checkmark
    } else {
        // Show orange sync pending indicator
    }
}
```

## Testing

1. Create a report while **offline** (airplane mode)
   - Report saves to local database
   - Shows orange "pending sync" badge

2. Go **online**
   - WorkManager syncs automatically (may take a few seconds)
   - Report shows green "synced" checkmark

3. Verify in Databricks:
   ```sql
   SELECT * FROM workspace.trailkarma.trail_reports WHERE synced = true;
   ```

## Architecture Diagram

```
Mobile App (offline-first)
├── Local Room DB
│   ├── reports (synced=false until upload)
│   └── locations (synced=false until upload)
└── Databricks Sync (when online)
    ├── ExecuteSql API
    ├── INSERT trail_reports / location_updates (Push)
    └── SELECT from trail_reports (Pull)
        ↓
    Databricks SQL Warehouse
    └── workspace.trailkarma.trail_reports/location_updates/relay_packets
```

## 🛠 Android 15 & 16KB Alignment
The app is fully optimized for **Android 15**. We have upgraded to **CameraX 1.4.1** and **NDK r27** to ensure that all native libraries are 16KB page-aligned, preventing "ELF alignment" warnings on modern hardware.


## Troubleshooting

**Sync Not Working?**
- Check: Is the warehouse running? (green checkmark in Databricks)
- Check: Is the token valid? (Settings → Access tokens)
- Check: Is network connectivity available?

**Data Not in Databricks?**
- Check Reports: `SELECT COUNT(*) FROM workspace.trailkarma.trail_reports;`
- Check Logs: Look for network errors in Android Studio logcat
- Check Status: Is `synced` column true or false?

## Next Steps

- Implement settings UI to let users enter Databricks credentials
- Add sync status notifications
- Implement selective sync (sync only hazards, etc.)
- Add conflict resolution if same report uploaded from multiple devices
