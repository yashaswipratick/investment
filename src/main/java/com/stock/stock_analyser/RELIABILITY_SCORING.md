# Reliability Scoring

This document explains how reliability is interpreted for historical-data backfill results.

## Score to Tier Mapping

| Score Range | Tier |
|---|---|
| `>= 85` | `RELIABLE` |
| `60 - 84` | `PARTIALLY_RELIABLE` |
| `< 60` | `UNRELIABLE` |

## What Triggers Warnings

Warnings are generated when any of the following conditions are found:

1. **Low overall fill-rate**
   - Trigger: overall fill-rate `< 70%`
   - Meaning: too many expected trading-day records are missing in the requested window.

2. **Large consecutive weekday gaps**
   - Trigger: consecutive weekday gaps `> 7`
   - Meaning: there are large missing stretches in the time series that may impact technical indicators.

3. **Problematic chunk status**
   - Trigger: any chunk has status `NSE_ERROR` or `PARTIAL`
   - Meaning:
     - `NSE_ERROR`: NSE fetch failed for that date chunk.
     - `PARTIAL`: data was returned, but chunk fill-rate is lower than expected.

## How to Read the Result Quickly

- **RELIABLE**: Good confidence for strategy calculations and trend analysis.
- **PARTIALLY_RELIABLE**: Use with caution; inspect `reliabilityWarnings` and affected chunk ranges.
- **UNRELIABLE**: Do not use directly for decisions; re-run backfill with safer parameters (smaller chunks, longer delays) and verify missing windows.

## Suggested Operational Actions

- If you see many `PARTIAL` chunks, reduce chunk size (for example, from 6 months to 3 months).
- If `NSE_ERROR` repeats, increase delay between chunk calls and retry during non-peak hours.
- If fill-rate remains below 70%, re-run only the problematic date windows and compare with DB coverage.

