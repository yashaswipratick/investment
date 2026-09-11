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

/** Converts the configured workbook layout into the normalized fundamental data contract. */
@Component
public class FundamentalDataParser {

    public FundamentalDataSet parse(Workbook workbook, String symbol) {
        Sheet sheet = workbook.getSheet("Data Sheet");
        if (sheet == null) {
            throw new IllegalArgumentException("Data Sheet is missing for " + symbol + ".");
        }

        Row dates = sheet.getRow(15), sales = sheet.getRow(16), profit = sheet.getRow(29),
                equity = sheet.getRow(56), reserves = sheet.getRow(57), debt = sheet.getRow(58),
                pbt = sheet.getRow(27), interest = sheet.getRow(26), otherIncome = sheet.getRow(24),
                cfo = sheet.getRow(81), currentPrice = sheet.getRow(7),
                currentMarketCap = sheet.getRow(8), shares = sheet.getRow(92);

        List<FundamentalPeriodData> periods = new ArrayList<>();
        for (int c = 1; c < 20; c++) {
            Double dateValue = value(dates, c);
            if (dateValue == null || Double.isNaN(dateValue)) continue;

            LocalDate date = DateUtil.getJavaDate(dateValue).toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDate();
            periods.add(new FundamentalPeriodData(
                    date, value(sales, c), value(profit, c), value(equity, c), value(reserves, c),
                    value(debt, c), value(pbt, c), value(interest, c), value(otherIncome, c),
                    value(cfo, c), value(currentPrice, 1), value(currentMarketCap, 1), value(shares, c)));
        }
        return new FundamentalDataSet(symbol, periods);
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
