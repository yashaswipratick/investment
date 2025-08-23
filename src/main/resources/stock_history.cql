-- 1. Create the UDT (User Defined Type) for StockHistoryDetails
CREATE TYPE stock_history_details (
    series text,
    stock_name text,
    open double,
    high double,
    low double,
    prevclose double,
    ltp double,
    close double,
    vwap double,
    fifty_two_week_high double,
    fifty_two_week_low double,
    volume text,
    value text,
    total_trades text,
    isin text,
    history_date date
);

-- 2. Create the main table using StockHistoryKey (partition key) and the UDT map
CREATE TABLE stock_history (
    key text PRIMARY KEY,
    stock_history_details map<date, frozen<stock_history_details>>
);
