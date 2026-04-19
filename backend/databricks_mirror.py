from __future__ import annotations

import os
from typing import Any

import requests


class DatabricksMirror:
    def __init__(self) -> None:
        self.host = os.getenv("DATABRICKS_HOST", "").rstrip("/")
        self.token = os.getenv("DATABRICKS_TOKEN", "")
        self.warehouse_id = os.getenv("DATABRICKS_WAREHOUSE", "")

    def mirror_event(self, event: dict[str, Any]) -> dict[str, Any]:
        if not (self.host and self.token and self.warehouse_id):
            return {"enabled": False, "status": "skipped"}

        sql = f"""
        INSERT INTO workspace.trailkarma.biodiversity_events
        (observation_id, timestamp, lat, lon, final_label, taxonomic_level, confidence, confidence_band, explanation, verification_status, photo_uri)
        VALUES (
            '{event["observation_id"]}',
            '{event["timestamp"]}',
            {event["lat"]},
            {event["lon"]},
            '{event["finalLabel"].replace("'", "''")}',
            '{event["finalTaxonomicLevel"]}',
            {event["confidence"]},
            '{event["confidenceBand"]}',
            '{event["explanation"].replace("'", "''")}',
            '{event.get("verification_status", "provisional")}',
            {f"'{event['photo_uri']}'" if event.get("photo_uri") else "NULL"}
        )
        """.strip()

        response = requests.post(
            f"{self.host}/api/2.0/sql/statements",
            headers={
                "Authorization": f"Bearer {self.token}",
                "Content-Type": "application/json",
            },
            json={"warehouse_id": self.warehouse_id, "statement": sql, "wait_timeout": "30s"},
            timeout=30,
        )
        response.raise_for_status()
        body = response.json()
        return {"enabled": True, "status": body.get("status", {}).get("state", "UNKNOWN")}
