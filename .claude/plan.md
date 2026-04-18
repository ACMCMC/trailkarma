# TrailKarma Android — Aldan's Implementation Plan

**Role:** Lead Android engineer. Offline-first hiker app on the PCT.  
**Stack:** Kotlin, Jetpack Compose, Room, BLE (BluetoothLeScanner/Advertiser), Retrofit  
**Package:** `fyi.acmc.trailkarma`  
**Scaffold status:** Basic XML fragment scaffold — needs full replacement with Compose + MVVM.

---

## Package Structure

```
fyi.acmc.trailkarma/
  models/        — data classes (TrailReport, LocationUpdate, RelayPacket, User)
  db/            — Room DB, DAOs, entities
  ble/           — BLE scan/advertise, packet send/receive
  api/           — Retrofit service, sync requests
  map/           — map screen, overlays, trail polyline
  ui/            — Compose screens + ViewModels
    login/
    home/
    report/
    history/
    ble/
    map/
  repository/    — ReportRepository, LocationRepository, BleRepository
```

---

## Phase 1 — Foundation (do first)

- [ ] Add dependencies to `build.gradle.kts`:
  - Compose BOM + Material3
  - Room (`room-runtime`, `room-ktx`, `room-compiler` via KSP)
  - Retrofit + Moshi/Gson
  - Kotlin Coroutines
  - Lifecycle ViewModel + Compose integration
  - OSMDroid (offline map tiles)
  - Switch plugin from `android.application` to Compose-enabled setup
- [ ] Create `models/` data classes matching DB schema
- [ ] Set up Room DB (`AppDatabase.kt`) with 4 tables:
  - `users` — user_id, display_name
  - `location_updates` — id, timestamp, lat, lng, synced
  - `trail_reports` — report_id, type, title, description, lat, lng, timestamp, species_name?, confidence?, source, synced
  - `relay_packets` — packet_id, payload_json, received_at, sender_device, uploaded
- [ ] Wire `MainActivity` to Compose `NavHost` (replace XML fragments)

---

## Phase 2 — Core UI Screens

- [ ] **LoginScreen** — enter display_name, store in Room `users`, persist to DataStore
- [ ] **HomeScreen** — trail status header, buttons to Report / Map / BLE / History
- [ ] **CreateReportScreen** — type (hazard/water/species), title, description, lat/lng from GPS, species_name if type=species; saves to Room with `synced=false`
- [ ] **ReportHistoryScreen** — list from Room, show synced/unsynced badge
- [ ] **BleScreen** — nearby devices list, send/receive status, packet log

Each screen: ViewModel → Repository → Room DAO. No business logic in Composables.

---

## Phase 3 — Location Logging

- [ ] `LocationRepository` — wraps `FusedLocationProviderClient`
- [ ] Foreground service (`LocationService`) logs location every 30s → Room `location_updates` with `synced=false`
- [ ] Show current coords on HomeScreen

---

## Phase 4 — BLE Relay

- [ ] `BleRepository` — advertise device presence, scan for nearby devices
- [ ] Packet format:
  ```json
  { "packet_id": "uuid", "type": "report|location", "created_at": "iso8601", "ttl": 3, "payload": {} }
  ```
- [ ] On connection: exchange all `relay_packets` where `uploaded=false`
- [ ] Dedup by `packet_id` before inserting; decrement TTL, drop if TTL ≤ 0
- [ ] Permissions: `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT` (API 31+)

---

## Phase 5 — Sync Queue

- [ ] `SyncRepository` — Retrofit calls (base URL via `BuildConfig.API_BASE_URL`)
- [ ] `SyncWorker` (WorkManager, triggers on network available):
  1. Upload `trail_reports` where `synced=false` → `POST /sync/report`
  2. Upload `location_updates` where `synced=false` → `POST /sync/location`
  3. Upload `relay_packets` where `uploaded=false` → `POST /sync/report` (source=relayed)
  4. Mark rows synced on 2xx
- [ ] Show sync status on HomeScreen

---

## Phase 6 — Map UI

- [ ] **MapScreen** — OSMDroid, works offline with cached tiles
- [ ] Show: current position, trail polyline (GeoJSON from Qian or local asset), report markers (hazard=red, water=blue, species=green)
- [ ] Hit `GET /trail/context?lat=&lng=` when online; cache response
- [ ] Markers tappable → show report title/description

---

## Phase 7 — Species + Rewards

- [ ] species_name + confidence fields on CreateReportScreen when type=species
- [ ] Static species JSON fallback if offline (from Qian)
- [ ] After sync: poll reward result, show collectible earned (Trail Scout / Relay Ranger / Species Spotter)

---

## API Contract

```kotlin
interface TrailKarmaApi {
    @POST("/sync/report")   suspend fun syncReport(body: ReportSyncRequest): Response<Unit>
    @POST("/sync/location") suspend fun syncLocation(body: LocationSyncRequest): Response<Unit>
    @GET("/trail/context")  suspend fun getTrailContext(@Query("lat") lat: Double, @Query("lng") lng: Double): TrailContextResponse
}
```

---

## Integration Deliverable (minimum viable demo)

1. Phone A: create offline report → stored in Room
2. Phone B: nearby, receives report over BLE → stored in relay_packets
3. Either phone online → SyncWorker uploads both
4. Backend confirms → synced/uploaded flags set

---

## Priority Order

1. Dependencies + Room DB + models
2. Compose NavHost + Login + Home
3. CreateReport → Room
4. LocationService
5. BLE send/receive
6. SyncWorker
7. MapScreen
8. Species fields + reward display
