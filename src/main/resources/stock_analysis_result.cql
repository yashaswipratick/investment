CREATE TABLE IF NOT EXISTS stock_analysis_result (
    symbol text,
    analysis_date date,
    total_data_points int,
    data_from date,
    data_to date,
    required_from date,
    window_status text,
    window_message text,
    data_note text,
    technical_json text,
    recommendation_json text,
    created_at timestamp,
    PRIMARY KEY ((symbol), analysis_date)
) WITH CLUSTERING ORDER BY (analysis_date DESC);

