#!/bin/bash

################################################################################
# Cassandra Schema Apply Script
#
# This script:
# 1. Accepts a keyspace name
# 2. Creates the keyspace if needed
# 3. Executes the schema CQL files in a fixed order
# 4. Verifies expected tables and types
#
# Usage:
#   bash /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/apply_cassandra_schema.sh --keyspace realtime_stock_data
#
################################################################################

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

CASSANDRA_HOST="localhost"
CASSANDRA_PORT="9042"
KEYSPACE=""
CQL_DIR="/Users/y0p03mn/preparation/investment-stock-market/investment/src/main/resources"
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
EXPECTED_TABLES=(
    nifty_fifty_details
    nifty_fifty_index_stock
    positional_aggregated_daily_income
    positional_daily_net_income
    positions
    sector_wise_stock_details
    stocks_daily_change
    stock_description
    stock_history
    stock_analysis_result
    stock_info_details
    stock_overview_details
)

log_info() {
    echo -e "${BLUE}ℹ️  INFO${NC}: $1"
}

log_success() {
    echo -e "${GREEN}✅ SUCCESS${NC}: $1"
}

log_warning() {
    echo -e "${YELLOW}⚠️  WARNING${NC}: $1"
}

log_error() {
    echo -e "${RED}❌ ERROR${NC}: $1"
}

usage() {
    cat <<EOF
Usage:
  bash $SCRIPT_DIR/apply_cassandra_schema.sh --keyspace <name> [--host localhost] [--port 9042] [--cql-dir path]

Options:
  --keyspace   Target keyspace name (required)
  --host       Cassandra host (default: localhost)
  --port       Cassandra port (default: 9042)
  --cql-dir    Directory containing the .cql schema files
  --help       Show this help message
EOF
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --keyspace)
                KEYSPACE="$2"
                shift 2
                ;;
            --host)
                CASSANDRA_HOST="$2"
                shift 2
                ;;
            --port)
                CASSANDRA_PORT="$2"
                shift 2
                ;;
            --cql-dir)
                CQL_DIR="$2"
                shift 2
                ;;
            --help|-h)
                usage
                exit 0
                ;;
            *)
                log_error "Unknown argument: $1"
                usage
                exit 1
                ;;
        esac
    done

    if [[ -z "$KEYSPACE" ]]; then
        log_error "--keyspace is required"
        usage
        exit 1
    fi
}

run_cqlsh() {
    cqlsh "$CASSANDRA_HOST" "$CASSANDRA_PORT" "$@"
}

check_prerequisites() {
    if ! command -v cqlsh >/dev/null 2>&1; then
        log_error "cqlsh is not installed or not in PATH"
        exit 1
    fi

    if ! command -v nc >/dev/null 2>&1; then
        log_error "nc is required to check Cassandra port status"
        exit 1
    fi

    if ! nc -z "$CASSANDRA_HOST" "$CASSANDRA_PORT" 2>/dev/null; then
        log_error "Cassandra is not reachable on $CASSANDRA_HOST:$CASSANDRA_PORT"
        log_info "Start it with: bash $SCRIPT_DIR/setup_cassandra.sh"
        exit 1
    fi

    if [[ ! -d "$CQL_DIR" ]]; then
        log_error "CQL directory not found: $CQL_DIR"
        exit 1
    fi
}

create_keyspace() {
    log_info "Creating keyspace '$KEYSPACE' if needed..."

    run_cqlsh <<EOF
CREATE KEYSPACE IF NOT EXISTS $KEYSPACE
WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}
AND durable_writes = true;
EOF

    log_success "Keyspace '$KEYSPACE' created/verified"
}

list_cql_files() {
    cat <<EOF
$CQL_DIR/daily_net_income.cql
$CQL_DIR/nift_fifty_details.cql
$CQL_DIR/nifty_fifty_index_stocks.cql
$CQL_DIR/positions.cql
$CQL_DIR/positional_aggregated_daily_income.cql
$CQL_DIR/sector_wise_stock_details.cql
$CQL_DIR/stock_daily_change.cql
$CQL_DIR/stock_description.cql
$CQL_DIR/stock_history.cql
$CQL_DIR/stock_analysis_result.cql
$CQL_DIR/stock_info_details.cql
$CQL_DIR/stock_overview_details.cql
EOF
}

execute_cql_files() {
    log_info "Executing schema files from: $CQL_DIR"

    local cql_files=()
    while IFS= read -r cql_file; do
        cql_files+=("$cql_file")
    done < <(list_cql_files)

    local success_count=0
    local failed_count=0

    for cql_file in "${cql_files[@]}"; do
        local filename
        filename=$(basename "$cql_file")

        if [[ ! -f "$cql_file" ]]; then
            log_error "Missing CQL file: $cql_file"
            failed_count=$((failed_count + 1))
            continue
        fi

        log_info "Executing: $filename"
        local cql_output
        cql_output=$(mktemp)

        if run_cqlsh -k "$KEYSPACE" -f "$cql_file" > "$cql_output" 2>&1; then
            log_success "✓ $filename"
            success_count=$((success_count + 1))
        else
            log_error "✗ $filename"
            sed 's/^/    /' "$cql_output"
            failed_count=$((failed_count + 1))
        fi

        rm -f "$cql_output"
    done

    log_info ""
    log_info "Schema execution summary:"
    echo -e "${GREEN}  ✅ Success: $success_count${NC}"
    if [[ "$failed_count" -gt 0 ]]; then
        echo -e "${RED}  ❌ Failed:  $failed_count${NC}"
        return 1
    fi
}

verify_schema() {
    log_info "Verifying schema in keyspace '$KEYSPACE'..."

    local keyspaces tables types table_count type_count
    keyspaces=$(run_cqlsh -e "DESCRIBE KEYSPACES;" 2>/dev/null | grep -i "$KEYSPACE" || true)
    if [[ -z "$keyspaces" ]]; then
        log_error "Keyspace '$KEYSPACE' not found after schema apply"
        return 1
    fi

    tables=$(run_cqlsh -k "$KEYSPACE" -e "DESCRIBE TABLES;" 2>/dev/null | tr '\n' ' ')
    types=$(run_cqlsh -k "$KEYSPACE" -e "DESCRIBE TYPES;" 2>/dev/null | tr '\n' ' ')

    for table_name in "${EXPECTED_TABLES[@]}"; do
        if [[ " $tables " != *" $table_name "* ]]; then
            log_error "Expected table missing: $table_name"
            return 1
        fi
    done

    if [[ -z "${types// }" ]]; then
        log_error "No user-defined types found in keyspace '$KEYSPACE'"
        return 1
    fi

    table_count=$(wc -w <<< "$tables" | tr -d ' ')
    type_count=$(wc -w <<< "$types" | tr -d ' ')
    log_success "Verified $table_count tables and $type_count types in keyspace '$KEYSPACE'"
}

print_summary() {
    echo ""
    echo "================================================================================"
    echo -e "${GREEN}Cassandra Schema Apply Complete!${NC}"
    echo "================================================================================"
    echo ""
    echo "📊 Schema Details:"
    echo "  Host:      $CASSANDRA_HOST"
    echo "  Port:      $CASSANDRA_PORT"
    echo "  Keyspace:  $KEYSPACE"
    echo ""
    echo "🔎 Check status:"
    echo "  bash $SCRIPT_DIR/cassandra_status.sh"
    echo ""
    echo "================================================================================"
}

main() {
    parse_args "$@"
    check_prerequisites
    create_keyspace
    execute_cql_files
    verify_schema
    print_summary
}

main "$@"

