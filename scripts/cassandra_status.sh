 #!/bin/bash

set -uo pipefail

CASSANDRA_HOST="localhost"
CASSANDRA_PORT="9042"
KEYSPACE="realtime_stock_data"

while [[ $# -gt 0 ]]; do
	case "$1" in
		--host)
			CASSANDRA_HOST="$2"
			shift 2
			;;
		--port)
			CASSANDRA_PORT="$2"
			shift 2
			;;
		--keyspace)
			KEYSPACE="$2"
			shift 2
			;;
		--help|-h)
			echo "Usage: bash cassandra_status.sh [--host localhost] [--port 9042] [--keyspace realtime_stock_data]"
			exit 0
			;;
		*)
			echo "❌ Unknown argument: $1"
			echo "Usage: bash cassandra_status.sh [--host localhost] [--port 9042] [--keyspace realtime_stock_data]"
			exit 1
			;;
	esac
done

echo "🔍 Cassandra Status Check"
echo "========================="
echo ""

if ! command -v nc >/dev/null 2>&1; then
	echo "❌ 'nc' is required to check Cassandra port status."
	exit 1
fi

if nc -z "$CASSANDRA_HOST" "$CASSANDRA_PORT" 2>/dev/null; then
	echo "✅ Cassandra is running on $CASSANDRA_HOST:$CASSANDRA_PORT"
	echo ""

	if command -v cqlsh >/dev/null 2>&1; then
		echo "📂 Keyspaces:"
		cqlsh "$CASSANDRA_HOST" "$CASSANDRA_PORT" -e "DESCRIBE KEYSPACES;" 2>/dev/null | grep -v '^$' || true
		echo ""

		if cqlsh "$CASSANDRA_HOST" "$CASSANDRA_PORT" -e "DESCRIBE KEYSPACE $KEYSPACE;" >/dev/null 2>&1; then
			echo "✅ Keyspace '$KEYSPACE' exists"
			echo ""
			echo "📋 Tables in '$KEYSPACE':"
			cqlsh "$CASSANDRA_HOST" "$CASSANDRA_PORT" -k "$KEYSPACE" -e "DESCRIBE TABLES;" 2>/dev/null | grep -v '^$' || true
		else
			echo "❌ Keyspace '$KEYSPACE' not found"
		fi
	else
		echo "⚠️  Cassandra port is open, but cqlsh is not installed or not in PATH."
	fi
else
	echo "❌ Cassandra is NOT running on $CASSANDRA_HOST:$CASSANDRA_PORT"
	echo ""
	echo "Start it with:"
	echo "  bash /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/setup_cassandra.sh"
fi

echo ""
echo "========================="

