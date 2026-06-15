# Investment Stock Market Scripts

Collection of Python and shell scripts for downloading NSE historical data and managing Cassandra database.

## 📋 Quick Reference

| Script | Purpose | Language |
|--------|---------|----------|
| [`setup_cassandra.sh`](#cassandra-setup) | Install Cassandra, create keyspace, run all CQL files | Bash |
| [`cassandra_status.sh`](#cassandra-status) | Check Cassandra status and keyspace info | Bash |
| [`nse_historical_curl_fetcher.py`](#nse-curl-fetcher) | Fetch NSE historical data via curl with auto cookie | Python |
| [`nse_historical_downloader.py`](#nse-downloader) | Alternative NSE downloader | Python |
| [`import_equity_l_to_cassandra.py`](#cassandra-import) | Import stock descriptions to Cassandra | Python |

---

## 🗄️ Cassandra Setup

### Quick Start: Install & Configure Cassandra

Automatically installs Cassandra, starts the daemon, creates the `realtime_stock_data` keyspace, and runs all 12 CQL files to create tables and types.

```bash
bash /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/setup_cassandra.sh
```

**What it does:**
- ✅ Installs Cassandra via Homebrew (macOS)
- ✅ Starts Cassandra daemon
- ✅ Creates `realtime_stock_data` keyspace
- ✅ Executes all CQL files:
  - Types: `daily_net_income`, `positional_daily_net_income_info`, etc.
  - Tables: `stock_description`, `stock_history`, `positions`, `sector_wise_stock_details`, etc.
- ✅ Verifies setup

**Full Documentation:** See [`CASSANDRA_SETUP.md`](./CASSANDRA_SETUP.md)

### Check Cassandra Status

```bash
bash /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/cassandra_status.sh
```

**Output:**
```
🔍 Cassandra Status Check
✅ Cassandra is running on localhost:9042
📂 Keyspaces: ...
✅ Keyspace 'realtime_stock_data' exists
📋 Tables: stock_description, stock_history, ...
```

---

## 🔍 NSE Historical Data Fetchers

### NSE Curl Fetcher (Recommended)

Fetch NSE historical stock data using **curl-based HTTP requests** with **automatic cookie generation and refresh**.

```bash
python3 /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/nse_historical_curl_fetcher.py \
  --symbols INFY,TCS,SBIN \
  --from-date 01-01-2025 \
  --to-date 14-06-2026
```

**Features:**
- ✅ Auto cookie generation (no pre-made cookie file needed)
- ✅ Auto cookie refresh on expiry
- ✅ JSON output (converts NSE CSV to structured JSON)
- ✅ reads symbols from Cassandra if not provided
- ✅ Batch processing with retry logic
- ✅ Standard library only (no pip install needed)

**Usage Examples:**

```bash
# From CLI symbols
python3 /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/nse_historical_curl_fetcher.py \
  --symbols INFY,TCS,SBIN,RELIANCE,HDFC

# From symbols file
python3 /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/nse_historical_curl_fetcher.py \
  --symbols-file ~/symbols.txt \
  --from-date 2025-01-01 \
  --to-date 2026-06-14

# From Cassandra (default)
python3 /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/nse_historical_curl_fetcher.py \
  --cassandra-host 192.168.1.100 \
  --cassandra-port 9042

# With custom retry/sleep settings
python3 /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/nse_historical_curl_fetcher.py \
  --symbols INFY,TCS,SBIN \
  --from-date 01-01-2025 \
  --to-date 14-06-2026 \
  --retry 2 \
  --sleep-ms 120
```

**Full Documentation:** See [`NSE_HISTORICAL_CURL_FETCHER.md`](./NSE_HISTORICAL_CURL_FETCHER.md)

**Output:**
```
Resolved 5 symbols | range=01-01-2025..14-06-2026 | series=EQ
[1/5] OK INFY -> /path/to/INFY-20250101-20260614.json
[2/5] OK TCS -> /path/to/TCS-20250101-20260614.json
[3/5] OK SBIN -> /path/to/SBIN-20250101-20260614.json
[4/5] FAIL RELIANCE: NSE response is not CSV (cookie/session may be expired)
[5/5] OK HDFC -> /path/to/HDFC-20250101-20260614.json
--- Summary ---
Success: 4
Failed:  1
```

---

### NSE Historical Downloader (Alternative)

Alternative Python downloader for NSE historical data (downloads CSV only).

```bash
python3 /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/nse_historical_downloader.py \
  --symbols INFY,TCS,SBIN
```

---

## 📥 Cassandra Import

### Import Stock Descriptions from EQUITY_L.csv

Import stock metadata from NSE's EQUITY_L.csv file into the Cassandra `stock_description` table.

```bash
python3 /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/import_equity_l_to_cassandra.py \
  --csv-file /path/to/EQUITY_L.csv \
  --cassandra-host localhost \
  --cassandra-port 9042
```

---

## 📂 Output Directories

All NSE historical data is saved to:
```
/Users/y0p03mn/preparation/investment-stock-market/investment/src/main/resources/historical-data/
```

**File Format:** JSON  
**Naming Pattern:** `{SYMBOL}-{YYYYMMDD}-{YYYYMMDD}.json`  
**Example:** `INFY-20250101-20260614.json`

---

## 🔧 Command-line Help

Get detailed options for any script:

```bash
python3 /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/nse_historical_curl_fetcher.py --help
python3 /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/import_equity_l_to_cassandra.py --help
```

---

## 📋 Common Workflows

### 1. Full Setup (First Time)

```bash
# 1. Install and configure Cassandra
bash ./setup_cassandra.sh

# 2. Import stock descriptions (if you have EQUITY_L.csv)
python3 ./import_equity_l_to_cassandra.py --csv-file ~/EQUITY_L.csv

# 3. Fetch historical data for specific symbols
python3 ./nse_historical_curl_fetcher.py --symbols INFY,TCS,SBIN --from-date 01-01-2025 --to-date 14-06-2026

# 4. Verify data was created
ls -lah ../src/main/resources/historical-data/
```

### 2. Quick Data Fetch

```bash
# Check if Cassandra is running
bash ./cassandra_status.sh

# Fetch data from specific symbols
python3 ./nse_historical_curl_fetcher.py --symbols INFY,TCS,SBIN
```

### 3. Batch Processing

```bash
# Create symbols file
cat > symbols.txt << EOF
INFY
TCS
SBIN
RELIANCE
HDFC
WIPRO
LT
MARUTI
TATAMOTORS
INFOSYS
EOF

# Fetch data with longer timeout and more retries
python3 ./nse_historical_curl_fetcher.py \
  --symbols-file symbols.txt \
  --from-date 01-01-2025 \
  --to-date 14-06-2026 \
  --retry 3 \
  --sleep-ms 200
```

---

## 🛠️ Troubleshooting

### Cassandra Issues

**Q: Cassandra won't start**
```bash
# Check disk space
df -h

# Check if port 9042 is in use
lsof -i :9042

# Check logs
tail -f /tmp/cassandra_setup.log
```

**Q: How do I stop Cassandra?**
```bash
killall cassandra
```

---

### Data Fetch Issues

**Q: "Failed to generate NSE cookie"**
- Check internet connection: `curl https://www.nseindia.com/`
- Ensure curl is installed: `curl --version`

**Q: "No symbols found to process"**
- Provide symbols via CLI: `--symbols INFY,TCS`
- Or ensure Cassandra is running and has stock_description table

---

## 📚 Documentation Files

- [`CASSANDRA_SETUP.md`](./CASSANDRA_SETUP.md) - Complete Cassandra setup guide
- [`NSE_HISTORICAL_CURL_FETCHER.md`](./NSE_HISTORICAL_CURL_FETCHER.md) - NSE curl fetcher detailed guide
- [`NSE_HISTORICAL_CURL_FETCHER.md`](./NSE_HISTORICAL_CURL_FETCHER.md) - NSE downloader documentation

---

## ✅ System Requirements

- **Python 3.6+** (for data fetchers)
- **Bash 4.0+** (for setup scripts)
- **macOS** (for Cassandra setup script; adapt for Linux)
- **curl** - for HTTP requests and status checks
- **cqlsh** - for Cassandra management (installed with Cassandra)
- **2GB+ RAM** - for Cassandra daemon
- **1-2GB disk space** - for Cassandra and historical data

---

## 📝 Notes

- All scripts use standard library only (no external pip dependencies required)
- Exit codes: `0` = success, `1` = partial failure, `2` = fatal error
- All data is saved in fixed directory: `/Users/y0p03mn/preparation/investment-stock-market/investment/src/main/resources/historical-data/`
- Cassandra keyspace: `realtime_stock_data`

---

**Last Updated:** June 15, 2026  
**Status:** ✅ Production Ready

