#!/usr/bin/env python3
"""Verify TrailKarma data in Databricks"""

import os
import sys
from databricks.sdk import WorkspaceClient

# Load .env
if os.path.exists('.env'):
    with open('.env') as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith('#'):
                key, value = line.split('=', 1)
                os.environ[key.strip()] = value.strip().strip('"\'')

# Read from env
workspace_url = os.getenv('DATABRICKS_HOST')
token = os.getenv('DATABRICKS_TOKEN')

if not workspace_url or not token:
    print("❌ Missing DATABRICKS_HOST or DATABRICKS_TOKEN in environment")
    sys.exit(1)

ws = WorkspaceClient(host=workspace_url, token=token)

print("\n📊 Checking Databricks data...\n")

# Get first running warehouse
warehouses = list(ws.warehouses.list())
warehouse_id = None

for wh in warehouses:
    if wh.state.name == "RUNNING":
        warehouse_id = wh.id
        print(f"Using warehouse: {wh.name} ({wh.id})")
        break

if not warehouse_id:
    print("❌ No RUNNING SQL warehouse found!")
    print("\n⚠️  You need to create and start a SQL warehouse:")
    print("   1. Go to your Databricks workspace")
    print("   2. Click 'SQL' in the sidebar")
    print("   3. Click 'Warehouses'")
    print("   4. Click 'Create SQL Warehouse'")
    print("   5. Select 'Starter' tier, click 'Create'")
    print("   6. Wait for it to start (green checkmark)")
    sys.exit(1)

print()

queries = [
    ("Users", "SELECT display_name, COUNT(*) as count FROM main.trailkarma.users GROUP BY display_name"),
    ("Trail Reports", "SELECT type, COUNT(*) as count FROM main.trailkarma.trail_reports GROUP BY type"),
    ("Location Updates", "SELECT COUNT(*) as count FROM main.trailkarma.location_updates"),
    ("Relay Packets", "SELECT COUNT(*) as count FROM main.trailkarma.relay_packets"),
]

for name, query in queries:
    try:
        print(f"Querying {name}...", end=" ")
        result = ws.sql_warehouses.execute_statement(warehouse_id, query)

        if result.get('statement_state') == 'SUCCEEDED':
            rows = result.get('result', {}).get('data_array', [])
            if rows:
                print(f"✅")
                for row in rows:
                    print(f"  {row}")
            else:
                print(f"⚠️  No data")
        else:
            print(f"❌ {result.get('statement_state')}")
    except Exception as e:
        print(f"❌ {str(e)[:60]}")

print("\n" + "="*60)
print("✅ Data exists! View it in Databricks:")
print("="*60)
print("\n1. Open your Databricks workspace")
print("2. Click 'SQL Editor' (left sidebar)")
print("3. Click '+ Create' → 'Query'")
print("4. Paste this and click 'Run':\n")
print("   SELECT * FROM main.trailkarma.trail_reports LIMIT 10;\n")
print("Or explore in Catalog:")
print("   Catalog → main → trailkarma → (select table)")
