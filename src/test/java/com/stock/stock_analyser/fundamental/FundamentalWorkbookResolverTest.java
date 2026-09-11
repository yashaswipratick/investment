package com.stock.stock_analyser.fundamental;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FundamentalWorkbookResolverTest {
    @Test void resolvesNormalizedSymbolInsideConfiguredDirectory() throws Exception {
        Path dir = Files.createTempDirectory("fundamentals-resolver");
        Path workbook = Files.createFile(dir.resolve("MUNJALAU.xlsx"));
        FundamentalWorkbookIndex index = new FundamentalWorkbookIndex(config(dir));
        index.refresh();
        FundamentalWorkbookResolver resolver = new FundamentalWorkbookResolver(index);
        assertEquals(workbook.toAbsolutePath().normalize(), resolver.resolve(" munjalau ").orElseThrow());
    }

    @Test void missingWorkbookReturnsEmpty() throws Exception {
        Path dir = Files.createTempDirectory("fundamentals-resolver-missing");
        FundamentalWorkbookIndex index = new FundamentalWorkbookIndex(config(dir));
        index.refresh();
        assertTrue(new FundamentalWorkbookResolver(index).resolve("UNKNOWN").isEmpty());
    }

    @Test void nullAndBlankSymbolsReturnEmpty() throws Exception {
        Path dir = Files.createTempDirectory("fundamentals-resolver-blank");
        FundamentalWorkbookResolver resolver = new FundamentalWorkbookResolver(new FundamentalWorkbookIndex(config(dir)));
        assertTrue(resolver.resolve(null).isEmpty());
        assertTrue(resolver.resolve(" ").isEmpty());
    }

    @Test void refreshExposesNewCanonicalWorkbook() throws Exception {
        Path dir = Files.createTempDirectory("fundamentals-resolver-refresh");
        FundamentalWorkbookIndex index = new FundamentalWorkbookIndex(config(dir));
        index.refresh();
        FundamentalWorkbookResolver resolver = new FundamentalWorkbookResolver(index);
        assertTrue(resolver.resolve("TRENT").isEmpty());
        Files.createFile(dir.resolve("TRENT.xlsx"));
        resolver.refresh();
        assertTrue(resolver.resolve("TRENT").isPresent());
    }

    @Test void traversalSymbolCannotEscapeDirectory() throws Exception {
        Path dir = Files.createTempDirectory("fundamentals-resolver-safe");
        FundamentalWorkbookIndex index = new FundamentalWorkbookIndex(config(dir));
        index.refresh();
        assertTrue(new FundamentalWorkbookResolver(index).resolve("../TRENT").isEmpty());
    }

    private FundamentalDataConfiguration config(Path dir) {
        FundamentalDataConfiguration config = new FundamentalDataConfiguration();
        config.setDataDirectory(dir.toString());
        config.setWorkbookExtension(".xlsx");
        return config;
    }
}
