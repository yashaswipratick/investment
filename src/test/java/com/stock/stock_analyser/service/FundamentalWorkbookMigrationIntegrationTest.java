package com.stock.stock_analyser.service;

import com.stock.stock_analyser.fundamental.FundamentalDataParser;
import com.stock.stock_analyser.fundamental.FundamentalDataConfiguration;
import com.stock.stock_analyser.fundamental.FundamentalWorkbookIndex;
import com.stock.stock_analyser.fundamental.FundamentalWorkbookResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FundamentalWorkbookMigrationIntegrationTest {
    @Test void migratedMunjalauWorkbookRemainsAnalyzable(@TempDir Path tempDirectory) throws IOException {
        Path directory = tempDirectory.resolve("fundamental-data");
        Files.createDirectories(directory);
        try (InputStream workbook = getClass().getResourceAsStream("/fundamental-data/MUNJALAU.xlsx")) {
            if (workbook == null) throw new IOException("Migrated MUNJALAU.xlsx test resource is missing");
            Files.copy(workbook, directory.resolve("MUNJALAU.xlsx"));
        }
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
