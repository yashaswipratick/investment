"""
India Stock Watchlist — Full Rebuild with Technical Analysis
Fetches historical OHLCV data, calculates all indicators, rebuilds Excel.
"""
import urllib.request, json, ssl, time, os, math, sys
from openpyxl import Workbook
from openpyxl.styles import PatternFill, Font, Alignment, Border, Side
from openpyxl.utils import get_column_letter
from datetime import datetime

# ── SSL context (bypasses Walmart corporate proxy) ────────────────────────
ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

EXCEL_PATH = os.path.expanduser("~/Desktop/India_Stock_Watchlist_June2026.xlsx")

# ════════════════════════════════════════════════════════════════════════════
# DATA PERIOD CONFIG — Ask user at startup
# ════════════════════════════════════════════════════════════════════════════

PERIOD_OPTIONS = {
    "1": ("6mo",  "6 Months",  "~126 days — RSI, MACD, Bollinger, 50DMA only. Fast. Quick daily check."),
    "2": ("1y",   "1 Year",    "~252 days — All above + 200DMA + 6M trend. ⭐ Best for daily monitoring."),
    "3": ("2y",   "2 Years",   "~504 days — Clearest support/resistance + full market cycle. 🎯 Best before investing."),
    "4": ("3y",   "3 Years",   "~756 days — Bull/bear cycle view. For long-term position sizing only."),
}

def ask_period():
    print("\n" + "═"*62)
    print("  INDIA STOCK WATCHLIST — ANALYSIS PERIOD SELECTOR")
    print("═"*62)
    print("\n  How much historical data should be used for analysis?\n")
    for key, (yf_range, label, desc) in PERIOD_OPTIONS.items():
        print(f"  [{key}]  {label:<12}  {desc}")
    print()
    print("  Recommendation:")
    print("    → Daily check        : choose 1 (6 Months)  — faster run")
    print("    → Weekly review      : choose 2 (1 Year)    — balanced")
    print("    → Before investing   : choose 3 (2 Years)   — most accurate")
    print("    → Long-term planning : choose 4 (3 Years)   — full cycle")
    print()

    # Allow passing as command-line argument: python3 script.py 2
    if len(sys.argv) > 1 and sys.argv[1] in PERIOD_OPTIONS:
        choice = sys.argv[1]
        yf_range, label, _ = PERIOD_OPTIONS[choice]
        print(f"  Using command-line choice: [{choice}] {label}")
        return yf_range, label

    while True:
        try:
            choice = input("  Enter choice [1-4] (default = 2): ").strip() or "2"
            if choice in PERIOD_OPTIONS:
                yf_range, label, desc = PERIOD_OPTIONS[choice]
                print(f"\n  ✓ Selected: {label} — {desc}\n")
                return yf_range, label
            print("  Invalid choice. Please enter 1, 2, 3, or 4.")
        except (KeyboardInterrupt, EOFError):
            print("\n  Defaulting to 1 Year.")
            return "1y", "1 Year"

YF_RANGE, PERIOD_LABEL = ask_period()

TICKER_MAP = {
    "BHARTIARTL":"BHARTIARTL.NS","ICICIBANK":"ICICIBANK.NS","RELIANCE":"RELIANCE.NS",
    "SUNPHARMA":"SUNPHARMA.NS","POLYCAB":"POLYCAB.NS","HINDUNILVR":"HINDUNILVR.NS",
    "SUZLON":"SUZLON.NS","COFORGE":"COFORGE.NS","NBCC":"NBCC.NS","PRESTIGE":"PRESTIGE.NS",
    "APOLLO":"APOLLO.NS","DATAPATTNS":"DATAPATTNS.NS","PARAS":"PARAS.NS",
    "AVANTEL":"AVANTEL.NS","JAIBALAJI":"JAIBALAJI.NS","MUNJALAU":"MUNJALAU.NS",
}

# Research levels (from Jun 22 analysis — note: some may be below current price)
RESEARCH = {
    # ticker: (buy_low, buy_high, stop_loss, target1, target2, conviction)
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

SECTOR = {
    "BHARTIARTL":"Telecom","ICICIBANK":"Banking","RELIANCE":"Diversified",
    "SUNPHARMA":"Pharma","POLYCAB":"Capital Goods","HINDUNILVR":"FMCG",
    "SUZLON":"Renewables","COFORGE":"IT","NBCC":"Infra PSU","PRESTIGE":"Real Estate",
    "APOLLO":"Defence","DATAPATTNS":"Defence","PARAS":"Defence",
    "AVANTEL":"Defence","JAIBALAJI":"Steel","MUNJALAU":"Auto",
}

RISK = {
    "BHARTIARTL":"LOW","ICICIBANK":"LOW","RELIANCE":"LOW","SUNPHARMA":"LOW",
    "POLYCAB":"MED","HINDUNILVR":"LOW","SUZLON":"MED","COFORGE":"MED",
    "NBCC":"MED","PRESTIGE":"MED",
    "APOLLO":"HIGH","DATAPATTNS":"HIGH","PARAS":"HIGH",
    "AVANTEL":"HIGH","JAIBALAJI":"HIGH","MUNJALAU":"HIGH",
}


# ════════════════════════════════════════════════════════════════════════════
# DATA FETCH
# ════════════════════════════════════════════════════════════════════════════

def fetch_history(yf_ticker):
    """Returns (closes, highs, lows, volumes, latest_prev_close) with Nones stripped."""
    try:
        url = (f"https://query1.finance.yahoo.com/v8/finance/chart/{yf_ticker}"
               f"?interval=1d&range={YF_RANGE}")
        req = urllib.request.Request(url, headers={"User-Agent":"Mozilla/5.0"})
        r   = urllib.request.urlopen(req, timeout=10, context=ctx)
        d   = json.loads(r.read())
        res = d["chart"]["result"][0]
        ind = res["indicators"]["quote"][0]
        raw_c = ind.get("close",  [])
        raw_h = ind.get("high",   [])
        raw_l = ind.get("low",    [])
        raw_v = ind.get("volume", [])
        # Zip and filter out any row where close is None
        rows = [(c,h,l,v) for c,h,l,v in zip(raw_c,raw_h,raw_l,raw_v)
                if c is not None]
        if not rows:
            return None,None,None,None,None
        closes  = [r[0] for r in rows]
        highs   = [r[1] for r in rows]
        lows    = [r[2] for r in rows]
        volumes = [r[3] if r[3] is not None else 0 for r in rows]
        # Use closes[-2] as previous close — chartPreviousClose in 1y range
        # can return the price from a year ago instead of yesterday's close.
        prev = round(closes[-2], 2) if len(closes) >= 2 else round(closes[-1], 2)
        return closes, highs, lows, volumes, prev
    except Exception as e:
        print(f"    WARN fetch {yf_ticker}: {e}")
        return None,None,None,None,None


# ════════════════════════════════════════════════════════════════════════════
# TECHNICAL INDICATORS (pure Python, no external libs)
# ════════════════════════════════════════════════════════════════════════════

def sma(arr, n):
    if len(arr) < n: return None
    return round(sum(arr[-n:]) / n, 2)

def ema_series(arr, period):
    """Returns full EMA series. Seeds with SMA of first `period` values."""
    if len(arr) < period:
        return []
    k  = 2 / (period + 1)
    e  = sum(arr[:period]) / period
    result = [e]
    for p in arr[period:]:
        e = p * k + e * (1 - k)
        result.append(e)
    return result

def calc_rsi(closes, period=14):
    """Wilder smoothed RSI."""
    if len(closes) < period + 2:
        return None
    deltas = [closes[i+1] - closes[i] for i in range(len(closes)-1)]
    gains  = [max(d, 0) for d in deltas]
    losses = [abs(min(d, 0)) for d in deltas]
    ag = sum(gains[:period]) / period
    al = sum(losses[:period]) / period
    for i in range(period, len(gains)):
        ag = (ag * (period-1) + gains[i]) / period
        al = (al * (period-1) + losses[i]) / period
    if al == 0:
        return 100.0
    rs = ag / al
    return round(100 - (100 / (1 + rs)), 1)

def calc_macd(closes, fast=12, slow=26, signal=9):
    """Returns (macd_val, signal_val, histogram, signal_text)."""
    if len(closes) < slow + signal:
        return None, None, None, "Insufficient data"
    e_fast   = ema_series(closes, fast)
    e_slow   = ema_series(closes, slow)
    # Align: e_slow is shorter; offset = slow - fast
    offset   = slow - fast
    macd_line = [ef - es for ef, es in zip(e_fast[offset:], e_slow)]
    sig_line  = ema_series(macd_line, signal)
    if not sig_line:
        return None, None, None, "Insufficient data"
    macd_val  = round(macd_line[-1], 3)
    sig_val   = round(sig_line[-1], 3)
    hist      = round(macd_val - sig_val, 3)
    if macd_val > sig_val and hist > 0:
        txt = "Bullish ▲"
    elif macd_val < sig_val and hist < 0:
        txt = "Bearish ▼"
    else:
        txt = "Neutral →"
    return macd_val, sig_val, hist, txt

def calc_bollinger(closes, period=20, num_std=2):
    """Returns (upper, middle, lower, position_text)."""
    if len(closes) < period:
        return None, None, None, "Insufficient data"
    window = closes[-period:]
    mid    = sum(window) / period
    std    = math.sqrt(sum((x - mid)**2 for x in window) / period)
    upper  = round(mid + num_std * std, 2)
    lower  = round(mid - num_std * std, 2)
    mid    = round(mid, 2)
    live   = closes[-1]
    if live >= upper:
        pos = "Near Upper Band (Overbought)"
    elif live <= lower:
        pos = "Near Lower Band (Oversold)"
    elif live > mid:
        pos = "Above Middle Band"
    else:
        pos = "Below Middle Band"
    return upper, mid, lower, pos

def calc_volume_trend(volumes):
    """Compare 5-day avg vs 20-day avg volume."""
    if len(volumes) < 20:
        return "Insufficient data"
    v5  = sum(volumes[-5:])  / 5
    v20 = sum(volumes[-20:]) / 20
    ratio = v5 / v20 if v20 else 1
    if ratio > 1.3:
        return "Rising ▲ (Strong)"
    elif ratio > 1.1:
        return "Rising ▲"
    elif ratio < 0.7:
        return "Falling ▼ (Weak)"
    elif ratio < 0.9:
        return "Falling ▼"
    else:
        return "Neutral →"

def calc_6m_change(closes):
    """% change over last ~126 trading days (~6 months)."""
    n = min(126, len(closes)-1)
    if n < 20:
        return None
    return round((closes[-1] - closes[-n]) / closes[-n] * 100, 1)

def calc_trend(closes, dma50, dma200):
    """
    Trend classification using 50DMA vs 200DMA + price position.
    Falls back to price slope if 200DMA unavailable.
    Returns: (trend_label, trend_detail)
    """
    live = closes[-1]
    if dma50 is None:
        return "Sideways ➡️", "Insufficient data for trend"
    if dma200 is not None:
        if live > dma50 and dma50 > dma200:
            return "Uptrend ↑", "Price > 50DMA > 200DMA — Strong uptrend"
        elif live < dma50 and dma50 < dma200:
            return "Downtrend ↓", "Price < 50DMA < 200DMA — Strong downtrend"
        elif live > dma200:
            return "Sideways ➡️", "Mixed signals above 200DMA — Consolidating"
        else:
            return "Downtrend ↓", "Price below 200DMA — Caution"
    else:
        # No 200DMA — use 50DMA slope from 10 days ago
        n = min(50, len(closes)-1)
        slope_pct = (dma50 - sma(closes[:-10], 50) or dma50) / dma50 * 100 if sma(closes[:-10], 50) else 0
        if live > dma50 and slope_pct >= 0:
            return "Uptrend ↑", f"Price > 50DMA (200DMA data limited)"
        elif live < dma50:
            return "Downtrend ↓", f"Price < 50DMA (200DMA data limited)"
        else:
            return "Sideways ➡️", f"Price near 50DMA (200DMA data limited)"

def support_resistance(highs, lows, period=50):
    """Recent support and resistance from last N candles."""
    n = min(period, len(highs))
    res = round(max(highs[-n:]), 2)
    sup = round(min(lows[-n:]), 2)
    return sup, res

def rsi_signal(rsi):
    if rsi is None: return "N/A"
    if rsi >= 70:   return f"{rsi} — Overbought ⚠️"
    if rsi <= 30:   return f"{rsi} — Oversold ✅"
    if rsi >= 60:   return f"{rsi} — Bullish"
    if rsi <= 40:   return f"{rsi} — Bearish"
    return f"{rsi} — Neutral"


# ════════════════════════════════════════════════════════════════════════════
# SMART ACTION LOGIC (trend-aware)
# ════════════════════════════════════════════════════════════════════════════

def smart_action(ticker, live, prev, trend, rsi, macd_txt, bb_pos):
    """
    Returns (action_label, bg_color, description)
    Priority: Stop Loss > Trend override > RSI > Price zone > Target
    """
    r = RESEARCH[ticker]
    buy_low, buy_high, sl, t1, t2 = r[0], r[1], r[2], r[3], r[4]

    if live is None:
        return "⚪ CHECK PRICE", "D9D9D9", "Price unavailable — check manually"

    # ── Hard rules ────────────────────────────────────────────────────────
    if live <= sl:
        return "🛑 SELL NOW", "FF2020", f"Stop loss ₹{sl} hit. Exit immediately to protect capital."

    if live >= t2:
        return "🏆 BOOK ALL PROFIT", "6A0DAD", f"Target 2 ₹{t2} achieved! Sell everything and celebrate."

    # ── Price zone flags ─────────────────────────────────────────────────
    in_zone    = buy_low * 0.98 <= live <= buy_high * 1.05
    above_zone = live > buy_high * 1.05
    below_zone = live < buy_low * 0.98
    near_t1    = live >= t1 * 0.97

    # ── Downtrend override ────────────────────────────────────────────────
    if "Downtrend" in trend:
        if in_zone:
            return ("⚠️ WAIT — Downtrend", "FF6600",
                    f"Price in buy zone but stock in downtrend. "
                    f"Wait for 50DMA to turn upward before buying.")
        elif below_zone:
            return ("❌ AVOID — Falling", "DD2200",
                    f"Downtrend active + price below buy zone. "
                    f"Do not catch a falling knife. Wait for trend reversal.")
        else:
            return ("⚠️ CAUTION — Downtrend", "FF6600",
                    f"Downtrend active. If you hold, tighten stop to ₹{sl}. "
                    f"Don't add new money.")

    # ── Near Target 1 ─────────────────────────────────────────────────────
    if near_t1:
        return ("💰 BOOK 50% PROFIT", "2196F3",
                f"Near Target 1 ₹{t1}. Sell 50% to lock in profit. "
                f"Let rest run to Target 2 ₹{t2}.")

    # ── RSI overbought in sideways / no strong uptrend ────────────────────
    if rsi and rsi >= 72 and "Uptrend" not in trend:
        return ("⏳ WAIT — Overbought", "FFAA00",
                f"RSI {rsi} is overbought. Price likely to pull back. "
                f"Wait for RSI to cool below 60 before buying.")

    # ── Sideways trend ────────────────────────────────────────────────────
    if "Sideways" in trend:
        if in_zone:
            if rsi and rsi <= 40:
                return ("✅ BUY — Oversold", "1E8A00",
                        f"In buy zone + RSI {rsi} oversold = good entry. "
                        f"Sideways trend — expect slow but steady move to ₹{t1}.")
            return ("⚠️ BUY CAUTIOUSLY", "E08000",
                    f"In buy zone but sideways trend. Buy a small position (50%). "
                    f"Add more only when uptrend confirms (price > 50DMA).")
        elif above_zone:
            return ("👀 HOLD / WATCH", "4CAF50",
                    f"Above buy zone in sideways market. "
                    f"Wait for dip to ₹{buy_low}–₹{buy_high} to enter.")
        else:
            return ("⏳ WAIT", "FFCC00",
                    f"Below buy zone. Price at ₹{live}, buy zone is ₹{buy_low}–₹{buy_high}.")

    # ── Uptrend ───────────────────────────────────────────────────────────
    if "Uptrend" in trend:
        if in_zone:
            if rsi and rsi >= 70:
                return ("⏳ WAIT — RSI High", "FFAA00",
                        f"In buy zone but RSI {rsi} is high. "
                        f"Wait for RSI to cool to 50–60 before entering.")
            return ("✅ BUY NOW ↑ Uptrend", "1E8A00",
                    f"Uptrend confirmed + price in buy zone ₹{buy_low}–₹{buy_high}. "
                    f"Strong signal. Target 1: ₹{t1}, Target 2: ₹{t2}.")
        elif above_zone:
            if rsi and rsi >= 70:
                return ("⏳ WAIT — Overbought", "FFAA00",
                        f"Uptrend but RSI {rsi} overbought. "
                        f"Wait for dip to ₹{buy_low}–₹{buy_high}.")
            return ("👀 HOLD / WATCH ↑", "4CAF50",
                    f"Uptrend active but price above buy zone. "
                    f"Wait for dip to ₹{buy_low}–₹{buy_high}. If you own it — hold.")
        elif below_zone:
            return ("⏳ WAIT FOR BOUNCE", "FFCC00",
                    f"Uptrend but price below buy zone at ₹{live}. "
                    f"Wait for price to enter ₹{buy_low}–₹{buy_high} before buying.")

    return ("👀 WATCH", "4CAF50", "Monitor — no clear signal right now.")


# ════════════════════════════════════════════════════════════════════════════
# FETCH + COMPUTE ALL DATA
# ════════════════════════════════════════════════════════════════════════════

print("\n" + "="*60)
print("  FETCHING DATA & COMPUTING INDICATORS...")
print("="*60)

all_data = {}

for ticker, yf_ticker in TICKER_MAP.items():
    print(f"  {ticker:<14}", end=" ", flush=True)
    closes, highs, lows, volumes, prev = fetch_history(yf_ticker)

    if closes is None or len(closes) < 30:
        print("⚠ Insufficient data")
        all_data[ticker] = None
        time.sleep(0.2)
        continue

    live = closes[-1]
    dma50  = sma(closes, 50)
    dma200 = sma(closes, 200)
    rsi    = calc_rsi(closes)
    macd_v, sig_v, hist_v, macd_txt = calc_macd(closes)
    bb_up, bb_mid, bb_lo, bb_pos   = calc_bollinger(closes)
    vol_trend  = calc_volume_trend(volumes)
    chg_6m     = calc_6m_change(closes)
    chg_1d     = round((live - prev) / prev * 100, 2) if prev else None
    trend, trend_detail = calc_trend(closes, dma50, dma200)
    sup, res   = support_resistance(highs, lows, 50) if highs else (None, None)
    action, acol, adesc = smart_action(ticker, live, prev, trend, rsi, macd_txt, bb_pos)

    all_data[ticker] = {
        "live": round(live, 2), "prev": prev, "chg_1d": chg_1d,
        "dma50": dma50, "dma200": dma200,
        "rsi": rsi, "rsi_sig": rsi_signal(rsi),
        "macd_v": macd_v, "sig_v": sig_v, "hist_v": hist_v, "macd_txt": macd_txt,
        "bb_up": bb_up, "bb_mid": bb_mid, "bb_lo": bb_lo, "bb_pos": bb_pos,
        "vol_trend": vol_trend, "chg_6m": chg_6m,
        "trend": trend, "trend_detail": trend_detail,
        "support": sup, "resistance": res,
        "action": action, "acol": acol, "adesc": adesc,
    }
    print(f"₹{round(live,2):<9} {trend:<16} RSI:{rsi}  {action}")
    time.sleep(0.2)


# ════════════════════════════════════════════════════════════════════════════
# EXCEL STYLES
# ════════════════════════════════════════════════════════════════════════════

thin  = Side(style="thin",   color="D0D0D0")
thick = Side(style="medium", color="999999")
bd    = Border(left=thin, right=thin, top=thin, bottom=thin)
bthk  = Border(left=thick, right=thick, top=thick, bottom=thick)

def mk(ws, row, col, val="", bg="FFFFFF", fg="000000",
       bold=False, sz=10, ah="center", av="center", wrap=True, bdr=None, it=False):
    cell = ws.cell(row=row, column=col, value=val)
    cell.fill      = PatternFill("solid", fgColor=bg)
    cell.font      = Font(name="Calibri", bold=bold, color=fg, size=sz, italic=it)
    cell.alignment = Alignment(horizontal=ah, vertical=av, wrap_text=wrap)
    cell.border    = bdr or bd
    return cell

def hdr(ws, row, col, val, w=None):
    c = mk(ws, row, col, val, bg="1565C0", fg="FFFFFF", bold=True, sz=10)
    if w: ws.column_dimensions[get_column_letter(col)].width = w
    return c

NOW_STR = datetime.now().strftime("%d %b %Y %H:%M")
PERIOD_LABEL_SAFE = PERIOD_LABEL  # used in sheet titles

# Ordered stock list
ORDERED = [
    "BHARTIARTL","ICICIBANK","RELIANCE","SUNPHARMA","POLYCAB",
    "HINDUNILVR","SUZLON","COFORGE","NBCC","PRESTIGE",
    "APOLLO","DATAPATTNS","PARAS","AVANTEL","JAIBALAJI","MUNJALAU",
]

def row_bg(i, is_mid=False):
    if is_mid: return "F5F0FF" if i % 2 == 0 else "FAF5FF"
    return "F9F9F9" if i % 2 == 0 else "FFFFFF"

RISK_BG   = {"LOW":"E8F5E9","MED":"FFF3E0","HIGH":"FFEBEE"}
RISK_ICON = {"LOW":"🟢 LOW","MED":"🟡 MED","HIGH":"🔴 HIGH"}

TREND_BG  = {
    "Uptrend ↑":   "C8E6C9",
    "Downtrend ↓": "FFCDD2",
    "Sideways ➡️": "FFF9C4",
}

wb = Workbook()


# ════════════════════════════════════════════════════════════════════════════
# SHEET 1 — Quick Actions
# ════════════════════════════════════════════════════════════════════════════
ws1 = wb.active
ws1.title = "🚦 Quick Actions"

ws1.merge_cells("A1:G1")
mk(ws1,1,1, f"🚦  WHAT TO DO WITH EACH STOCK  |  Data: {PERIOD_LABEL}  |  Updated: {NOW_STR}",
   bg="0D47A1", fg="FFFFFF", bold=True, sz=14)
ws1.row_dimensions[1].height = 36

ws1.merge_cells("A2:G2")
mk(ws1,2,1,
   "✅ Green = Buy  |  ⚠️ Orange = Wait (trend issue)  |  👀 Teal = Hold & watch  "
   "|  💰 Blue = Book profit  |  🛑 Red = Sell  |  "
   "Run refresh_stock_prices.py for latest prices",
   bg="E3F2FD", fg="1565C0", sz=10, it=True)
ws1.row_dimensions[2].height = 20

for col,lbl,w in [(1,"#",4),(2,"Company",22),(3,"Live Price",13),
                   (4,"Today ±%",12),(5,"6-Month Trend",18),(6,"RSI Signal",22),(7,"👉 ACTION — What to do right now",64)]:
    hdr(ws1, 3, col, lbl)
    ws1.column_dimensions[get_column_letter(col)].width = w
ws1.row_dimensions[3].height = 30

r = 4
for i, ticker in enumerate(ORDERED):
    if i == 10:
        ws1.merge_cells(f"A{r}:G{r}")
        mk(ws1,r,1,"── MID-CAP & SMALL-CAP  (Higher Risk — Max 5% per stock) ──",
           bg="6A0DAD", fg="FFFFFF", bold=True, sz=11)
        ws1.row_dimensions[r].height = 22
        r += 1

    d  = all_data.get(ticker)
    bg = row_bg(i, i >= 10)

    if d:
        live    = d["live"]
        chg1d   = f"{d['chg_1d']:+.2f}%" if d["chg_1d"] is not None else "N/A"
        trend   = d["trend"]
        rsi_s   = d["rsi_sig"]
        action  = d["action"]
        acol    = d["acol"]
        adesc   = d["adesc"]
        chg_bg  = "DCFCE7" if (d["chg_1d"] or 0) > 0 else ("FFE4E4" if (d["chg_1d"] or 0) < 0 else "F5F5F5")
        tbg     = TREND_BG.get(trend, "F5F5F5")
    else:
        live,chg1d,trend,rsi_s = "N/A","N/A","N/A","N/A"
        action,acol,adesc = "⚪ NO DATA","D9D9D9","Could not fetch price"
        chg_bg,tbg = "F5F5F5","F5F5F5"

    mk(ws1,r,1, i+1,   bg=bg, bold=True, sz=11)
    mk(ws1,r,2, NAMES[ticker], bg=bg, bold=True, sz=11, ah="left")
    mk(ws1,r,3, f"₹{live}" if isinstance(live,float) else live, bg=bg, bold=True, sz=12)
    mk(ws1,r,4, chg1d, bg=chg_bg, bold=True, sz=11)
    mk(ws1,r,5, trend, bg=tbg, bold=True, sz=10)
    mk(ws1,r,6, rsi_s, bg=bg, sz=9)
    mk(ws1,r,7, f"{action}   {adesc}",
       bg=acol, bold=True,
       fg="FFFFFF" if acol in ["1E8A00","FF2020","6A0DAD","2196F3","DD2200","FF6600"] else "1A1A1A",
       ah="left", sz=10)
    ws1.row_dimensions[r].height = 34
    r += 1


# ════════════════════════════════════════════════════════════════════════════
# SHEET 2 — Full Details
# ════════════════════════════════════════════════════════════════════════════
ws2 = wb.create_sheet("📊 Full Details")

ws2.merge_cells("A1:N1")
mk(ws2,1,1, f"📊  COMPLETE STOCK DETAILS  |  Data: {PERIOD_LABEL}  |  Updated: {NOW_STR}",
   bg="0D47A1", fg="FFFFFF", bold=True, sz=14)
ws2.row_dimensions[1].height = 34

hdrs2 = [
    (1,"#",4),(2,"Company",20),(3,"Risk",10),(4,"Live\nPrice",12),
    (5,"Today\n±%",11),(6,"6M Trend",15),(7,"Buy Zone (₹)\nGood price to enter",18),
    (8,"Stop Loss (₹)\nSell if falls here",17),(9,"Target 1 (₹)\nSell 50% here",16),
    (10,"Target 2 (₹)\nSell all here",16),(11,"Max\nProfit",13),(12,"Max\nRisk",11),
    (13,"Support\n(Recent low)",14),(14,"Resistance\n(Recent high)",14),
]
for col,lbl,w in hdrs2:
    hdr(ws2,2,col,lbl)
    ws2.column_dimensions[get_column_letter(col)].width = w
ws2.row_dimensions[2].height = 40

r2 = 3
for i, ticker in enumerate(ORDERED):
    if i == 10:
        ws2.merge_cells(f"A{r2}:N{r2}")
        mk(ws2,r2,1,"── MID-CAP / SMALL-CAP (Higher Risk — Max 5% each) ──",
           bg="6A0DAD", fg="FFFFFF", bold=True, sz=11)
        ws2.row_dimensions[r2].height = 22; r2 += 1

    d   = all_data.get(ticker)
    res = RESEARCH[ticker]
    buy_low, buy_high, sl, t1, t2 = res[0], res[1], res[2], res[3], res[4]
    profit = f"+{round((t2-buy_low)/buy_low*100)}%"
    loss   = f"-{round((buy_low-sl)/buy_low*100)}%"
    bg2    = row_bg(i, i>=10)
    rbg    = RISK_BG[RISK[ticker]]

    live   = f"₹{d['live']}" if d else "N/A"
    chg1d  = f"{d['chg_1d']:+.2f}%" if d and d["chg_1d"] is not None else "N/A"
    trend  = d["trend"] if d else "N/A"
    sup    = f"₹{d['support']}" if d and d["support"] else "N/A"
    res_r  = f"₹{d['resistance']}" if d and d["resistance"] else "N/A"
    tbg    = TREND_BG.get(trend, "F5F5F5") if d else "F5F5F5"
    acol   = d["acol"] if d else "D9D9D9"
    chg_bg = "DCFCE7" if d and (d["chg_1d"] or 0)>0 else ("FFE4E4" if d and (d["chg_1d"] or 0)<0 else "F5F5F5")

    mk(ws2,r2,1,  i+1,                    bg=bg2,  bold=True)
    mk(ws2,r2,2,  NAMES[ticker],          bg=acol, bold=True, sz=10, ah="left",
       fg="FFFFFF" if acol in ["1E8A00","FF2020","DD2200","FF6600","6A0DAD"] else "1A1A1A")
    mk(ws2,r2,3,  RISK_ICON[RISK[ticker]],bg=rbg,  sz=9)
    mk(ws2,r2,4,  live,                   bg=bg2,  bold=True, sz=12)
    mk(ws2,r2,5,  chg1d,                  bg=chg_bg, bold=True)
    mk(ws2,r2,6,  trend,                  bg=tbg,  bold=True, sz=10)
    mk(ws2,r2,7,  f"₹{buy_low}–₹{buy_high}", bg="E8F5E9", bold=True)
    mk(ws2,r2,8,  f"₹{sl}",              bg="FFEBEE", bold=True, fg="C62828")
    mk(ws2,r2,9,  f"₹{t1}",              bg="E3F2FD", bold=True)
    mk(ws2,r2,10, f"₹{t2}",              bg="E8EAF6", bold=True)
    mk(ws2,r2,11, profit,                  bg="E8F5E9")
    mk(ws2,r2,12, loss,                    bg="FFEBEE", fg="C62828")
    mk(ws2,r2,13, sup,                     bg="FFF8E1")
    mk(ws2,r2,14, res_r,                   bg="E8EAF6")
    ws2.row_dimensions[r2].height = 34
    r2 += 1


# ════════════════════════════════════════════════════════════════════════════
# SHEET 3 — Technical Analysis (all indicators)
# ════════════════════════════════════════════════════════════════════════════
ws3 = wb.create_sheet("📈 Technical Analysis")

ws3.merge_cells("A1:M1")
mk(ws3,1,1, f"📈  TECHNICAL INDICATORS  |  Based on: {PERIOD_LABEL} of data  |  Updated: {NOW_STR}",
   bg="0D47A1", fg="FFFFFF", bold=True, sz=14)
ws3.row_dimensions[1].height = 34

ws3.merge_cells("A2:M2")
mk(ws3,2,1,
   "50DMA = average of last 50 days price  |  200DMA = average of last 200 days  |  "
   "RSI <30 = oversold (good buy)  RSI >70 = overbought (wait)  |  "
   "MACD Bullish ▲ = trend strengthening  |  Bollinger = price volatility bands",
   bg="E3F2FD", fg="1565C0", sz=9, it=True)
ws3.row_dimensions[2].height = 22

th3 = [(1,"#",4),(2,"Company",20),(3,"Live\nPrice",12),(4,"50-Day\nMoving Avg",14),
       (5,"200-Day\nMoving Avg",14),(6,"Price vs\n50DMA",13),(7,"Price vs\n200DMA",13),
       (8,"RSI (14)\nMomentum",20),(9,"MACD\nSignal",16),
       (10,"Bollinger\nPosition",24),(11,"6-Month\nPrice Change",14),
       (12,"Volume\nTrend",16),(13,"6-Month\nTrend Verdict",18)]
for col,lbl,w in th3:
    hdr(ws3,3,col,lbl)
    ws3.column_dimensions[get_column_letter(col)].width = w
ws3.row_dimensions[3].height = 40

r3 = 4
for i, ticker in enumerate(ORDERED):
    if i == 10:
        ws3.merge_cells(f"A{r3}:M{r3}")
        mk(ws3,r3,1,"── MID-CAP / SMALL-CAP ──",bg="6A0DAD",fg="FFFFFF",bold=True,sz=11)
        ws3.row_dimensions[r3].height = 22; r3 += 1

    d   = all_data.get(ticker)
    bg3 = row_bg(i, i>=10)

    if d:
        live   = d["live"]
        dma50  = d["dma50"]
        dma200 = d["dma200"]
        v50    = f"{((live-dma50)/dma50*100):+.1f}%" if dma50 else "N/A"
        v200   = f"{((live-dma200)/dma200*100):+.1f}%" if dma200 else "N/A"
        v50_bg  = "DCFCE7" if dma50 and live>dma50 else ("FFE4E4" if dma50 and live<dma50 else "F5F5F5")
        v200_bg = "DCFCE7" if dma200 and live>dma200 else ("FFE4E4" if dma200 and live<dma200 else "FFF9C4")
        rsi_bg  = "FFCDD2" if d["rsi"] and d["rsi"]>=70 else ("DCFCE7" if d["rsi"] and d["rsi"]<=35 else "F5F5F5")
        macd_bg = "DCFCE7" if "Bull" in d["macd_txt"] else ("FFE4E4" if "Bear" in d["macd_txt"] else "F5F5F5")
        chg6_bg = "DCFCE7" if d["chg_6m"] and d["chg_6m"]>0 else ("FFE4E4" if d["chg_6m"] and d["chg_6m"]<0 else "F5F5F5")
        tbg     = TREND_BG.get(d["trend"], "F5F5F5")
    else:
        live=dma50=dma200="N/A"; v50=v200=v50_bg=v200_bg="N/A"
        v50_bg=v200_bg=rsi_bg=macd_bg=chg6_bg=tbg="F5F5F5"

    def sv(key, fmt=None): return (fmt(d[key]) if fmt else d[key]) if d and d.get(key) is not None else "N/A"

    mk(ws3,r3,1,  i+1,              bg=bg3, bold=True)
    mk(ws3,r3,2,  NAMES[ticker],    bg=bg3, bold=True, sz=11, ah="left")
    mk(ws3,r3,3,  f"₹{sv('live')}", bg=bg3, bold=True, sz=11)
    mk(ws3,r3,4,  f"₹{sv('dma50')}" if d and d.get("dma50") else "N/A", bg=bg3)
    mk(ws3,r3,5,  f"₹{sv('dma200')}" if d and d.get("dma200") else "Insuff. data", bg=bg3)
    mk(ws3,r3,6,  v50, bg=v50_bg, bold=True)
    mk(ws3,r3,7,  v200, bg=v200_bg, bold=True)
    mk(ws3,r3,8,  sv('rsi_sig'), bg=rsi_bg, sz=9)
    mk(ws3,r3,9,  sv('macd_txt'), bg=macd_bg, bold=True)
    mk(ws3,r3,10, sv('bb_pos'), bg=bg3, sz=9)
    mk(ws3,r3,11, f"{sv('chg_6m')}%" if d and d.get("chg_6m") is not None else "N/A", bg=chg6_bg, bold=True)
    mk(ws3,r3,12, sv('vol_trend'), bg=bg3, sz=9)
    mk(ws3,r3,13, sv('trend'), bg=tbg if d else "F5F5F5", bold=True, sz=10)
    ws3.row_dimensions[r3].height = 34
    r3 += 1


# ════════════════════════════════════════════════════════════════════════════
# SHEET 4 — Glossary / How to use
# ════════════════════════════════════════════════════════════════════════════
ws4 = wb.create_sheet("📖 How to Use")
ws4.column_dimensions["A"].width = 28
ws4.column_dimensions["B"].width = 78

guide = [
    ("","📖  BEGINNER'S GUIDE & GLOSSARY","0D47A1",True,15),
    ("","","FFFFFF",False,6),
    ("TERM","WHAT IT MEANS","1565C0",True,12),
    ("Live Price","The current price of the stock. Updates every time you run the refresh script.","F0F4FF",False,10),
    ("Today ±%","How much price moved vs yesterday's close. Green = up, Red = down.","F0F4FF",False,10),
    ("Buy Zone","The price range where the stock is considered a good deal. Like a sale window.","E8F5E9",False,10),
    ("Stop Loss","If price hits this level — SELL IMMEDIATELY. Your safety net. Never ignore it.","FFEBEE",False,10),
    ("Target 1","First profit target. Sell 50% of your holding here to lock in profit.","E3F2FD",False,10),
    ("Target 2","Final profit target. Sell the remaining 50% here.","E8EAF6",False,10),
    ("Support","Recent lowest price (last 50 days). Strong buying usually happens near here.","FFF8E1",False,10),
    ("Resistance","Recent highest price (last 50 days). Selling pressure often appears near here.","E8EAF6",False,10),
    ("","","FFFFFF",False,6),
    ("TECHNICAL INDICATORS","","1565C0",True,12),
    ("50-Day Moving Average\n(50 DMA)","Average closing price of the last 50 trading days. Think of it as the medium-term direction. Price above 50DMA = healthy. Price below = caution.","E8F5E9",False,10),
    ("200-Day Moving Average\n(200 DMA)","Average of last 200 days. The big picture trend. Price > 200DMA = long-term uptrend. Golden rule: only buy if price is above 200DMA.","E8F5E9",False,10),
    ("RSI (Relative Strength Index)","Measures momentum on a 0-100 scale. Below 30 = Oversold (good time to buy). Above 70 = Overbought (wait for pullback). 40-60 = Neutral.","FFF3E0",False,10),
    ("MACD","Trend strength indicator. Bullish ▲ = momentum increasing (good to buy). Bearish ▼ = momentum weakening (wait). Neutral = no strong signal.","FFF3E0",False,10),
    ("Bollinger Bands","Volatility bands around the price. Near Upper Band = overbought. Near Lower Band = oversold (potential buy). Near Middle = neutral.","FFF3E0",False,10),
    ("Volume Trend","Is trading activity increasing or decreasing? Rising volume on up days = strong move. Falling volume = weak move (less conviction).","FFF3E0",False,10),
    ("6-Month Trend","Is the stock generally going up, down, or sideways over 6 months? The most important factor for timing your entry.","FFF3E0",False,10),
    ("","","FFFFFF",False,6),
    ("ACTION SIGNALS","WHAT TO DO","1565C0",True,12),
    ("✅ BUY NOW ↑ Uptrend","Strong signal. Price in buy zone AND stock in uptrend. Best time to buy.","1E8A00",True,11),
    ("✅ BUY — Oversold","Price in buy zone AND RSI is oversold. Likely bottom — good entry.","1E8A00",False,10),
    ("⚠️ BUY CAUTIOUSLY","In buy zone but trend is sideways. Buy a small amount first. Add more on confirmation.","E08000",False,10),
    ("👀 HOLD / WATCH ↑","Uptrend active but price above buy zone. If you own it — hold. If not — wait for dip.","4CAF50",False,10),
    ("⏳ WAIT","Price not at right level yet, or RSI too high. Be patient.","FFCC00",False,10),
    ("⏳ WAIT — Overbought","RSI above 70. Price likely to pull back soon. Wait for RSI to cool below 60.","FFAA00",False,10),
    ("⚠️ WAIT — Downtrend","Price looks good but stock is in a downtrend. Do NOT buy a falling stock. Wait.","FF6600",True,11),
    ("❌ AVOID — Falling","Downtrend + price below buy zone. High risk of further fall. Stay away.","DD2200",True,11),
    ("💰 BOOK 50% PROFIT","Stock near Target 1. Sell half to lock in profit. Let the rest run.","2196F3",True,11),
    ("🏆 BOOK ALL PROFIT","Target 2 hit. Sell everything. Book your full profit.","6A0DAD",True,11),
    ("🛑 SELL NOW","STOP LOSS HIT. Sell immediately, no questions asked. Protect your capital.","FF2020",True,11),
    ("","","FFFFFF",False,6),
    ("6-MONTH TREND","WHAT IT MEANS","1565C0",True,12),
    ("Uptrend ↑","Stock making higher highs and higher lows. Price above 50DMA which is above 200DMA. Best scenario to buy.","C8E6C9",False,10),
    ("Sideways ➡️","Stock moving in a range — no clear direction. Buy cautiously with smaller position. Wait for breakout.","FFF9C4",False,10),
    ("Downtrend ↓","Stock making lower highs and lower lows. Price below both 50DMA and 200DMA. Avoid buying until trend reverses.","FFCDD2",False,10),
    ("","","FFFFFF",False,6),
    ("GOLDEN RULES","NEVER BREAK THESE","B71C1C",True,12),
    ("Rule 1 — Diversify","Never put more than 25% in one stock. Spread across at least 5-6 different stocks.","FFF3E0",False,10),
    ("Rule 2 — Stop Loss","Decide your stop loss BEFORE you buy. Set it. Respect it. No exceptions.","FFEBEE",False,10),
    ("Rule 3 — Take Profits","Book 50% at Target 1. Don't wait for the perfect exit — greed kills profits.","E8F5E9",False,10),
    ("Rule 4 — Risk Sizing","🔴 HIGH RISK stocks = max 5% of total money. 🟡 MED = max 10-15%. 🟢 LOW = up to 25%.","FFEBEE",False,10),
    ("Rule 5 — Trend is your friend","Only buy stocks in UPTREND or SIDEWAYS. Never fight a downtrend.","E8F5E9",False,10),
    ("Rule 6 — Refresh often","Run refresh_stock_prices.py on Desktop to get latest prices before any decision.","E3F2FD",False,10),
]

for i, row in enumerate(guide, 1):
    if not row[0] and not row[1]:
        ws4.row_dimensions[i].height = 8
        continue
    if i == 1:
        ws4.merge_cells(f"A{i}:B{i}")
        ca = ws4.cell(row=i, column=1, value=row[1])
        cb = ws4["B1"]
    else:
        ca = ws4.cell(row=i, column=1, value=row[0])
        cb = ws4.cell(row=i, column=2, value=row[1])
    dark = row[2] in ["0D47A1","1565C0","B71C1C","FF2020","1E8A00","6A0DAD","DD2200","FF6600"]
    for cell in ([ca] if i == 1 else [ca, cb]):
        cell.fill      = PatternFill("solid", fgColor=row[2])
        cell.font      = Font(name="Calibri", bold=row[3], color="FFFFFF" if dark else "1A1A1A", size=row[4])
        cell.alignment = Alignment(horizontal="left", vertical="center", wrap_text=True)
        cell.border    = bd
    ws4.row_dimensions[i].height = 28 if row[3] else 22


wb.save(EXCEL_PATH)
print("\n" + "="*60)
print(f"  ✓ SAVED: {EXCEL_PATH}")
print("  Sheets:")
print("    1. 🚦 Quick Actions       — trend-aware buy/sell signals")
print("    2. 📊 Full Details        — buy zone, targets, support/resistance")
print("    3. 📈 Technical Analysis  — 50DMA, 200DMA, RSI, MACD, Bollinger")
print("    4. 📖 How to Use          — beginner glossary + golden rules")
print("="*60)

# Print alerts
print("\n  ACTION ALERTS:")
for ticker in ORDERED:
    d = all_data.get(ticker)
    if d and ("SELL" in d["action"] or "AVOID" in d["action"] or "PROFIT" in d["action"]):
        print(f"  → {ticker:<14} {d['action']}")
print()
