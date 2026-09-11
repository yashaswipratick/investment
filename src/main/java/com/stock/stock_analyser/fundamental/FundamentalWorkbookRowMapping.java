package com.stock.stock_analyser.fundamental;

import java.util.Map;

/** Verified Data Sheet row mapping. Indexes are zero-based POI row numbers; labels guard against layout drift. */
public final class FundamentalWorkbookRowMapping {
    private FundamentalWorkbookRowMapping() {}

    public static final int DATES = 15;
    public static final int SALES = 16;
    public static final int OTHER_INCOME = 24;
    public static final int OPERATING_PROFIT = 25;
    public static final int INTEREST = 26;
    public static final int PBT = 27;
    public static final int PROFIT = 29;
    public static final int EQUITY = 56;
    public static final int RESERVES = 57;
    public static final int BORROWINGS = 58;
    public static final int CFO = 81;
    public static final int SHARES = 92;
    public static final int CURRENT_PRICE = 7;
    public static final int CURRENT_MARKET_CAP = 8;

    public static final Map<Integer, String[]> EXPECTED_LABELS = Map.ofEntries(
            Map.entry(SALES, new String[]{"sales", "revenue"}),
            Map.entry(PROFIT, new String[]{"profit", "profit after tax", "pat", "net profit"}),
            Map.entry(OPERATING_PROFIT, new String[]{"operating profit"}),
            Map.entry(PBT, new String[]{"pbt", "profit before tax"}),
            Map.entry(INTEREST, new String[]{"interest"}),
            Map.entry(EQUITY, new String[]{"equity", "equity share capital"}),
            Map.entry(RESERVES, new String[]{"reserves", "reserves & surplus", "reserves and surplus"}),
            Map.entry(BORROWINGS, new String[]{"borrowings", "debt"}),
            Map.entry(CFO, new String[]{"cash from operating", "cash flow from operating", "cash from operations", "cfo"}),
            Map.entry(SHARES, new String[]{"adjusted equity shares", "equity shares", "shares"})
    );
}
