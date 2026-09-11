-- Run once against the existing realtime_stock_data keyspace.
-- Adds persistence for the new fundamental analysis JSON payload.
USE realtime_stock_data;

ALTER TABLE stock_analysis_result ADD fundamental_json text;
