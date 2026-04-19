#!/usr/bin/env python3
"""
TrailKarma iNaturalist Sync Job
Fetches the last 24h of research-grade wildlife observations within the PCT
Southern California corridor and upserts them into workspace.trailkarma.trail_reports.

Deploy as a Databricks Job (Tasks > Python script) on a daily schedule.
Requires: DATABRICKS_HOST, DATABRICKS_TOKEN, DATABRICKS_WAREHOUSE in environment or .env
"""

import os
import uuid
import json
import requests
from datetime import datetime, timedelta, UTC

try:
    import h3
except ImportError:
    raise SystemExit("pip install h3")

SOCAL_BOUNDS = {
    "north": 35.0,
    "south": 32.0,
    "west": -118.0,
    "east": -116.0,
}

TARGET_TAXA = [
    "Crotalus", "Ursus americanus", "Puma concolor", "Canis lupus",
    "Vulpes vulpes", "Ovis canadensis", "Cervus canadensis",
    "Odocoileus", "Antilocapra americana", "Aquila chrysaetos",
]


def load_env():
    config = {}
    if os.path.exists(".env"):
        with open(".env") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    k, v = line.split("=", 1)
                    config[k.strip()] = v.strip().strip("\"'")
    config.update({k: v for k, v in os.environ.items() if k.startswith("DATABRICKS")})
    return config


def fetch_observations(bounds: dict, since: datetime) -> list[dict]:
    """Fetch research-grade observations from iNaturalist updated since `since`."""
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
                data = resp.json()
                batch = data.get("results", [])
                if not batch:
                    break
                for obs in batch:
                    coords = obs.get("geom", {}).get("coordinates")
                    if not coords:
                        continue
                    photos = obs.get("photos", [])
                    results.append({
                        "observation_id": str(obs["id"]),
                        "taxon": taxon,
                        "common_name": (obs.get("taxon") or {}).get("preferred_common_name") or taxon,
                        "scientific_name": (obs.get("taxon") or {}).get("name") or "",
                        "lat": coords[1],
                        "lng": coords[0],
                        "image_url": photos[0]["url"] if photos else "",
                        "observed_on": obs.get("observed_on") or obs.get("time_observed_at") or "",
                        "place_guess": obs.get("place_guess") or "",
                    })
                page += 1
            except Exception as e:
                print(f"  ⚠️  iNaturalist fetch error ({taxon}): {e}")
                break

    print(f"  ✓ Fetched {len(results)} observations from iNaturalist")
    return results


def build_insert(obs: dict, full_schema: str) -> str:
    report_id = str(uuid.uuid4())
    title = obs["common_name"].replace("'", "\\'")
    desc = obs["place_guess"].replace("'", "\\'")
    lat, lng = obs["lat"], obs["lng"]
    h3_cell = h3.latlng_to_cell(lat, lng, 9)
    ts = obs["observed_on"] or datetime.now(UTC).isoformat().replace("+00:00", "Z")
    image_url = obs["image_url"].replace("'", "\\'")
    species_name = obs["scientific_name"].replace("'", "\\'")
    user_id = f"inaturalist-{obs['observation_id']}"

    return (
        f"INSERT INTO {full_schema}.trail_reports VALUES ("
        f"'{report_id}', '{user_id}', 'species', "
        f"'{title}', '{desc}', {lat}, {lng}, '{h3_cell}', '{ts}', "
        f"'{image_url}', '{species_name}', NULL, 'self', 0, true, "
        f"current_timestamp(), current_timestamp())"
    )


def execute_sql(workspace_url: str, token: str, warehouse_id: str, sql: str) -> bool:
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    payload = {"warehouse_id": warehouse_id, "statement": sql, "wait_timeout": "30s"}
    try:
        r = requests.post(f"{workspace_url}/api/2.0/sql/statements", json=payload, headers=headers, timeout=60)
        state = r.json().get("status", {}).get("state", "UNKNOWN")
        return state in ("SUCCEEDED", "PENDING")
    except Exception as e:
        print(f"  ❌ SQL error: {e}")
        return False


def main():
    config = load_env()
    workspace_url = config.get("DATABRICKS_HOST", "").rstrip("/")
    token = config.get("DATABRICKS_TOKEN", "")
    warehouse_id = config.get("DATABRICKS_WAREHOUSE", "")

    if not workspace_url or not token or not warehouse_id:
        raise SystemExit("❌ Missing DATABRICKS_HOST / DATABRICKS_TOKEN / DATABRICKS_WAREHOUSE")

    full_schema = "workspace.trailkarma"
    since = datetime.now(UTC) - timedelta(hours=1)

    print(f"\n🌿 TrailKarma iNaturalist Sync")
    print(f"   Fetching observations since {since.strftime('%Y-%m-%d %H:%M')} UTC\n")

    observations = fetch_observations(SOCAL_BOUNDS, since)

    if not observations:
        print("  ℹ️  No new observations found.")
        return

    success = 0
    for i, obs in enumerate(observations, 1):
        sql = build_insert(obs, full_schema)
        ok = execute_sql(workspace_url, token, warehouse_id, sql)
        if ok:
            success += 1
        if i % 20 == 0:
            print(f"  [{i}/{len(observations)}] inserted so far...")

    print(f"\n✅ Sync complete: {success}/{len(observations)} reports inserted")


if __name__ == "__main__":
    main()
