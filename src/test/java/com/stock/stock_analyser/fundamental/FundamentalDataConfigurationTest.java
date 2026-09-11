package com.stock.stock_analyser.fundamental;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FundamentalDataConfigurationTest {
    @Test void configurationContainsOnlyGenericWorkbookSettings() {
        FundamentalDataConfiguration config = new FundamentalDataConfiguration();
        config.setDataDirectory("/tmp/test-fundamentals");
        config.setWorkbookExtension(".xlsx");
        assertEquals("/tmp/test-fundamentals", config.getDataDirectory());
        assertEquals(".xlsx", config.getWorkbookExtension());
    }
}
