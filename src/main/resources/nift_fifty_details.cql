CREATE TYPE quote_preopen_status (
    equity_time TEXT,
    pre_open_time TEXT,
    quote_pre_open_flag BOOLEAN
);
CREATE TYPE meta (
    symbol TEXT,
    company_name TEXT,
    industry TEXT,
    active_series LIST<TEXT>,
    debt_series LIST<TEXT>,
    is_fno_sec BOOLEAN,
    is_ca_sec BOOLEAN,
    is_slb_sec BOOLEAN,
    is_debt_sec BOOLEAN,
    is_suspended BOOLEAN,
    temp_suspended_series LIST<TEXT>,
    is_etf_sec BOOLEAN,
    is_delisted BOOLEAN,
    isin TEXT,
    slb_isin TEXT,
    listing_date TEXT,
    is_municipal_bond BOOLEAN,
    quotepreopenstatus FROZEN<quote_preopen_status>
);
CREATE TABLE stock_data (
    symbol TEXT PRIMARY KEY,
    priority INT,
    identifier TEXT,
    series TEXT,
    open DOUBLE,
    day_high DOUBLE,
    day_low DOUBLE,
    last_price DOUBLE,
    previous_close DOUBLE,
    change DOUBLE,
    p_change DOUBLE,
    total_traded_volume BIGINT,
    total_traded_value DOUBLE,
    last_update_time TEXT,
    ffmc DOUBLE,
    year_high DOUBLE,
    year_low DOUBLE,
    near_wkh DOUBLE,
    near_wkl DOUBLE,
    per_change_365d DOUBLE,
    date_365d_ago TEXT,
    chart_365d_path TEXT,
    date_30d_ago TEXT,
    per_change_30d DOUBLE,
    chart_30d_path TEXT,
    chart_today_path TEXT,
    meta FROZEN<meta>
);
CREATE TYPE advance (
    declines TEXT,
    advances TEXT,
    unchanged TEXT
);
CREATE TABLE nifty_fifty_details (
    name TEXT PRIMARY KEY,
    advance FROZEN<advance>,
    nifty_fifty_timestamp TEXT,
    stock_data set<frozen<stock_data>>
);
