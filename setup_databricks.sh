#!/bin/bash
# TrailKarma Databricks Setup Script
# One-line execution for full setup

set -e

echo "🔧 TrailKarma Databricks Setup"
echo ""

# Check if .env exists
if [ ! -f .env ]; then
    echo "❌ .env file not found!"
    echo ""
    echo "Create one by running:"
    echo "  cp .env.example .env"
    echo "  # Then edit .env with your Databricks credentials"
    exit 1
fi

# Check Python
if ! command -v python3 &> /dev/null; then
    echo "❌ Python3 not found!"
    exit 1
fi

echo "📦 Installing dependencies..."
python3 -m pip install -r requirements_databricks.txt -q

echo "🗄️  Running Databricks setup..."
python3 setup_databricks.py "$@"

echo ""
echo "✅ Complete!"
