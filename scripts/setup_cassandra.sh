#!/bin/bash

################################################################################
# Cassandra Setup Script
#
# This script:
# 1. Installs Cassandra (macOS via Homebrew)
# 2. Starts Cassandra daemon
# 3. Waits for Cassandra to be ready
# 4. Creates keyspace and runs all CQL files to set up tables and types
#
# Usage:
#   bash /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/setup_cassandra.sh
#
################################################################################

set -e  # Exit on any error

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
CASSANDRA_HOST="localhost"
CASSANDRA_PORT="9042"
KEYSPACE="realtime_stock_data"
CASSANDRA_HOME="/usr/local/opt/cassandra"
CQL_DIR="/Users/y0p03mn/preparation/investment-stock-market/investment/src/main/resources"
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Logging functions
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

################################################################################
# 1. Check if running on macOS
################################################################################
check_os() {
    log_info "Checking OS..."
    if [[ "$OSTYPE" != "darwin"* ]]; then
        log_warning "This script is optimized for macOS. For Linux, please install Cassandra manually."
        log_info "Linux installation: https://cassandra.apache.org/doc/latest/cassandra/getting_started/installing.html"
        exit 1
    fi
    log_success "Running on macOS"
}

################################################################################
# 2. Install Homebrew if needed
################################################################################
install_homebrew() {
    if ! command -v brew &> /dev/null; then
        log_info "Homebrew not found. Installing..."
        /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
        log_success "Homebrew installed"
    else
        log_success "Homebrew already installed"
    fi
}

################################################################################
# 3. Install Cassandra
################################################################################
install_cassandra() {
    log_info "Checking if Cassandra is installed..."

    if command -v cassandra &> /dev/null; then
        CASSANDRA_VERSION=$(cassandra -v 2>/dev/null | head -1 || echo "unknown")
        log_success "Cassandra already installed: $CASSANDRA_VERSION"
        return
    fi

    log_info "Cassandra not found. Installing via Homebrew..."
    brew install cassandra
    log_success "Cassandra installed successfully"
}

################################################################################
# 4. Check if Cassandra is running
################################################################################
is_cassandra_running() {
    nc -z $CASSANDRA_HOST $CASSANDRA_PORT 2>/dev/null
    return $?
}

################################################################################
# 5. Start Cassandra daemon
################################################################################
start_cassandra() {
    log_info "Checking if Cassandra is running on $CASSANDRA_HOST:$CASSANDRA_PORT..."

    if is_cassandra_running; then
        log_success "Cassandra is already running"
        return
    fi

    log_info "Starting Cassandra daemon..."

    # Start Cassandra in background (output to log file)
    CASSANDRA_LOG="/tmp/cassandra_setup.log"
    cassandra -f > "$CASSANDRA_LOG" 2>&1 &
    CASSANDRA_PID=$!
    log_info "Cassandra PID: $CASSANDRA_PID"

    # Wait for Cassandra to be ready (up to 60 seconds)
    log_info "Waiting for Cassandra to be ready (timeout: 60s)..."
    MAX_RETRIES=60
    RETRY_COUNT=0

    while ! is_cassandra_running; do
        RETRY_COUNT=$((RETRY_COUNT + 1))
        if [ $RETRY_COUNT -gt $MAX_RETRIES ]; then
            log_error "Cassandra failed to start within 60 seconds"
            log_error "Check log: $CASSANDRA_LOG"
            exit 1
        fi
        sleep 1
    done

    log_success "Cassandra is ready (took ${RETRY_COUNT}s)"
}

################################################################################
# 6. Create keyspace
################################################################################
create_keyspace() {
    log_info "Creating keyspace '$KEYSPACE'..."

    cqlsh -e "
        CREATE KEYSPACE IF NOT EXISTS $KEYSPACE
        WITH replication = {
            'class': 'SimpleStrategy',
            'replication_factor': 1
        }
        AND durable_writes = true;
    " $CASSANDRA_HOST $CASSANDRA_PORT 2>/dev/null

    log_success "Keyspace '$KEYSPACE' created/verified"
}

################################################################################
# 7. List all CQL files
################################################################################
list_cql_files() {
    find "$CQL_DIR" -name "*.cql" -type f | sort
}

################################################################################
# 8. Execute all CQL files
################################################################################
execute_cql_files() {
    log_info "Executing CQL files from: $CQL_DIR"

    CQL_FILES=($(list_cql_files))

    if [ ${#CQL_FILES[@]} -eq 0 ]; then
        log_warning "No CQL files found in $CQL_DIR"
        return
    fi

    log_info "Found ${#CQL_FILES[@]} CQL files to execute"

    SUCCESS_COUNT=0
    FAILED_COUNT=0

    for CQL_FILE in "${CQL_FILES[@]}"; do
        FILENAME=$(basename "$CQL_FILE")
        log_info "Executing: $FILENAME"

        # Use 'cqlsh -f' to execute the file
        if cqlsh -k $KEYSPACE -f "$CQL_FILE" $CASSANDRA_HOST $CASSANDRA_PORT > /dev/null 2>&1; then
            log_success "✓ $FILENAME"
            ((SUCCESS_COUNT++))
        else
            log_error "✗ $FILENAME (check syntax or dependencies)"
            ((FAILED_COUNT++))
        fi
    done

    log_info ""
    log_info "CQL Execution Summary:"
    echo -e "${GREEN}  ✅ Success: $SUCCESS_COUNT${NC}"
    if [ $FAILED_COUNT -gt 0 ]; then
        echo -e "${RED}  ❌ Failed:  $FAILED_COUNT${NC}"
    fi

    if [ $FAILED_COUNT -gt 0 ]; then
        log_warning "Some CQL files failed. Manual review may be needed."
    fi
}

################################################################################
# 9. Verify setup
################################################################################
verify_setup() {
    log_info "Verifying Cassandra setup..."

    # Check keyspace exists
    KEYSPACES=$(cqlsh -e "DESCRIBE KEYSPACES;" $CASSANDRA_HOST $CASSANDRA_PORT 2>/dev/null | grep -i "$KEYSPACE" || echo "")

    if [ -z "$KEYSPACES" ]; then
        log_error "Keyspace '$KEYSPACE' not found!"
        return 1
    fi

    log_success "Keyspace '$KEYSPACE' exists"

    # Count tables in keyspace
    TABLE_COUNT=$(cqlsh -k $KEYSPACE -e "DESCRIBE TABLES;" $CASSANDRA_HOST $CASSANDRA_PORT 2>/dev/null | wc -l)
    log_success "Found approximately $TABLE_COUNT tables/types in keyspace"

    return 0
}

################################################################################
# 10. Print summary
################################################################################
print_summary() {
    echo ""
    echo "================================================================================"
    echo -e "${GREEN}Cassandra Setup Complete!${NC}"
    echo "================================================================================"
    echo ""
    echo "📊 Cassandra Details:"
    echo "  Host:      $CASSANDRA_HOST"
    echo "  Port:      $CASSANDRA_PORT"
    echo "  Keyspace:  $KEYSPACE"
    echo ""
    echo "🔗 Connection Examples:"
    echo "  cqlsh:     cqlsh $CASSANDRA_HOST $CASSANDRA_PORT"
    echo "  Python:    from cassandra.cluster import Cluster"
    echo "             cluster = Cluster(['$CASSANDRA_HOST'])"
    echo ""
    echo "📂 CQL Files Processed:"
    for CQL_FILE in $(list_cql_files); do
        echo "  ✓ $(basename "$CQL_FILE")"
    done
    echo ""
    echo "🛑 To stop Cassandra:"
    echo "  killall cassandra"
    echo ""
    echo "================================================================================"
}

################################################################################
# Main execution
################################################################################
main() {
    echo "================================================================================"
    echo "🚀 Cassandra Setup Script"
    echo "================================================================================"
    echo ""

    check_os
    install_homebrew
    install_cassandra
    start_cassandra
    create_keyspace
    execute_cql_files
    verify_setup
    print_summary

    log_success "All done! Cassandra is ready to use."
}

# Run main function
main "$@"

