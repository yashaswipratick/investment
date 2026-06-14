#!/usr/bin/env python3
"""Download NSE historical CSV data for a list of stock symbols.

Default range: current day back to one calendar year.
Saves files into:
/Users/y0p03mn/preparation/investment-stock-market/investment/src/main/resources/historical-data
"""

from __future__ import annotations

import argparse
import datetime as dt
import gzip
import http.cookiejar
import random
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Iterable, List

BASE_URL = "https://www.nseindia.com/"
HISTORICAL_API = "https://www.nseindia.com/api/historical/cm/equity"
DEFAULT_OUTPUT_DIR = Path(
    "/Users/y0p03mn/preparation/investment-stock-market/investment/src/main/resources/historical-data"
)

USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/135.0.0.0 Safari/537.36"
)


def parse_date(value: str) -> dt.date:
    return dt.datetime.strptime(value, "%d-%m-%Y").date()


def fmt_date(value: dt.date) -> str:
    return value.strftime("%d-%m-%Y")


def safe_symbol(symbol: str) -> str:
    return re.sub(r"[^A-Za-z0-9._-]", "_", symbol.strip().upper())


def build_opener() -> urllib.request.OpenerDirector:
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
    opener.addheaders = [
        ("User-Agent", USER_AGENT),
        ("Accept-Language", "en-GB,en-US;q=0.9,en;q=0.8"),
        ("Accept-Encoding", "gzip, deflate"),
        ("Referer", BASE_URL),
        ("Connection", "keep-alive"),
    ]
    return opener


def warmup_session(opener: urllib.request.OpenerDirector, timeout: int) -> None:
    req = urllib.request.Request(BASE_URL, method="GET")
    with opener.open(req, timeout=timeout):
        pass


def decode_body(raw: bytes, content_encoding: str) -> str:
    if "gzip" in (content_encoding or "").lower():
        raw = gzip.decompress(raw)
    return raw.decode("utf-8", errors="replace")


def build_url(symbol: str, series: str, from_date: dt.date, to_date: dt.date) -> str:
    params = {
        "symbol": symbol,
        "series": f'["{series}"]',
        "from": fmt_date(from_date),
        "to": fmt_date(to_date),
        "csv": "true",
    }
    return f"{HISTORICAL_API}?{urllib.parse.urlencode(params)}"


def looks_like_csv(text: str) -> bool:
    first = text[:200].strip().lower()
    if "<html" in first or "<!doctype" in first:
        return False
    return "date" in first and "," in first


def download_symbol_csv(
    opener: urllib.request.OpenerDirector,
    symbol: str,
    series: str,
    from_date: dt.date,
    to_date: dt.date,
    timeout: int,
    max_retries: int,
    retry_delay: float,
) -> str:
    url = build_url(symbol, series, from_date, to_date)

    for attempt in range(1, max_retries + 1):
        try:
            req = urllib.request.Request(url, method="GET")
            with opener.open(req, timeout=timeout) as resp:
                body = decode_body(resp.read(), resp.headers.get("Content-Encoding", ""))

            if not looks_like_csv(body):
                raise RuntimeError("NSE returned non-CSV content")
            return body

        except urllib.error.HTTPError as exc:
            if exc.code in (401, 403):
                warmup_session(opener, timeout)
            if exc.code in (401, 403, 429, 500, 502, 503, 504) and attempt < max_retries:
                sleep_for = retry_delay * attempt + random.uniform(0.1, 0.5)
                time.sleep(sleep_for)
                continue
            raise
        except (urllib.error.URLError, TimeoutError, RuntimeError):
            if attempt < max_retries:
                sleep_for = retry_delay * attempt + random.uniform(0.1, 0.5)
                time.sleep(sleep_for)
                try:
                    warmup_session(opener, timeout)
                except Exception:
                    pass
                continue
            raise

    raise RuntimeError("Exhausted retries")


def load_symbols(inline_symbols: str | None, symbols_file: Path | None) -> List[str]:
    symbols: List[str] = []

    if inline_symbols:
        symbols.extend([s.strip() for s in inline_symbols.split(",") if s.strip()])

    if symbols_file:
        if not symbols_file.exists():
            raise FileNotFoundError(f"Symbols file not found: {symbols_file}")
        for line in symbols_file.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = [p.strip() for p in line.split(",") if p.strip()]
            symbols.extend(parts)

    unique = sorted({safe_symbol(s) for s in symbols if s.strip()})
    if not unique:
        raise ValueError("No stock symbols provided")
    return unique


def save_csv(output_dir: Path, symbol: str, series: str, from_date: dt.date, to_date: dt.date, csv_text: str) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)
    file_name = f"{safe_symbol(symbol)}_{series}_{from_date:%Y%m%d}_{to_date:%Y%m%d}.csv"
    target = output_dir / file_name
    target.write_text(csv_text, encoding="utf-8")
    return target


def parse_args() -> argparse.Namespace:
    today = dt.date.today()
    default_from = today - dt.timedelta(days=365)

    parser = argparse.ArgumentParser(description="Download NSE historical CSV for stock symbols")
    parser.add_argument("--symbols", help="Comma-separated stock symbols. Example: INFY,TCS,SBIN")
    parser.add_argument("--symbols-file", type=Path, help="File with symbols (comma or line-separated)")
    parser.add_argument("--series", default="EQ", help="Series value. Default: EQ")
    parser.add_argument("--from-date", default=fmt_date(default_from), help="From date in dd-mm-yyyy")
    parser.add_argument("--to-date", default=fmt_date(today), help="To date in dd-mm-yyyy")
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR, help="Output directory")
    parser.add_argument("--timeout-sec", type=int, default=20, help="HTTP timeout in seconds")
    parser.add_argument("--max-retries", type=int, default=4, help="Retries per symbol")
    parser.add_argument("--retry-delay-sec", type=float, default=1.5, help="Base delay between retries")
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    try:
        from_date = parse_date(args.from_date)
        to_date = parse_date(args.to_date)
        if from_date > to_date:
            raise ValueError("from-date must be <= to-date")

        symbols = load_symbols(args.symbols, args.symbols_file)
    except Exception as exc:
        print(f"Input error: {exc}", file=sys.stderr)
        return 2

    opener = build_opener()
    try:
        warmup_session(opener, args.timeout_sec)
    except Exception as exc:
        print(f"Warning: initial NSE warm-up failed ({exc}); continuing with retries.")

    successes: List[Path] = []
    failures: List[str] = []

    print(f"Downloading {len(symbols)} symbols from {fmt_date(from_date)} to {fmt_date(to_date)}")
    print(f"Output directory: {args.output_dir}")

    for symbol in symbols:
        try:
            csv_text = download_symbol_csv(
                opener=opener,
                symbol=symbol,
                series=args.series,
                from_date=from_date,
                to_date=to_date,
                timeout=args.timeout_sec,
                max_retries=args.max_retries,
                retry_delay=args.retry_delay_sec,
            )
            saved = save_csv(args.output_dir, symbol, args.series, from_date, to_date, csv_text)
            successes.append(saved)
            print(f"OK   {symbol} -> {saved.name}")
        except Exception as exc:
            failures.append(symbol)
            print(f"FAIL {symbol}: {exc}", file=sys.stderr)

    print("\nSummary")
    print(f"  Success: {len(successes)}")
    print(f"  Failed : {len(failures)}")

    if failures:
        print(f"  Failed symbols: {', '.join(failures)}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

