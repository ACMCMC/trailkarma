# Databricks notebook source
# TrailKarma iNaturalist Hourly Sync
# Fetches the last 1h of research-grade wildlife observations (SoCal PCT corridor)
# and upserts them into workspace.trailkarma.trail_reports.

# COMMAND ----------

import subprocess
subprocess.run(["pip", "install", "h3", "-q"], check=True)

import os, uuid, requests, h3
from datetime import datetime, timedelta, timezone

# COMMAND ----------

dbutils.widgets.text("DATABRICKS_HOST", "")
dbutils.widgets.text("DATABRICKS_TOKEN", "")
dbutils.widgets.text("DATABRICKS_WAREHOUSE", "")

workspace_url = dbutils.widgets.get("DATABRICKS_HOST").rstrip("/")
token         = dbutils.widgets.get("DATABRICKS_TOKEN")
warehouse_id  = dbutils.widgets.get("DATABRICKS_WAREHOUSE")

assert workspace_url and token and warehouse_id, "Missing job parameters"

# COMMAND ----------

SOCAL_BOUNDS = {"north": 35.0, "south": 32.0, "west": -118.0, "east": -116.0}

TARGET_TAXA = [
    "Crotalus", "Ursus americanus", "Puma concolor", "Canis lupus",
    "Vulpes vulpes", "Ovis canadensis", "Cervus canadensis",
    "Odocoileus", "Antilocapra americana", "Aquila chrysaetos",
]

# COMMAND ----------

def fetch_observations(bounds, since):
    base = "https://api.inaturalist.org/v1/observations"
    since_str = since.strftime("%Y-%m-%dT%H:%M:%S+00:00")
    results = []
    for taxon in TARGET_TAXA:
        params = {
            "q": taxon,
            "nelat": bounds["north"], "nelng": bounds["east"],
            "swlat": bounds["south"], "swlng": bounds["west"],
            "quality_grade": "research",
            "has": ["photos"],
            "updated_since": since_str,
            "per_page": 200,
            "order": "desc",
            "order_by": "updated_at",
        }
        page = 1
        while True:
            params["page"] = page
            try:
                resp = requests.get(base, params=params, timeout=15)
                resp.raise_for_status()
                batch = resp.json().get("results", [])
                if not batch:
                    break
                for obs in batch:
                    coords = (obs.get("geom") or {}).get("coordinates")
                    if not coords:
                        continue
                    photos = obs.get("photos", [])
                    results.append({
                        "observation_id": str(obs["id"]),
                        "common_name": ((obs.get("taxon") or {}).get("preferred_common_name") or taxon),
                        "scientific_name": ((obs.get("taxon") or {}).get("name") or ""),
                        "lat": coords[1], "lng": coords[0],
                        "image_url": photos[0]["url"] if photos else "",
                        "observed_on": obs.get("observed_on") or obs.get("time_observed_at") or "",
                        "place_guess": obs.get("place_guess") or "",
                    })
                page += 1
            except Exception as e:
                print(f"⚠️  iNaturalist error ({taxon}): {e}")
                break
    print(f"✓ Fetched {len(results)} observations")
    return results


def execute_sql(sql):
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    payload = {"warehouse_id": warehouse_id, "statement": sql, "wait_timeout": "30s"}
    r = requests.post(f"{workspace_url}/api/2.0/sql/statements", json=payload, headers=headers, timeout=60)
    state = r.json().get("status", {}).get("state", "UNKNOWN")
    return state in ("SUCCEEDED", "PENDING")


def build_insert(obs, schema):
    def s(v): return f"'{v}'" if v else "NULL"
    report_id   = str(uuid.uuid4())
    title       = obs["common_name"].replace("'", "\\'")
    desc        = obs["place_guess"].replace("'", "\\'")
    lat, lng    = obs["lat"], obs["lng"]
    h3_cell     = h3.latlng_to_cell(lat, lng, 9)
    ts          = obs["observed_on"] or datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    image_url   = obs["image_url"].replace("'", "\\'")
    species     = obs["scientific_name"].replace("'", "\\'")
    user_id     = f"inaturalist-{obs['observation_id']}"
    return (
        f"INSERT INTO {schema}.trail_reports VALUES ("
        f"'{report_id}','{user_id}','species','{title}','{desc}',"
        f"{lat},{lng},'{h3_cell}','{ts}',{s(image_url)},{s(species)},"
        f"NULL,'self',0,true,current_timestamp(),current_timestamp())"
    )

# COMMAND ----------

since = datetime.now(timezone.utc) - timedelta(hours=1)
print(f"🌿 TrailKarma iNaturalist Sync — since {since.strftime('%Y-%m-%d %H:%M')} UTC")

observations = fetch_observations(SOCAL_BOUNDS, since)

if not observations:
    print("ℹ️  No new observations in the last hour.")
else:
    schema = "workspace.trailkarma"
    success = sum(execute_sql(build_insert(obs, schema)) for obs in observations)
    print(f"✅ Inserted {success}/{len(observations)} reports into {schema}.trail_reports")
