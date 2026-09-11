package com.stock.stock_analyser.fundamental;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Converts the configured workbook layout into the normalized fundamental data contract. */
@Component
public class FundamentalDataParser {

    public FundamentalDataSet parse(Workbook workbook, String symbol) {
        Sheet sheet = workbook.getSheet("Data Sheet");
        if (sheet == null) {
            throw new IllegalArgumentException("Data Sheet is missing for " + symbol + ".");
        }

        // Verified source mapping. POI row indexes are zero-based and centralized in
        // FundamentalWorkbookRowMapping. Label validation prevents silent row drift.
        Row dates = sheet.getRow(FundamentalWorkbookRowMapping.DATES);
        Row sales = validatedRow(sheet, FundamentalWorkbookRowMapping.SALES, "Sales/Revenue");
        Row profit = validatedRow(sheet, FundamentalWorkbookRowMapping.PROFIT, "Profit/PAT");
        Row equity = validatedRow(sheet, FundamentalWorkbookRowMapping.EQUITY, "Equity");
        Row reserves = validatedRow(sheet, FundamentalWorkbookRowMapping.RESERVES, "Reserves");
        Row debt = validatedRow(sheet, FundamentalWorkbookRowMapping.BORROWINGS, "Borrowings");
        Row operatingProfit = validatedRow(sheet, FundamentalWorkbookRowMapping.OPERATING_PROFIT, "Operating Profit");
        Row pbt = validatedRow(sheet, FundamentalWorkbookRowMapping.PBT, "PBT");
        Row interest = validatedRow(sheet, FundamentalWorkbookRowMapping.INTEREST, "Interest");
        Row otherIncome = sheet.getRow(FundamentalWorkbookRowMapping.OTHER_INCOME);
        Row cfo = validatedRow(sheet, FundamentalWorkbookRowMapping.CFO, "CFO");
        Row currentPrice = sheet.getRow(FundamentalWorkbookRowMapping.CURRENT_PRICE);
        Row currentMarketCap = sheet.getRow(FundamentalWorkbookRowMapping.CURRENT_MARKET_CAP);
        Row shares = validatedRow(sheet, FundamentalWorkbookRowMapping.SHARES, "Adjusted Equity Shares");

        List<String> dataQualityNotes = new ArrayList<>();
        FundamentalWorkbookRowMapping.EXPECTED_LABELS.forEach((rowIndex, labels) -> {
            if (sheet.getRow(rowIndex) == null || !labelMatches(sheet.getRow(rowIndex), labels)) {
                dataQualityNotes.add("Expected source label missing/mismatched at row " + rowIndex + ": " + String.join(" / ", labels));
            }
        });
        List<FundamentalPeriodData> periods = new ArrayList<>();
        for (int c = 1; c < 20; c++) {
            Double dateValue = value(dates, c);
            if (dateValue == null || Double.isNaN(dateValue)) continue;

            LocalDate date = DateUtil.getJavaDate(dateValue).toInstant()
                    .atZone(ZoneId.of("Asia/Kolkata")).toLocalDate();
            periods.add(new FundamentalPeriodData(
                    date, value(sales, c), value(profit, c), value(equity, c), value(reserves, c),
                    value(debt, c), value(operatingProfit, c), value(pbt, c), value(interest, c),
                    value(otherIncome, c), value(cfo, c), value(shares, c)));
        }
        return new FundamentalDataSet(symbol, periods,
                new FundamentalMarketSnapshot(value(currentPrice, 1), value(currentMarketCap, 1)), dataQualityNotes);
    }

    private Row validatedRow(Sheet sheet, int rowIndex, String metric) {
        Row row = sheet.getRow(rowIndex);
        String[] labels = FundamentalWorkbookRowMapping.EXPECTED_LABELS.get(rowIndex);
        return row != null && labels != null && labelMatches(row, labels) ? row : null;
    }

    private boolean labelMatches(Row row, String[] expectedLabels) {
        if (row == null || row.getCell(0) == null) return false;
        String actual = row.getCell(0).toString().trim().toLowerCase(Locale.ROOT);
        for (String expected : expectedLabels) {
            if (actual.contains(expected.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private Double value(Row row, int column) {
        if (row == null || row.getCell(column) == null) return null;
        Cell cell = row.getCell(column);
        try {
            if (cell.getCellType() == CellType.NUMERIC) return cell.getNumericCellValue();
            if (cell.getCellType() == CellType.FORMULA && cell.getCachedFormulaResultType() == CellType.NUMERIC) {
                return cell.getNumericCellValue();
            }
            String text = cell.toString().replace(",", "").trim();
            return text.isEmpty() ? null : Double.parseDouble(text);
        } catch (Exception ignored) {
            return null;
        }
    }
}
