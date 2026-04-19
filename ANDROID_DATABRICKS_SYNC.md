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
    url = "https://dbc-f1d1578e-8435.cloud.databricks.com",
    token = "dapi...",  // Your Databricks personal access token
    warehouse = "5fa7bca37483870e"  // Current SQL warehouse ID
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

- **Pushes Trail Reports**: Pushes locally generated reports (type, title, description, lat, lng) to Databricks.
- **Pulls Trail Reports**: Pulls the most recent global reports from Databricks (idempotent deduplication via UUID).
- **Pushes Location Updates**: Pushes GPS pings with ID-based `MERGE INTO` protection.
- **Pushes Relay Packets**: Synchronizes encounter logs and relayed data from the BLE mesh.
- **Status**: Records show an animated orange spinner while pending, and a green badge once synced.
- **Server-Side H3**: The app sends raw coordinates; Databricks computes the `h3_cell` index automatically during ingestion.

### 4. Sync Behavior

- **Automatic**: WorkManager triggers sync when network becomes available.
- **Manual**: Triggered via `DatabricksSyncRepository` in the ViewModel.
- **Retry**: If sync fails, WorkManager will retry on next network change.
- **Offline**: All data remains accessible locally until sync succeeds.
- **GATT Mesh**: Data from nearby hikers is exchanged via a GATT server/client protocol. Devices "diff" manifests to sync missing reports phone-to-phone.

## 📡 BLE Mesh Networking (The "Wild" Sync)
The app uses a persistent BLE foreground service (`BleService`) to synchronize data between hikers in zero-signal areas.

- **Persistent Mesh**: Running as a Foreground Service with a custom notification, it ensures you are always "connected" to the trail community even when the app is in your pocket.
- **Manifest Diffing**: When two hikers meet, their phones exchange "manifests" (lists of report IDs). They then pull only the missing reports from each other.
- **GATT Sync**: Data is streamed over BLE GATT characteristics using a chunked protocol to handle reports larger than the standard 512-byte MTU.
- **Relay Logs**: Every peer encounter is logged as a `RelayPacket` and uploaded to Databricks to visualize the "gossiped" path of a report through the mesh.

## 📱 User Interface Updates

### Full-Screen Report Detail
Tapping any marker on the map or an item in the history list launches the **Full-Screen Report Detail**. This view provides a rich card layout with:
- Color-coded hero banners (Red for Hazards, Teal for Water, Green for Species).
- Species identification confidence badges.
- Explicit sync status (Animated spinner vs. Green "✓ synced" badge).
- "via BLE relay" indicator for data that traveled through the mesh.

### Hiker Nickname (Login)
The initial login screen asks for your **"Hiker Nickname (Trail Name)"**. This is your personal alias in the TrailKarma community (e.g., "Strider").

### Dynamic Trail Selection
You can select your active trail (e.g., PCT) from the Map Screen. The list is pulled live from Databricks, with spatial data optimized via H3 for the current region.


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
│   ├── reports (animated spinner until synced)
│   ├── locations (synced=false until upload)
│   └── relay_packets (BLE mesh encounter logs)
└── Databricks Sync (WorkManager)
    ├── ExecuteSql API (idempotent MERGE INTO)
    ├── PUSH: Reports, Locations, RelayLogs
    └── PULL: Global Reports (Manifest diffing)
        ↓
    Databricks SQL Warehouse (H3 Spatial Cluster)
    └── workspace.trailkarma.trail_reports (H3 computed on ingest)
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
