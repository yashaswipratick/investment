# NSE Historical JSON Fetcher (Curl-Based)

Download NSE historical stock price data using curl-style HTTP requests with automatic cookie generation and refresh. Response is saved as JSON format.

## Features

- **Auto Cookie Generation**: Generates NSE session cookies inside the script (no pre-generated cookie file needed)
- **Cookie Refresh**: Automatically refreshes expired cookies on retry
- **JSON Output**: Converts NSE CSV response to structured JSON format with metadata
- **Fixed Output Directory**: All files saved to `/Users/y0p03mn/preparation/investment-stock-market/investment/src/main/resources/historical-data/`
- **Cassandra Integration**: Reads symbols from `realtime_stock_data.stock_description` table if symbols are not provided
- **Flexible Symbol Input**: Accept symbols via CLI, file, or auto-load from Cassandra
- **Batch Processing**: Downloads data for multiple symbols with configurable pause between requests
- **Error Handling**: Retries failed downloads with automatic cookie refresh
- **Standard Library Only**: No pip dependencies needed

## Prerequisites

1. **Python 3.6+**: Standard library only (no external packages required)
2. **curl**: Must be available in PATH (used for HTTP requests and cookie extraction)
3. **cqlsh** (optional): Only needed if symbols will be loaded from Cassandra
4. **Cassandra Running** (optional): Only if using `--cassandra-*` flags for symbol resolution

## Installation

No installation needed. Just ensure Python 3 and curl are available:

```bash
python3 --version
curl --version
```

## Usage

### Scenario 1: Auto-load symbols from Cassandra (Default)

Reads all symbols from `realtime_stock_data.stock_description` table and downloads historical data for each symbol. Files are saved to `/Users/y0p03mn/preparation/investment-stock-market/investment/src/main/resources/historical-data/`

```bash
python3 /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/nse_historical_curl_fetcher.py
```


### Scenario 2: Provide symbols via CLI (Comma-separated)

```bash
python3 /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/nse_historical_curl_fetcher.py \
  --symbols INFY,TCS,SBIN,RELIANCE,HDFC
```

### Scenario 3: Provide symbols via file (One per line)

Create a file `symbols.txt`:
```
INFY
TCS
SBIN
RELIANCE
HDFC
```

Then run:
```bash
python3 /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/nse_historical_curl_fetcher.py \
  --symbols-file /path/to/symbols.txt
```

### Scenario 4: Custom date range

```bash
python3 /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/nse_historical_curl_fetcher.py \
  --symbols INFY,TCS \
  --from-date 01-01-2025 \
  --to-date 14-06-2026
```

### Scenario 5: Custom Cassandra connection

```bash
python3 /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/nse_historical_curl_fetcher.py \
  --cassandra-host 192.168.1.100 \
  --cassandra-port 9042 \
  --keyspace realtime_stock_data \
  --table stock_description
```


## Command-line Options

```
--symbols SYMBOLS
    Comma-separated stock symbols (e.g., INFY,TCS,SBIN)
    If provided, overrides symbols-file and Cassandra lookup.

--symbols-file SYMBOLS_FILE
    Path to file with one symbol per line (comments starting with #).
    Used if --symbols is not provided.

--series SERIES
    Series code (default: EQ)
    Common values: EQ, BE, BL, BO, etc.

--from-date FROM_DATE
    Start date in dd-mm-yyyy format (default: 1 year ago)

--to-date TO_DATE
    End date in dd-mm-yyyy format (default: today)


--retry RETRY
    Number of retries per symbol when request fails (default: 2)

--sleep-ms SLEEP_MS
    Milliseconds to pause between symbol downloads (default: 120)

--cassandra-host CASSANDRA_HOST
    Cassandra host address (default: localhost)

--cassandra-port CASSANDRA_PORT
    Cassandra port (default: 9042)

--keyspace KEYSPACE
    Cassandra keyspace (default: realtime_stock_data)

--table TABLE
    Cassandra table name (default: stock_description)
```

## Output

### JSON File Naming

Files are saved with naming pattern: `{symbol}-{YYYYMMDD}-{YYYYMMDD}.json`

Example: `INFY-20250614-20260614.json`

### JSON Structure

Each JSON file contains:
- `symbol`: Stock symbol
- `from_date`: Start date (dd-mm-yyyy format)
- `to_date`: End date (dd-mm-yyyy format)
- `record_count`: Number of records fetched
- `data`: Array of price records with fields from NSE (Date, Open, High, Low, Close, Volume, etc.)

**Example**:
```json
{
  "symbol": "INFY",
  "from_date": "14-06-2025",
  "to_date": "14-06-2026",
  "record_count": 252,
  "data": [
    {
      "Date": "14-06-2026",
      "Open": "1850.50",
      "High": "1865.85",
      "Low": "1840.20",
      "Close": "1858.00",
      "Volume": "2850000"
    },
    ...
  ]
}
```

### Console Output

```
Resolved 5 symbols
[1/5] OK INFY -> /path/to/INFY-20250614-20260614.json
[2/5] OK TCS -> /path/to/TCS-20250614-20260614.json
[3/5] OK SBIN -> /path/to/SBIN-20250614-20260614.json
[4/5] FAIL RELIANCE: curl failed for RELIANCE: Failed to download after retries
[5/5] OK HDFC -> /path/to/HDFC-20250614-20260614.json
--- Summary ---
Success: 4
Failed:  1
```

## How It Works

### 1. Cookie Generation
- Calls `curl` to warm up NSE session: `GET https://www.nseindia.com/`
- Parses `Set-Cookie` response headers
- Merges all cookies into a single `Cookie:` header

### 2. Symbol Resolution (Priority Order)
1. If `--symbols` provided → use those
2. Else if `--symbols-file` provided → read from file
3. Else → query Cassandra via `cqlsh SELECT symbol FROM {keyspace}.{table};`

### 3. CSV Fetch & JSON Conversion
- For each symbol:
  - Build NSE API URL: `https://www.nseindia.com/api/NextApi/apiClient/GetQuoteApi?functionName=getHistoricalTradeData&symbol={symbol}&series={series}&fromDate={fromDate}&toDate={toDate}&csv=true`
  - Call `curl` with all required headers (User-Agent, Referer, Cookie, etc.)
  - NSE returns CSV response
  - Validate response looks like CSV (contains commas and newlines, not HTML)
  - Parse CSV with Python's `csv.DictReader` (headers become object keys)
  - Convert to JSON with metadata (symbol, dates, record count, data array)
  - Save as `{symbol}-{YYYYMMDD}-{YYYYMMDD}.json`
  - If validation fails → refresh cookie and retry

### 4. Error Handling
- On HTTP error or invalid CSV response → refresh cookie and retry up to `--retry` times
- Failed symbols are logged but don't stop the batch
- Exit code: 0 if all succeed, 1 if any fail

## Examples

### Download Nifty 50 companies for past 1 year

```bash
python3 /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/nse_historical_curl_fetcher.py \
  --symbols "RELIANCE,TCS,HDFCBANK,ICICIBANK,SBIN,BHARTIARTL,INFY,LT,MARUTI,BAJAJ-AUTO,WIPRO,AXISBANK,DMARUTI,POWERGRID,HCLTECH,PIDILITIND,BRITANNIA,TATAMOTORS,NESTLEIND,KOTAKBANK"
```

### Download from all Cassandra symbols with custom dates

```bash
python3 /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/nse_historical_curl_fetcher.py \
  --from-date 01-01-2024 \
  --to-date 31-12-2024 \
  --sleep-ms 200
```

### Download symbols from file with more retries

```bash
python3 /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/nse_historical_curl_fetcher.py \
  --symbols-file ~/my_symbols.txt \
  --retry 5 \
  --sleep-ms 150
```

### Download multiple symbols with custom date range and retry settings

Downloads historical data for INFY, TCS, and SBIN from January 1, 2025 to June 14, 2026 with 2 retries and 120ms pause between symbols:

```bash
python3 /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/nse_historical_curl_fetcher.py \
  --symbols INFY,TCS,SBIN \
  --from-date 01-01-2025 \
  --to-date 14-06-2026 \
  --retry 2 \
  --sleep-ms 120
```

### Download from symbols file with yyyy-mm-dd date format

Reads symbols from a file and accepts date in `yyyy-mm-dd` format (auto-converted to dd-mm-yyyy internally):

```bash
python3 /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/nse_historical_curl_fetcher.py \
  --symbols-file /absolute/path/symbols.txt \
  --from-date 2025-01-01 \
  --to-date 2026-06-14
```

## Troubleshooting

### Error: "Failed to resolve symbols: cqlsh failed"

**Cause**: Cassandra is not running or cqlsh is not installed  
**Solution**:
1. Start Cassandra: `cassandra -f` (or `cassandra` in background)
2. Or provide symbols via CLI: `--symbols INFY,TCS`

### Error: "Failed to generate NSE cookie"

**Cause**: NSE website is unreachable or curl is not installed  
**Solution**:
1. Check internet connection: `curl https://www.nseindia.com/`
2. Ensure curl is installed: `curl --version`

### Error: "Batch 1 failed: curl failed for {SYMBOL}"

**Cause**: NSE API returned an error or cookie expired  
**Solution**:
- Script will automatically retry with refreshed cookie (up to `--retry` times)
- If still fails, NSE might be blocking the request (check IP/headers)

### No JSON files created

**Cause**: Symbols list might be empty or all symbols failed  
**Solution**:
1. Check if symbols resolved correctly: `python3 ... --symbols INFY` (test with 1 symbol)
2. Check output directory permissions: `ls -la /Users/y0p03mn/preparation/investment-stock-market/investment/src/main/resources/historical-data`

## Performance Tips

- Use `--sleep-ms 50` for faster downloads (default 120ms is safe)
- Use `--retry 1` if you're confident cookies won't expire, (default 2)
- Download symbols in parallel (run multiple instances with different symbol ranges)

## Exit Codes

| Code | Meaning |
|------|---------|
| 0 | All symbols downloaded successfully |
| 1 | At least one symbol failed to download |
| 2 | Failed to resolve symbols or generate cookie |

## Limitations

- **Single-threaded**: Downloads one symbol at a time (by design, to avoid NSE rate limiting)
- **NSE Session**: Cookie is refreshed per script run; each symbol uses the same session
- **CSV format**: Expects NSE to return valid CSV; invalid responses trigger retry

## See Also

- [nse_historical_downloader.py](./nse_historical_downloader.py) - Alternative downloader (downloads CSV only)
- [import_equity_l_to_cassandra.py](./import_equity_l_to_cassandra.py) - Import stock descriptions from EQUITY_L.csv
- README.md (parent) - Overview of all scripts

