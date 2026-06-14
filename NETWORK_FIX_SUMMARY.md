# Network Connectivity Fix Summary

## Problem
The Spring Boot app couldn't reach NSE (`www.nseindia.com`) from the Walmart corporate Mac while curl commands work from Postman (likely run from personal machine or Postman Cloud).

**Root Cause:**
- Corporate Mac is on Walmart network that blocks direct access to external NSE
- Requires outbound traffic through corporate proxy: `proxy.wal-mart.com:9080`
- curl in Postman runs from a different machine/context (personal laptop with unblocked internet)

---

## Solution Implemented

### 1. Made Proxy Optional with Flag
- **File:** `src/main/resources/application.yml`
- **New setting:** `nse.proxy.enabled` (default: `false`)
- **Benefit:** Works on both corporate Mac (with proxy) and personal laptop (without proxy)

### 2. Updated HTTP Entry Loaders
- **Files:**
  - `StockDescriptionHttpEntryLoader.java`
  - `StockHistoryDataHttpEntryLoader.java`
- **Changes:**
  - Added `@Value("${nse.proxy.enabled:false}")` to inject proxy flag
  - Added `@Value("${nse.proxy.host:}")` to inject proxy hostname
  - Added `@Value("${nse.proxy.port:8080}")` to inject proxy port
  - Modified `buildHttpClient()` to check: `if (proxyEnabled && StringUtils.isNotBlank(proxyHost))` before applying proxy
  - Removed `DefaultAddressResolverGroup.INSTANCE` (was forcing JVM DNS, not honoring proxy)

### 3. Cleaned Up Previous Hardcoded Path
- **File:** `ReadCookie.java`
- **Change:** Already fixed to use configurable `${user.home}/nse-cookie.txt`

### 4. Added Documentation
- **File:** `NSE_PROXY_SETUP.md` (in project root)
- **Contains:** Setup instructions, troubleshooting, and scenario-based configurations

---

## For Walmart Corporate Mac (Current Setup)

**In `application.yml`:**
```yaml
nse:
  proxy:
    enabled: true              # ← Set to true
    host: proxy.wal-mart.com   # ← Corporate proxy
    port: 9080
  cookie:
    file-path: ${user.home}/nse-cookie.txt
```

**Then restart the app:**
```bash
mvn clean install
mvn spring-boot:run
```

**Test:**
```bash
curl -X GET "http://localhost:8080/stock/investment/v1.0/stockDescription" \
  -H "Accept: application/json"
```

**Expected result:**
- ✅ **HTTP 200** with stock data (NSE reachable through proxy)
- ⚠️ **HTTP 503** with clean error (proxy works, but NSE blocked by firewall rules)
- ❌ **HTTP 500** with stack trace (proxy config wrong)

If you get **HTTP 503**, contact Walmart IT and ask them to whitelist these domains in the proxy:
- `www.nseindia.com`
- `archives.nseindia.com`

---

## For Personal Laptop

**In `application.yml`:** (default is already set)
```yaml
nse:
  proxy:
    enabled: false             # ← No proxy needed
    host: ""
    port: 8080
  cookie:
    file-path: ${user.home}/nse-cookie.txt
```

**Then restart the app:**
```bash
mvn spring-boot:run
```

**Test:**
```bash
curl -X GET "http://localhost:8080/stock/investment/v1.0/stockDescription" \
  -H "Accept: application/json"
```

**Expected result:**
- ✅ **HTTP 200** with stock data (direct connection to NSE)

---

## Files Changed

| File | Change | Reason |
|------|--------|--------|
| `application.yml` | Added `nse.proxy.enabled`, `nse.proxy.host`, `nse.proxy.port` | Make proxy optional and configurable |
| `StockDescriptionHttpEntryLoader.java` | Added proxy flag check, removed `DefaultAddressResolverGroup.INSTANCE` | Support optional proxy routing |
| `StockHistoryDataHttpEntryLoader.java` | Added proxy flag check, removed `DefaultAddressResolverGroup.INSTANCE` | Support optional proxy routing |
| `ReadCookie.java` | Already configurable via `${user.home}/nse-cookie.txt` | No changes needed |

---

## Key Insights

1. **curl from Postman works** because Postman likely runs from your personal machine (unblocked) or Postman Cloud (unrestricted network)
2. **JVM DNS doesn't honour proxy** — we had to use Netty's `ProxyProvider` instead of `DefaultAddressResolverGroup.INSTANCE`
3. **Corporate proxy has exemptions** — the "No Proxy for" list includes Walmart domains but NOT NSE, so NSE requests must go through proxy
4. **Cookie is secondary** — even with a perfect cookie, DNS must first resolve NSE to an IP; the proxy handles that

---

## Next Steps

1. **Ensure NSE cookie is fresh** (`~/.nse-cookie.txt`)
   - Open https://www.nseindia.com in browser
   - Copy all cookies from DevTools → Application → Cookies
   - Paste into `~/.nse-cookie.txt`

2. **Update `application.yml` for corporate Mac:**
   ```yaml
   nse:
     proxy:
       enabled: true
       host: proxy.wal-mart.com
       port: 9080
   ```

3. **Restart the app and test:**
   ```bash
   mvn clean spring-boot:run
   ```

4. **If HTTP 503 error, contact Walmart IT** to whitelist NSE domains in the proxy

---

## Technical Details

**Netty Proxy Implementation:**
```java
if (proxyEnabled && StringUtils.isNotBlank(proxyHost)) {
    httpClient = httpClient.proxy(spec -> spec
            .type(ProxyProvider.Proxy.HTTP)
            .host(proxyHost)
            .port(proxyPort));
}
```

This tells Netty's HTTP client to:
1. Connect to `proxy.wal-mart.com:9080`
2. Tunnel all HTTPS requests through the proxy
3. Let the proxy resolve DNS and forward cookies

---

## Related Documents
- See `NSE_PROXY_SETUP.md` for detailed setup guide and troubleshooting


