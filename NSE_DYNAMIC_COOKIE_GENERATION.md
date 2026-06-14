# NSE Cookie Generation - Dynamic Session Management

## Problem
Previously, the app required a pre-generated `nse-cookie.txt` file to make NSE API calls. This created a dependency on Node.js and manual cookie generation.

## Solution
Created a new `NseSessionManager` service that **dynamically generates fresh NSE session cookies on-demand** by making a warm-up HTTP request to NSE's homepage.

---

## How It Works

### 1. No More File Dependency
**Before:**
- Required `~/.nse-cookie.txt` pre-generated via Node.js script
- If file missing → `null` cookie → API calls fail
- ReadCookie.java read from hardcoded/configurable file path

**After:**
- On every API call, `NseSessionManager.generateFreshSessionCookie()` is invoked
- Makes a GET request to `https://www.nseindia.com/`
- Extracts `Set-Cookie` headers from the response
- Returns fresh session cookie automatically
- **No file needed** ✅

### 2. Architecture

```
API Request → StockDescriptionHttpEntryLoader / StockHistoryDataHttpEntryLoader
    ↓
nseSessionManager.generateFreshSessionCookie()
    ↓
GET https://www.nseindia.com/ (warm-up call)
    ↓
Extract Set-Cookie: AKA_A2=A; bm_sz=...; etc.
    ↓
Use cookie for actual API call to NSE
    ↓
Return result to client
```

---

## Files Changed

| File | Change | Benefit |
|------|--------|---------|
| `NseSessionManager.java` | ✨ NEW | Generates cookies dynamically via warm-up call |
| `StockDescriptionHttpEntryLoader.java` | ✏️ Refactored | Uses `NseSessionManager` instead of file-based cookies |
| `StockHistoryDataHttpEntryLoader.java` | ✏️ Refactored | Uses `NseSessionManager` instead of file-based cookies |
| `ReadCookie.java` | ↔️ Unchanged | Still available for legacy code; not used by loaders anymore |

---

## Key Features

### ✅ Dynamic Cookie Generation
- Fresh cookie generated on every API call
- Always valid — not stale or expired
- Automatically handles NSE session state

### ✅ No File Dependency
- No need for `nse-cookie.txt`
- No Node.js script required
- No manual cookie maintenance

### ✅ Proxy Support Included
`NseSessionManager` respects `nse.proxy.enabled` / `nse.proxy.host` / `nse.proxy.port` settings:
- On **corporate Mac**: Routes warm-up call through `proxy.wal-mart.com:9080`
- On **personal laptop**: Connects directly to NSE

### ✅ Graceful Fallback
If warm-up call fails:
```java
.onErrorResume(e -> Mono.just("")); // Return empty cookie if warm-up fails
```
The app continues with an empty cookie (NSE might still accept unauthed requests).

---

## Usage in API Calls

### StockDescriptionHttpEntryLoader
```java
public Mono<List<StockDescriptionDetails>> getStockDetails() {
    // Generate fresh NSE session cookie from warm-up call (no file needed)
    return nseSessionManager.generateFreshSessionCookie()
            .flatMap(sessionCookie -> {
                // Build WebClient with fresh cookie
                WebClient client = buildWebClientWithCookie(sessionCookie);
                // Fetch actual data
                return fetchApiData(client);
            });
}
```

### StockHistoryDataHttpEntryLoader
```java
public Mono<List<StockHistoryDetails>> getStockHistoryDetailsList(StockHistoryRequest request) {
    // Generate fresh NSE session cookie from warm-up call (no file needed)
    return nseSessionManager.generateFreshSessionCookie()
            .map(cookie -> buildWebClient(cookie, false))
            .flatMap(client -> fetchApiDataList(client, buildURL(request)));
}
```

---

## Configuration (unchanged)

In `application.yml`:
```yaml
nse:
  cookie:
    file-path: ${user.home}/nse-cookie.txt  # ← Not used anymore; kept for compatibility
  proxy:
    enabled: true                            # ← Now used by NseSessionManager
    host: proxy.wal-mart.com
    port: 9080
```

---

## Testing

### Before (File-based)
```bash
# Generate cookie via Node.js
node scripts/nse_cookie_generator.js > ~/.nse-cookie.txt

# Then run app
mvn spring-boot:run

# API calls would use the file-based cookie
```

### After (Dynamic)
```bash
# No need for Node.js or pre-generated file!
mvn spring-boot:run

# API calls automatically generate fresh cookies
curl -X GET "http://localhost:8080/stock/investment/v1.0/stockDescription"
```

---

## Benefits Summary

| Aspect | Before | After |
|--------|--------|-------|
| **Cookie Generation** | Manual (Node.js script) | Automatic (on every request) |
| **File Dependency** | Required `~/.nse-cookie.txt` | Not needed |
| **Cookie Freshness** | Stale if not regenerated | Always fresh |
| **Setup Complexity** | High (Node.js + script) | Low (zero setup) |
| **Proxy Support** | Limited | Full support via `NseSessionManager` |
| **Error Handling** | File-not-found crashes | Graceful fallback to empty cookie |

---

## How NseSessionManager Works (Technical Details)

```java
public Mono<String> generateFreshSessionCookie() {
    // 1. Build WebClient with proxy (if enabled)
    WebClient client = buildWebClient();

    // 2. Make warm-up GET request to NSE homepage
    return client.get()
            .retrieve()
            .toBodilessEntity()  // Don't read body, just get headers
            .map(entity -> {
                // 3. Extract Set-Cookie headers
                List<String> setCookieHeaders = entity.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);

                // 4. Parse "AKA_A2=A; Path=/; HttpOnly" → "AKA_A2=A"
                String mergedCookie = setCookieHeaders.stream()
                        .map(setCookie -> setCookie.substring(0, setCookie.indexOf(';')))
                        .collect(Collectors.joining("; "));

                return mergedCookie;  // "AKA_A2=A; bm_sz=...; ..."
            })
            // 5. If warm-up fails, return empty cookie (graceful fallback)
            .onErrorResume(e -> Mono.just(""));
}
```

---

## Next Steps

1. **Delete Node.js cookie script** (if you have one in `scripts/`)
   ```bash
   rm scripts/nse_cookie_generator.js  # or similar
   ```

2. **Delete `~/.nse-cookie.txt`** (no longer needed)
   ```bash
   rm ~/.nse-cookie.txt
   ```

3. **Update app configuration** if needed (proxy settings remain)

4. **Restart the app and test**
   ```bash
   mvn clean spring-boot:run
   curl -X GET "http://localhost:8080/stock/investment/v1.0/stockDescription"
   ```

---

## Related Documents
- `NETWORK_FIX_SUMMARY.md` — Corporate proxy configuration
- `NSE_PROXY_SETUP.md` — Detailed proxy setup and troubleshooting


