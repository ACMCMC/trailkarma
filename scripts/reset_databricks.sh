#!/bin/bash
set -e

DATABRICKS_HOST="${DATABRICKS_HOST:-https://dbc-f1d1578e-8435.cloud.databricks.com}"
DATABRICKS_TOKEN="${DATABRICKS_TOKEN:-}"
DATABRICKS_WAREHOUSE="${DATABRICKS_WAREHOUSE:-}"
SQL_FILE="$(dirname "$0")/reset.sql"

if [ -z "$DATABRICKS_TOKEN" ] || [ -z "$DATABRICKS_WAREHOUSE" ]; then
    echo "Error: DATABRICKS_TOKEN and DATABRICKS_WAREHOUSE required"
    exit 1
fi

if [ ! -f "$SQL_FILE" ]; then
    echo "Error: $SQL_FILE not found"
    exit 1
fi

execute_sql() {
    local sql="$1"
    echo "  > ${sql:0:70}..."

    response=$(curl -s -X POST "$DATABRICKS_HOST/api/2.0/sql/statements" \
        -H "Authorization: Bearer $DATABRICKS_TOKEN" \
        -H "Content-Type: application/json" \
        -d "{\"warehouse_id\": \"$DATABRICKS_WAREHOUSE\", \"statement\": $(echo "$sql" | jq -Rs .), \"wait_timeout\": \"45s\"}")

    state=$(echo "$response" | jq -r '.status.state // "UNKNOWN"')

    if [ "$state" = "FAILED" ]; then
        error=$(echo "$response" | jq -r '.status.error.message // "Unknown error"')
        echo "    ❌ $error"
        return 1
    fi

    echo "    ✅"
    sleep 1
}

echo "🔄 Wiping and recreating Databricks..."
echo ""

# Read SQL file and execute each statement (split by semicolons)
while IFS= read -r line; do
    # Skip empty lines and comments
    [ -z "$line" ] && continue
    [[ "$line" =~ ^-- ]] && continue

    # Accumulate statements until we hit a semicolon
    statement="$statement$line "

    if [[ "$line" =~ \;$ ]]; then
        # Remove trailing semicolon
        statement="${statement%; }"
        execute_sql "$statement"
        statement=""
    fi
done < "$SQL_FILE"

echo ""
echo "✅ Complete!"
