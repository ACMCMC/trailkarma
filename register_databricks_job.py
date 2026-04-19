#!/usr/bin/env python3
"""
Registers (or updates) the TrailKarma iNaturalist hourly sync job in Databricks.
Run once to set it up; re-run to update it.
"""

import json
import os
import requests


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


def main():
    config = load_env()
    host = config.get("DATABRICKS_HOST", "").rstrip("/")
    token = config.get("DATABRICKS_TOKEN", "")
    warehouse_id = config.get("DATABRICKS_WAREHOUSE", "")

    if not host or not token:
        raise SystemExit("❌ Missing DATABRICKS_HOST / DATABRICKS_TOKEN in .env")

    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

    # Check if a job with this name already exists
    job_name = "TrailKarma iNaturalist Hourly Sync"
    list_resp = requests.get(f"{host}/api/2.1/jobs/list", headers=headers, params={"name": job_name}, timeout=10)
    list_resp.raise_for_status()
    existing_jobs = list_resp.json().get("jobs", [])

    job_spec = {
        "name": job_name,
        "schedule": {
            "quartz_cron_expression": "0 0 * * * ?",  # every hour at :00
            "timezone_id": "UTC",
            "pause_status": "UNPAUSED",
        },
        "environments": [
            {
                "environment_key": "default",
                "spec": {
                    "client": "1",
                    "dependencies": ["h3", "requests"],
                },
            }
        ],
        "tasks": [
            {
                "task_key": "inaturalist_sync",
                "description": "Fetch last 1h of iNaturalist observations and upsert into trail_reports",
                "python_wheel_task": None,  # replaced below
                "environment_key": "default",
            }
        ],
        "email_notifications": {"no_alert_for_skipped_runs": True},
        "max_concurrent_runs": 1,
    }

    # Serverless: use notebook_task pointing at the script uploaded to workspace,
    # or spark_python_task with source=GIT. Simplest for serverless: notebook_task
    # replaced with a python script task using the file path directly.
    del job_spec["tasks"][0]["python_wheel_task"]
    job_spec["tasks"][0]["spark_python_task"] = {
        "python_file": "/inaturalist_sync_job.py",
        "parameters": [
            f"--host={host}",
            f"--token={token}",
            f"--warehouse={warehouse_id}",
        ],
    }
    print("  ✓ Using serverless compute")

    if existing_jobs:
        job_id = existing_jobs[0]["job_id"]
        r = requests.post(
            f"{host}/api/2.1/jobs/reset",
            headers=headers,
            json={"job_id": job_id, "new_settings": job_spec},
            timeout=15,
        )
        r.raise_for_status()
        print(f"✅ Updated existing job (id={job_id}): {job_name}")
    else:
        r = requests.post(f"{host}/api/2.1/jobs/create", headers=headers, json=job_spec, timeout=15)
        if not r.ok:
            print(f"  ❌ {r.status_code}: {r.text}")
            raise SystemExit(1)
        job_id = r.json()["job_id"]
        print(f"✅ Created job (id={job_id}): {job_name}")

    print(f"   Schedule: every hour (UTC)")
    print(f"   View at: {host}/jobs/{job_id}")


if __name__ == "__main__":
    main()
