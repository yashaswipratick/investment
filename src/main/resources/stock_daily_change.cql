CREATE TYPE IF NOT EXISTS stocks_daily_change_info (
    instrument text,
    last_traded_price double,
    created_date timestamp
);

CREATE TABLE IF NOT EXISTS stocks_daily_change (
    key text PRIMARY KEY,
    stocks_daily_change_info_details map<text, frozen<stocks_daily_change_info>>,
    created_date timestamp
);
