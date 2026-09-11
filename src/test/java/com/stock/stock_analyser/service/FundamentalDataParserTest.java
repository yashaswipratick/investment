package com.stock.stock_analyser.service;

import com.stock.stock_analyser.fundamental.FundamentalDataParser;
import com.stock.stock_analyser.fundamental.FundamentalDataSet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FundamentalDataParserTest {
    @Test
    void parsesVerifiedFinancialRowsAndCurrentMarketSnapshot() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Data Sheet");
            put(sheet, 15, 1, 45647);
            put(sheet, 16, 1, 100.0);
            put(sheet, 29, 1, 10.0);
            put(sheet, 56, 1, 20.0);
            put(sheet, 57, 1, 30.0);
            put(sheet, 58, 1, 5.0);
            put(sheet, 25, 1, 30.0);
            put(sheet, 27, 1, 28.0);
            put(sheet, 26, 1, 5.0);
            put(sheet, 24, 1, 3.0);
            put(sheet, 81, 1, 10.0);
            put(sheet, 92, 1, 2.0);
            put(sheet, 7, 1, 150.0);
            put(sheet, 8, 1, 750.0);

            FundamentalDataSet result = new FundamentalDataParser().parse(workbook, "TEST");
            assertEquals(1, result.annualPeriods().size());
            var period = result.annualPeriods().get(0);
            assertEquals(100.0, period.sales());
            assertEquals(10.0, period.profit());
            assertEquals(20.0, period.equity());
            assertEquals(30.0, period.reserves());
            assertEquals(5.0, period.borrowings());
            assertEquals(30.0, period.operatingProfit());
            assertEquals(28.0, period.pbt());
            assertEquals(5.0, period.interest());
            assertEquals(3.0, period.otherIncome());
            assertEquals(10.0, period.cfo());
            assertEquals(2.0, period.shares());
            assertEquals(150.0, result.marketSnapshot().currentPrice());
            assertEquals(750.0, result.marketSnapshot().currentMarketCap());
            assertNull(result.marketSnapshot().promoterHolding());
            assertNull(result.marketSnapshot().promoterPledge());
        }
    }


    @Test
    void promoterAndPledgeAreUnavailableWhenSourceHasNoVerifiedFields() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Data Sheet");
            put(sheet, 15, 1, 45647);
            put(sheet, 16, 1, 100.0);
            FundamentalDataSet result = new FundamentalDataParser().parse(workbook, "TEST");
            assertNull(result.marketSnapshot().promoterHolding());
            assertNull(result.marketSnapshot().promoterPledge());
        }
    }

    private void put(org.apache.poi.ss.usermodel.Sheet sheet, int rowIndex, int column, double value) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) row = sheet.createRow(rowIndex);
        row.createCell(column).setCellValue(value);
    }
}
