# Stock Analyser — cURL Reference

Base URL: `http://localhost:8080`

## `lookbackDays` — why it matters

| Value | Coverage | Notes |
|-------|----------|-------|
| `250` | ~1 year  | **Minimum** — SMA200 barely covered, zero buffer for gaps. Stocks with missing days may silently fail SMA200. |
| `500` | ~2 years | Acceptable — safe SMA200 buffer, misses full market cycle. |
| `750` | ~3 years | **Recommended** — reliable SMA200 + Golden/Death Cross, covers at least one full bull+bear cycle. |

---

## 1. Analyse Stock — 3 Years, WITH AI Commentary ✅ Recommended

```bash
curl -X POST http://localhost:8080/stock/investment/v1.0/stockAnalyser/analyse \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "INFY",
    "lookbackDays": 750,
    "includeAiCommentary": true
  }'
```

---

## 2. Analyse Stock — 3 Years, WITHOUT AI Commentary

Skips the OpenAI call — faster, works without a valid API key.

```bash
curl -X POST http://localhost:8080/stock/investment/v1.0/stockAnalyser/analyse \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "RELIANCE",
    "lookbackDays": 750,
    "includeAiCommentary": false
  }'
```

---

## 3. Analyse Stock — 2 Years (acceptable)

```bash
curl -X POST http://localhost:8080/stock/investment/v1.0/stockAnalyser/analyse \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "TCS",
    "lookbackDays": 500,
    "includeAiCommentary": true
  }'
```

---

## 4. Analyse Stock — 1 Year (minimum, use with caution ⚠️)

```bash
curl -X POST http://localhost:8080/stock/investment/v1.0/stockAnalyser/analyse \
  -H "Content-Type: application/json" \
  -d '{
    "symbol": "HDFCBANK",
    "lookbackDays": 250,
    "includeAiCommentary": true
  }'
```

> ⚠️ 250 days is the absolute minimum for SMA200 to have any values at all.
> Any stock with missing trading days (holidays, halts) may be silently skipped
> from SMA200-based strategies. Use 750 wherever possible.

---

## 5. OpenAI API Key Status

Returns the key validation result evaluated once at application startup.

```bash
curl -X GET http://localhost:8080/stock/investment/v1.0/stockAnalyser/openai/key-status
```

### Example response — key valid

```json
{
  "keyValid": true,
  "validationMessage": "VALID",
  "aiCommentaryEnabled": true,
  "checkedAt": "2026-06-15T14:32:01.123Z"
}
```

### Example response — key file missing or empty

```json
{
  "keyValid": false,
  "validationMessage": "MISSING_OR_EMPTY_KEY_FILE",
  "aiCommentaryEnabled": false,
  "checkedAt": "2026-06-15T14:32:01.123Z"
}
```

### `validationMessage` reference

| Value                       | Meaning                                                        |
|-----------------------------|----------------------------------------------------------------|
| `VALID`                     | Key accepted by OpenAI (`/v1/models` returned HTTP 200)        |
| `INVALID_HTTP_401`          | Key rejected — wrong or expired                                |
| `INVALID_HTTP_429`          | Rate-limited — key is valid but quota exhausted                |
| `VALIDATION_ERROR`          | Network or unexpected runtime error during startup validation  |
| `MISSING_OR_EMPTY_KEY_FILE` | File path not configured, file not found, or file is blank     |
| `NOT_VALIDATED`             | Service has not yet completed startup                          |

---

## Request field reference

| Field                | Type    | Required | Description                                                           |
|----------------------|---------|----------|-----------------------------------------------------------------------|
| `symbol`             | string  | yes      | NSE stock symbol, e.g. `INFY`, `RELIANCE`, `TCS`                     |
| `lookbackDays`       | integer | yes      | Historical trading days — use `750` for 3 years (recommended)        |
| `includeAiCommentary`| boolean | no       | `true` → appends GPT-5.5 commentary; requires valid OpenAI key        |

---

## Configuration reference (`application.yml`)

```yaml
openai:
  api-key: /Users/y0p03mn/Documents/OPEN_API_KEY.txt   # file path containing the raw API key
  model-name: gpt-5.5
  service-tier: priority
```

The key file is read **once** at startup (`@PostConstruct`) and validated against
`https://api.openai.com/v1/models` with an 8-second timeout.
