"""
India Stock Watchlist — Cassandra Edition
==========================================
Reads stock history from:
  Keyspace : realtime_stock_data
  Table    : stock_history
  Schema   : key text PRIMARY KEY,
             stock_history_details map<date, frozen<stock_history_details>>

UDT stock_history_details fields used:
  open, high, low, close, prevclose, volume (text), history_date

Run:
    python3 ~/Desktop/refresh_from_cassandra.py          # interactive period menu
    python3 ~/Desktop/refresh_from_cassandra.py 2        # 1 Year (skip prompt)

Falls back to Yahoo Finance for any symbol not found in Cassandra.
"""

import subprocess, json, ssl, sys, os, math, time, urllib.request
from datetime import datetime, date, timedelta
from openpyxl import Workbook
from openpyxl.styles import PatternFill, Font, Alignment, Border, Side
from openpyxl.utils import get_column_letter

# ── Config ────────────────────────────────────────────────────────────────
CASSANDRA_HOST = "localhost"
CASSANDRA_PORT = "9042"
KEYSPACE       = "realtime_stock_data"
TABLE          = "stock_history"
EXCEL_PATH     = os.path.expanduser("~/Desktop/India_Stock_Watchlist_June2026.xlsx")
CQLSH          = "cqlsh"   # full path if needed: /opt/homebrew/bin/cqlsh

# Yahoo Finance fallback (SSL bypass for Walmart proxy)
ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode    = ssl.CERT_NONE

TICKER_MAP = {
    "BHARTIARTL":"BHARTIARTL.NS","ICICIBANK":"ICICIBANK.NS","RELIANCE":"RELIANCE.NS",
    "SUNPHARMA":"SUNPHARMA.NS","POLYCAB":"POLYCAB.NS","HINDUNILVR":"HINDUNILVR.NS",
    "SUZLON":"SUZLON.NS","COFORGE":"COFORGE.NS","NBCC":"NBCC.NS","PRESTIGE":"PRESTIGE.NS",
    "APOLLO":"APOLLO.NS","DATAPATTNS":"DATAPATTNS.NS","PARAS":"PARAS.NS",
    "AVANTEL":"AVANTEL.NS","JAIBALAJI":"JAIBALAJI.NS","MUNJALAU":"MUNJALAU.NS",
}

RESEARCH = {
    "BHARTIARTL":(1700,1800,1540,2100,2370,5),
    "ICICIBANK": (1300,1380,1160,1550,1750,5),
    "RELIANCE":  (1280,1360,1195,1580,1803,4),
    "SUNPHARMA": (1740,1840,1640,2050,2370,4),
    "POLYCAB":   (8500,8800,7700,9800,11500,3),
    "HINDUNILVR":(2100,2160,1980,2480,2700,3),
    "SUZLON":    (52,56,46,65,72,3),
    "COFORGE":   (1400,1470,1330,1645,1994,3),
    "NBCC":      (100,108,99,118,130,3),
    "PRESTIGE":  (1470,1520,1380,1745,2025,3),
    "APOLLO":    (380,415,340,480,580,4),
    "DATAPATTNS":(4400,4700,4000,5400,6500,4),
    "PARAS":     (1250,1350,1100,1600,1900,3),
    "AVANTEL":   (155,175,140,210,260,3),
    "JAIBALAJI": (60,68,52,95,120,2),
    "MUNJALAU":  (88,95,80,115,135,2),
}

NAMES = {
    "BHARTIARTL":"Bharti Airtel","ICICIBANK":"ICICI Bank","RELIANCE":"Reliance Inds",
    "SUNPHARMA":"Sun Pharma","POLYCAB":"Polycab India","HINDUNILVR":"HUL",
    "SUZLON":"Suzlon Energy","COFORGE":"Coforge","NBCC":"NBCC India","PRESTIGE":"Prestige Estates",
    "APOLLO":"Apollo Micro Systems","DATAPATTNS":"Data Patterns","PARAS":"Paras Defence",
    "AVANTEL":"Avantel","JAIBALAJI":"Jai Balaji","MUNJALAU":"Munjal Auto",
}

RISK = {
    "BHARTIARTL":"LOW","ICICIBANK":"LOW","RELIANCE":"LOW","SUNPHARMA":"LOW",
    "POLYCAB":"MED","HINDUNILVR":"LOW","SUZLON":"MED","COFORGE":"MED",
    "NBCC":"MED","PRESTIGE":"MED",
    "APOLLO":"HIGH","DATAPATTNS":"HIGH","PARAS":"HIGH",
    "AVANTEL":"HIGH","JAIBALAJI":"HIGH","MUNJALAU":"HIGH",
}

ORDERED = [
    "BHARTIARTL","ICICIBANK","RELIANCE","SUNPHARMA","POLYCAB",
    "HINDUNILVR","SUZLON","COFORGE","NBCC","PRESTIGE",
    "APOLLO","DATAPATTNS","PARAS","AVANTEL","JAIBALAJI","MUNJALAU",
]

# ════════════════════════════════════════════════════════════════════════════
# PERIOD SELECTOR
# ════════════════════════════════════════════════════════════════════════════
PERIOD_OPTIONS = {
    "1": (180,  "6 Months",  "~126 trading days — RSI, MACD, 50DMA. Fast daily check."),
    "2": (365,  "1 Year",    "~252 trading days — + 200DMA + 6M trend. ⭐ Recommended."),
    "3": (730,  "2 Years",   "~504 trading days — Full cycle view. 🎯 Best before investing."),
    "4": (1095, "3 Years",   "~756 trading days — Bull/bear cycle. Long-term positioning."),
}

def ask_period():
    print("\n" + "═"*62)
    print("  INDIA STOCK WATCHLIST — CASSANDRA EDITION")
    print("═"*62)
    print("\n  How much historical data should be used?\n")
    for k, (days, label, desc) in PERIOD_OPTIONS.items():
        print(f"  [{k}]  {label:<12}  {desc}")
    print()
    if len(sys.argv) > 1 and sys.argv[1] in PERIOD_OPTIONS:
        choice = sys.argv[1]
    else:
        try:
            choice = input("  Enter choice [1-4] (default=2): ").strip() or "2"
            if choice not in PERIOD_OPTIONS:
                choice = "2"
        except (KeyboardInterrupt, EOFError):
            choice = "2"
    days, label, desc = PERIOD_OPTIONS[choice]
    print(f"\n  ✓ {label} selected — fetching last {days} calendar days\n")
    return days, label

PERIOD_DAYS, PERIOD_LABEL = ask_period()
cutoff_date = date.today() - timedelta(days=PERIOD_DAYS)


# ════════════════════════════════════════════════════════════════════════════
# CASSANDRA FETCH via cqlsh subprocess
# ════════════════════════════════════════════════════════════════════════════

def _run_cql(cql):
    """Execute a CQL statement via cqlsh and return stdout text."""
    result = subprocess.run(
        [CQLSH, CASSANDRA_HOST, CASSANDRA_PORT, "--execute", cql],
        capture_output=True, text=True, timeout=30
    )
    return result.stdout, result.returncode

def _parse_volume(vol_str):
    """Parse volume from text: '37,98,060' or '3798060' → int."""
    if not vol_str:
        return 0
    try:
        return int(str(vol_str).replace(",", "").replace(" ", ""))
    except:
        return 0

def fetch_from_cassandra(symbol):
    """
    Returns (closes, highs, lows, volumes, prev_close) from Cassandra.
    Filters to PERIOD_DAYS. Returns all Nones if symbol not found.
    """
    # Use SELECT JSON to get structured output
    cql = (
        f"SELECT JSON key, stock_history_details "
        f"FROM {KEYSPACE}.{TABLE} "
        f"WHERE key='{symbol}';"
    )
    stdout, rc = _run_cql(cql)
    if rc != 0 or "(0 rows)" in stdout:
        return None, None, None, None, None

    # Extract JSON line(s) from cqlsh output
    # cqlsh SELECT JSON output format:
    #   [json]
    #   --------------------------------
    #    {"key": "...", "stock_history_details": {...}}
    #   (1 rows)
    json_lines = []
    for line in stdout.splitlines():
        line = line.strip()
        if line.startswith("{") and '"key"' in line:
            json_lines.append(line)

    if not json_lines:
        return None, None, None, None, None

    try:
        row = json.loads(json_lines[0])
    except json.JSONDecodeError:
        return None, None, None, None, None

    history_map = row.get("stock_history_details", {})
    if not history_map:
        return None, None, None, None, None

    # Sort by date, filter to period
    sorted_entries = sorted(
        ((k, v) for k, v in history_map.items()),
        key=lambda x: x[0]
    )
    # Filter to cutoff
    sorted_entries = [
        (d, v) for d, v in sorted_entries
        if d >= str(cutoff_date)
    ]

    if not sorted_entries:
        return None, None, None, None, None

    closes  = []
    highs   = []
    lows    = []
    volumes = []

    for d, v in sorted_entries:
        c = v.get("close") or v.get("ltp")
        h = v.get("high")
        l = v.get("low")
        vol = _parse_volume(v.get("volume", "0"))
        if c is not None and h is not None and l is not None:
            closes.append(float(c))
            highs.append(float(h))
            lows.append(float(l))
            volumes.append(vol)

    if len(closes) < 5:
        return None, None, None, None, None

    prev_close = closes[-2] if len(closes) >= 2 else closes[-1]
    return closes, highs, lows, volumes, round(prev_close, 2)


# ════════════════════════════════════════════════════════════════════════════
# YAHOO FINANCE FALLBACK
# ════════════════════════════════════════════════════════════════════════════

YF_RANGE_MAP = {180: "6mo", 365: "1y", 730: "2y", 1095: "3y"}

def fetch_from_yahoo(yf_ticker):
    yf_range = YF_RANGE_MAP.get(PERIOD_DAYS, "1y")
    try:
        url = (f"https://query1.finance.yahoo.com/v8/finance/chart/{yf_ticker}"
               f"?interval=1d&range={yf_range}")
        req = urllib.request.Request(url, headers={"User-Agent":"Mozilla/5.0"})
        r   = urllib.request.urlopen(req, timeout=10, context=ctx)
        d   = json.loads(r.read())
        res = d["chart"]["result"][0]
        ind = res["indicators"]["quote"][0]
        rows = [
            (c, h, l, v if v else 0)
            for c, h, l, v in zip(
                ind.get("close",[]), ind.get("high",[]),
                ind.get("low",[]),   ind.get("volume",[])
            )
            if c is not None
        ]
        if not rows:
            return None,None,None,None,None
        closes  = [r[0] for r in rows]
        highs   = [r[1] for r in rows]
        lows    = [r[2] for r in rows]
        volumes = [r[3] for r in rows]
        prev    = round(closes[-2], 2) if len(closes) >= 2 else round(closes[-1], 2)
        return closes, highs, lows, volumes, prev
    except Exception as e:
        print(f"    Yahoo fallback failed for {yf_ticker}: {e}")
        return None,None,None,None,None


# ════════════════════════════════════════════════════════════════════════════
# TECHNICAL INDICATORS
# ════════════════════════════════════════════════════════════════════════════

def sma(arr, n):
    if len(arr) < n: return None
    return round(sum(arr[-n:]) / n, 2)

def ema_series(arr, period):
    if len(arr) < period: return []
    k = 2 / (period + 1)
    e = sum(arr[:period]) / period
    result = [e]
    for p in arr[period:]:
        e = p * k + e * (1 - k)
        result.append(e)
    return result

def calc_rsi(closes, period=14):
    if len(closes) < period + 2: return None
    deltas = [closes[i+1] - closes[i] for i in range(len(closes)-1)]
    gains  = [max(d, 0) for d in deltas]
    losses = [abs(min(d, 0)) for d in deltas]
    ag = sum(gains[:period]) / period
    al = sum(losses[:period]) / period
    for i in range(period, len(gains)):
        ag = (ag*(period-1) + gains[i]) / period
        al = (al*(period-1) + losses[i]) / period
    if al == 0: return 100.0
    return round(100 - (100 / (1 + ag/al)), 1)

def calc_macd(closes):
    if len(closes) < 35: return None, None, None, "Insufficient data"
    ef = ema_series(closes, 12)
    es = ema_series(closes, 26)
    ml = [a - b for a, b in zip(ef[14:], es)]
    sl = ema_series(ml, 9)
    if not sl: return None, None, None, "Insufficient data"
    mv, sv = round(ml[-1], 3), round(sl[-1], 3)
    hist   = round(mv - sv, 3)
    txt = "Bullish ▲" if mv > sv and hist > 0 else ("Bearish ▼" if mv < sv and hist < 0 else "Neutral →")
    return mv, sv, hist, txt

def calc_bollinger(closes):
    if len(closes) < 20: return None, None, None, "Insufficient data"
    w   = closes[-20:]
    mid = sum(w) / 20
    std = math.sqrt(sum((x-mid)**2 for x in w) / 20)
    up  = round(mid + 2*std, 2)
    lo  = round(mid - 2*std, 2)
    mid = round(mid, 2)
    lv  = closes[-1]
    if lv >= up:   pos = "Near Upper Band (Overbought)"
    elif lv <= lo: pos = "Near Lower Band (Oversold)"
    elif lv > mid: pos = "Above Middle Band"
    else:          pos = "Below Middle Band"
    return up, mid, lo, pos

def calc_volume_trend(volumes):
    if len(volumes) < 20: return "Insufficient data"
    v5  = sum(volumes[-5:])  / 5
    v20 = sum(volumes[-20:]) / 20
    r   = v5 / v20 if v20 else 1
    if r > 1.3:   return "Rising ▲ (Strong)"
    if r > 1.1:   return "Rising ▲"
    if r < 0.7:   return "Falling ▼ (Weak)"
    if r < 0.9:   return "Falling ▼"
    return "Neutral →"

def calc_6m_change(closes):
    n = min(126, len(closes)-1)
    if n < 20: return None
    return round((closes[-1] - closes[-n]) / closes[-n] * 100, 1)

def calc_trend(closes, dma50, dma200):
    live = closes[-1]
    if dma50 is None: return "Sideways ➡️", "Insufficient data"
    if dma200:
        if live > dma50 > dma200:  return "Uptrend ↑",   "Price > 50DMA > 200DMA"
        if live < dma50 < dma200:  return "Downtrend ↓", "Price < 50DMA < 200DMA"
        if live > dma200:          return "Sideways ➡️", "Mixed signals above 200DMA"
        return "Downtrend ↓", "Price below 200DMA"
    if live > dma50: return "Uptrend ↑",   "Price > 50DMA (200DMA data limited)"
    return "Downtrend ↓", "Price < 50DMA (200DMA data limited)"

def support_resistance(highs, lows, n=50):
    k = min(n, len(highs))
    return round(min(lows[-k:]), 2), round(max(highs[-k:]), 2)

def rsi_signal(rsi):
    if rsi is None: return "N/A"
    if rsi >= 70:   return f"{rsi} — Overbought ⚠️"
    if rsi <= 30:   return f"{rsi} — Oversold ✅"
    if rsi >= 60:   return f"{rsi} — Bullish"
    if rsi <= 40:   return f"{rsi} — Bearish"
    return f"{rsi} — Neutral"

def smart_action(ticker, live, trend, rsi, macd_txt):
    r = RESEARCH[ticker]
    bl, bh, sl, t1, t2 = r[0], r[1], r[2], r[3], r[4]
    if live is None: return "⚪ CHECK PRICE", "D9D9D9", "Price unavailable"
    if live <= sl:   return "🛑 SELL NOW",    "FF2020", f"Stop loss ₹{sl} hit. Exit immediately."
    if live >= t2:   return "🏆 BOOK ALL PROFIT","6A0DAD", f"Target 2 ₹{t2} achieved!"
    near_t1 = live >= t1 * 0.97
    if near_t1:      return "💰 BOOK 50% PROFIT","2196F3", f"Near Target 1 ₹{t1}. Sell half."
    in_zone = bl*0.98 <= live <= bh*1.05
    above   = live > bh*1.05
    below   = live < bl*0.98
    if "Downtrend" in trend:
        if in_zone: return "⚠️ WAIT — Downtrend","FF6600", "Price OK but downtrend active. Wait for reversal."
        if below:   return "❌ AVOID — Falling", "DD2200", "Downtrend + below buy zone. Don't buy."
        return "⚠️ CAUTION — Downtrend","FF6600", "Downtrend active. Tighten stop loss."
    if rsi and rsi >= 72 and "Uptrend" not in trend:
        return "⏳ WAIT — Overbought","FFAA00", f"RSI {rsi} overbought. Wait for RSI < 60."
    if "Sideways" in trend:
        if in_zone:
            if rsi and rsi <= 40: return "✅ BUY — Oversold","1E8A00", f"In buy zone + RSI oversold ({rsi})."
            return "⚠️ BUY CAUTIOUSLY","E08000", f"In buy zone but sideways. Buy 50% position."
        if above: return "👀 HOLD / WATCH","4CAF50", f"Above buy zone. Wait for dip to ₹{bl}–₹{bh}."
        return "⏳ WAIT","FFCC00", f"Price ₹{live} not in buy zone ₹{bl}–₹{bh}."
    if "Uptrend" in trend:
        if in_zone:
            if rsi and rsi >= 70: return "⏳ WAIT — RSI High","FFAA00", f"In buy zone but RSI {rsi} high. Wait."
            return "✅ BUY NOW ↑ Uptrend","1E8A00", f"Uptrend + buy zone. Target 1: ₹{t1}, T2: ₹{t2}."
        if above:
            if rsi and rsi >= 70: return "⏳ WAIT — Overbought","FFAA00", f"RSI {rsi} overbought. Wait for dip."
            return "👀 HOLD / WATCH ↑","4CAF50", f"Uptrend active. Wait for dip to ₹{bl}–₹{bh}."
        return "⏳ WAIT FOR BOUNCE","FFCC00", f"Uptrend but below buy zone at ₹{live}."
    return "👀 WATCH","4CAF50", "No clear signal right now."


# ════════════════════════════════════════════════════════════════════════════
# FETCH + COMPUTE ALL STOCKS
# ════════════════════════════════════════════════════════════════════════════
print(f"{'='*62}")
print(f"  FETCHING DATA  |  Source: Cassandra → Yahoo fallback")
print(f"  Period: {PERIOD_LABEL}  |  Cutoff: {cutoff_date}")
print(f"{'='*62}\n")
print(f"  {'TICKER':<14} {'SOURCE':<12} {'LIVE':>9}  {'TREND':<18}  RSI  ACTION")
print("  " + "-"*68)

all_data = {}
for ticker in ORDERED:
    # ── Try Cassandra first ──────────────────────────────────────────────
    closes, highs, lows, volumes, prev = fetch_from_cassandra(ticker)
    source = "Cassandra"

    # ── Fall back to Yahoo Finance if not in Cassandra ───────────────────
    if closes is None:
        yf_ticker = TICKER_MAP.get(ticker)
        if yf_ticker:
            closes, highs, lows, volumes, prev = fetch_from_yahoo(yf_ticker)
            source = "Yahoo (fallback)"
        time.sleep(0.15)

    if closes is None or len(closes) < 10:
        print(f"  {ticker:<14} {'NO DATA':<12}")
        all_data[ticker] = None
        continue

    live   = closes[-1]
    dma50  = sma(closes, 50)
    dma200 = sma(closes, 200)
    rsi    = calc_rsi(closes)
    _, _, _, macd_txt = calc_macd(closes)
    bb_up, bb_mid, bb_lo, bb_pos = calc_bollinger(closes)
    vol_t  = calc_volume_trend(volumes)
    chg6m  = calc_6m_change(closes)
    chg1d  = round((live - prev) / prev * 100, 2) if prev else None
    trend, trend_detail = calc_trend(closes, dma50, dma200)
    sup, res = support_resistance(highs, lows) if highs else (None, None)
    action, acol, adesc = smart_action(ticker, live, trend, rsi, macd_txt)

    all_data[ticker] = {
        "live": round(live, 2), "prev": prev, "chg_1d": chg1d,
        "dma50": dma50, "dma200": dma200,
        "rsi": rsi, "rsi_sig": rsi_signal(rsi),
        "macd_txt": macd_txt,
        "bb_up": bb_up, "bb_mid": bb_mid, "bb_lo": bb_lo, "bb_pos": bb_pos,
        "vol_trend": vol_t, "chg_6m": chg6m,
        "trend": trend, "trend_detail": trend_detail,
        "support": sup, "resistance": res,
        "action": action, "acol": acol, "adesc": adesc,
        "source": source,
    }
    print(f"  {ticker:<14} {source:<18} ₹{round(live,2):<9}  {trend:<18}  {rsi or 'N/A'}  {action}")


# ════════════════════════════════════════════════════════════════════════════
# EXCEL BUILD (same structure as Yahoo version)
# ════════════════════════════════════════════════════════════════════════════
TREND_BG  = {"Uptrend ↑":"C8E6C9","Downtrend ↓":"FFCDD2","Sideways ➡️":"FFF9C4"}
RISK_BG   = {"LOW":"E8F5E9","MED":"FFF3E0","HIGH":"FFEBEE"}
RISK_ICON = {"LOW":"🟢 LOW","MED":"🟡 MED","HIGH":"🔴 HIGH"}

thin  = Side(style="thin",   color="D0D0D0")
bd    = Border(left=thin, right=thin, top=thin, bottom=thin)
NOW_STR = datetime.now().strftime("%d %b %Y %H:%M")

def mk(ws, row, col, val="", bg="FFFFFF", fg="000000",
       bold=False, sz=10, ah="center", av="center", wrap=True):
    c = ws.cell(row=row, column=col, value=val)
    c.fill      = PatternFill("solid", fgColor=bg)
    c.font      = Font(name="Calibri", bold=bold, color=fg, size=sz)
    c.alignment = Alignment(horizontal=ah, vertical=av, wrap_text=wrap)
    c.border    = bd
    return c

def row_bg(i, mid=False):
    if mid: return "F5F0FF" if i%2==0 else "FAF5FF"
    return "F9F9F9" if i%2==0 else "FFFFFF"

def mid_sep(ws, r, cols):
    ws.merge_cells(f"A{r}:{get_column_letter(cols)}{r}")
    mk(ws,r,1,"── MID-CAP / SMALL-CAP  (Higher Risk — Max 5% per stock) ──",
       bg="6A0DAD",fg="FFFFFF",bold=True,sz=11)
    ws.row_dimensions[r].height = 22

wb = Workbook()

# ── Sheet 1: Quick Actions ────────────────────────────────────────────────
ws1 = wb.active
ws1.title = "🚦 Quick Actions"
ws1.merge_cells("A1:H1")
mk(ws1,1,1,f"🚦 WHAT TO DO  |  Data: {PERIOD_LABEL}  |  Source: Cassandra + Yahoo fallback  |  {NOW_STR}",
   bg="0D47A1",fg="FFFFFF",bold=True,sz=13)
ws1.row_dimensions[1].height = 34
ws1.merge_cells("A2:H2")
mk(ws1,2,1,"✅ Green=Buy  ⚠️ Orange=Wait (trend)  👀 Teal=Hold  💰 Blue=Book profit  🛑 Red=Sell  ⚪ Grey=No data",
   bg="E3F2FD",fg="1565C0",sz=9,it=True) if False else None
c2=ws1.cell(row=2,column=1,value="✅ Green=Buy  ⚠️ Orange=Wait (trend)  👀 Teal=Hold  💰 Blue=Book profit  🛑 Red=Sell  ⚪ Grey=No data")
c2.fill=PatternFill("solid",fgColor="E3F2FD"); c2.font=Font(name="Calibri",color="1565C0",size=9,italic=True)
c2.alignment=Alignment(horizontal="left",vertical="center"); c2.border=bd
ws1.row_dimensions[2].height = 18

for col,lbl,w in [(1,"#",4),(2,"Company",22),(3,"Live Price",13),(4,"Today ±%",12),
                   (5,"Data Source",16),(6,"6-Month Trend",18),(7,"RSI Signal",22),(8,"👉 ACTION",64)]:
    mk(ws1,3,col,lbl,bg="1565C0",fg="FFFFFF",bold=True,sz=10)
    ws1.column_dimensions[get_column_letter(col)].width = w
ws1.row_dimensions[3].height = 28

r=4
for i,ticker in enumerate(ORDERED):
    if i==10:
        mid_sep(ws1,r,8); r+=1
    d   = all_data.get(ticker)
    bg  = row_bg(i, i>=10)
    if d:
        live   = d["live"]
        chg1d  = f"{d['chg_1d']:+.2f}%" if d["chg_1d"] is not None else "N/A"
        trend  = d["trend"]
        rsi_s  = d["rsi_sig"]
        action = d["action"]
        acol   = d["acol"]
        adesc  = d["adesc"]
        src    = d["source"]
        chg_bg = "DCFCE7" if (d["chg_1d"] or 0)>0 else ("FFE4E4" if (d["chg_1d"] or 0)<0 else "F5F5F5")
        tbg    = TREND_BG.get(trend,"F5F5F5")
        src_bg = "E8F5E9" if "Cassandra" in src else "FFF3E0"
    else:
        live=chg1d=trend=rsi_s="N/A"; action,acol,adesc="⚪ NO DATA","D9D9D9","Not in Cassandra or Yahoo"
        chg_bg=tbg=src_bg="F5F5F5"; src="N/A"
    mk(ws1,r,1,i+1,bg=bg,bold=True,sz=11)
    mk(ws1,r,2,NAMES[ticker],bg=bg,bold=True,sz=11,ah="left")
    mk(ws1,r,3,f"₹{live}" if isinstance(live,float) else live,bg=bg,bold=True,sz=12)
    mk(ws1,r,4,chg1d,bg=chg_bg,bold=True,sz=11)
    mk(ws1,r,5,src,bg=src_bg,sz=9)
    mk(ws1,r,6,trend,bg=tbg,bold=True,sz=10)
    mk(ws1,r,7,rsi_s,bg=bg,sz=9)
    mk(ws1,r,8,f"{action}   {adesc}",bg=acol,bold=True,
       fg="FFFFFF" if acol in ["1E8A00","FF2020","6A0DAD","2196F3","DD2200","FF6600"] else "1A1A1A",
       ah="left",sz=10)
    ws1.row_dimensions[r].height = 34; r+=1

# ── Sheet 2: Full Details ─────────────────────────────────────────────────
ws2 = wb.create_sheet("📊 Full Details")
ws2.merge_cells("A1:N1")
mk(ws2,1,1,f"📊 COMPLETE DETAILS  |  {PERIOD_LABEL}  |  {NOW_STR}",bg="0D47A1",fg="FFFFFF",bold=True,sz=14)
ws2.row_dimensions[1].height = 32

for col,lbl,w in [(1,"#",4),(2,"Company",20),(3,"Risk",10),(4,"Live Price",12),(5,"Today ±%",11),
                   (6,"6M Trend",15),(7,"Buy Zone (₹)",18),(8,"Stop Loss (₹)",17),
                   (9,"Target 1 (₹)",16),(10,"Target 2 (₹)",16),(11,"Max Profit",13),
                   (12,"Max Risk",11),(13,"Support",14),(14,"Resistance",14)]:
    mk(ws2,2,col,lbl,bg="1565C0",fg="FFFFFF",bold=True,sz=10)
    ws2.column_dimensions[get_column_letter(col)].width=w
ws2.row_dimensions[2].height=36

r2=3
for i,ticker in enumerate(ORDERED):
    if i==10: mid_sep(ws2,r2,14); r2+=1
    d   = all_data.get(ticker)
    res = RESEARCH[ticker]
    bl,bh,sl,t1,t2 = res[0],res[1],res[2],res[3],res[4]
    bg2 = row_bg(i,i>=10)
    profit=f"+{round((t2-bl)/bl*100)}%"; loss=f"-{round((bl-sl)/bl*100)}%"
    live  = f"₹{d['live']}"    if d else "N/A"
    chg1d = f"{d['chg_1d']:+.2f}%" if d and d["chg_1d"] is not None else "N/A"
    trend = d["trend"]          if d else "N/A"
    sup   = f"₹{d['support']}"  if d and d["support"] else "N/A"
    resr  = f"₹{d['resistance']}"if d and d["resistance"] else "N/A"
    tbg   = TREND_BG.get(trend,"F5F5F5") if d else "F5F5F5"
    acol  = d["acol"]           if d else "D9D9D9"
    chg_bg= "DCFCE7" if d and (d["chg_1d"] or 0)>0 else ("FFE4E4" if d and (d["chg_1d"] or 0)<0 else "F5F5F5")
    mk(ws2,r2,1,i+1,bg=bg2,bold=True)
    mk(ws2,r2,2,NAMES[ticker],bg=acol,bold=True,sz=10,ah="left",
       fg="FFFFFF" if acol in ["1E8A00","FF2020","DD2200","FF6600","6A0DAD"] else "1A1A1A")
    mk(ws2,r2,3,RISK_ICON[RISK[ticker]],bg=RISK_BG[RISK[ticker]],sz=9)
    mk(ws2,r2,4,live,bg=bg2,bold=True,sz=12)
    mk(ws2,r2,5,chg1d,bg=chg_bg,bold=True)
    mk(ws2,r2,6,trend,bg=tbg,bold=True,sz=10)
    mk(ws2,r2,7,f"₹{bl}–₹{bh}",bg="E8F5E9",bold=True)
    mk(ws2,r2,8,f"₹{sl}",bg="FFEBEE",bold=True,fg="C62828")
    mk(ws2,r2,9,f"₹{t1}",bg="E3F2FD",bold=True)
    mk(ws2,r2,10,f"₹{t2}",bg="E8EAF6",bold=True)
    mk(ws2,r2,11,profit,bg="E8F5E9")
    mk(ws2,r2,12,loss,bg="FFEBEE",fg="C62828")
    mk(ws2,r2,13,sup,bg="FFF8E1")
    mk(ws2,r2,14,resr,bg="E8EAF6")
    ws2.row_dimensions[r2].height=32; r2+=1

# ── Sheet 3: Technical Analysis ───────────────────────────────────────────
ws3 = wb.create_sheet("📈 Technical Analysis")
ws3.merge_cells("A1:M1")
mk(ws3,1,1,f"📈 TECHNICAL INDICATORS  |  {PERIOD_LABEL}  |  {NOW_STR}",bg="0D47A1",fg="FFFFFF",bold=True,sz=14)
ws3.row_dimensions[1].height=32

for col,lbl,w in [(1,"#",4),(2,"Company",20),(3,"Live Price",12),(4,"50DMA",13),(5,"200DMA",13),
                   (6,"vs 50DMA",12),(7,"vs 200DMA",12),(8,"RSI (14)",20),(9,"MACD",15),
                   (10,"Bollinger",24),(11,"6M Change",13),(12,"Vol Trend",16),(13,"6M Trend",18)]:
    mk(ws3,2,col,lbl,bg="1565C0",fg="FFFFFF",bold=True,sz=10)
    ws3.column_dimensions[get_column_letter(col)].width=w
ws3.row_dimensions[2].height=36

r3=3
for i,ticker in enumerate(ORDERED):
    if i==10: mid_sep(ws3,r3,13); r3+=1
    d   = all_data.get(ticker)
    bg3 = row_bg(i,i>=10)
    if d:
        live=d["live"]; dma50=d["dma50"]; dma200=d["dma200"]
        v50  = f"{((live-dma50)/dma50*100):+.1f}%"   if dma50  else "N/A"
        v200 = f"{((live-dma200)/dma200*100):+.1f}%"  if dma200 else "N/A"
        v50bg  = "DCFCE7" if dma50  and live>dma50  else ("FFE4E4" if dma50  and live<dma50  else "F5F5F5")
        v200bg = "DCFCE7" if dma200 and live>dma200 else ("FFE4E4" if dma200 and live<dma200 else "FFF9C4")
        rsibg  = "FFCDD2" if d["rsi"] and d["rsi"]>=70 else ("DCFCE7" if d["rsi"] and d["rsi"]<=35 else "F5F5F5")
        macdbg = "DCFCE7" if "Bull" in d["macd_txt"] else ("FFE4E4" if "Bear" in d["macd_txt"] else "F5F5F5")
        chg6bg = "DCFCE7" if d["chg_6m"] and d["chg_6m"]>0 else ("FFE4E4" if d["chg_6m"] and d["chg_6m"]<0 else "F5F5F5")
        tbg    = TREND_BG.get(d["trend"],"F5F5F5")
    else:
        live=dma50=dma200=v50=v200="N/A"
        v50bg=v200bg=rsibg=macdbg=chg6bg=tbg="F5F5F5"

    sv = lambda k, fmt=None: (fmt(d[k]) if fmt else d[k]) if d and d.get(k) is not None else "N/A"

    mk(ws3,r3,1,i+1,bg=bg3,bold=True)
    mk(ws3,r3,2,NAMES[ticker],bg=bg3,bold=True,sz=11,ah="left")
    mk(ws3,r3,3,f"₹{sv('live')}" if d else "N/A",bg=bg3,bold=True,sz=11)
    mk(ws3,r3,4,f"₹{sv('dma50')}" if d and d.get("dma50") else "N/A",bg=bg3)
    mk(ws3,r3,5,f"₹{sv('dma200')}" if d and d.get("dma200") else "Insuff. data",bg=bg3)
    mk(ws3,r3,6,v50,bg=v50bg,bold=True)
    mk(ws3,r3,7,v200,bg=v200bg,bold=True)
    mk(ws3,r3,8,sv("rsi_sig"),bg=rsibg,sz=9)
    mk(ws3,r3,9,sv("macd_txt"),bg=macdbg,bold=True)
    mk(ws3,r3,10,sv("bb_pos"),bg=bg3,sz=9)
    mk(ws3,r3,11,f"{sv('chg_6m')}%" if d and d.get("chg_6m") is not None else "N/A",bg=chg6bg,bold=True)
    mk(ws3,r3,12,sv("vol_trend"),bg=bg3,sz=9)
    mk(ws3,r3,13,sv("trend"),bg=tbg if d else "F5F5F5",bold=True,sz=10)
    ws3.row_dimensions[r3].height=32; r3+=1

wb.save(EXCEL_PATH)

print(f"\n{'='*62}")
print(f"  ✓ SAVED: {EXCEL_PATH}")
print(f"  Sheets: 🚦 Quick Actions | 📊 Full Details | 📈 Technical Analysis")
print(f"\n  Data sources used:")
cassandra_count = sum(1 for d in all_data.values() if d and "Cassandra" in d.get("source",""))
yahoo_count     = sum(1 for d in all_data.values() if d and "Yahoo"     in d.get("source",""))
nodata_count    = sum(1 for d in all_data.values() if not d)
print(f"  Cassandra          : {cassandra_count} stocks")
print(f"  Yahoo (fallback)   : {yahoo_count} stocks")
print(f"  No data            : {nodata_count} stocks")

print(f"\n  Action Alerts:")
for ticker in ORDERED:
    d = all_data.get(ticker)
    if d and ("SELL" in d["action"] or "AVOID" in d["action"] or "PROFIT" in d["action"]):
        print(f"  → {ticker:<14} {d['action']}")
print(f"{'='*62}")
