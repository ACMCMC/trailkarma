#!/usr/bin/env python3
"""TrailKarma Databricks Setup - Wipes and Recreates Database Structure"""

import os
import uuid
import json
import math
import csv
import requests
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta, UTC

try:
    import h3
except ImportError:
    print("❌ ERROR: 'h3' library is not installed.")
    print("   H3 spatial indexing is required for this script.")
    print("   Fix: pip install h3")
    import sys; sys.exit(1)

def latlng_to_h3(lat, lng, resolution=9):
    """Convert lat/lng to H3 cell string at given resolution."""
    return h3.latlng_to_cell(lat, lng, resolution)

def sql_str(val):
    """Wrap a value in SQL quotes or return NULL."""
    return f"'{val}'" if val is not None else "NULL"


def iso_z(dt):
    """Serialize datetimes as RFC3339 with Z suffix."""
    return dt.isoformat().replace("+00:00", "Z")

PCT_GEOJSON = os.path.join(os.path.dirname(__file__), "data", "Southern_California.geojson")
LIBRARY_WALK_GPX = os.path.join(os.path.dirname(__file__), "data", "library_walk.gpx")
SPECIES_CSV = os.path.join(os.path.dirname(__file__), "data", "observations-712152.csv")
WATER_CSV = os.path.join(os.path.dirname(__file__), "data", "water_reports.csv")

SOCAL_LAT_MIN, SOCAL_LAT_MAX = 32.0, 35.0
SOCAL_LNG_MIN, SOCAL_LNG_MAX = -118.0, -116.0


def _haversine_miles(coords):
    """Approximate total length of a LineString in miles."""
    total = 0.0
    for i in range(len(coords) - 1):
        lng1, lat1 = coords[i][0], coords[i][1]
        lng2, lat2 = coords[i+1][0], coords[i+1][1]
        dlat = math.radians(lat2 - lat1)
        dlng = math.radians(lng2 - lng1)
        a = math.sin(dlat/2)**2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlng/2)**2
        total += 3958.8 * 2 * math.asin(math.sqrt(a))
    return round(total, 2)


def _simplify_coords(coords, tolerance=0.005):
    """Reduce coordinate count to keep geometry size manageable."""
    if len(coords) <= 2:
        return coords
    result = [coords[0]]
    for pt in coords[1:-1]:
        if abs(pt[0] - result[-1][0]) >= tolerance or abs(pt[1] - result[-1][1]) >= tolerance:
            result.append(pt)
    result.append(coords[-1])
    return result


def load_pct_trail_statements(full_schema, now):
    """Read Southern California GeoJSON and return (trail_inserts, first_trail_id, section_centroids). 
    Uses H3 cells for spatial indexing.

    section_centroids: list of (trail_id, section_name, centroid_lat, centroid_lng)
    """
    statements = []
    first_trail_id = None
    section_centroids = []

    try:
        with open(PCT_GEOJSON, encoding="utf-8") as f:
            data = json.load(f)
    except FileNotFoundError:
        print(f"  WARNING: PCT GeoJSON not found at {PCT_GEOJSON}, using mock trail")
        return [], None, []

    sections = {}
    for feat in data.get("features", []):
        props = feat.get("properties", {})
        section_name = props.get("Section_Name", "Unknown")
        region = props.get("Region", "Southern California")
        coords = feat.get("geometry", {}).get("coordinates", [])
        if not coords:
            continue
        if section_name not in sections:
            sections[section_name] = {"region": region, "coords": []}
        sections[section_name]["coords"].extend(coords)

    for section_name, data in sorted(sections.items()):
        trail_id = str(uuid.uuid4())
        if first_trail_id is None:
            first_trail_id = trail_id
        coords = _simplify_coords(data["coords"])
        length_miles = _haversine_miles(coords)
        geom = json.dumps({"type": "LineString", "coordinates": coords}).replace("'", "\\'")
        desc = f"PCT {section_name} through {data['region']}".replace("'", "\\'")

        # Compute H3 cells for all trail coordinates (res-9 ~174m hexagons)
        h3_cells = [latlng_to_h3(lat, lng) for lng, lat in coords]
        h3_cells_json = json.dumps(list(set(h3_cells)))  # deduplicate and store as JSON

        statements.append(
            f"INSERT INTO {full_schema}.trails VALUES ("
            f"'{trail_id}', '{section_name}', '{desc}', "
            f"{length_miles}, '{data['region']}', '{geom}', '{h3_cells_json}', "
            f"current_timestamp(), current_timestamp())"
        )
        mid = coords[len(coords) // 2]
        section_centroids.append((trail_id, section_name, round(mid[1], 6), round(mid[0], 6)))

    print(f"  ✓ Loaded {len(sections)} PCT sections from GeoJSON with H3 spatial indexing")
    return statements, first_trail_id, section_centroids

def load_gpx_trail_statements(full_schema, gpx_path, trail_name, region, description=None):
    """Read GPX file and return INSERT statements for the trails table."""
    statements = []
    trail_id = str(uuid.uuid4())
    
    try:
        tree = ET.parse(gpx_path)
        root = tree.getroot()
        # GPX namespace
        ns = {'gpx': 'http://www.topografix.com/GPX/1/1'}
        
        coords = []
        for trkpt in root.findall('.//gpx:trkpt', ns):
            lat = float(trkpt.get('lat'))
            lon = float(trkpt.get('lon'))
            coords.append([lon, lat])
            
        if not coords:
            print(f"  ⚠️  No coordinates found in GPX {gpx_path}")
            return []

        coords = _simplify_coords(coords, tolerance=0.0001)
        length_miles = _haversine_miles(coords)
        geom = json.dumps({"type": "LineString", "coordinates": coords}).replace("'", "\\'")
        desc = (description or f"Trail recording: {trail_name}").replace("'", "\\'")

        # Compute H3 cells for all trail coordinates
        h3_cells = [latlng_to_h3(lat, lng) for lng, lat in coords]
        h3_cells_json = json.dumps(list(set(h3_cells)))

        statements.append(
            f"INSERT INTO {full_schema}.trails VALUES ("
            f"'{trail_id}', '{trail_name}', '{desc}', "
            f"{length_miles}, '{region}', '{geom}', '{h3_cells_json}', "
            f"current_timestamp(), current_timestamp())"
        )
        print(f"  ✓ Loaded GPX trail {trail_name} from {os.path.basename(gpx_path)} with H3 spatial indexing")
        return statements
    except Exception as e:
        print(f"  ⚠️  Failed to load GPX {gpx_path}: {e}")
        return []

def load_species_report_statements(full_schema):
    """Read iNaturalist CSV, filter to Southern California, return INSERT statements with H3 cells."""
    statements = []
    try:
        with open(SPECIES_CSV, encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                try:
                    lat = float(row["latitude"])
                    lng = float(row["longitude"])
                except (ValueError, KeyError):
                    continue
                if not (SOCAL_LAT_MIN <= lat <= SOCAL_LAT_MAX and SOCAL_LNG_MIN <= lng <= SOCAL_LNG_MAX):
                    continue

                report_id = str(uuid.uuid4())
                title = (row.get("common_name") or row.get("species_guess") or "Unknown Species").replace("'", "\\'")
                desc = (row.get("description") or row.get("place_guess") or "").replace("'", "\\'")
                ts = row.get("time_observed_at") or row.get("observed_on") or iso_z(datetime.now(UTC))
                image_url = (row.get("image_url") or "").replace("'", "\\'")
                species_name = (row.get("scientific_name") or "").replace("'", "\\'")
                h3_cell = latlng_to_h3(lat, lng)

                user_id = row.get("user_id", "unknown")
                statements.append(
                    f"INSERT INTO {full_schema}.trail_reports VALUES ("
                    f"'{report_id}', '{user_id}', 'species', "
                    f"'{title}', '{desc}', {lat}, {lng}, {sql_str(h3_cell)}, '{ts}', "
                    f"{sql_str(image_url)}, {sql_str(species_name)}, NULL, 'self', 0, true, "
                    f"current_timestamp(), current_timestamp())"
                )
    except FileNotFoundError:
        print(f"  ⚠️  Species CSV not found at {SPECIES_CSV}, skipping")

    print(f"  ✓ Loaded {len(statements)} Southern California species reports with H3 spatial indexing")
    return statements


def load_water_report_statements(full_schema):
    """Read water_reports.csv and return INSERT statements for trail_reports."""
    statements = []
    try:
        with open(WATER_CSV, encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                try:
                    lat = float(row["lat"])
                    lng = float(row["lng"])
                except (ValueError, KeyError):
                    continue

                report_id = str(uuid.uuid4())
                title = row.get("title", "").replace("'", "\\'")
                desc = row.get("description", "").replace("'", "\\'")
                created_at = row.get("created_at") or None
                updated_at = row.get("updated_at") or None

                created_val = f"CAST('{created_at}' AS TIMESTAMP)" if created_at else "current_timestamp()"
                updated_val = f"CAST('{updated_at}' AS TIMESTAMP)" if updated_at else "current_timestamp()"

                statements.append(
                    f"INSERT INTO {full_schema}.trail_reports VALUES ("
                    f"'{report_id}', 'water-system', 'water', "
                    f"'{title}', '{desc}', {lat}, {lng}, "
                    f"'{updated_at or datetime.utcnow().isoformat()}Z', "
                    f"NULL, NULL, NULL, 'self', 0, true, "
                    f"{created_val}, {updated_val})"
                )
    except FileNotFoundError:
        print(f"  ⚠️  Water CSV not found at {WATER_CSV}, skipping")

    print(f"  ✓ Loaded {len(statements)} water source reports")
    return statements



def load_weather_cache_statements(full_schema, section_centroids):
    """Seed weather_cache with one row per PCT section using pre-computed centroids.

    section_centroids: list of (trail_id, section_name, centroid_lat, centroid_lng)
    trail_id matches the trails table — weather columns are NULL until first Job run.
    """
    statements = []
    for trail_id, name, lat, lng in section_centroids:
        statements.append(
            f"INSERT INTO {full_schema}.weather_cache VALUES ("
            f"'{trail_id}', '{name}', {lat}, {lng}, "
            f"NULL, NULL, NULL, NULL, NULL, current_timestamp())"
        )
    print(f"  Seeded {len(statements)} weather_cache rows (weather pending first Job run)")
    return statements

def load_env():
    config = {}
    if os.path.exists('.env'):
        with open('.env') as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith('#'):
                    if '=' in line:
                        key, value = line.split('=', 1)
                        config[key.strip()] = value.strip().strip('"\'')
    return config

def main():
    config = load_env()
    workspace_url = config.get('DATABRICKS_HOST', '').rstrip('/')
    token = config.get('DATABRICKS_TOKEN', '')

    if not workspace_url or not token:
        print("❌ Missing .env credentials!")
        return False

    print(f"\n🔧 TrailKarma Databricks Setup")
    print(f"🌐 Workspace: {workspace_url}\n")

    catalog = "workspace"
    schema = "trailkarma"
    full_schema = f"{catalog}.{schema}"

    now = datetime.now(UTC)

    # The 4 demo hikers (id, name, wallet, karma, active_trail_id)
    pct_trail_id = str(uuid.uuid4())

    users = [
        (str(uuid.uuid4()), 'Aldan', '8Bse...1a', 150, pct_trail_id),
        (str(uuid.uuid4()), 'Qianqian', 'C9fe...2b', 320, pct_trail_id),
        (str(uuid.uuid4()), 'Suraj', 'A1bc...3c', 50, pct_trail_id),
        (str(uuid.uuid4()), 'Edith', 'D4de...4d', 420, pct_trail_id),
    ]

    base_lat, base_lng = 32.88, -117.24

    # Pre-compute H3 cells for seed data (resolution 9 ~= 174m hexagons)
    base_h3 = latlng_to_h3(base_lat, base_lng)

    # Reports — now include h3_cell
    reports = [
        ('mock-1', 'hazard', 'Rockslide ahead',    'Section near mile 24 has debris', base_lat,         base_lng,         'self',    None,              None,  latlng_to_h3(base_lat,         base_lng)),
        ('mock-2', 'hazard', 'Rattlesnake spotted','Stay alert near water',           base_lat - 0.01,  base_lng - 0.01,  'relayed', None,              None,  latlng_to_h3(base_lat - 0.01,  base_lng - 0.01)),
        ('mock-3', 'water',  'Water source confirmed','Spring flowing, fresh water',  base_lat + 0.01,  base_lng - 0.01,  'self',    None,              None,  latlng_to_h3(base_lat + 0.01,  base_lng - 0.01)),
        ('mock-4', 'species','Mule deer herd',     '6-8 deer at sunrise',             base_lat + 0.005, base_lng + 0.005, 'self',    'Mule Deer',       0.92,  latlng_to_h3(base_lat + 0.005, base_lng + 0.005)),
        ('mock-5', 'species','California quail',   'Small covey',                     base_lat - 0.005, base_lng + 0.005, 'relayed', 'California Quail',0.87,  latlng_to_h3(base_lat - 0.005, base_lng + 0.005)),
    ]

    # Locations — now include h3_cell
    locations = [
        (str(uuid.uuid4()), iso_z(now), base_lat, base_lng, latlng_to_h3(base_lat, base_lng)),
        (str(uuid.uuid4()), iso_z(now - timedelta(minutes=30)), base_lat + 0.002, base_lng - 0.002, latlng_to_h3(base_lat + 0.002, base_lng - 0.002)),
        (str(uuid.uuid4()), iso_z(now - timedelta(minutes=60)), base_lat - 0.002, base_lng + 0.002, latlng_to_h3(base_lat - 0.002, base_lng + 0.002)),
    ]

    # Relay Packets
    relay_packets = [
        (str(uuid.uuid4()), '{"type":"report","report_id":"mock-2"}', iso_z(now - timedelta(hours=2)), 'device-123'),
    ]

    sql_statements = [
        f"CREATE SCHEMA IF NOT EXISTS {schema}",

        # WIPE OUT EXISTING TABLES (order matters for FK constraints)
        f"DROP TABLE IF EXISTS {full_schema}.user_contacts",
        f"DROP TABLE IF EXISTS {full_schema}.location_updates",
        f"DROP TABLE IF EXISTS {full_schema}.relay_packets",
        f"DROP TABLE IF EXISTS {full_schema}.trail_reports",
        f"DROP TABLE IF EXISTS {full_schema}.trail_segments",
        f"DROP TABLE IF EXISTS {full_schema}.weather_cache",
        f"DROP TABLE IF EXISTS {full_schema}.trail_waypoints",
        f"DROP TABLE IF EXISTS {full_schema}.users",
        f"DROP TABLE IF EXISTS {full_schema}.trails",
        
        # RECREATE FULL STRUCTURE
        f"""CREATE TABLE {full_schema}.trails (
            trail_id          STRING    NOT NULL PRIMARY KEY,
            name              STRING    NOT NULL,
            description       STRING,
            total_length_miles DOUBLE,
            region            STRING,
            geometry_json     STRING,
            -- H3: JSON array of all res-9 cells that cover this trail
            -- populated by Qianqian's PCT script via h3_longlatash3
            h3_cells          STRING,
            created_at        TIMESTAMP,
            updated_at        TIMESTAMP
        ) USING DELTA""",

        # trail_segments: one row per H3 cell covering the trail line.
        # Enables O(1) "is this hiker on this trail?" via h3_cell equality.
        f"""CREATE TABLE {full_schema}.trail_segments (
            segment_id  STRING NOT NULL PRIMARY KEY,
            trail_id    STRING NOT NULL,
            h3_cell     STRING NOT NULL,  -- res-9 H3 cell (~174m hexagon)
            sequence    INT,              -- vertex order along trail
            lat         DOUBLE,           -- centroid of vertex (for display)
            lng         DOUBLE,
            created_at  TIMESTAMP
        ) USING DELTA""",

        f"""CREATE TABLE {full_schema}.weather_cache (
            trail_id STRING NOT NULL PRIMARY KEY,
            trail_name STRING NOT NULL,
            centroid_lat DOUBLE NOT NULL,
            centroid_lng DOUBLE NOT NULL,
            temperature_c DOUBLE,
            precipitation_mm DOUBLE,
            windspeed_ms DOUBLE,
            weathercode INT,
            fetched_at TIMESTAMP,
            updated_at TIMESTAMP
        ) USING DELTA""",

        f"""CREATE TABLE {full_schema}.trail_waypoints (
            waypoint_id STRING    NOT NULL PRIMARY KEY,
            trail_id    STRING    NOT NULL,
            name        STRING    NOT NULL,
            type        STRING    NOT NULL,
            lat         DOUBLE    NOT NULL,
            lng         DOUBLE    NOT NULL,
            h3_cell     STRING,           -- res-9 cell for fast spatial lookup
            description STRING,
            created_at  TIMESTAMP,
            updated_at  TIMESTAMP,
            CONSTRAINT fk_waypoint_trail FOREIGN KEY (trail_id) REFERENCES {full_schema}.trails(trail_id)
        ) USING DELTA""",

        f"""CREATE TABLE {full_schema}.users (
            user_id           STRING    NOT NULL PRIMARY KEY,
            display_name      STRING    NOT NULL,
            wallet_address    STRING,
            karma_points      INT,
            profile_image_url STRING,
            active_trail_id   STRING,
            created_at        TIMESTAMP,
            updated_at        TIMESTAMP,
            CONSTRAINT fk_users_trail FOREIGN KEY (active_trail_id) REFERENCES {full_schema}.trails(trail_id)
        ) USING DELTA""",

        f"""CREATE TABLE {full_schema}.trail_reports (
            report_id    STRING    NOT NULL PRIMARY KEY,
            user_id      STRING    NOT NULL,
            type         STRING    NOT NULL,
            title        STRING    NOT NULL,
            description  STRING,
            lat          DOUBLE    NOT NULL,
            lng          DOUBLE    NOT NULL,
            h3_cell      STRING,           -- res-9 H3 cell: h3_longlatash3(lng, lat, 9)
            timestamp    STRING    NOT NULL,
            image_url    STRING,
            species_name STRING,
            confidence   DOUBLE,
            source       STRING    NOT NULL,
            upvotes      INT,
            synced       BOOLEAN,
            created_at   TIMESTAMP,
            updated_at   TIMESTAMP,
            CONSTRAINT fk_reports_user FOREIGN KEY (user_id) REFERENCES {full_schema}.users(user_id)
        ) USING DELTA""",

        f"""CREATE TABLE {full_schema}.location_updates (
            id        STRING    NOT NULL PRIMARY KEY,
            user_id   STRING    NOT NULL,
            timestamp STRING    NOT NULL,
            lat       DOUBLE    NOT NULL,
            lng       DOUBLE    NOT NULL,
            h3_cell   STRING,             -- res-9 H3 cell: h3_longlatash3(lng, lat, 9)
            synced    BOOLEAN,
            created_at TIMESTAMP,
            updated_at TIMESTAMP,
            CONSTRAINT fk_locations_user FOREIGN KEY (user_id) REFERENCES {full_schema}.users(user_id)
        ) USING DELTA""",

        f"""CREATE TABLE {full_schema}.user_contacts (
            contact_id      STRING NOT NULL PRIMARY KEY,
            user_id         STRING NOT NULL,
            contact_user_id STRING NOT NULL,
            status          STRING NOT NULL,
            created_at      TIMESTAMP,
            updated_at      TIMESTAMP,
            CONSTRAINT fk_contacts_user1 FOREIGN KEY (user_id)         REFERENCES {full_schema}.users(user_id),
            CONSTRAINT fk_contacts_user2 FOREIGN KEY (contact_user_id) REFERENCES {full_schema}.users(user_id)
        ) USING DELTA""",

        f"""CREATE TABLE {full_schema}.relay_packets (
            packet_id    STRING NOT NULL PRIMARY KEY,
            payload_json STRING NOT NULL,
            received_at  STRING NOT NULL,
            sender_device STRING,
            uploaded     BOOLEAN,
            created_at   TIMESTAMP,
            updated_at   TIMESTAMP
        ) USING DELTA""",

        # --- H3 SPATIAL OPTIMIZATION ---
        # ZORDER physically clusters Delta files by h3_cell so spatial queries
        # ("find all reports in this hex") skip irrelevant files entirely.
        f"OPTIMIZE {full_schema}.trail_reports   ZORDER BY (h3_cell)",
        f"OPTIMIZE {full_schema}.location_updates ZORDER BY (h3_cell)",
        f"OPTIMIZE {full_schema}.trail_segments   ZORDER BY (h3_cell)",
    ]

    # PUT IN ALL THE DATA SO WE START FRESH
    # Trails — load real PCT Southern California sections with H3 spatial indexing
    trail_statements, first_trail_id, section_centroids = load_pct_trail_statements(full_schema, now)
    if trail_statements:
        sql_statements.extend(trail_statements)
        pct_trail_id = first_trail_id  # use real trail id for FK references
    else:
        # fallback mock if GeoJSON not found
        mock_geojson = '{"type": "LineString", "coordinates": [[-117.24, 32.88], [-117.23, 32.89]]}'
        mock_h3 = latlng_to_h3(32.88, -117.24)
        sql_statements.append(f"INSERT INTO {full_schema}.trails VALUES ('{pct_trail_id}', 'Pacific Crest Trail - Mock', 'Mock trail data', 2650.0, 'Southern California', '{mock_geojson}', '{json.dumps([mock_h3])}', current_timestamp(), current_timestamp())")

    # Library Walk GPX
    library_walk_statements = load_gpx_trail_statements(
        full_schema, LIBRARY_WALK_GPX, "Library Walk", "UCSD", 
        description="A walk around UCSD through the heart of campus."
    )
    if library_walk_statements:
        sql_statements.extend(library_walk_statements)

    sql_statements.append(f"INSERT INTO {full_schema}.trail_waypoints VALUES ('{str(uuid.uuid4())}', '{pct_trail_id}', 'Southern Terminus', 'trailhead', 32.5896, -116.4669, {sql_str(latlng_to_h3(32.5896, -116.4669))}, 'The official start of the PCT at the US-Mexico border.', current_timestamp(), current_timestamp())")

    for user_id, name, wallet, karma, trail_id in users:
        sql_statements.append(f"INSERT INTO {full_schema}.users VALUES ('{user_id}', '{name}', '{wallet}', {karma}, NULL, '{trail_id}', current_timestamp(), current_timestamp())")

    for i, (rid, rtype, title, desc, lat, lng, source, species, conf, h3_cell) in enumerate(reports):
        user_id = users[i % len(users)][0]
        ts = iso_z(now - timedelta(hours=i * 2))
        sql_statements.append(
            f"INSERT INTO {full_schema}.trail_reports VALUES "
            f"('{rid}', '{user_id}', '{rtype}', '{title}', '{desc}', "
            f"{lat}, {lng}, {sql_str(h3_cell)}, '{ts}', NULL, "
            f"{sql_str(species)}, {conf if conf is not None else 'NULL'}, "
            f"'{source}', 0, true, current_timestamp(), current_timestamp())"
        )

    for i, (loc_id, ts, lat, lng, h3_cell) in enumerate(locations):
        user_id = users[i % len(users)][0]
        sql_statements.append(
            f"INSERT INTO {full_schema}.location_updates VALUES "
            f"('{loc_id}', '{user_id}', '{ts}', {lat}, {lng}, {sql_str(h3_cell)}, true, current_timestamp(), current_timestamp())"
        )
    for packet_id, payload, ts, device in relay_packets:
        sql_statements.append(f"INSERT INTO {full_schema}.relay_packets VALUES ('{packet_id}', '{payload}', '{ts}', '{device}', true, current_timestamp(), current_timestamp())")

    # Species reports from iNaturalist (Southern California only)
    sql_statements.extend(load_species_report_statements(full_schema))

    # Water source reports from PCT Water Report
    sql_statements.extend(load_water_report_statements(full_schema))

    # Weather cache — one row per PCT section, weather filled by scheduled Job
    sql_statements.extend(load_weather_cache_statements(full_schema, section_centroids))
    # Contacts
    # Aldan and Qianqian are contacts
    sql_statements.append(f"INSERT INTO {full_schema}.user_contacts VALUES ('{str(uuid.uuid4())}', '{users[0][0]}', '{users[1][0]}', 'accepted', current_timestamp(), current_timestamp())")
    # Suraj requested Edith
    sql_statements.append(f"INSERT INTO {full_schema}.user_contacts VALUES ('{str(uuid.uuid4())}', '{users[2][0]}', '{users[3][0]}', 'pending', current_timestamp(), current_timestamp())")

    # Get warehouse
    print("🔍 Finding SQL warehouse...")
    headers = {'Authorization': f'Bearer {token}'}
    try:
        r = requests.get(f"{workspace_url}/api/2.0/sql/warehouses", headers=headers, timeout=10)
        warehouses = r.json().get('warehouses', [])
    except Exception as e:
        print(f"  ❌ Failed to connect to Databricks API: {e}")
        return False

    warehouse_id = config.get("DATABRICKS_WAREHOUSE") or None
    if warehouse_id:
        print(f"  ✓ Using configured warehouse: {warehouse_id}\n")
    else:
        for wh in warehouses:
            if wh.get('state') == 'RUNNING':
                warehouse_id = wh.get('id')
                print(f"  ✓ Using: {wh.get('name')}\n")
                break

    if not warehouse_id:
        print("  ❌ No running warehouses! Please start a warehouse in Databricks and rerun.")
        return False

    print(f"🗄️  Executing {len(sql_statements)} SQL statements to wipe, recreate, and populate DB...\n")
    headers['Content-Type'] = 'application/json'
    success = 0

    for i, sql in enumerate(sql_statements, 1):
        label = sql[:55].strip().replace('\n', ' ') if len(sql) > 55 else sql.replace('\n', ' ')
        print(f"  [{i:2d}/{len(sql_statements)}] {label}...", end=" ", flush=True)

        try:
            payload = {'warehouse_id': warehouse_id, 'statement': sql, 'wait_timeout': '30s'}
            r = requests.post(f"{workspace_url}/api/2.0/sql/statements", json=payload, headers=headers, timeout=60)
            
            if r.status_code in [200, 201]:
                result = r.json()
                state = result.get('status', {}).get('state')
                if state == 'SUCCEEDED':
                    print("✅")
                    success += 1
                elif state == 'FAILED':
                    error_msg = result.get('status', {}).get('error', {}).get('message', 'Unknown Error')
                    print(f"❌ ({error_msg})")
                else:
                    print("⏳ (Pending)")
                    success += 1
            else:
                print(f"❌ (HTTP {r.status_code})")
        except Exception as e:
            print(f"❌ ({e})")

    print(f"\n✅ Setup complete! {success}/{len(sql_statements)} succeeded")
    print(f"\n🎉 Query your data:")
    print(f"   SELECT * FROM {full_schema}.trail_reports;")
    return True

if __name__ == '__main__':
    main()
