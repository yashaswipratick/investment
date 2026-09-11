package com.stock.stock_analyser.fundamental;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FundamentalWorkbookConfigurationTest {
    @Test void configuredDirectoryAndSymbolMappingAreMachineIndependent() {
        FundamentalWorkbookConfiguration config = new FundamentalWorkbookConfiguration();
        config.setDataDirectory("/tmp/test-fundamentals");
        config.setWorkbooks(Map.of("MUNJALAU", "Munjal Auto Inds.xlsx"));
        assertEquals("/tmp/test-fundamentals", config.getDataDirectory());
        assertEquals("Munjal Auto Inds.xlsx", config.getWorkbookForSymbol(" munjalau "));
        assertNull(config.getWorkbookForSymbol("UNKNOWN"));
    }
}
