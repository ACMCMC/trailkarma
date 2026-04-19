# TrailKarma Databricks Setup

Reproducible database setup for TrailKarma with mock data for hackathon demos.

## Quick Start

### 1. Get Databricks Credentials

Go to your Databricks workspace:
- Click your username (top-right) → Personal settings
- Click "Access tokens" 
- Generate a new token
- Copy it (keep it secret!)

Also copy your workspace URL from the browser:
- Format: `https://adb-1234567890.cloud.databricks.com`

### 2. Set Environment Variables

```bash
# Option A: Export directly
export DATABRICKS_HOST=https://your-workspace.cloud.databricks.com
export DATABRICKS_TOKEN=dapi...

# Option B: Create .env file (easier for reuse)
cp .env.example .env
# Edit .env and fill in your credentials
```

### 3. Install Dependencies

```bash
pip install -r requirements_databricks.txt
```

### 4. Run Setup

```bash
python setup_databricks.py
```

That's it! Tables are created and populated with mock data automatically.

### Rerunning (Reset Database)

```bash
python setup_databricks.py
```

Safe to run anytime—drops old tables and creates fresh ones.

## What Gets Created

```
main.trailkarma/
├── trails (1 master trail - PCT)
├── trail_waypoints (master POIs)
├── users (4 demo hikers)
├── user_contacts (2 friendships/requests)
├── trail_reports (5 reports: 2 hazards, 1 water, 2 species)
├── location_updates (3 GPS pings)
└── relay_packets (BLE mesh data / encounters)
```

### 📍 H3 Spatial Indexing (Advanced Analytics)
To win the **Cloud/Analytics tracks**, we are leveraging **Uber H3 Indexing**. Instead of slow raw-coordinate queries, we index all trail data and reports into hexagonal cells.
- **Server-Side Computation**: The Android client sends raw lat/lng; Databricks uses the built-in `h3_longlatash3()` function during `MERGE INTO` to compute the cell ID (Resolution 9) server-side.
- **Z-Ordering**: The Delta Lake is clustered using `OPTIMIZE ... ZORDER BY (h3_cell)` for O(1) spatial lookups.
- **Trail Snapping**: The `trail_segments` table allows for instant snapping of hiker positions to the nearest trail index.


### trails (Master Data)
Stores master definitions of trails (like the PCT) to allow hikers to switch context. Includes a `geometry_json` column specifically designed to hold GeoJSON representations. This allows you to import external GIS datasets (Shapefiles, GPX, KMZ converted to GeoJSON) directly into Databricks so the Android client can pull and render the trail path.

### trail_waypoints (Master POIs)
Stores fixed trail metadata like trailheads, permanent water sources, peaks, and established campsites. This keeps static trail data separate from dynamic, user-generated `trail_reports`.

## Schema

### trails (Master Data)
```
trail_id STRING (PK)
name STRING
description STRING
total_length_miles DOUBLE
region STRING
geometry_json STRING (GeoJSON representation of the path)
created_at TIMESTAMP, updated_at TIMESTAMP
```

### trail_waypoints (Master POIs)
```
waypoint_id STRING (PK)
trail_id STRING (FK to trails)
name STRING
type STRING (e.g. trailhead | water | campsite | peak)
lat DOUBLE, lng DOUBLE
description STRING
created_at TIMESTAMP, updated_at TIMESTAMP
```

### users
```
user_id STRING (PK)
display_name STRING
wallet_address STRING
karma_points INT
profile_image_url STRING
active_trail_id STRING (FK to trails)
created_at TIMESTAMP
updated_at TIMESTAMP
```

### user_contacts
```
contact_id STRING (PK)
user_id STRING (FK to users)
contact_user_id STRING (FK to users)
status STRING ('pending' | 'accepted')
created_at TIMESTAMP, updated_at TIMESTAMP
```

### trail_reports
```
report_id STRING (PK)
user_id STRING (FK to users.user_id)
type STRING (hazard | water | species)
title STRING
description STRING
lat DOUBLE, lng DOUBLE (coordinates)
h3_cell STRING (Computed server-side: Res-9)
timestamp STRING (ISO 8601)
image_url STRING (optional)
species_name STRING (optional)
confidence DOUBLE (optional, 0-1)
source STRING (self | relayed)
upvotes INT
synced BOOLEAN
created_at TIMESTAMP, updated_at TIMESTAMP
```

### location_updates
```
id STRING (PK)
user_id STRING (FK to users.user_id)
timestamp STRING
lat DOUBLE, lng DOUBLE
h3_cell STRING (Computed server-side: Res-9)
synced BOOLEAN
created_at TIMESTAMP, updated_at TIMESTAMP
```

### trail_segments (Path Geometry)
```
segment_id STRING (PK)
trail_id STRING (FK to trails)
h3_cell STRING (Index for fast line-snapping)
geometry_json STRING (GeoJSON LineString segment)
```

### relay_packets
```
packet_id STRING (PK)
payload_json STRING (BLE mesh packet data)
received_at STRING
sender_device STRING
uploaded BOOLEAN
created_at TIMESTAMP, updated_at TIMESTAMP
```

## Rerunning (Resets DB)

Both scripts are **idempotent**—safe to run anytime. They:
1. Drop old tables
2. Create fresh schema
3. Repopulate with clean mock data

Great for demo resets between pitches!

## Next Steps

1. **Create SQL Warehouse** (needed for API access):
   - Workspace → SQL Warehouses → Create
   - Use SQL Warehouse ID in your API calls

2. **Create REST Endpoint** (for mobile app sync):
   - Workspace → SQL Warehouses → (your warehouse) → Connection Details
   - Get the HTTP path
   - Set auth token from Personal → Access tokens

3. **Mobile App Integration**:
   - Update API endpoint in your Retrofit client
   - POST trail_reports, location_updates to Databricks SQL endpoint
   - Databricks handles INSERT automatically

## Demo Queries

Run these in Databricks SQL to impress judges:

```sql
-- Active hazards on PCT using H3 optimization
SELECT title, description, lat, lng, timestamp
FROM workspace.trailkarma.trail_reports
WHERE type = 'hazard'
AND h3_cell = h3_longlatash3(-117.24, 32.88, 9)
ORDER BY timestamp DESC;

-- Species sightings heatmap (Hexagonal Aggregation)
SELECT h3_cell, species_name, AVG(confidence) as avg_conf, COUNT(*) as sightings
FROM workspace.trailkarma.trail_reports
WHERE type = 'species'
GROUP BY h3_cell, species_name;

-- Real-time hiker trail snapping
-- Finds hikers currently in the same H3 cell as a known trail segment
SELECT u.display_name, r.h3_cell, t.name as trail_name
FROM workspace.trailkarma.location_updates r
JOIN workspace.trailkarma.users u ON r.user_id = u.user_id
JOIN workspace.trailkarma.trail_segments s ON r.h3_cell = s.h3_cell
JOIN workspace.trailkarma.trails t ON s.trail_id = t.trail_id;
```

## Troubleshooting

**"Permission denied" error:**
- Check your token is valid
- Verify workspace URL format (no trailing slash)

**"Table already exists" error:**
- The notebook version handles this—it drops and recreates
- Run the full notebook again

**"Query timed out":**
- Make sure your SQL warehouse is running
- Start it from Workspace → SQL Warehouses

## Architecture for Cloud Track Demo

```
┌─────────────────────────────────────────────────┐
│ Mobile App (TrailKarma Android)                 │
│ - Offline-first (Room DB)                       │
│ - Syncs unsynced reports/locations to cloud     │
└────────────────────────┬────────────────────────┘
                         │ REST API (HTTP POST)
                         ↓
┌─────────────────────────────────────────────────┐
│ Databricks SQL Warehouse                        │
│ - Ingests trail reports, locations, packets     │
│ - Real-time aggregation (hazard heatmaps, etc)  │
│ - Auto-scaling cluster for high traffic         │
└────────────────────────┬────────────────────────┘
                         │ SQL Queries
                         ↓
┌─────────────────────────────────────────────────┐
│ Demo Dashboard / Analytics                      │
│ - Live hazard map                               │
│ - Species sighting heatmap                      │
│ - Active hiker tracking                         │
└─────────────────────────────────────────────────┘
```

---

**Last updated:** 2026-04-18  
**For:** DataHacks26 Hackathon - Cloud Track
