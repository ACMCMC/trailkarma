# Databricks notebook source
# TrailKarma Setup Notebook
# Copy this entire notebook into a Databricks notebook and run it
# It will create schema, tables, and populate with mock data
# Safe to rerun anytime to reset the database

# COMMAND ----------

# Configuration
catalog = "main"
schema = "trailkarma"

print(f"🔧 Setting up TrailKarma in {catalog}.{schema}")

# COMMAND ----------

# Create schema
sql(f"CREATE SCHEMA IF NOT EXISTS {catalog}.{schema}")
print(f"✓ Schema {catalog}.{schema} ready")

# COMMAND ----------

# Drop existing tables (safe reset)
sql(f"DROP TABLE IF EXISTS {catalog}.{schema}.users")
sql(f"DROP TABLE IF EXISTS {catalog}.{schema}.trail_reports")
sql(f"DROP TABLE IF EXISTS {catalog}.{schema}.location_updates")
sql(f"DROP TABLE IF EXISTS {catalog}.{schema}.relay_packets")
print("✓ Old tables dropped (clean slate)")

# COMMAND ----------

# Create users table
sql(f"""
    CREATE TABLE {catalog}.{schema}.users (
        user_id STRING NOT NULL,
        display_name STRING NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP(),
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP()
    )
    USING DELTA
""")
print("✓ Users table created")

# COMMAND ----------

# Create trail_reports table
sql(f"""
    CREATE TABLE {catalog}.{schema}.trail_reports (
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
        synced BOOLEAN DEFAULT FALSE,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP(),
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP()
    )
    USING DELTA
""")
print("✓ Trail reports table created")

# COMMAND ----------

# Create location_updates table
sql(f"""
    CREATE TABLE {catalog}.{schema}.location_updates (
        id STRING NOT NULL,
        timestamp STRING NOT NULL,
        lat DOUBLE NOT NULL,
        lng DOUBLE NOT NULL,
        synced BOOLEAN DEFAULT FALSE,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP(),
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP()
    )
    USING DELTA
""")
print("✓ Location updates table created")

# COMMAND ----------

# Create relay_packets table
sql(f"""
    CREATE TABLE {catalog}.{schema}.relay_packets (
        packet_id STRING NOT NULL,
        payload_json STRING NOT NULL,
        received_at STRING NOT NULL,
        sender_device STRING,
        uploaded BOOLEAN DEFAULT FALSE,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP(),
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP()
    )
    USING DELTA
""")
print("✓ Relay packets table created")

# COMMAND ----------

# Generate and insert mock data
from datetime import datetime, timedelta
import uuid
import json

now = datetime.utcnow()
base_lat, base_lng = 32.88, -117.24

# Users
users_data = [
    ("Aldan", str(uuid.uuid4())),
    ("Alex", str(uuid.uuid4())),
    ("Jordan", str(uuid.uuid4())),
]

for name, user_id in users_data:
    sql(f"""
        INSERT INTO {catalog}.{schema}.users (user_id, display_name)
        VALUES ('{user_id}', '{name}')
    """)

print(f"✓ Inserted {len(users_data)} users")

# COMMAND ----------

# Trail reports with realistic data
reports_data = [
    ("mock-1", "hazard", "Rockslide ahead", "Section near mile 24 has debris and loose rocks", base_lat, base_lng, "self", None, None),
    ("mock-2", "hazard", "Rattlesnake spotted", "Stay alert, seen near water source at dusk", base_lat - 0.01, base_lng - 0.01, "relayed", None, None),
    ("mock-3", "water", "Water source confirmed", "Spring flowing, fresh water tested and safe", base_lat + 0.01, base_lng - 0.01, "self", None, None),
    ("mock-4", "species", "Mule deer herd", "About 6-8 deer spotted grazing at sunrise", base_lat + 0.005, base_lng + 0.005, "self", "Mule Deer", 0.92),
    ("mock-5", "species", "California quail", "Small covey moving through brush", base_lat - 0.005, base_lng + 0.005, "relayed", "California Quail", 0.87),
]

for i, (rid, rtype, title, desc, lat, lng, source, species, conf) in enumerate(reports_data):
    ts = (now - timedelta(hours=i*2)).isoformat() + 'Z'
    species_col = f"'{species}'" if species else "NULL"
    conf_col = conf if conf else "NULL"

    sql(f"""
        INSERT INTO {catalog}.{schema}.trail_reports
        (report_id, type, title, description, lat, lng, timestamp, species_name, confidence, source, synced)
        VALUES (
            '{rid}',
            '{rtype}',
            '{title}',
            '{desc}',
            {lat},
            {lng},
            '{ts}',
            {species_col},
            {conf_col},
            '{source}',
            TRUE
        )
    """)

print(f"✓ Inserted {len(reports_data)} trail reports")

# COMMAND ----------

# Location updates
locations_data = [
    (now.isoformat() + 'Z', base_lat, base_lng),
    ((now - timedelta(minutes=30)).isoformat() + 'Z', base_lat + 0.002, base_lng - 0.002),
    ((now - timedelta(minutes=60)).isoformat() + 'Z', base_lat - 0.002, base_lng + 0.002),
]

for ts, lat, lng in locations_data:
    loc_id = str(uuid.uuid4())
    sql(f"""
        INSERT INTO {catalog}.{schema}.location_updates
        (id, timestamp, lat, lng, synced)
        VALUES ('{loc_id}', '{ts}', {lat}, {lng}, TRUE)
    """)

print(f"✓ Inserted {len(locations_data)} location updates")

# COMMAND ----------

# Relay packets (BLE mesh)
packet_id = str(uuid.uuid4())
payload = json.dumps({
    "type": "report",
    "report_id": "mock-2",
    "created_at": (now - timedelta(hours=2)).isoformat() + 'Z'
})

sql(f"""
    INSERT INTO {catalog}.{schema}.relay_packets
    (packet_id, payload_json, received_at, sender_device, uploaded)
    VALUES (
        '{packet_id}',
        '{payload.replace("'", "\\'")}',
        '{now.isoformat() + 'Z'}',
        'device-123',
        TRUE
    )
""")

print("✓ Inserted relay packet")

# COMMAND ----------

# Verification queries
print("\n🎉 Setup complete! Running verification...\n")

print("=== Users ===")
display(sql(f"SELECT COUNT(*) as count FROM {catalog}.{schema}.users"))

print("\n=== Trail Reports ===")
display(sql(f"SELECT COUNT(*) as count FROM {catalog}.{schema}.trail_reports"))
display(sql(f"SELECT type, COUNT(*) as count FROM {catalog}.{schema}.trail_reports GROUP BY type"))

print("\n=== Location Updates ===")
display(sql(f"SELECT COUNT(*) as count FROM {catalog}.{schema}.location_updates"))

print("\n=== Relay Packets ===")
display(sql(f"SELECT COUNT(*) as count FROM {catalog}.{schema}.relay_packets"))

print("\n✅ All tables populated and ready!")
