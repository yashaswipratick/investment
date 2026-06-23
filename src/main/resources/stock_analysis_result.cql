-- stock_analysis_result
-- Stores technical analysis results per stock symbol, per analysis period, per date.
--
-- period_label values: 6M | 1Y | 2Y | 3Y
--   Each period uses its own candle window so indicators reflect the correct historical context:
--   6M  → last 126 trading days
--   1Y  → last 252 trading days
--   2Y  → last 504 trading days
--   3Y  → last 756 trading days
--
-- A single /analyse call with lookbackDays=756 writes 4 rows:
--   (INFY, 3Y, 2026-06-23), (INFY, 2Y, 2026-06-23), (INFY, 1Y, 2026-06-23), (INFY, 6M, 2026-06-23)
--
-- To recreate in Cassandra:
--   USE realtime_stock_data;
--   DROP TABLE IF EXISTS stock_analysis_result;
--   <paste DDL below>

CREATE TABLE IF NOT EXISTS stock_analysis_result (
    symbol              text,
    period_label        text,
    analysis_date       date,
    total_data_points   int,
    data_from           date,
    data_to             date,
    required_from       date,
    window_status       text,
    window_message      text,
    data_note           text,
    technical_json          text,
    recommendation_json     text,
    projections_json        text,   -- period-wise return projections: 3M,6M,9M,1Y,2Y,3Y,5Y
    entry_timing_json       text,   -- is NOW a good time to invest + trigger to watch
    stop_loss_strategy_json text,   -- stop-loss, recovery plan, re-entry condition
    created_at              timestamp,
    PRIMARY KEY ((symbol), period_label, analysis_date)
) WITH CLUSTERING ORDER BY (period_label ASC, analysis_date DESC)
  AND comment = 'Stock analysis results keyed by symbol + period (6M/1Y/2Y/3Y) + date';

