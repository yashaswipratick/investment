CREATE TABLE IF NOT EXISTS stock_description (
    symbol text PRIMARY KEY,
    company_name text,
    series text,
    dat_of_listing text,
    paid_up_value double,
    market_lot int,
    is_in_number text,
    face_value int,
    created_date timestamp
);