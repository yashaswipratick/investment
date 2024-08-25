CREATE TYPE positional_daily_net_income_info (
    position_date text,
    basket_id text,
    instrument text,
    quantity int,
    average_price_per_stock double,
    last_traded_price_per_stock double,
    pnl_per_stock double,
    change_per_stock double,
    total_invested_amount double,
    total_ltp_amount double,
    total_pnl double,
    total_pnl_percentage double,
    created_date timestamp
);
CREATE TABLE positional_daily_net_income (
    key text PRIMARY KEY,
    positional_daily_net_income_info_details map<text, frozen<positional_daily_net_income_info>>,
    created_date timestamp
);