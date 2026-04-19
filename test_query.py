import os
import requests
import json

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

config = load_env()
workspace_url = config.get('DATABRICKS_HOST', '').rstrip('/')
token = config.get('DATABRICKS_TOKEN', '')
headers = {'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'}

print("Finding SQL warehouse...")
r = requests.get(f"{workspace_url}/api/2.0/sql/warehouses", headers=headers, timeout=10)
warehouse_id = next((wh.get('id') for wh in r.json().get('warehouses', []) if wh.get('state') == 'RUNNING'), None)

print(f"Executing query on {warehouse_id}...")
payload = {'warehouse_id': warehouse_id, 'statement': 'SELECT trail_id, name, description FROM workspace.trailkarma.trails;', 'wait_timeout': '30s'}
r = requests.post(f"{workspace_url}/api/2.0/sql/statements", json=payload, headers=headers)
result = r.json()

print(json.dumps(result.get('result', {}).get('data_array', []), indent=2))
