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
└── relay_packets (BLE mesh data)
```

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
synced BOOLEAN
created_at TIMESTAMP, updated_at TIMESTAMP
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
-- Active hazards on PCT around current location
SELECT title, description, lat, lng, timestamp
FROM main.trailkarma.trail_reports
WHERE type = 'hazard'
AND ABS(lat - 32.88) < 0.05
AND ABS(lng + 117.24) < 0.05
ORDER BY timestamp DESC;

-- Species sightings heatmap
SELECT lat, lng, species_name, confidence, COUNT(*) as sightings
FROM main.trailkarma.trail_reports
WHERE type = 'species'
GROUP BY lat, lng, species_name, confidence;

-- Real-time hiker positions
SELECT * FROM main.trailkarma.location_updates
ORDER BY timestamp DESC LIMIT 10;
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
