import os
import requests

def main():
    token = os.environ.get("DATABRICKS_TOKEN", "")
    host = os.environ.get("DATABRICKS_HOST", "").rstrip('/')
    if not token and os.path.exists('.env'):
        with open('.env') as f:
            for line in f:
                if line.startswith('DATABRICKS_TOKEN='): token = line.split('=',1)[1].strip().strip('"\'')
                if line.startswith('DATABRICKS_HOST='): host = line.split('=',1)[1].strip().strip('"\'').rstrip('/')

    headers = {'Authorization': f'Bearer {token}'}
    r = requests.get(f"{host}/api/2.0/sql/warehouses", headers=headers)
    wid = r.json()['warehouses'][0]['id']

    sql = "SELECT trail_id, name, description, total_length_miles, region, geometry_json FROM workspace.trailkarma.trails"
    res = requests.post(f"{host}/api/2.0/sql/statements", headers=headers, json={"warehouse_id": wid, "statement": sql, "wait_timeout": "30s"})
    print(res.json())

main()
