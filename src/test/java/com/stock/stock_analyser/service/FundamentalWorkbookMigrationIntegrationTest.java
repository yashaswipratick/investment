package com.stock.stock_analyser.service;

import com.stock.stock_analyser.fundamental.FundamentalDataParser;
import com.stock.stock_analyser.fundamental.FundamentalDataConfiguration;
import com.stock.stock_analyser.fundamental.FundamentalWorkbookIndex;
import com.stock.stock_analyser.fundamental.FundamentalWorkbookResolver;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FundamentalWorkbookMigrationIntegrationTest {
    @Test void migratedMunjalauWorkbookRemainsAnalyzable() {
        Path directory = Path.of(System.getProperty("user.dir"), "src/main/resources/fundamental-data");
        FundamentalDataConfiguration config = new FundamentalDataConfiguration();
        config.setDataDirectory(directory.toString());
        config.setWorkbookExtension(".xlsx");
        FundamentalWorkbookIndex index = new FundamentalWorkbookIndex(config);
        index.refresh();
        FundamentalWorkbookResolver resolver = new FundamentalWorkbookResolver(index);
        assertEquals(directory.resolve("MUNJALAU.xlsx").toAbsolutePath().normalize(), resolver.resolve("MUNJALAU").orElseThrow());
        FundamentalAnalysisService service = new FundamentalAnalysisService(config, new FundamentalDataParser(), resolver);
        assertEquals("AVAILABLE", service.analyse("MUNJALAU").getStatus());
    }
}
