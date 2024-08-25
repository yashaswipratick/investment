CREATE TABLE positional_aggregated_daily_income (
    key text,
    positioned_date date,
    basket_id text,
    total_pnl double,
    total_pnl_percentage double,
    agg_invested_amount double,
    agg_daily_ltp_amount double,
    created_date timestamp,
    PRIMARY KEY ((key, positioned_date, basket_id))
);
