#!/usr/bin/env python3
"""TrailKarma Databricks Setup - Wipes and Recreates Database Structure"""

import os
import uuid
import requests
from datetime import datetime, timedelta

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

    catalog = "hive_metastore"
    schema = "trailkarma"
    full_schema = f"{catalog}.{schema}"

    now = datetime.utcnow()

    # The 4 demo hikers
    users = [
        (str(uuid.uuid4()), 'Aldan'),
        (str(uuid.uuid4()), 'Qianqian'),
        (str(uuid.uuid4()), 'Suraj'),
        (str(uuid.uuid4()), 'Edith'),
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
        f"DROP TABLE IF EXISTS {full_schema}.users",
        f"DROP TABLE IF EXISTS {full_schema}.trail_reports",
        f"DROP TABLE IF EXISTS {full_schema}.location_updates",
        f"DROP TABLE IF EXISTS {full_schema}.relay_packets",
        
        # RECREATE FULL STRUCTURE
        f"""CREATE TABLE {full_schema}.users (
            user_id STRING NOT NULL, 
            display_name STRING NOT NULL,
            created_at TIMESTAMP,
            updated_at TIMESTAMP
        ) USING DELTA""",
        
        f"""CREATE TABLE {full_schema}.trail_reports (
            report_id STRING NOT NULL, 
            type STRING NOT NULL, 
            title STRING NOT NULL,
            description STRING, 
            lat DOUBLE NOT NULL, 
            lng DOUBLE NOT NULL, 
            timestamp STRING NOT NULL,
            species_name STRING, 
            confidence DOUBLE, 
            source STRING NOT NULL, 
            synced BOOLEAN,
            created_at TIMESTAMP,
            updated_at TIMESTAMP
        ) USING DELTA""",
        
        f"""CREATE TABLE {full_schema}.location_updates (
            id STRING NOT NULL, 
            timestamp STRING NOT NULL, 
            lat DOUBLE NOT NULL,
            lng DOUBLE NOT NULL, 
            synced BOOLEAN,
            created_at TIMESTAMP,
            updated_at TIMESTAMP
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
    for user_id, name in users:
        sql_statements.append(f"INSERT INTO {full_schema}.users VALUES ('{user_id}', '{name}', current_timestamp(), current_timestamp())")

    for i, (rid, rtype, title, desc, lat, lng, source, species, conf) in enumerate(reports):
        species_val = f"'{species}'" if species else "NULL"
        conf_val = conf if conf is not None else "NULL"
        ts = (now - timedelta(hours=i*2)).isoformat() + 'Z'
        sql_statements.append(
            f"INSERT INTO {full_schema}.trail_reports VALUES ('{rid}', '{rtype}', '{title}', '{desc}', "
            f"{lat}, {lng}, '{ts}', {species_val}, {conf_val}, '{source}', true, current_timestamp(), current_timestamp())"
        )

    for loc_id, ts, lat, lng in locations:
        sql_statements.append(f"INSERT INTO {full_schema}.location_updates VALUES ('{loc_id}', '{ts}', {lat}, {lng}, true, current_timestamp(), current_timestamp())")

    for packet_id, payload, ts, device in relay_packets:
        sql_statements.append(f"INSERT INTO {full_schema}.relay_packets VALUES ('{packet_id}', '{payload}', '{ts}', '{device}', true, current_timestamp(), current_timestamp())")

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
