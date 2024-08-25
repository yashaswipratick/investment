CREATE TYPE IF NOT EXISTS stock_overview (
    open DOUBLE,
    previous_close DOUBLE,
    volume TEXT,
    value_in_lacs DOUBLE,
    vwap DOUBLE,
    beta DOUBLE,
    market_cap_in_cr DOUBLE,
    high DOUBLE,
    low DOUBLE,
    uc_limit DOUBLE,
    lc_limit DOUBLE,
    fifty_two_weeks_high DOUBLE,
    fifty_two_weeks_low DOUBLE,
    face_value INT,
    all_time_high DOUBLE,
    all_time_low DOUBLE,
    twenty_day_avg_vol TEXT,
    twenty_day_avg_delivery_percentage DOUBLE,
    book_value_per_share DOUBLE,
    dividend_yield DOUBLE,
    ttm_eps TEXT,
    ttm_pe TEXT,
    pb_ratio TEXT,
    sector_pe DOUBLE
);
CREATE TABLE IF NOT EXISTS stock_overview_details (
    date DATE PRIMARY KEY,
    company_stock_overview MAP<TEXT, FROZEN<stock_overview>>,
    created_date TIMESTAMP
);