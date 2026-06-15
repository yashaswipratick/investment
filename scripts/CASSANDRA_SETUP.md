# Cassandra Setup Guide

Split workflow for Cassandra: one script installs/starts Cassandra, and a separate script applies tables/types from the CQL files to a chosen keyspace.

## Quick Start

```bash
bash /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/setup_cassandra.sh

bash /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/apply_cassandra_schema.sh \
  --keyspace realtime_stock_data
```

The scripts will:
1. ✅ Install Cassandra (via Homebrew on macOS)
2. ✅ Start Cassandra daemon
3. ✅ Check that Cassandra is reachable on `localhost:9042`
4. ✅ Create the supplied keyspace
5. ✅ Execute the ordered schema CQL files to create tables and types
6. ✅ Verify the schema setup

## What the Scripts Do

### Step 1: Check OS
- Verifies you're running macOS
- Exits with instructions for Linux users

### Step 2: Install Homebrew (if needed)
- Checks if Homebrew is installed
- Installs it if not available

### Step 3: Install Cassandra (if needed)
- Checks if Cassandra is already installed
- Installs it via Homebrew: `brew install cassandra`
- Shows installed version

### Step 4: Start Cassandra Daemon
- Checks if Cassandra is already running on `localhost:9042`
- Starts Cassandra in background if needed
- Waits up to 60 seconds for Cassandra to be ready
- Confirms it's accepting connections

### Step 5: Apply Schema to a Keyspace
- Run `apply_cassandra_schema.sh --keyspace <name>`
- Creates the supplied keyspace if it doesn't exist
- Uses SimpleStrategy with replication_factor=1 (suitable for development)

### Step 6: Execute CQL Files
Runs the schema CQL files in a fixed order:
1. `daily_net_income.cql` - Types and tables for daily income tracking
2. `nift_fifty_details.cql` - Nifty 50 index details
3. `nifty_fifty_index_stocks.cql` - Stocks in Nifty 50
4. `positions.cql` - Investment positions
5. `positional_aggregated_daily_income.cql` - Aggregated daily income
6. `sector_wise_stock_details.cql` - Sector-wise stock information
7. `stock_daily_change.cql` - Daily stock price changes
8. `stock_description.cql` - Stock metadata and descriptions
9. `stock_history.cql` - Historical stock data
10. `stock_info_details.cql` - Stock info details
11. `stock_overview_details.cql` - Stock overview information

> Note: `stock_info_details_bkp.cql` is intentionally skipped because it conflicts with `stock_info_details.cql`.

### Step 7: Verify Setup
- Confirms the keyspace was created
- Counts tables/types in the keyspace

## Prerequisites

- macOS (10.13 or higher recommended)
- Internet connection (for Homebrew and Cassandra download)
- ~2GB disk space (Cassandra installation and data)
- Optional: `nc` (netcat) - usually pre-installed on macOS

## System Requirements

- **Memory**: Cassandra needs ~2GB RAM minimum (adjust if needed)
- **Port**: Uses `localhost:9042` by default
- **Disk**: ~1-2GB initial space

## CQL Files Processed

The script processes the schema CQL files from:
```
/Users/y0p03mn/preparation/investment-stock-market/investment/src/main/resources/
```

These files create:
- **Custom Types**: For complex data structures (e.g., positional_daily_net_income_info)
- **Tables**: For storing stock data, positions, income tracking, etc.

## Usage Examples

### Full Setup (Recommended)
```bash
bash /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/setup_cassandra.sh

bash /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/apply_cassandra_schema.sh \
  --keyspace realtime_stock_data
```

### Run with Debug Output
```bash
bash -x /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/setup_cassandra.sh
bash -x /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/apply_cassandra_schema.sh --keyspace realtime_stock_data
```

### Manual Steps (if needed)

Start Cassandra:
```bash
cassandra -f  # Run in foreground
cassandra     # Run in background
```

Connect with cqlsh:
```bash
cqlsh localhost 9042
```

Execute the full schema:
```bash
bash /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/apply_cassandra_schema.sh \
  --keyspace realtime_stock_data
```

Execute a single CQL file:
```bash
cqlsh -k realtime_stock_data -f /path/to/file.cql localhost 9042
```

## Configuration

Edit these variables in the scripts if you need to change them:

```bash
CASSANDRA_HOST="localhost"      # Cassandra server address
CASSANDRA_PORT="9042"           # Cassandra CQL port
KEYSPACE="realtime_stock_data"  # Keyspace name to create (schema script)
CQL_DIR="..."                   # Path to CQL files (schema script)
```

## Troubleshooting

### Issue: "Cassandra failed to start within 60 seconds"

**Solution**:
1. Check if another Cassandra instance is running:
   ```bash
   ps aux | grep cassandra
   ```
2. Check available memory: `top` or `Activity Monitor`
3. Increase timeout by editing the script (change `MAX_RETRIES=60`)
4. Check log file: `/tmp/cassandra_setup.log`

### Issue: "Some CQL files failed"

**Solution**:
1. Check for circular dependencies between types and tables
2. Run individual CQL file to see specific error:
   ```bash
   cqlsh -k realtime_stock_data -f /path/to/file.cql
   ```
3. Manually fix the CQL file if needed
4. Re-run the script

### Issue: "Port 9042 already in use"

**Solution**:
```bash
# Find process using port 9042
lsof -i :9042

# Kill the process
kill -9 <PID>
```

### Issue: Cassandra crashes after start

**Solution**:
1. Check `/tmp/cassandra_setup.log` for errors
2. Verify enough disk space: `df -h`
3. Try removing Cassandra data and reinstalling:
   ```bash
   rm -rf /usr/local/var/cassandra
   brew uninstall cassandra
   brew install cassandra
   ```

## Stopping Cassandra

To stop the Cassandra daemon:

```bash
# Kill all Cassandra processes
killall cassandra

# Or find and kill specific PID
ps aux | grep cassandra
kill -9 <PID>
```

## Checking Cassandra Status

```bash
# Check if running
nc -zv localhost 9042

# Or using cqlsh
cqlsh localhost 9042 -e "SELECT now() FROM system.local;"
```

## Connecting to Cassandra

### cqlsh (Command-line)
```bash
cqlsh localhost 9042
# Then in cqlsh:
USE realtime_stock_data;
DESCRIBE TABLES;
SELECT * FROM stock_description LIMIT 1;
```

### Python
```python
from cassandra.cluster import Cluster

cluster = Cluster(['localhost'])
session = cluster.connect('realtime_stock_data')
rows = session.execute('SELECT * FROM stock_description LIMIT 1')
for row in rows:
    print(row)
```

### Java/Scala
```java
Cluster cluster = Cluster.builder()
    .addContactPoint("localhost")
    .build();
Session session = cluster.connect("realtime_stock_data");
```

## Performance Tuning

### For Development (Default)
- SimpleStrategy replication
- replication_factor = 1
- Write consistency = ONE
- Read consistency = ONE

### For Production
Edit keyspace creation in script:
```cql
CREATE KEYSPACE realtime_stock_data
WITH replication = {
    'class': 'NetworkTopologyStrategy',
    'datacenter1': 3
};
```

## Uninstalling Cassandra

To completely remove Cassandra from your system:

```bash
# Stop Cassandra
killall cassandra

# Uninstall via Homebrew
brew uninstall cassandra

# Remove data directory (optional)
rm -rf ~/Library/Caches/cassandra
rm -rf /usr/local/var/cassandra

# Remove Cassandra user (optional)
sudo dscl . -delete /Users/cassandra
```

## Backup and Restore

### Backup Data
```bash
nodetool snapshot -t my_backup realtime_stock_data
```

### List Snapshots
```bash
nodetool listsnapshots
```

### Restore Data
```bash
nodetool restore -from_backup /path/to/backup
```

## Monitoring

### Check Cassandra Logs
```bash
# If running in foreground
# Logs appear in terminal

# If running in background
tail -f /tmp/cassandra_setup.log

# System logs (macOS)
log show --predicate 'process == "cassandra"' --last 1h
```

### Monitor Performance
```bash
# Connection count
cqlsh localhost 9042 -e "SELECT count(*) FROM system_traces.sessions;"

# Check node status
nodetool status

# Check disk usage
nodetool disk usage realtime_stock_data
```

## Automation

Schedule Cassandra startup on system boot (optional):

### Create LaunchAgent (macOS)

Create `~/Library/LaunchAgents/com.cassandra.startup.plist`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.cassandra.startup</string>
    <key>ProgramArguments</key>
    <array>
        <string>bash</string>
        <string>/Users/y0p03mn/preparation/investment-stock-market/investment/scripts/setup_cassandra.sh</string>
    </array>
    <key>RunAtLoad</key>
    <true/>
</dict>
</plist>
```

Then load it:
```bash
launchctl load ~/Library/LaunchAgents/com.cassandra.startup.plist
```

## FAQ

**Q: Can I run this script multiple times safely?**  
A: Yes! The script uses `CREATE ... IF NOT EXISTS`, so it's idempotent.

**Q: How much disk space does Cassandra use?**  
A: Initial installation is ~500MB. Data size depends on your dataset.

**Q: Can I use a different keyspace name?**  
A: Yes, pass it at runtime: `bash apply_cassandra_schema.sh --keyspace my_keyspace`.

**Q: Does this work on Linux?**  
A: This script is macOS-only. For Linux, install Cassandra manually or adapt the script.

**Q: Can I run Cassandra on a different port?**  
A: Yes, edit `CASSANDRA_PORT="9042"` in the script. Also update `cassandra.yaml` after installation.

## See Also

- [Cassandra Official Documentation](https://cassandra.apache.org/doc/)
- [apply_cassandra_schema.sh](./apply_cassandra_schema.sh) - Apply tables/types to a chosen keyspace
- [nse_historical_curl_fetcher.py](./nse_historical_curl_fetcher.py) - Data fetcher that uses this Cassandra setup
- [NSE_HISTORICAL_CURL_FETCHER.md](./NSE_HISTORICAL_CURL_FETCHER.md) - NSE data fetcher documentation

---

**Last Updated**: June 15, 2026  
**Status**: ✅ Production Ready

