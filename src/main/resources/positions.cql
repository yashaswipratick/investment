CREATE TYPE positions_stock_info (
    instrument text,
    quantity int,
    average_price_per_stock double,
    last_traded_price double,
    pnl_per_stock double,
    change_per_stock double,
    total_invested_amount double,
    total_ltp_amount double,
    total_pnl double,
    total_pnl_percentage double,
    created_date timestamp
);

CREATE TABLE positions (
    key text,
    basket_id text,
    positions_stock_info_details map<text, frozen<positions_stock_info>>,
    is_active_position text,
    created_date timestamp,
    PRIMARY KEY ((key, basket_id))
);
