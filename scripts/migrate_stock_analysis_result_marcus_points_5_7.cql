-- MARCUS Points 5-7: persist hard criteria and authoritative decision state.
USE realtime_stock_data;
ALTER TABLE stock_analysis_result ADD technical_criteria_json text;
ALTER TABLE stock_analysis_result ADD fundamental_criteria_json text;
ALTER TABLE stock_analysis_result ADD marcus_decision text;
ALTER TABLE stock_analysis_result ADD decision_reason text;
