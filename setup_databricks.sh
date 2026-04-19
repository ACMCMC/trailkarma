#!/bin/bash
# TrailKarma Databricks Setup — single entry point
# Wipes, recreates, and repopulates the entire DB, then registers the iNaturalist sync job.
# Usage: ./setup_databricks.sh

set -e

echo "🔧 TrailKarma Databricks Setup"
echo ""

if [ ! -f .env ]; then
    echo "❌ .env file not found!"
    echo ""
    echo "Create one by running:"
    echo "  cp .env.example .env"
    echo "  # Then edit .env with your Databricks credentials"
    exit 1
fi

if ! command -v python3 &> /dev/null; then
    echo "❌ Python3 not found!"
    exit 1
fi

echo "📦 Installing dependencies..."
python3 -m pip install -r requirements_databricks.txt -q

echo ""
echo "Step 1/2 — Wipe, recreate, and populate database..."
python3 setup_databricks.py

echo ""
echo "Step 2/2 — Register iNaturalist hourly sync job..."
python3 register_databricks_job.py

echo ""
echo "✅ Setup complete!"
