#!/bin/bash

################################################################################
# QUICK START: Cassandra + NSE Data Fetcher
#
# Complete setup in 5 minutes
#
################################################################################

echo ""
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║           📊 Investment Stock Market - Quick Setup             ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

SCRIPTS_DIR="/Users/y0p03mn/preparation/investment-stock-market/investment/scripts"

echo "📋 This script shows you the quick setup process."
echo ""

# Step 1: Cassandra
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "STEP 1️⃣ : Install Cassandra & Start Service"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Run this ONCE to set up Cassandra:"
echo ""
echo "  bash $SCRIPTS_DIR/setup_cassandra.sh"
echo ""
echo "⏱️  Takes: 2-3 minutes"
echo "✅ Creates: Cassandra daemon and ensures port 9042 is ready"
echo ""

# Step 2: Apply Schema
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "STEP 2️⃣ : Create Tables / Types in Keyspace"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Apply schema to your target keyspace:"
echo ""
echo "  bash $SCRIPTS_DIR/apply_cassandra_schema.sh --keyspace realtime_stock_data"
echo ""

# Step 3: Verify
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "STEP 3️⃣ : Verify Cassandra"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Check status anytime:"
echo ""
echo "  bash $SCRIPTS_DIR/cassandra_status.sh"
echo ""

# Step 4: Fetch Data
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "STEP 4️⃣ : Fetch NSE Historical Data"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Fetch data for specific symbols:"
echo ""
echo "  python3 $SCRIPTS_DIR/nse_historical_curl_fetcher.py \\"
echo "    --symbols INFY,TCS,SBIN \\"
echo "    --from-date 01-01-2025 \\"
echo "    --to-date 14-06-2026"
echo ""
echo "⏱️  Takes: 30 seconds per symbol"
echo "✅ Creates: JSON files in src/main/resources/historical-data/"
echo ""

# Examples
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📚 MORE EXAMPLES"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

echo "✓ Fetch with custom date range:"
echo "  python3 $SCRIPTS_DIR/nse_historical_curl_fetcher.py \\"
echo "    --symbols INFY,TCS,SBIN \\"
echo "    --from-date 2025-01-01 \\"
echo "    --to-date 2026-06-14"
echo ""

echo "✓ Fetch from symbols file:"
echo "  python3 $SCRIPTS_DIR/nse_historical_curl_fetcher.py \\"
echo "    --symbols-file ~/symbols.txt"
echo ""

echo "✓ Fetch from Cassandra (all symbols):"
echo "  python3 $SCRIPTS_DIR/nse_historical_curl_fetcher.py"
echo ""

echo "✓ Fetch with more retries (for flaky network):"
echo "  python3 $SCRIPTS_DIR/nse_historical_curl_fetcher.py \\"
echo "    --symbols INFY,TCS,SBIN \\"
echo "    --retry 5 \\"
echo "    --sleep-ms 200"
echo ""

# Documentation
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📖 DOCUMENTATION"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Full guides:"
echo "  - Cassandra: $SCRIPTS_DIR/CASSANDRA_SETUP.md"
echo "  - Schema Apply: $SCRIPTS_DIR/apply_cassandra_schema.sh"
echo "  - NSE Fetcher: $SCRIPTS_DIR/NSE_HISTORICAL_CURL_FETCHER.md"
echo "  - All Scripts: $SCRIPTS_DIR/README.md"
echo ""

# Troubleshooting
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🆘 TROUBLESHOOTING"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

echo "❓ Cassandra won't start?"
echo "   → Check disk space: df -h"
echo "   → Check if port 9042 is in use: lsof -i :9042"
echo "   → Restart: killall cassandra"
echo ""

echo "❓ NSE API returns 'cookie expired'?"
echo "   → Retry with more retries: --retry 3"
echo "   → Increase sleep between requests: --sleep-ms 200"
echo ""

echo "❓ 'Failed to resolve symbols'?"
echo "   → Provide symbols via CLI: --symbols INFY,TCS,SBIN"
echo "   → Or run setup_cassandra.sh + apply_cassandra_schema.sh first"
echo ""

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║                    ✅ You're Ready!                            ║"
echo "║                                                                ║"
echo "║  Next: Run setup_cassandra.sh, apply schema, then fetch data! ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

