# NSE Proxy Configuration Guide

## Overview
This application fetches stock data from the National Stock Exchange (NSE) website. Depending on your network environment, you may need to configure a corporate proxy to access NSE.

---

## Configuration

All NSE proxy settings are in `src/main/resources/application.yml`:

```yaml
nse:
  proxy:
    # Enable/disable corporate proxy for NSE access.
    # Set enabled=true on corporate network; false on personal laptop
    enabled: false
    # Corporate proxy host and port (only used if enabled=true)
    host: ""
    port: 8080
```

---

## Scenarios

### Scenario 1: Personal Laptop (Default)
**Location:** Home or non-corporate network  
**Configuration:**
```yaml
nse:
  proxy:
    enabled: false
    host: ""
    port: 8080
```
**Result:** App connects directly to NSE without proxy. No issues.

---

### Scenario 2: Walmart Corporate Mac (Current)
**Location:** Walmart corporate network  
**Proxy:** `proxy.wal-mart.com:9080`  
**Configuration:**
```yaml
nse:
  proxy:
    enabled: true
    host: proxy.wal-mart.com
    port: 9080
```
**Result:** All NSE API calls (`www.nseindia.com`, `archives.nseindia.com`) route through the corporate proxy.

**Note:** The proxy has a "No Proxy for" list that includes Walmart domains (`*.wal-mart.com`, `*.walmart.com`). NSE is NOT on this list, so all traffic routes through the proxy ✅

---

### Scenario 3: Other Corporate Network
**Location:** Non-Walmart corporate network  
**Proxy:** Ask your IT team for the outbound HTTPS proxy hostname and port  
**Configuration:**
```yaml
nse:
  proxy:
    enabled: true
    host: proxy.yourcompany.com
    port: 8080  # or whatever your company uses
```

---

## How It Works

When `nse.proxy.enabled=true`:
1. **DNS Resolution:** Proxy resolves `www.nseindia.com` → IP address
2. **HTTPS Tunnel:** All NSE traffic is tunneled through the proxy
3. **Cookie Forwarding:** Session cookies from `nse-cookie.txt` are forwarded to NSE

When `nse.proxy.enabled=false`:
- App connects directly to NSE (no proxy intermediary)
- Requires direct internet access to `www.nseindia.com`

---

## Affected Components

**StockDescriptionHttpEntryLoader**
- Fetches `https://archives.nseindia.com/content/equities/EQUITY_L.csv` (list of all stock symbols)
- Called by: `GET /stock/investment/v1.0/stockDescription`

**StockHistoryDataHttpEntryLoader**
- Fetches historical price data from NSE API
- Called by: `POST /stock/investment/v1.0/stockHistoryDetail`, etc.

Both loaders check the `nse.proxy.enabled` flag at runtime and apply the proxy if needed.

---

## Testing

### On Personal Laptop
```bash
# Should work without proxy
curl -X GET "http://localhost:8080/stock/investment/v1.0/stockDescription" \
  -H "Accept: application/json"
```

### On Walmart Corporate Mac
```bash
# Update application.yml to enable proxy, then restart app
curl -X GET "http://localhost:8080/stock/investment/v1.0/stockDescription" \
  -H "Accept: application/json"
```

**Expected responses:**
- ✅ **HTTP 200** + stock data → NSE is reachable
- ⚠️ **HTTP 503** + "NSE is currently unreachable" → Proxy config is correct, but NSE is blocked by proxy rules
- ❌ **HTTP 500** + stack trace → Proxy config is wrong (host/port invalid)

---

## Troubleshooting

### "NSE is currently unreachable. Check network/VPN or proxy settings."
**Cause:** Either proxy is wrong, or NSE is blocked by proxy rules  
**Action:**
1. Verify `proxy.wal-mart.com:9080` is correct (ask IT team)
2. Ask IT to whitelist `www.nseindia.com` and `archives.nseindia.com` in the proxy
3. Test curl directly from terminal: `curl https://www.nseindia.com/`

### "Proxy enabled but host is empty."
**Cause:** `nse.proxy.enabled=true` but `nse.proxy.host=""` (blank)  
**Action:** Either set the host or disable the proxy.

### Works on Postman but not in app
**Cause:** Postman runs from your terminal (different network context), app runs from JVM (different DNS/proxy context)  
**Action:** Ensure `application.yml` has correct proxy settings for your current machine.

---

## For Developers

When switching between machines:

**Personal Mac → Walmart Corporate Mac**
```yaml
nse:
  proxy:
    enabled: true  # ← change this
    host: proxy.wal-mart.com
    port: 9080
```
Then restart the app.

**Walmart Corporate Mac → Personal Mac**
```yaml
nse:
  proxy:
    enabled: false  # ← change this
    host: ""
    port: 8080
```
Then restart the app.

---

## Cookie File

The app also needs a valid NSE session cookie. Store it in:
- **Default:** `~/.nse-cookie.txt` (or set via `nse.cookie.file-path` in `application.yml`)

The cookie file should contain something like:
```
AKA_A2=A; bm_sz=...; _ga=...; ak_bmsc=...; bm_sv=...
```

Get a valid cookie by:
1. Open `https://www.nseindia.com/` in your browser
2. Open DevTools → Application → Cookies
3. Copy all cookies, paste into `~/.nse-cookie.txt`
4. Restart the app

---

## Code References

- `StockDescriptionHttpEntryLoader.java` — builds WebClient with optional proxy
- `StockHistoryDataHttpEntryLoader.java` — builds WebClient with optional proxy
- `application.yml` — `nse.proxy.*` configuration


