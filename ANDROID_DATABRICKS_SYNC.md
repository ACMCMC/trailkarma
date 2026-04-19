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
    └── workspace.trailkarma.trail_reports/location_updates
```

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
