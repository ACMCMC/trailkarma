#!/usr/bin/env python3
"""TrailKarma Databricks Setup - Wipes and Recreates Database Structure"""

import os
import uuid
import json
import math
import glob
import csv
import requests
from datetime import datetime, timedelta


PCT_GEOJSON = os.path.join(os.path.dirname(__file__), "data", "Southern_California.geojson")
SPECIES_CSV = os.path.join(os.path.dirname(__file__), "data", "observations-712152.csv")
WATER_CSV = os.path.join(os.path.dirname(__file__), "data", "water_reports.csv")

SOCAL_LAT_MIN, SOCAL_LAT_MAX = 32.0, 35.0
SOCAL_LNG_MIN, SOCAL_LNG_MAX = -118.0, -116.0



def _haversine_miles(coords):
    """Approximate total length of a LineString in miles."""
    total = 0.0
    for i in range(len(coords) - 1):
        # Extract consecutive coordinate pairs
        lng1, lat1 = coords[i][0], coords[i][1]
        lng2, lat2 = coords[i+1][0], coords[i+1][1]
        # Convert differences to radians
        dlat = math.radians(lat2 - lat1)
        dlng = math.radians(lng2 - lng1)
        # Haversine formula: compute distance between two points
        a = math.sin(dlat/2)**2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlng/2)**2
        # Add distance in miles (Earth's radius ≈ 3958.8 miles)
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
    """Read Southern California GeoJSON and return (trail_inserts, first_trail_id)."""
    statements = []
    first_trail_id = None

    try:
        with open(PCT_GEOJSON, encoding="utf-8") as f:
            data = json.load(f)
    except FileNotFoundError:
        print(f"  ⚠️  PCT GeoJSON not found at {PCT_GEOJSON}, using mock trail")
        return [], None

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
        statements.append(
            f"INSERT INTO {full_schema}.trails VALUES ("
            f"'{trail_id}', '{section_name}', '{desc}', "
            f"{length_miles}, '{data['region']}', '{geom}', "
            f"current_timestamp(), current_timestamp())"
        )

    print(f"  ✓ Loaded {len(sections)} PCT sections from GeoJSON")
    return statements, first_trail_id

def load_species_report_statements(full_schema):
    """Read iNaturalist CSV, filter to Southern California, return INSERT statements."""
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
                ts = row.get("time_observed_at") or row.get("observed_on") or datetime.utcnow().isoformat() + "Z"
                image_url = (row.get("image_url") or "").replace("'", "\\'")
                species_name = (row.get("scientific_name") or "").replace("'", "\\'")

                user_id = row.get("user_id", "unknown")
                statements.append(
                    f"INSERT INTO {full_schema}.trail_reports VALUES ("
                    f"'{report_id}', '{user_id}', 'species', "
                    f"'{title}', '{desc}', {lat}, {lng}, '{ts}', "
                    f"'{image_url}', '{species_name}', NULL, 'self', 0, true, "
                    f"current_timestamp(), current_timestamp())"
                )
    except FileNotFoundError:
        print(f"  ⚠️  Species CSV not found at {SPECIES_CSV}, skipping")

    print(f"  ✓ Loaded {len(statements)} Southern California species reports")
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

    now = datetime.utcnow()

    # The 4 demo hikers (id, name, wallet, karma, active_trail_id)
    pct_trail_id = str(uuid.uuid4())
    
    users = [
        (str(uuid.uuid4()), 'Aldan', '8Bse...1a', 150, pct_trail_id),
        (str(uuid.uuid4()), 'Qianqian', 'C9fe...2b', 320, pct_trail_id),
        (str(uuid.uuid4()), 'Suraj', 'A1bc...3c', 50, pct_trail_id),
        (str(uuid.uuid4()), 'Edith', 'D4de...4d', 420, pct_trail_id),
    ]

    base_lat, base_lng = 32.88, -117.24

    # Reports
    reports = [
        ('mock-1', 'hazard', 'Rockslide ahead', 'Section near mile 24 has debris', base_lat, base_lng, 'self', None, None),
        ('mock-2', 'hazard', 'Rattlesnake spotted', 'Stay alert near water', base_lat - 0.01, base_lng - 0.01, 'relayed', None, None),
        ('mock-3', 'water', 'Water source confirmed', 'Spring flowing, fresh water', base_lat + 0.01, base_lng - 0.01, 'self', None, None),
        ('mock-4', 'species', 'Mule deer herd', '6-8 deer at sunrise', base_lat + 0.005, base_lng + 0.005, 'self', 'Mule Deer', 0.92),
        ('mock-5', 'species', 'California quail', 'Small covey', base_lat - 0.005, base_lng + 0.005, 'relayed', 'California Quail', 0.87),
    ]

    # Locations
    locations = [
        (str(uuid.uuid4()), now.isoformat() + 'Z', base_lat, base_lng),
        (str(uuid.uuid4()), (now - timedelta(minutes=30)).isoformat() + 'Z', base_lat + 0.002, base_lng - 0.002),
        (str(uuid.uuid4()), (now - timedelta(minutes=60)).isoformat() + 'Z', base_lat - 0.002, base_lng + 0.002),
    ]

    # Relay Packets
    relay_packets = [
        (str(uuid.uuid4()), '{"type":"report","report_id":"mock-2"}', (now - timedelta(hours=2)).isoformat() + 'Z', 'device-123'),
    ]

    sql_statements = [
        f"CREATE SCHEMA IF NOT EXISTS {full_schema}",
        
        # WIPE OUT EXISTING TABLES
        f"DROP TABLE IF EXISTS {full_schema}.user_contacts",
        f"DROP TABLE IF EXISTS {full_schema}.location_updates",
        f"DROP TABLE IF EXISTS {full_schema}.relay_packets",
        f"DROP TABLE IF EXISTS {full_schema}.trail_reports",
        f"DROP TABLE IF EXISTS {full_schema}.users",
        f"DROP TABLE IF EXISTS {full_schema}.trail_waypoints",
        f"DROP TABLE IF EXISTS {full_schema}.trails",
        f"DROP TABLE IF EXISTS {full_schema}.relay_packets",
        
        # RECREATE FULL STRUCTURE
        f"""CREATE TABLE {full_schema}.trails (
            trail_id STRING NOT NULL PRIMARY KEY,
            name STRING NOT NULL,
            description STRING,
            total_length_miles DOUBLE,
            region STRING,
            geometry_json STRING,
            created_at TIMESTAMP,
            updated_at TIMESTAMP
        ) USING DELTA""",
        
        f"""CREATE TABLE {full_schema}.trail_waypoints (
            waypoint_id STRING NOT NULL PRIMARY KEY,
            trail_id STRING NOT NULL,
            name STRING NOT NULL,
            type STRING NOT NULL,
            lat DOUBLE NOT NULL,
            lng DOUBLE NOT NULL,
            description STRING,
            created_at TIMESTAMP,
            updated_at TIMESTAMP,
            CONSTRAINT fk_waypoint_trail FOREIGN KEY (trail_id) REFERENCES {full_schema}.trails(trail_id)
        ) USING DELTA""",

        f"""CREATE TABLE {full_schema}.users (
            user_id STRING NOT NULL PRIMARY KEY, 
            display_name STRING NOT NULL,
            wallet_address STRING,
            karma_points INT,
            profile_image_url STRING,
            active_trail_id STRING,
            created_at TIMESTAMP,
            updated_at TIMESTAMP,
            CONSTRAINT fk_users_trail FOREIGN KEY (active_trail_id) REFERENCES {full_schema}.trails(trail_id)
        ) USING DELTA""",
        
        f"""CREATE TABLE {full_schema}.trail_reports (
            report_id STRING NOT NULL PRIMARY KEY, 
            user_id STRING NOT NULL,
            type STRING NOT NULL, 
            title STRING NOT NULL,
            description STRING, 
            lat DOUBLE NOT NULL, 
            lng DOUBLE NOT NULL, 
            timestamp STRING NOT NULL,
            image_url STRING,
            species_name STRING, 
            confidence DOUBLE, 
            source STRING NOT NULL, 
            upvotes INT,
            synced BOOLEAN,
            created_at TIMESTAMP,
            updated_at TIMESTAMP,
            CONSTRAINT fk_reports_user FOREIGN KEY (user_id) REFERENCES {full_schema}.users(user_id)
        ) USING DELTA""",
        
        f"""CREATE TABLE {full_schema}.location_updates (
            id STRING NOT NULL PRIMARY KEY, 
            user_id STRING NOT NULL,
            timestamp STRING NOT NULL, 
            lat DOUBLE NOT NULL,
            lng DOUBLE NOT NULL, 
            synced BOOLEAN,
            created_at TIMESTAMP,
            updated_at TIMESTAMP,
            CONSTRAINT fk_locations_user FOREIGN KEY (user_id) REFERENCES {full_schema}.users(user_id)
        ) USING DELTA""",
        
        f"""CREATE TABLE {full_schema}.user_contacts (
            contact_id STRING NOT NULL PRIMARY KEY,
            user_id STRING NOT NULL,
            contact_user_id STRING NOT NULL,
            status STRING NOT NULL,
            created_at TIMESTAMP,
            updated_at TIMESTAMP,
            CONSTRAINT fk_contacts_user1 FOREIGN KEY (user_id) REFERENCES {full_schema}.users(user_id),
            CONSTRAINT fk_contacts_user2 FOREIGN KEY (contact_user_id) REFERENCES {full_schema}.users(user_id)
        ) USING DELTA""",
        
        f"""CREATE TABLE {full_schema}.relay_packets (
            packet_id STRING NOT NULL, 
            payload_json STRING NOT NULL, 
            received_at STRING NOT NULL,
            sender_device STRING, 
            uploaded BOOLEAN,
            created_at TIMESTAMP,
            updated_at TIMESTAMP
        ) USING DELTA"""
    ]

    # PUT IN ALL THE DATA SO WE START FRESH

    # Trails — load real PCT Southern California sections
    trail_statements, first_trail_id = load_pct_trail_statements(full_schema, now)
    if trail_statements:
        sql_statements.extend(trail_statements)
        pct_trail_id = first_trail_id  # use real trail id for FK references
    else:
        # fallback mock if GeoJSON not found
        mock_geojson = '{"type": "LineString", "coordinates": [[-117.24, 32.88], [-117.23, 32.89]]}'
        sql_statements.append(f"INSERT INTO {full_schema}.trails VALUES ('{pct_trail_id}', 'Pacific Crest Trail - Mock', 'Mock trail data', 2650.0, 'Southern California', '{mock_geojson}', current_timestamp(), current_timestamp())")

    sql_statements.append(f"INSERT INTO {full_schema}.trail_waypoints VALUES ('{str(uuid.uuid4())}', '{pct_trail_id}', 'Southern Terminus', 'trailhead', 32.5896, -116.4669, 'The official start of the PCT at the US-Mexico border.', current_timestamp(), current_timestamp())")
    
    for user_id, name, wallet, karma, trail_id in users:
        sql_statements.append(f"INSERT INTO {full_schema}.users VALUES ('{user_id}', '{name}', '{wallet}', {karma}, NULL, '{trail_id}', current_timestamp(), current_timestamp())")

    for i, (rid, rtype, title, desc, lat, lng, source, species, conf) in enumerate(reports):
        user_id = users[i % len(users)][0]
        species_val = f"'{species}'" if species else "NULL"
        conf_val = conf if conf is not None else "NULL"
        ts = (now - timedelta(hours=i*2)).isoformat() + 'Z'
        sql_statements.append(
            f"INSERT INTO {full_schema}.trail_reports VALUES ('{rid}', '{user_id}', '{rtype}', '{title}', '{desc}', "
            f"{lat}, {lng}, '{ts}', NULL, {species_val}, {conf_val}, '{source}', 0, true, current_timestamp(), current_timestamp())"
        )

    for i, (loc_id, ts, lat, lng) in enumerate(locations):
        user_id = users[i % len(users)][0]
        sql_statements.append(f"INSERT INTO {full_schema}.location_updates VALUES ('{loc_id}', '{user_id}', '{ts}', {lat}, {lng}, true, current_timestamp(), current_timestamp())")

    for packet_id, payload, ts, device in relay_packets:
        sql_statements.append(f"INSERT INTO {full_schema}.relay_packets VALUES ('{packet_id}', '{payload}', '{ts}', '{device}', true, current_timestamp(), current_timestamp())")

    # Species reports from iNaturalist (Southern California only)
    sql_statements.extend(load_species_report_statements(full_schema))

    # Water source reports from PCT Water Report
    sql_statements.extend(load_water_report_statements(full_schema))

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

    warehouse_id = None
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
