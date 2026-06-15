#!/usr/bin/env python3
"""Download NSE historical CSV via curl and save as JSON.

- Uses provided NSE NextApi historical endpoint pattern.
- Auto-generates/refreshes NSE cookie inside this script.
- If symbols are not provided, reads symbols from Cassandra `stock_description` table.
- Converts CSV response to JSON format.
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import json
import os
import re
import subprocess
import sys
import time
from io import StringIO
from typing import Iterable, List
from urllib.parse import quote


NSE_BASE = "https://www.nseindia.com"
DEFAULT_OUTPUT_DIR = "/Users/y0p03mn/preparation/investment-stock-market/investment/src/main/resources/historical-data"
USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"
)


def run_cmd(args: List[str], timeout_sec: int = 60) -> subprocess.CompletedProcess:
    return subprocess.run(args, capture_output=True, text=True, timeout=timeout_sec)


def default_dates() -> tuple[str, str]:
    today = dt.date.today()
    one_year_back = today - dt.timedelta(days=365)
    return one_year_back.strftime("%d-%m-%Y"), today.strftime("%d-%m-%Y")


def parse_symbols_arg(raw: str) -> List[str]:
    symbols = [s.strip().upper() for s in raw.split(",") if s.strip()]
    return list(dict.fromkeys(symbols))


def normalize_date(raw: str) -> str:
    raw = raw.strip()
    for fmt in ("%d-%m-%Y", "%d/%m/%Y", "%Y-%m-%d"):
        try:
            return dt.datetime.strptime(raw, fmt).strftime("%d-%m-%Y")
        except ValueError:
            continue
    raise ValueError(
        f"Invalid date '{raw}'. Use dd-mm-yyyy, dd/mm/yyyy, or yyyy-mm-dd format"
    )


def read_symbols_file(path: str) -> List[str]:
    symbols: List[str] = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            s = line.strip().upper()
            if s and not s.startswith("#"):
                symbols.append(s)
    return list(dict.fromkeys(symbols))


def get_symbols_from_cassandra(host: str, port: int, keyspace: str, table: str) -> List[str]:
    query = f"SELECT symbol FROM {keyspace}.{table};"
    cmd = ["cqlsh", host, str(port), "-e", query]
    proc = run_cmd(cmd, timeout_sec=120)
    if proc.returncode != 0:
        raise RuntimeError(f"cqlsh failed: {proc.stderr.strip() or proc.stdout.strip()}")

    symbols: List[str] = []
    for raw_line in proc.stdout.splitlines():
        line = raw_line.strip()
        if not line:
            continue
        if line.lower() == "symbol":
            continue
        if set(line) <= {"-"}:
            continue
        if line.startswith("(") and "rows" in line:
            continue
        if "|" in line:
            line = line.split("|", 1)[0].strip()
        if re.fullmatch(r"[A-Za-z0-9_.&-]+", line):
            symbols.append(line.upper())

    # Deduplicate while keeping order
    deduped = list(dict.fromkeys(symbols))
    return deduped


def refresh_cookie() -> str:
    cmd = [
        "curl",
        "--silent",
        "--show-error",
        "--location",
        "--dump-header",
        "-",
        "--output",
        "/dev/null",
        f"{NSE_BASE}/",
        "--header",
        "Accept: */*",
        "--header",
        "Accept-Language: en-GB,en;q=0.9",
        "--header",
        f"User-Agent: {USER_AGENT}",
    ]
    proc = run_cmd(cmd)
    if proc.returncode != 0:
        raise RuntimeError(f"Cookie warm-up failed: {proc.stderr.strip() or proc.stdout.strip()}")

    cookie_pairs: List[str] = []
    seen = set()
    for line in proc.stdout.splitlines():
        if not line.lower().startswith("set-cookie:"):
            continue
        value = line.split(":", 1)[1].strip()
        pair = value.split(";", 1)[0].strip()
        if not pair or "=" not in pair:
            continue
        name = pair.split("=", 1)[0].strip()
        if name in seen:
            continue
        seen.add(name)
        cookie_pairs.append(pair)

    if not cookie_pairs:
        raise RuntimeError("Cookie warm-up succeeded but no Set-Cookie headers were found")

    return "; ".join(cookie_pairs)


def build_url(symbol: str, series: str, from_date: str, to_date: str) -> str:
    return (
        f"{NSE_BASE}/api/NextApi/apiClient/GetQuoteApi?"
        f"functionName=getHistoricalTradeData&symbol={quote(symbol)}&series={quote(series)}"
        f"&fromDate={quote(from_date)}&toDate={quote(to_date)}&csv=true"
    )


def fetch_csv(symbol: str, series: str, from_date: str, to_date: str, cookie: str) -> bytes:
    url = build_url(symbol, series, from_date, to_date)
    referer = f"{NSE_BASE}/get-quote/equity/{quote(symbol)}"

    cmd = [
        "curl",
        "--silent",
        "--show-error",
        "--location",
        "--globoff",
        "--connect-timeout",
        "15",
        "--max-time",
        "45",
        url,
        "--header",
        "Accept: */*",
        "--header",
        "Accept-Language: en-GB,en;q=0.9",
        "--header",
        "Connection: keep-alive",
        "--header",
        f"Referer: {referer}",
        "--header",
        "Sec-Fetch-Dest: empty",
        "--header",
        "Sec-Fetch-Mode: cors",
        "--header",
        "Sec-Fetch-Site: same-origin",
        "--header",
        f"User-Agent: {USER_AGENT}",
        "--header",
        'sec-ch-ua: "Google Chrome";v="149", "Chromium";v="149", "Not)A;Brand";v="24"',
        "--header",
        "sec-ch-ua-mobile: ?0",
        "--header",
        'sec-ch-ua-platform: "macOS"',
        "--header",
        f"Cookie: {cookie}",
    ]

    proc = subprocess.run(cmd, capture_output=True)
    if proc.returncode != 0:
        stderr = proc.stderr.decode("utf-8", errors="ignore")
        raise RuntimeError(f"curl failed for {symbol}: {stderr.strip()}")
    return proc.stdout


def looks_like_csv(body: bytes) -> bool:
    text = body[:1200].decode("utf-8", errors="ignore").lower()
    if not text.strip():
        return False
    bad_markers = ("<html", "<!doctype", "access denied", "unauthorized", "forbidden")
    if any(m in text for m in bad_markers):
        return False
    if "," not in text or "\n" not in text:
        return False
    return True


def ensure_dir(path: str) -> None:
    os.makedirs(path, exist_ok=True)


def save_json(output_dir: str, symbol: str, from_date: str, to_date: str, csv_body: str) -> str:
    """Convert CSV response to JSON and save.

    Args:
        output_dir: Directory to save JSON file
        symbol: Stock symbol
        from_date: Start date in dd-mm-yyyy format
        to_date: End date in dd-mm-yyyy format
        csv_body: CSV response as string

    Returns:
        Path to saved JSON file
    """
    safe_from = from_date.replace("-", "")
    safe_to = to_date.replace("-", "")
    filename = f"{symbol}-{safe_from}-{safe_to}.json"
    out_path = os.path.join(output_dir, filename)

    # Parse CSV and convert to JSON
    try:
        reader = csv.DictReader(StringIO(csv_body))
        rows = list(reader)

        json_data = {
            "symbol": symbol,
            "from_date": from_date,
            "to_date": to_date,
            "record_count": len(rows),
            "data": rows
        }

        with open(out_path, "w", encoding="utf-8") as f:
            json.dump(json_data, f, indent=2)
    except Exception as exc:
        raise RuntimeError(f"Failed to convert CSV to JSON for {symbol}: {exc}")

    return out_path


def resolve_symbols(args: argparse.Namespace) -> List[str]:
    if args.symbols:
        symbols = parse_symbols_arg(args.symbols)
        if symbols:
            return symbols

    if args.symbols_file:
        symbols = read_symbols_file(args.symbols_file)
        if symbols:
            return symbols

    return get_symbols_from_cassandra(args.cassandra_host, args.cassandra_port, args.keyspace, args.table)


def main() -> int:
    d_from, d_to = default_dates()

    parser = argparse.ArgumentParser(description="Fetch NSE historical CSV using curl + auto cookie refresh")
    parser.add_argument("--symbols", help="Comma-separated symbols, e.g. INFY,TCS,SBIN")
    parser.add_argument("--symbols-file", help="Optional file with one symbol per line")
    parser.add_argument("--series", default="EQ", help="Series value (default: EQ)")
    parser.add_argument("--from-date", default=d_from, help="From date in dd-mm-yyyy")
    parser.add_argument("--to-date", default=d_to, help="To date in dd-mm-yyyy")
    parser.add_argument("--retry", type=int, default=2, help="Retries per symbol when cookie expires")
    parser.add_argument("--sleep-ms", type=int, default=120, help="Pause between symbols")

    parser.add_argument("--cassandra-host", default="localhost", help="Cassandra host (used if symbols not provided)")
    parser.add_argument("--cassandra-port", type=int, default=9042, help="Cassandra port")
    parser.add_argument("--keyspace", default="realtime_stock_data", help="Cassandra keyspace")
    parser.add_argument("--table", default="stock_description", help="Cassandra table with symbols")

    args = parser.parse_args()

    try:
        args.from_date = normalize_date(args.from_date)
        args.to_date = normalize_date(args.to_date)
        from_dt = dt.datetime.strptime(args.from_date, "%d-%m-%Y").date()
        to_dt = dt.datetime.strptime(args.to_date, "%d-%m-%Y").date()
    except ValueError as exc:
        print(str(exc), file=sys.stderr)
        return 2

    if from_dt > to_dt:
        print("Invalid range: --from-date must be <= --to-date", file=sys.stderr)
        return 2

    try:
        symbols = resolve_symbols(args)
    except Exception as exc:  # noqa: BLE001
        print(f"Failed to resolve symbols: {exc}", file=sys.stderr)
        return 2

    if not symbols:
        print("No symbols found to process.", file=sys.stderr)
        return 2

    ensure_dir(DEFAULT_OUTPUT_DIR)
    print(
        f"Resolved {len(symbols)} symbols | range={args.from_date}..{args.to_date} | series={args.series}"
    )

    try:
        cookie = refresh_cookie()
    except Exception as exc:  # noqa: BLE001
        print(f"Failed to generate NSE cookie: {exc}", file=sys.stderr)
        return 2

    success = 0
    failed = 0

    for idx, symbol in enumerate(symbols, start=1):
        done = False
        err_msg = ""
        max_attempts = args.retry + 1

        for attempt in range(1, max_attempts + 1):
            try:
                body = fetch_csv(symbol, args.series, args.from_date, args.to_date, cookie)
                if not looks_like_csv(body):
                    raise RuntimeError("NSE response is not CSV (cookie/session may be expired)")

                # Decode bytes to string for JSON conversion
                csv_text = body.decode("utf-8", errors="replace")
                out_path = save_json(DEFAULT_OUTPUT_DIR, symbol, args.from_date, args.to_date, csv_text)
                print(f"[{idx}/{len(symbols)}] OK {symbol} -> {out_path}")
                success += 1
                done = True
                break
            except Exception as exc:  # noqa: BLE001
                err_msg = str(exc)
                if attempt < max_attempts:
                    try:
                        print(
                            f"[{idx}/{len(symbols)}] RETRY {symbol} ({attempt}/{max_attempts - 1}) after error: {err_msg}"
                        )
                        cookie = refresh_cookie()
                    except Exception:
                        pass
                time.sleep(0.2)

        if not done:
            failed += 1
            print(f"[{idx}/{len(symbols)}] FAIL {symbol}: {err_msg}", file=sys.stderr)

        if args.sleep_ms > 0:
            time.sleep(args.sleep_ms / 1000.0)

    print("--- Summary ---")
    print(f"Success: {success}")
    print(f"Failed:  {failed}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())

