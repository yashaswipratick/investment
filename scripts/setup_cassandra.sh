#!/bin/bash

################################################################################
# Cassandra Install + Start Script
#
# This script:
# 1. Installs Cassandra (macOS via Homebrew)
# 2. Starts Cassandra daemon if needed
# 3. Waits for Cassandra to be ready
# 4. Shows status / next steps
#
# Usage:
#   bash /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/setup_cassandra.sh
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
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
CASSANDRA_LOG="/tmp/cassandra_setup.log"

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

check_os() {
    log_info "Checking OS..."
    if [[ "$OSTYPE" != "darwin"* ]]; then
        log_warning "This script is optimized for macOS. For Linux, install/start Cassandra manually."
        log_info "Linux installation: https://cassandra.apache.org/doc/latest/cassandra/getting_started/installing.html"
        exit 1
    fi
    log_success "Running on macOS"
}

install_homebrew() {
    if ! command -v brew >/dev/null 2>&1; then
        log_info "Homebrew not found. Installing..."
        /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
        log_success "Homebrew installed"
    else
        log_success "Homebrew already installed"
    fi
}

install_cassandra() {
    log_info "Checking if Cassandra is installed..."

    if command -v cassandra >/dev/null 2>&1; then
        local version
        version=$(cassandra -v 2>/dev/null | head -1 || echo "unknown")
        log_success "Cassandra already installed: $version"
        return
    fi

    log_info "Cassandra not found. Installing via Homebrew..."
    brew install cassandra
    log_success "Cassandra installed successfully"
}

is_cassandra_running() {
    nc -z "$CASSANDRA_HOST" "$CASSANDRA_PORT" 2>/dev/null
}

start_cassandra() {
    log_info "Checking if Cassandra is running on $CASSANDRA_HOST:$CASSANDRA_PORT..."

    if is_cassandra_running; then
        log_success "Cassandra is already running"
        return
    fi

    log_info "Starting Cassandra daemon..."
    cassandra -f > "$CASSANDRA_LOG" 2>&1 &
    local cassandra_pid=$!
    log_info "Cassandra PID: $cassandra_pid"
}

wait_for_cassandra() {
    log_info "Waiting for Cassandra to be ready (timeout: 60s)..."
    local max_retries=60
    local retry_count=0

    while ! is_cassandra_running; do
        retry_count=$((retry_count + 1))
        if [ "$retry_count" -gt "$max_retries" ]; then
            log_error "Cassandra failed to start within 60 seconds"
            log_error "Check log: $CASSANDRA_LOG"
            exit 1
        fi
        sleep 1
    done

    log_success "Cassandra is ready (took ${retry_count}s)"
}

print_summary() {
    echo ""
    echo "================================================================================"
    echo -e "${GREEN}Cassandra Install/Start Complete!${NC}"
    echo "================================================================================"
    echo ""
    echo "📊 Cassandra Details:"
    echo "  Host:      $CASSANDRA_HOST"
    echo "  Port:      $CASSANDRA_PORT"
    echo "  Status:    RUNNING"
    echo ""
    echo "🧩 Next step: apply schema to a keyspace"
    echo "  bash $SCRIPT_DIR/apply_cassandra_schema.sh --keyspace realtime_stock_data"
    echo ""
    echo "🔎 Status check:"
    echo "  bash $SCRIPT_DIR/cassandra_status.sh"
    echo ""
    echo "🛑 To stop Cassandra:"
    echo "  killall cassandra"
    echo ""
    echo "================================================================================"
}

main() {
    echo "================================================================================"
    echo "🚀 Cassandra Install/Start Script"
    echo "================================================================================"
    echo ""

    check_os
    install_homebrew
    install_cassandra
    start_cassandra
    wait_for_cassandra
    bash "$SCRIPT_DIR/cassandra_status.sh"
    print_summary

    log_success "Cassandra is up. Apply schema with apply_cassandra_schema.sh."
}

main "$@"

