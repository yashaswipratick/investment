# NSE Historical CSV Downloader

Use `nse_historical_downloader.py` to fetch NSE historical CSV for stock symbols.

Default output directory:
`/Users/y0p03mn/preparation/investment-stock-market/investment/src/main/resources/historical-data`

## Run

```bash
python3 /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/nse_historical_downloader.py --symbols INFY,TCS,SBIN
```

## Optional

```bash
python3 /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/nse_historical_downloader.py \
  --symbols-file /path/to/symbols.txt \
  --from-date 01-01-2025 \
  --to-date 14-06-2026 \
  --output-dir /Users/y0p03mn/preparation/investment-stock-market/investment/src/main/resources/historical-data
```

## Curl-based Downloader (Auto Cookie + Cassandra Symbols)

Use `nse_historical_curl_fetcher.py` when you want to:
- call NSE historical API through curl-style headers,
- auto-generate and refresh cookie inside the script,
- auto-load symbols from Cassandra table `realtime_stock_data.stock_description` if symbols are not passed.

```bash
python3 /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/nse_historical_curl_fetcher.py
```

```bash
python3 /Users/y0p03mn/preparation/investment-stock-market/investment/scripts/nse_historical_curl_fetcher.py \
  --symbols INFY,TCS,SBIN \
  --from-date 14-06-2025 \
  --to-date 14-06-2026 \
  --output-dir /Users/y0p03mn/preparation/investment-stock-market/investment/src/main/resources/historical-data
```

## Notes

- Uses standard library only (no pip install needed).
- Script warms NSE session and retries transient errors.
- Exit code is non-zero if any symbol fails.
