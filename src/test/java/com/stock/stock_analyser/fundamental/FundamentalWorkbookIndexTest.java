package com.stock.stock_analyser.fundamental;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FundamentalWorkbookIndexTest {
    @Test void discoversCanonicalWorkbookAndNormalizesSymbol() throws Exception {
        Path dir = Files.createTempDirectory("fundamentals");
        Path trent = Files.createFile(dir.resolve("trent.xlsx"));
        FundamentalWorkbookIndex index = new FundamentalWorkbookIndex(config(dir));
        index.refresh();
        assertEquals(trent.toAbsolutePath().normalize(), index.find(" TRENT ").orElseThrow());
    }

    @Test void ignoresNonXlsxFiles() throws Exception {
        Path dir = Files.createTempDirectory("fundamentals");
        Files.createFile(dir.resolve("TRENT.csv"));
        Files.createFile(dir.resolve("TRENT.xlsx"));
        FundamentalWorkbookIndex index = new FundamentalWorkbookIndex(config(dir));
        index.refresh();
        assertTrue(index.find("TRENT").isPresent());
        assertEquals(1, index.snapshot().size());
    }

    @Test void detectsDuplicateSymbolsWithoutPickingOne() {
        FundamentalWorkbookIndex.Discovery discovery = FundamentalWorkbookIndex.buildIndex(
                List.of(Path.of("/tmp/BEL.xlsx"), Path.of("/tmp/bel.xlsx")), ".xlsx");
        assertEquals(2, discovery.discoveredWorkbookCount());
        assertEquals(0, discovery.indexedSymbolCount());
        assertEquals(1, discovery.conflicts().size());
        assertTrue(discovery.conflicts().contains("BEL"));
        assertFalse(discovery.index().containsKey("BEL"));
    }

    @Test void countsUniqueValidWorkbooks() {
        FundamentalWorkbookIndex.Discovery discovery = FundamentalWorkbookIndex.buildIndex(
                List.of(Path.of("MUNJALAU.xlsx"), Path.of("BEL.xlsx"), Path.of("HAL.xlsx")), ".xlsx");
        assertEquals(3, discovery.discoveredWorkbookCount());
        assertEquals(3, discovery.indexedSymbolCount());
        assertEquals(0, discovery.conflicts().size());
        assertEquals(0, discovery.invalidFiles().size());
    }

    @Test void countsOnlyAcceptedWorkbookFiles() {
        FundamentalWorkbookIndex.Discovery discovery = FundamentalWorkbookIndex.buildIndex(
                List.of(Path.of("TRENT.csv"), Path.of("README.txt"), Path.of("TRENT.xlsx")), ".xlsx");
        // discoveredWorkbookCount counts accepted canonical .xlsx files; rejected files are invalidFiles.
        assertEquals(1, discovery.discoveredWorkbookCount());
        assertEquals(1, discovery.indexedSymbolCount());
        assertEquals(0, discovery.conflicts().size());
        assertEquals(2, discovery.invalidFiles().size());
    }

    @Test void indexesFiveHundredCanonicalWorkbooks() throws Exception {
        Path dir = Files.createTempDirectory("fundamentals-500");
        for (int i = 1; i <= 500; i++) Files.createFile(dir.resolve("STOCK" + i + ".xlsx"));
        FundamentalWorkbookIndex index = new FundamentalWorkbookIndex(config(dir));
        index.refresh();
        assertEquals(500, index.snapshot().size());
        assertTrue(index.find("STOCK500").isPresent());
    }

    @Test void refreshDiscoversNewWorkbook() throws Exception {
        Path dir = Files.createTempDirectory("fundamentals-refresh");
        FundamentalWorkbookIndex index = new FundamentalWorkbookIndex(config(dir));
        index.refresh();
        assertTrue(index.find("TRENT").isEmpty());
        Files.createFile(dir.resolve("TRENT.xlsx"));
        index.refresh();
        assertTrue(index.find("TRENT").isPresent());
    }

    @Test void unavailableDirectoryRetainsLastKnownGoodSnapshot() throws Exception {
        Path dir = Files.createTempDirectory("fundamentals-retain-missing");
        Files.createFile(dir.resolve("TRENT.xlsx"));
        FundamentalDataConfiguration config = config(dir);
        FundamentalWorkbookIndex index = new FundamentalWorkbookIndex(config);
        index.refresh();
        Path expected = index.find("TRENT").orElseThrow();
        assertTrue(index.find("TRENT").isPresent());
        config.setDataDirectory(dir.resolve("missing").toString());
        index.refresh();
        assertEquals(expected, index.find("TRENT").orElseThrow());
        assertEquals(Set.of(), index.conflicts());
    }

    @Test void failedScanRetainsLastKnownGoodSnapshot() throws Exception {
        Path dir = Files.createTempDirectory("fundamentals-retain-scan");
        Files.createFile(dir.resolve("TRENT.xlsx"));
        FundamentalDataConfiguration config = config(dir);
        FundamentalWorkbookIndex index = new FundamentalWorkbookIndex(config);
        index.refresh();
        assertTrue(index.find("TRENT").isPresent());
        config.setDataDirectory(dir.resolve("missing").toString());
        index.refresh();
        assertTrue(index.find("TRENT").isPresent());
    }

    @Test void nullAndBlankSymbolsAreHandledSafely() throws Exception {
        Path dir = Files.createTempDirectory("fundamentals-symbol");
        FundamentalWorkbookIndex index = new FundamentalWorkbookIndex(config(dir));
        index.refresh();
        assertTrue(index.find(null).isEmpty());
        assertTrue(index.find("   ").isEmpty());
    }

    @Test void invalidHumanReadableNameIsNotFuzzyMatched() throws Exception {
        Path dir = Files.createTempDirectory("fundamentals-invalid");
        Files.createFile(dir.resolve("Munjal Auto Inds.xlsx"));
        FundamentalWorkbookIndex index = new FundamentalWorkbookIndex(config(dir));
        index.refresh();
        assertTrue(index.find("MUNJALAU").isEmpty());
    }

    private FundamentalDataConfiguration config(Path dir) {
        FundamentalDataConfiguration config = new FundamentalDataConfiguration();
        config.setDataDirectory(dir.toString());
        config.setWorkbookExtension(".xlsx");
        return config;
    }
}
