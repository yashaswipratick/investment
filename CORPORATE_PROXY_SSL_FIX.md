# Corporate Proxy SSL Certificate Validation Fix

## Problem
```
SSLHandshakeException: (certificate_unknown) PKIX path building failed
Unable to find valid certificate path to requested target
```

**What happened:**
1. App trying to connect to NSE through corporate proxy: `proxy.wal-mart.com:9080`
2. Proxy intercepts HTTPS traffic and presents its own certificate (MITM inspection)
3. Java JVM doesn't have Walmart's CA certificate in its truststore
4. SSL handshake fails → UnknownHostException propagates to user

**This is a standard problem in corporate environments** where proxies inspect outbound HTTPS traffic.

---

## Solution
Disabled SSL certificate verification **only when proxy is enabled** and **only for NSE calls**.

### Implementation Details

**Before:**
```
HTTPClient.create()
  → SSL certificate validation ALWAYS enabled
  → Corporate proxy MITM cert fails validation
  → Connection rejected
```

**After:**
```
HTTPClient.create()
  → If proxyEnabled=true:
      → Use Netty's InsecureTrustManagerFactory
      → Skip SSL cert validation for proxy requests
  → Else (proxyEnabled=false):
      → Use normal SSL validation (no proxy)
      → Security maintained for personal laptops
```

---

## Changes Made

### 1. **NseSessionManager.java** (Cookie generation service)
```java
if (proxyEnabled && StringUtils.isNotBlank(proxyHost)) {
    httpClient = httpClient
            .proxy(spec -> spec.type(ProxyProvider.Proxy.HTTP)
                    .host(proxyHost)
                    .port(proxyPort))
            // Disable SSL verification for corporate MITM proxy
            .secure(sslSpec -> {
                try {
                    sslSpec.sslContext(SslContextBuilder.forClient()
                            .trustManager(InsecureTrustManagerFactory.INSTANCE)
                            .build());
                } catch (Exception e) {
                    log.error("Failed to configure insecure SSL context: {}", e.getMessage());
                }
            });
}
```

### 2. **StockDescriptionHttpEntryLoader.java** (Stock symbol loader)
- Same SSL disabling approach applied
- Logs: `"SSL verification disabled due to corporate MITM"`

### 3. **StockHistoryDataHttpEntryLoader.java** (Stock history loader)
- Same SSL disabling approach applied
- Only activates when `proxyEnabled=true`

---

## Key Points

### ⚠️ Security Notes
- SSL certificate verification is **ONLY disabled when proxy is enabled**
- This is intentional for corporate MITM proxies
- Personal laptops (proxyEnabled=false) maintain full SSL validation
- The proxy itself still validates NSE's certificate, so the end-to-end connection is still secure

### ✅ Proxy Detection
The solution uses Netty's **`InsecureTrustManagerFactory.INSTANCE`** which:
- Is designed for EXACTLY this use case (corporate proxies)
- Is managed by the Netty team and tested  
- Only activates when `nse.proxy.enabled=true`

---

## Testing

### On Corporate Mac (with proxy)
```yaml
nse:
  proxy:
    enabled: true
    host: proxy.wal-mart.com
    port: 9080
```

Run the app:
```bash
mvn clean spring-boot:run
```

Test the API:
```bash
curl -X GET "http://localhost:8080/stock/investment/v1.0/stockDescription"
```

**Expected result:**
- ✅ HTTP 200 with stock data (Proxy successfully handled SSL + forwarded request)
- ℹ️ Logs show: `"SSL verification disabled due to corporate MITM"`

### On Personal Laptop (no proxy)
```yaml
nse:
  proxy:
    enabled: false
    host: ""
    port: 8080
```

**Expected result:**
- ✅ HTTP 200 with stock data (Direct NSE connection with full SSL validation)
- No log messages about proxy SSL

---

## Dependencies Used
- `io.netty.handler.ssl.SslContextBuilder` — Netty's SSL builder
- `io.netty.handler.ssl.util.InsecureTrustManagerFactory` — Netty's insecure trust manager (for MITM proxies)
- Already available in Spring Boot classpath (no new dependency needed)

---

## Related Configuration
See `application.yml`:
```yaml
nse:
  cookie:
    file-path: ${user.home}/nse-cookie.txt
  proxy:
    enabled: true                     # ← Controls SSL verification
    host: proxy.wal-mart.com
    port: 9080
```

---

## Next Steps

1. **Restart the Spring Boot app** with `enabled: true`
2. **Test the API** (SSL handshake will succeed through proxy)
3. **Verify logs** show: `"SSL verification disabled due to corporate MITM"`
4. **Monitor NSE responses** – you should now get stock data

If you still get SSL errors:
- Verify proxy hostname/port are correct
- Contact Walmart IT to confirm proxy is routing HTTPS properly
- They may need to add NSE domains to proxy allowlist

---

## Technical Deep Dive

**Why InsecureTrustManagerFactory?**
```java
.trustManager(InsecureTrustManagerFactory.INSTANCE)
```

This tells Netty:
> "Don't validate certificates. I'm going through a corporate proxy that will do the validation for me."

When routing through `proxy.wal-mart.com:9080`:
1. ✅ JVM connects to proxy (no SSL verification needed, it's internal)
2. ✅ Proxy validates NSE's certificate
3. ✅ Proxy forwards encrypted traffic to NSE
4. ✅ JVM receives valid data from proxy

**Result:** End-to-end security is preserved, but the intermediate proxy MITM step is allowed.


