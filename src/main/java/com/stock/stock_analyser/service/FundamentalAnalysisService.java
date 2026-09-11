package com.stock.stock_analyser.service;

import com.stock.stock_analyser.dto.FundamentalAnalysis;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Slf4j
@Service
public class FundamentalAnalysisService {

    private static final Map<String, String> WORKBOOKS = Map.ofEntries(
            Map.entry("ABB", "ABB India.xlsx"), Map.entry("APOLLO", "Apollo Hospitals.xlsx"),
            Map.entry("BEL", "Bharat Electron.xlsx"), Map.entry("BHARTIARTL", "Bharti Airtel.xlsx"),
            Map.entry("COFORGE", "Coforge.xlsx"), Map.entry("CUMMINSIND", "Cummins India.xlsx"),
            Map.entry("FINEORG", "Fine Organic.xlsx"), Map.entry("HDFCBANK", "HDFC Bank.xlsx"),
            Map.entry("HAL", "Hindustan Aeronaut.xlsx"), Map.entry("ICICIBANK", "ICICI Bank.xlsx"),
            Map.entry("INGERRAND", "Ingersoll-Rand India.xlsx"), Map.entry("LT", "Larsen & Toubro.xlsx"),
            Map.entry("LAURUSLABS", "Laurus Labs.xlsx"), Map.entry("MUNJALAU", "Munjal Auto Inds.xlsx"),
            Map.entry("ONGC", "ONGC.xlsx"), Map.entry("OIL", "Oil India.xlsx"),
            Map.entry("PERSISTENT", "Persistent Systems.xlsx"), Map.entry("RELIANCE", "Reliance Industries.xlsx"),
            Map.entry("SBIN", "SBI.xlsx"), Map.entry("SIEMENS", "Siemens.xlsx"),
            Map.entry("SOLARINDS", "Solar Industries.xlsx"), Map.entry("SUNPHARMA", "Sun Pharma.xlsx")
    );

    private final Path dataDirectory;

    public FundamentalAnalysisService(@Value("${fundamental.data-directory:${user.home}/Downloads/stock-fundamental-data}") String dataDirectory) {
        this.dataDirectory = Paths.get(dataDirectory);
    }

    public FundamentalAnalysis analyse(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        String filename = WORKBOOKS.get(normalized);
        if (filename == null) return unavailable("No configured fundamental workbook for " + normalized + ".");
        Path file = dataDirectory.resolve(filename);
        if (!Files.isRegularFile(file)) return unavailable("Fundamental workbook not found: " + file);
        try (InputStream in = Files.newInputStream(file); Workbook workbook = WorkbookFactory.create(in)) {
            return calculate(workbook, normalized);
        } catch (Exception e) {
            log.warn("Unable to read fundamentals for {}: {}", normalized, e.getMessage());
            return unavailable("Unable to read fundamental data: " + e.getMessage());
        }
    }

    private FundamentalAnalysis calculate(Workbook workbook, String symbol) {
        Sheet sheet = workbook.getSheet("Data Sheet");
        if (sheet == null) return unavailable("Data Sheet is missing for " + symbol + ".");

        List<Period> periods = readAnnualPeriods(sheet);
        if (periods.size() < 2) return unavailable("Insufficient annual fundamental history for " + symbol + ".");

        Period latest = periods.get(periods.size() - 1);
        Period previous = periods.get(periods.size() - 2);
        Period threeYearsAgo = periods.get(Math.max(0, periods.size() - 4));
        Period fiveYearsAgo = periods.get(Math.max(0, periods.size() - 6));

        double netWorth = latest.equity + latest.reserves;
        double previousNetWorth = previous.equity + previous.reserves;
        double avgNetWorth = (netWorth + previousNetWorth) / 2.0;
        double eps = safeRatio(latest.profit, latest.shares);
        double previousEps = safeRatio(previous.profit, previous.shares);
        double revenueCagr3Y = cagr(latest.sales, threeYearsAgo.sales, periods.size() >= 4 ? 3 : periods.size() - 1);
        double revenueCagr5Y = cagr(latest.sales, fiveYearsAgo.sales, periods.size() >= 6 ? 5 : periods.size() - 1);
        double epsCagr3Y = cagr(eps, safeRatio(threeYearsAgo.profit, threeYearsAgo.shares), periods.size() >= 4 ? 3 : periods.size() - 1);
        double epsGrowthYoY = pct(eps, previousEps);
        double netMargin = safeRatio(latest.profit, latest.sales) * 100;
        double roe = safeRatio(latest.profit, avgNetWorth) * 100;
        double debtToEquity = safeRatio(latest.borrowings, netWorth);
        double ebit = latest.pbt + latest.interest - latest.otherIncome;
        double interestCoverage = latest.interest > 0 ? ebit / latest.interest : Double.NaN;
        double cfoToPat = safeRatio(latest.cfo, latest.profit) * 100;

        int positiveProfitYears = (int) periods.stream().filter(p -> p.profit > 0).count();
        int positiveRevenueGrowthYears = 0;
        for (int i = 1; i < periods.size(); i++) if (periods.get(i).sales > periods.get(i - 1).sales) positiveRevenueGrowthYears++;

        boolean bank = Set.of("HDFCBANK", "ICICIBANK", "SBIN").contains(symbol);
        double pe = latest.price > 0 ? safeRatio(latest.price, eps) : Double.NaN;
        double pb = latest.marketCap > 0 ? safeRatio(latest.marketCap, netWorth) : Double.NaN;

        double growth = average(scoreGrowth(revenueCagr3Y), scoreGrowth(epsGrowthYoY), scoreGrowth(epsCagr3Y));
        double profitability = average(scoreRoe(roe), scoreMargin(netMargin));
        double health = bank ? Double.NaN : average(scoreDebt(debtToEquity), scoreCoverage(interestCoverage), scoreNetWorthTrend(netWorth - previousNetWorth));
        double cash = average(scoreCashConversion(cfoToPat), scoreConsistency(positiveProfitYears, periods.size()), scoreCfo(latest.cfo));
        double consistency = average(scoreConsistency(positiveProfitYears, periods.size()), scoreConsistency(positiveRevenueGrowthYears, Math.max(1, periods.size() - 1)));
        double valuation = average(scorePe(pe), scorePb(pb));
        double overall = weightedAvailable(growth, 25, profitability, 20, health, 15, cash, 15, consistency, 10, valuation, 15);

        return FundamentalAnalysis.builder()
                .status("AVAILABLE").confidence(periods.size() >= 8 ? "HIGH" : periods.size() >= 5 ? "MEDIUM" : "LOW")
                .latestPeriod(latest.date).periodsAvailable(periods.size())
                .revenueCagr3Y(round(revenueCagr3Y)).revenueCagr5Y(round(revenueCagr5Y)).eps(round(eps)).epsGrowthYoY(round(epsGrowthYoY)).epsCagr3Y(round(epsCagr3Y))
                .netMargin(round(netMargin)).roe(round(roe)).debtToEquity(bank ? null : round(debtToEquity)).interestCoverage(bank ? null : round(interestCoverage)).cfoToPat(round(cfoToPat))
                .positiveProfitYears(positiveProfitYears).positiveRevenueGrowthYears(positiveRevenueGrowthYears).peRatio(round(pe)).pbRatio(round(pb))
                .growthScore(round(growth)).profitabilityScore(round(profitability)).financialHealthScore(bank ? null : round(health)).cashFlowScore(round(cash))
                .consistencyScore(round(consistency)).valuationScore(round(valuation)).overallScore(round(overall))
                .valuationLabel(valuationLabel(pe, pb)).summary(summary(growth, profitability, cash, valuationLabel(pe, pb)))
                .dataNote("Derived from the configured annual workbook; latest period " + latest.date + ".")
                .build();
    }

    private List<Period> readAnnualPeriods(Sheet s) {
        Row dates = s.getRow(15), sales = s.getRow(16), profit = s.getRow(29), equity = s.getRow(56), reserves = s.getRow(57), debt = s.getRow(58), pbt = s.getRow(27), interest = s.getRow(26), otherIncome = s.getRow(24), cfo = s.getRow(81), currentPrice = s.getRow(7), currentMarketCap = s.getRow(8), shares = s.getRow(92);
        List<Period> result = new ArrayList<>();
        for (int c = 1; c < 20; c++) {
            Double dateValue = value(dates, c);
            if (dateValue == null || Double.isNaN(dateValue)) continue;
            LocalDate date = DateUtil.getJavaDate(dateValue).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            result.add(new Period(date, n(sales,c), n(profit,c), n(equity,c), n(reserves,c), n(debt,c), n(pbt,c), n(interest,c), n(otherIncome,c), n(cfo,c), n(currentPrice,1), n(currentMarketCap,1), n(shares,c)));
        }
        return result;
    }

    private double n(Row r, int c) { Double v = value(r,c); return v == null || Double.isNaN(v) ? 0 : v; }
    private Double value(Row r, int c) {
        if (r == null || r.getCell(c) == null) return null;
        Cell cell = r.getCell(c);
        try {
            if (cell.getCellType() == CellType.NUMERIC) return cell.getNumericCellValue();
            if (cell.getCellType() == CellType.FORMULA && cell.getCachedFormulaResultType() == CellType.NUMERIC) return cell.getNumericCellValue();
            String text = cell.toString().replace(",", "").trim();
            return text.isEmpty() ? null : Double.parseDouble(text);
        } catch (Exception e) { return null; }
    }

    private double safeRatio(double a, double b) { return b == 0 ? Double.NaN : a / b; }
    private double pct(double a, double b) { return Double.isNaN(a) || Double.isNaN(b) || b == 0 ? Double.NaN : (a / b - 1) * 100; }
    private double cagr(double end, double start, int years) { return end > 0 && start > 0 && years > 0 ? (Math.pow(end / start, 1.0 / years) - 1) * 100 : Double.NaN; }
    private double average(double... values) { double sum=0,n=0; for(double v:values) if(!Double.isNaN(v)){sum+=v;n++;} return n==0?Double.NaN:sum/n; }
    private double weightedAvailable(double... values) { double sum=0,w=0; for(int i=0;i<values.length;i+=2){double v=values[i], wt=values[i+1]; if(!Double.isNaN(v)){sum+=v*wt;w+=wt;}} return w==0?Double.NaN:sum/w; }
    private double scoreGrowth(double v){return bucket(v,5,10,15,25);}
    private double scoreRoe(double v){return bucket(v,8,12,18,25);}
    private double scoreMargin(double v){return bucket(v,5,8,12,20);}
    private double scoreDebt(double v){if(Double.isNaN(v))return Double.NaN; return v<=0.1?100:v<=0.25?90:v<=0.5?75:v<=0.8?50:25;}
    private double scoreCoverage(double v){return Double.isInfinite(v)?100:bucket(v,2,4,7,10);}
    private double scoreNetWorthTrend(double v){return v>1000?100:v>500?85:v>0?70:v==0?50:25;}
    private double scoreCashConversion(double v){return bucket(v,50,80,100,130);}
    private double scoreConsistency(int positive,int total){return total<=0?Double.NaN:Math.min(100, Math.max(25, positive*100.0/total));}
    private double scoreCfo(double v){return v>1000?100:v>500?85:v>100?70:v>0?50:25;}
    private double scorePe(double v){if(Double.isNaN(v)||v<=0)return Double.NaN; return v<=15?100:v<=20?90:v<=30?75:v<=40?50:25;}
    private double scorePb(double v){if(Double.isNaN(v)||v<=0)return Double.NaN; return v<=2?100:v<=3?90:v<=5?75:v<=10?50:25;}
    private double bucket(double v,double a,double b,double c,double d){if(Double.isNaN(v))return Double.NaN; return v<=a?25:v<=b?50:v<=c?75:100;}
    private String valuationLabel(double pe,double pb){if((!Double.isNaN(pe)&&pe>50)||(!Double.isNaN(pb)&&pb>10))return "EXPENSIVE"; if((!Double.isNaN(pe)&&pe>30)||(!Double.isNaN(pb)&&pb>5))return "RICH"; if((!Double.isNaN(pe)&&pe>0&&pe<18)&&(!Double.isNaN(pb)&&pb<3))return "ATTRACTIVE"; return "FAIR";}
    private String summary(double growth,double profitability,double cash,String valuation){return String.format("Growth %.0f/100, profitability %.0f/100, cash flow %.0f/100; valuation %s.",growth,profitability,cash,valuation);}
    private Double round(double v){return Double.isNaN(v)||Double.isInfinite(v)?null:Math.round(v*100.0)/100.0;}
    private FundamentalAnalysis unavailable(String note){return FundamentalAnalysis.builder().status("UNAVAILABLE").confidence("UNAVAILABLE").summary("Fundamental analysis is unavailable; technical analysis can continue normally.").dataNote(note).build();}

    private record Period(LocalDate date,double sales,double profit,double equity,double reserves,double borrowings,double pbt,double interest,double otherIncome,double cfo,double price,double marketCap,double shares) {}
}
