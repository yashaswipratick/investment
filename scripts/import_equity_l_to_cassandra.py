#!/usr/bin/env python3
"""Import NSE EQUITY_L.csv rows into Cassandra via stockDescription import API."""

from __future__ import annotations

import argparse
import csv
import json
import sys
import time
import urllib.error
import urllib.request
from typing import Dict, Iterable, List


DEFAULT_CSV_PATH = "/Users/y0p03mn/Downloads/EQUITY_L.csv"
DEFAULT_API_URL = "http://localhost:8080/stock/investment/v1.0/stockDescription/import"


def parse_int(value: str) -> int:
    text = (value or "").strip().replace(",", "")
    if not text:
        return 0
    return int(float(text))


def parse_float(value: str) -> float:
    text = (value or "").strip().replace(",", "")
    if not text:
        return 0.0
    return float(text)


def read_rows(csv_path: str) -> List[Dict[str, object]]:
    rows: List[Dict[str, object]] = []
    with open(csv_path, "r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        for row in reader:
            symbol = (row.get("SYMBOL") or "").strip()
            if not symbol:
                continue
            rows.append(
                {
                    "symbol": symbol,
                    "companyName": (row.get("NAME OF COMPANY") or "").strip(),
                    "series": (row.get("SERIES") or "").strip(),
                    "dateOfListing": (row.get(" DATE OF LISTING") or row.get("DATE OF LISTING") or "").strip(),
                    "paidUpValue": parse_float(row.get(" PAID UP VALUE") or row.get("PAID UP VALUE") or ""),
                    "marketLot": parse_int(row.get(" MARKET LOT") or row.get("MARKET LOT") or ""),
                    "isInNumber": (row.get(" ISIN NUMBER") or row.get("ISIN NUMBER") or "").strip(),
                    "faceValue": parse_int(row.get(" FACE VALUE") or row.get("FACE VALUE") or ""),
                }
            )
    return rows


def chunks(items: List[Dict[str, object]], batch_size: int) -> Iterable[List[Dict[str, object]]]:
    for start in range(0, len(items), batch_size):
        yield items[start : start + batch_size]


def post_batch(api_url: str, batch: List[Dict[str, object]], timeout_sec: int) -> Dict[str, object]:
    payload = json.dumps(batch).encode("utf-8")
    request = urllib.request.Request(
        api_url,
        data=payload,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=timeout_sec) as response:
        raw = response.read().decode("utf-8")
        return json.loads(raw) if raw else {}


def main() -> int:
    parser = argparse.ArgumentParser(description="Import NSE EQUITY_L.csv into Cassandra via API")
    parser.add_argument("--csv-path", default=DEFAULT_CSV_PATH, help="Path to EQUITY_L.csv")
    parser.add_argument("--api-url", default=DEFAULT_API_URL, help="Import API URL")
    parser.add_argument("--batch-size", type=int, default=300, help="Rows per request")
    parser.add_argument("--timeout-sec", type=int, default=30, help="HTTP request timeout")
    parser.add_argument("--sleep-ms", type=int, default=100, help="Pause between batches")
    args = parser.parse_args()

    if args.batch_size <= 0:
        print("batch-size must be > 0", file=sys.stderr)
        return 2

    rows = read_rows(args.csv_path)
    if not rows:
        print("No rows found in CSV. Nothing to import.")
        return 0

    print(f"Loaded {len(rows)} rows from {args.csv_path}")

    total_saved = 0
    total_requested = 0
    failures = 0

    for index, batch in enumerate(chunks(rows, args.batch_size), start=1):
        try:
            result = post_batch(args.api_url, batch, args.timeout_sec)
            requested = int(result.get("requestedCount", len(batch)))
            saved = int(result.get("savedCount", 0))
            total_requested += requested
            total_saved += saved
            print(f"Batch {index}: requested={requested}, saved={saved}")
        except urllib.error.HTTPError as exc:
            failures += 1
            body = exc.read().decode("utf-8", errors="ignore")
            print(f"Batch {index} failed: HTTP {exc.code} {body}", file=sys.stderr)
        except urllib.error.URLError as exc:
            failures += 1
            print(f"Batch {index} failed: URL error {exc.reason}", file=sys.stderr)
        except Exception as exc:  # noqa: BLE001
            failures += 1
            print(f"Batch {index} failed: {exc}", file=sys.stderr)

        if args.sleep_ms > 0:
            time.sleep(args.sleep_ms / 1000.0)

    print("--- Import Summary ---")
    print(f"Total requested: {total_requested}")
    print(f"Total saved:     {total_saved}")
    print(f"Failed batches:  {failures}")

    return 1 if failures > 0 else 0


if __name__ == "__main__":
    raise SystemExit(main())

