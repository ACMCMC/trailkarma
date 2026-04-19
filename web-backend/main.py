import os
import time
import requests
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

app = FastAPI()

# 允许前端跨域访问
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["GET"],
    allow_headers=["*"],
)

DATABRICKS_HOST  = os.environ.get("DATABRICKS_HOST", "").rstrip("/")
DATABRICKS_TOKEN = os.environ.get("DATABRICKS_TOKEN", "")
WAREHOUSE_ID     = "5fa7bca37483870e"


def run_query(sql: str) -> list[dict]:
    """
    通过 Databricks REST API 执行 SQL，返回行列表（每行是一个字典）。
    使用轮询等待结果，适合一次性查询。
    """
    headers = {"Authorization": f"Bearer {DATABRICKS_TOKEN}"}

    # 1. 提交 SQL 语句
    resp = requests.post(
        f"{DATABRICKS_HOST}/api/2.0/sql/statements",
        headers=headers,
        json={
            "statement": sql,
            "warehouse_id": WAREHOUSE_ID,
            "wait_timeout": "30s",   # 最多等 30 秒，超时则轮询
            "on_wait_timeout": "CONTINUE",
        },
    )
    resp.raise_for_status()
    data = resp.json()
    statement_id = data["statement_id"]

    # 2. 如果还在运行中，轮询等待
    for _ in range(30):
        status = data.get("status", {}).get("state", "")
        if status in ("SUCCEEDED", "FAILED", "CANCELED", "CLOSED"):
            break
        time.sleep(1)
        data = requests.get(
            f"{DATABRICKS_HOST}/api/2.0/sql/statements/{statement_id}",
            headers=headers,
        ).json()

    if data.get("status", {}).get("state") != "SUCCEEDED":
        raise HTTPException(status_code=500, detail=f"Query failed: {data.get('status')}")

    # 3. 把列名、类型和数据组合成字典列表，数值列自动转换类型
    result = data.get("result", {})
    schema = data.get("manifest", {}).get("schema", {}).get("columns", [])
    col_names = [c["name"] for c in schema]
    col_types = [c.get("type_name", "STRING") for c in schema]
    rows = result.get("data_array", []) or []

    output = []
    for row in rows:
        d = {}
        for name, type_name, val in zip(col_names, col_types, row):
            if val is None:
                d[name] = None
            elif type_name in ("DOUBLE", "FLOAT", "DECIMAL"):
                d[name] = float(val)
            elif type_name in ("INT", "INTEGER", "LONG", "BIGINT", "SHORT"):
                d[name] = int(val)
            elif type_name == "BOOLEAN":
                d[name] = val.lower() == "true" if isinstance(val, str) else val
            else:
                d[name] = val
        output.append(d)
    return output


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/users")
def get_users():
    """返回所有 hiker 的 user_id 和 display_name"""
    return run_query("""
        SELECT user_id, display_name, karma_points
        FROM workspace.trailkarma.users
        ORDER BY display_name
    """)


@app.get("/users/{user_id}/locations")
def get_locations(user_id: str):
    """返回某个 hiker 的所有位置记录，按时间升序"""
    rows = run_query(f"""
        SELECT id, user_id, timestamp, lat, lng
        FROM workspace.trailkarma.location_updates
        WHERE user_id = '{user_id}'
        ORDER BY timestamp ASC
    """)
    if not rows:
        raise HTTPException(status_code=404, detail="No location data found for this user")
    return rows


@app.get("/trail-reports")
def get_trail_reports():
    """返回所有 trail report（在地图上显示 emoji 标志用）"""
    return run_query("""
        SELECT report_id, type, title, description, lat, lng, timestamp
        FROM workspace.trailkarma.trail_reports
        ORDER BY timestamp DESC
    """)


@app.get("/pct")
def get_pct():
    """返回所有 PCT sections 的 GeoJSON geometry，供前端地图渲染"""
    import json
    rows = run_query("""
        SELECT name, region, total_length_miles, geometry_json
        FROM workspace.trailkarma.trails
        ORDER BY name
    """)
    features = []
    for row in rows:
        try:
            geom = json.loads(row["geometry_json"])
            features.append({
                "type": "Feature",
                "properties": {"name": row["name"], "region": row["region"], "miles": row["total_length_miles"]},
                "geometry": geom
            })
        except Exception:
            pass
    return {"type": "FeatureCollection", "features": features}


@app.get("/pct-waypoints")
def get_pct_waypoints():
    return run_query("SELECT * FROM workspace.trailkarma.trail_waypoints LIMIT 5")

@app.get("/pct-segments")
def get_pct_segments():
    return run_query("SELECT * FROM workspace.trailkarma.trail_segments LIMIT 5")

@app.get("/pct-trails")
def get_pct_trails():
    return run_query("SELECT * FROM workspace.trailkarma.trails LIMIT 5")

@app.get("/describe-waypoints")
def describe_waypoints():
    return run_query("DESCRIBE TABLE workspace.trailkarma.trail_waypoints")

@app.get("/describe-segments")
def describe_segments():
    return run_query("DESCRIBE TABLE workspace.trailkarma.trail_segments")
