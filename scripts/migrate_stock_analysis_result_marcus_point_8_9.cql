USE realtime_stock_data;

ALTER TABLE stock_analysis_result ADD analysis_execution_date date;
ALTER TABLE stock_analysis_result ADD market_data_latest_date date;
ALTER TABLE stock_analysis_result ADD fundamental_latest_period date;
