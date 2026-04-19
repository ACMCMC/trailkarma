#!/usr/bin/env python3
from __future__ import annotations

import os
import sys
from typing import Iterable

import requests


def execute_sql(host: str, token: str, warehouse_id: str, statements: Iterable[str]) -> None:
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
    }
    for sql in statements:
        response = requests.post(
            f"{host.rstrip('/')}/api/2.0/sql/statements",
            headers=headers,
            json={"warehouse_id": warehouse_id, "statement": sql, "wait_timeout": "30s"},
            timeout=60,
        )
        response.raise_for_status()
        body = response.json()
        state = body.get("status", {}).get("state")
        if state != "SUCCEEDED":
            raise RuntimeError(f"Statement failed with state={state}: {sql}")


def resolve_warehouse(host: str, token: str) -> str:
    response = requests.get(
        f"{host.rstrip('/')}/api/2.0/sql/warehouses",
        headers={"Authorization": f"Bearer {token}"},
        timeout=30,
    )
    response.raise_for_status()
    warehouses = response.json().get("warehouses", [])
    for warehouse in warehouses:
        if warehouse.get("state") == "RUNNING":
            return warehouse["id"]
    raise RuntimeError("No RUNNING Databricks SQL warehouse found")


def main() -> int:
    host = os.environ.get("DATABRICKS_HOST")
    token = os.environ.get("DATABRICKS_TOKEN")
    warehouse_id = os.environ.get("DATABRICKS_WAREHOUSE")
    if not host or not token:
        print("DATABRICKS_HOST and DATABRICKS_TOKEN must be set", file=sys.stderr)
        return 1
    if not warehouse_id:
        warehouse_id = resolve_warehouse(host, token)

    statements = [
        "CREATE SCHEMA IF NOT EXISTS workspace.trailkarma",
        """
        CREATE TABLE IF NOT EXISTS workspace.trailkarma.biodiversity_events (
            observation_id STRING NOT NULL,
            timestamp STRING NOT NULL,
            lat DOUBLE NOT NULL,
            lon DOUBLE NOT NULL,
            final_label STRING,
            taxonomic_level STRING,
            confidence DOUBLE,
            confidence_band STRING,
            explanation STRING,
            verification_status STRING,
            photo_uri STRING,
            created_at TIMESTAMP
        ) USING DELTA
        """,
    ]

    execute_sql(host, token, warehouse_id, statements)
    print("Databricks biodiversity tables are ready.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
