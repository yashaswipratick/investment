CREATE TYPE IF NOT EXISTS index_stock_details (
    companyname TEXT,
    industry TEXT,
    last_price DOUBLE,
    change DOUBLE,
    percentage_change DOUBLE,
    market_cap DOUBLE,
    link TEXT
);

CREATE TABLE IF NOT EXISTS nifty_fifty_index_stock (
    date DATE PRIMARY KEY,
    index_stock_details MAP<TEXT, FROZEN<index_stock_details>>,
    created_date TIMESTAMP
);