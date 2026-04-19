import os
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from databricks import sql

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["GET"],
    allow_headers=["*"],
)

DATABRICKS_HOST = os.environ.get("DATABRICKS_HOST", "").replace("https://", "")
DATABRICKS_TOKEN = os.environ.get("DATABRICKS_TOKEN", "")
HTTP_PATH = "/sql/1.0/warehouses/5fa7bca37483870e"


def query(sql_text: str):
    with sql.connect(
        server_hostname=DATABRICKS_HOST,
        http_path=HTTP_PATH,
        access_token=DATABRICKS_TOKEN,
    ) as conn:
        with conn.cursor() as cursor:
            cursor.execute(sql_text)
            cols = [d[0] for d in cursor.description]
            rows = cursor.fetchall()
            return [dict(zip(cols, row)) for row in rows]


@app.get("/users")
def get_users():
    return query("SELECT user_id, display_name, karma_points FROM workspace.trailkarma.users ORDER BY display_name")


@app.get("/users/{user_id}/locations")
def get_locations(user_id: str):
    rows = query(f"""
        SELECT id, user_id, timestamp, lat, lng
        FROM workspace.trailkarma.location_updates
        WHERE user_id = '{user_id}'
        ORDER BY timestamp ASC
    """)
    if not rows:
        raise HTTPException(status_code=404, detail="User not found or no location data")
    return rows


@app.get("/trail-reports")
def get_trail_reports():
    return query("SELECT report_id, type, title, description, lat, lng, timestamp FROM workspace.trailkarma.trail_reports ORDER BY timestamp DESC")


@app.get("/health")
def health():
    return {"status": "ok"}
