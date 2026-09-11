package com.stock.stock_analyser.service;

import com.stock.stock_analyser.dto.FundamentalAnalysis;
import com.stock.stock_analyser.fundamental.FundamentalDataParser;
import com.stock.stock_analyser.fundamental.FundamentalDataSet;
import com.stock.stock_analyser.fundamental.FundamentalMarketSnapshot;
import com.stock.stock_analyser.fundamental.FundamentalPeriodData;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
public class FundamentalAnalysisService {

    private static final double DAYS_PER_YEAR = 365.2425;
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
            Map.entry("SOLARINDS", "Solar Industries.xlsx"), Map.entry("SUNPHARMA", "Sun Pharma.xlsx"));

    private final Path dataDirectory;
    private final FundamentalDataParser parser;

    public FundamentalAnalysisService(
            @Value("${fundamental.data-directory:${user.home}/Downloads/stock-fundamental-data}") String dataDirectory,
            FundamentalDataParser parser) {
        this.dataDirectory = Paths.get(dataDirectory);
        this.parser = parser;
    }

    public FundamentalAnalysis analyse(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        String filename = WORKBOOKS.get(normalized);
        if (filename == null) return unavailable("No configured fundamental workbook for " + normalized + ".");
        Path file = dataDirectory.resolve(filename);
        if (!Files.isRegularFile(file)) return unavailable("Fundamental workbook not found: " + file);
        try (InputStream in = Files.newInputStream(file); Workbook workbook = WorkbookFactory.create(in)) {
            return analyse(parser.parse(workbook, normalized));
        } catch (Exception e) {
            log.warn("Unable to read fundamentals for {}: {}", normalized, e.getMessage());
            return unavailable("Unable to read fundamental data: " + e.getMessage());
        }
    }

    /** Pure analysis layer: calculations operate only on the normalized data contract. */
    FundamentalAnalysis analyse(FundamentalDataSet data) {
        List<FundamentalPeriodData> periods = validatedPeriods(data.annualPeriods());
        if (periods.size() < 2) return unavailable("Insufficient valid annual fundamental history for " + data.symbol() + ".");

        FundamentalPeriodData latest = periods.get(periods.size() - 1);
        FundamentalPeriodData previous = periods.get(periods.size() - 2);
        FundamentalPeriodData threeYearsAgo = findHistoricalPeriod(periods, latest.date(), 3.0);
        FundamentalPeriodData fiveYearsAgo = findHistoricalPeriod(periods, latest.date(), 5.0);

        Double netWorth = sum(latest.equity(), latest.reserves());
        Double previousNetWorth = sum(previous.equity(), previous.reserves());
        Double avgNetWorth = averageValue(netWorth, previousNetWorth);
        Double eps = calculateEps(latest);
        Double previousEps = calculateEps(previous);
        Double revenueCagr3Y = calculateCagr(latest.sales(), valueAt(threeYearsAgo, FundamentalPeriodData::sales), latest.date(), dateOf(threeYearsAgo));
        Double revenueCagr5Y = calculateCagr(latest.sales(), valueAt(fiveYearsAgo, FundamentalPeriodData::sales), latest.date(), dateOf(fiveYearsAgo));
        Double threeYearsAgoEps = calculateEps(threeYearsAgo);
        Double epsCagr3Y = calculateCagr(eps, threeYearsAgoEps, latest.date(), dateOf(threeYearsAgo));
        Double epsGrowthYoY = calculateEpsGrowth(eps, previousEps);
        Double netMargin = percentage(safeRatio(latest.profit(), latest.sales()));
        Double roe = calculateRoe(latest.profit(), avgNetWorth);
        Double debtToEquity = safeRatio(latest.borrowings(), netWorth);
        Double interestCoverage = calculateInterestCoverage(latest);
        Double cfoToPat = percentage(safeRatio(latest.cfo(), latest.profit()));

        int availableProfitYears = (int) periods.stream().filter(p -> p.profit() != null).count();
        int positiveProfitYears = (int) periods.stream().filter(p -> p.profit() != null && p.profit() > 0).count();
        int availableRevenueGrowthYears = 0;
        int positiveRevenueGrowthYears = 0;
        for (int i = 1; i < periods.size(); i++) {
            Double currentSales = periods.get(i).sales();
            Double previousSales = periods.get(i - 1).sales();
            if (currentSales != null && previousSales != null) {
                availableRevenueGrowthYears++;
                if (currentSales > previousSales) positiveRevenueGrowthYears++;
            }
        }

        boolean bank = Set.of("HDFCBANK", "ICICIBANK", "SBIN").contains(data.symbol());
        FundamentalMarketSnapshot market = data.marketSnapshot() == null
                ? new FundamentalMarketSnapshot(null, null) : data.marketSnapshot();
        Double pe = calculatePe(market.currentPrice(), eps);
        Double pb = calculatePb(market.currentMarketCap(), netWorth);

        Double growth = average(scoreGrowth(revenueCagr3Y), scoreGrowth(epsGrowthYoY), scoreGrowth(epsCagr3Y));
        Double profitability = average(scoreRoe(roe), scoreMargin(netMargin));
        Double health = bank ? null : average(scoreDebt(debtToEquity), scoreCoverage(interestCoverage), scoreNetWorthTrend(subtract(netWorth, previousNetWorth)));
        Double cash = average(scoreCashConversion(cfoToPat), scoreConsistency(positiveProfitYears, availableProfitYears), scoreCfo(latest.cfo()));
        Double consistency = average(scoreConsistency(positiveProfitYears, availableProfitYears), scoreConsistency(positiveRevenueGrowthYears, availableRevenueGrowthYears));
        Double valuation = average(scorePe(pe), scorePb(pb));
        Double overall = weightedAvailable(growth, 25.0, profitability, 20.0, health, 15.0, cash, 15.0, consistency, 10.0, valuation, 15.0);

        String periodNote = "Derived from the configured annual workbook; latest period " + latest.date() + ".";
        if (threeYearsAgo != null) periodNote += " Revenue CAGR 3Y uses " + threeYearsAgo.date() + " to " + latest.date() + ".";
        if (fiveYearsAgo != null) periodNote += " Revenue CAGR 5Y uses " + fiveYearsAgo.date() + " to " + latest.date() + ".";

        return FundamentalAnalysis.builder()
                .status("AVAILABLE").confidence(periods.size() >= 8 ? "HIGH" : periods.size() >= 5 ? "MEDIUM" : "LOW")
                .latestPeriod(latest.date()).periodsAvailable(periods.size())
                .revenueCagr3Y(round(revenueCagr3Y)).revenueCagr5Y(round(revenueCagr5Y)).eps(round(eps)).epsGrowthYoY(round(epsGrowthYoY)).epsCagr3Y(round(epsCagr3Y))
                .netMargin(round(netMargin)).roe(round(roe)).debtToEquity(bank ? null : round(debtToEquity)).interestCoverage(bank ? null : round(interestCoverage)).cfoToPat(round(cfoToPat))
                .positiveProfitYears(positiveProfitYears).positiveRevenueGrowthYears(positiveRevenueGrowthYears).peRatio(round(pe)).pbRatio(round(pb))
                .growthScore(round(growth)).profitabilityScore(round(profitability)).financialHealthScore(bank ? null : round(health)).cashFlowScore(round(cash))
                .consistencyScore(round(consistency)).valuationScore(round(valuation)).overallScore(round(overall))
                .valuationLabel(valuationLabel(pe, pb)).summary(summary(growth, profitability, cash, valuationLabel(pe, pb)))
                .dataNote(periodNote)
                .build();
    }

    List<FundamentalPeriodData> validatedPeriods(List<FundamentalPeriodData> input) {
        if (input == null || input.isEmpty()) return List.of();
        List<FundamentalPeriodData> sorted = new ArrayList<>(input);
        sorted.removeIf(Objects::isNull);
        sorted.sort(Comparator.comparing(FundamentalPeriodData::date, Comparator.nullsLast(Comparator.naturalOrder())));
        List<FundamentalPeriodData> valid = new ArrayList<>();
        LocalDate previousDate = null;
        for (FundamentalPeriodData period : sorted) {
            if (period.date() == null) continue;
            if (previousDate != null && !period.date().isAfter(previousDate)) continue;
            valid.add(period);
            previousDate = period.date();
        }
        return valid;
    }

    private static final long HISTORICAL_PERIOD_TOLERANCE_DAYS = 183;

    /** Selects the nearest prior financial period to the target date within a six-month tolerance. */
    FundamentalPeriodData findHistoricalPeriod(List<FundamentalPeriodData> periods, LocalDate latestDate, double targetYears) {
        if (periods == null || latestDate == null) return null;
        LocalDate targetDate = latestDate.minusDays(Math.round(targetYears * DAYS_PER_YEAR));
        FundamentalPeriodData best = null;
        long bestDistance = Long.MAX_VALUE;
        for (FundamentalPeriodData period : periods) {
            if (period.date() == null || !period.date().isBefore(latestDate)) continue;
            long distance = Math.abs(ChronoUnit.DAYS.between(period.date(), targetDate));
            if (distance < bestDistance) {
                best = period;
                bestDistance = distance;
            }
        }
        return bestDistance <= HISTORICAL_PERIOD_TOLERANCE_DAYS ? best : null;
    }

    double elapsedYears(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || !endDate.isAfter(startDate)) return 0.0;
        return ChronoUnit.DAYS.between(startDate, endDate) / DAYS_PER_YEAR;
    }

    Double calculateCagr(Double end, Double start, LocalDate endDate, LocalDate startDate) {
        double years = elapsedYears(startDate, endDate);
        if (end == null || start == null || end <= 0 || start <= 0 || years <= 0) return null;
        return (Math.pow(end / start, 1.0 / years) - 1) * 100;
    }

    Double calculateEps(FundamentalPeriodData period) {
        if (period == null || period.profit() == null || period.shares() == null || period.shares() == 0) return null;
        return period.profit() / period.shares();
    }

    Double calculateEpsGrowth(Double currentEps, Double previousEps) {
        if (currentEps == null || previousEps == null || previousEps <= 0) return null;
        return (currentEps / previousEps - 1) * 100;
    }

    Double calculateRoe(Double profit, Double averageNetWorth) {
        if (profit == null || averageNetWorth == null || averageNetWorth == 0) return null;
        return profit / averageNetWorth * 100;
    }

    Double calculateInterestCoverage(FundamentalPeriodData period) {
        if (period == null || period.interest() == null || period.interest() <= 0) return null;
        Double ebit = period.operatingProfit();
        if (ebit == null) {
            // The workbook exposes Operating Profit as the preferred EBIT proxy; do not derive it
            // from PBT + Interest - Other Income when the operating-profit field is unavailable.
            return null;
        }
        double coverage = ebit / period.interest();
        return Double.isFinite(coverage) ? coverage : null;
    }

    Double calculatePe(Double currentPrice, Double eps) {
        return currentPrice == null || currentPrice <= 0 || eps == null || eps <= 0 ? null : currentPrice / eps;
    }

    Double calculatePb(Double currentMarketCap, Double latestNetWorth) {
        return currentMarketCap == null || currentMarketCap <= 0 || latestNetWorth == null || latestNetWorth <= 0
                ? null : currentMarketCap / latestNetWorth;
    }

    private LocalDate dateOf(FundamentalPeriodData period) { return period == null ? null : period.date(); }
    private Double valueAt(FundamentalPeriodData period, java.util.function.Function<FundamentalPeriodData, Double> getter) { return period == null ? null : getter.apply(period); }
    private Double sum(Double a, Double b) { return a == null || b == null ? null : a + b; }
    private Double subtract(Double a, Double b) { return a == null || b == null ? null : a - b; }
    private Double averageValue(Double a, Double b) { return a == null || b == null ? null : (a + b) / 2.0; }
    private Double safeRatio(Double numerator, Double denominator) { return numerator == null || denominator == null || denominator == 0 ? null : numerator / denominator; }
    private Double percentage(Double value) { return value == null ? null : value * 100; }
    private Double average(Double... values) { double sum=0; int count=0; for(Double v:values) if(v!=null && Double.isFinite(v)){sum+=v;count++;} return count==0?null:sum/count; }
    private Double weightedAvailable(Double... values) { double sum=0,w=0; for(int i=0;i<values.length;i+=2){Double v=values[i], wt=values[i+1]; if(v!=null && Double.isFinite(v)){sum+=v*wt;w+=wt;}} return w==0?null:sum/w; }
    private Double scoreGrowth(Double v){return bucket(v,5,10,15,25);}
    private Double scoreRoe(Double v){return bucket(v,8,12,18,25);}
    private Double scoreMargin(Double v){return bucket(v,5,8,12,20);}
    private Double scoreDebt(Double v){if(v==null)return null; return v<=0.1?100.0:v<=0.25?90.0:v<=0.5?75.0:v<=0.8?50.0:25.0;}
    private Double scoreCoverage(Double v){return v==null?null:bucket(v,2,4,7,10);}
    private Double scoreNetWorthTrend(Double v){return v==null?null:(v>1000?100.0:v>500?85.0:v>0?70.0:v==0?50.0:25.0);}
    private Double scoreCashConversion(Double v){return bucket(v,50,80,100,130);}
    private Double scoreConsistency(int positive,int total){return total<=0?null:Math.min(100, Math.max(25, positive*100.0/total));}
    private Double scoreCfo(Double v){return v==null?null:(v>1000?100.0:v>500?85.0:v>100?70.0:v>0?50.0:25.0);}
    private Double scorePe(Double v){if(v==null||v<=0)return null; return v<=15?100.0:v<=20?90.0:v<=30?75.0:v<=40?50.0:25.0;}
    private Double scorePb(Double v){if(v==null||v<=0)return null; return v<=2?100.0:v<=3?90.0:v<=5?75.0:v<=10?50.0:25.0;}
    private Double bucket(Double v,double a,double b,double c,double d){if(v==null||!Double.isFinite(v))return null; return v<=a?25.0:v<=b?50.0:v<=c?75.0:100.0;}
    private String valuationLabel(Double pe,Double pb){if((pe!=null&&pe>50)||(pb!=null&&pb>10))return "EXPENSIVE"; if((pe!=null&&pe>30)||(pb!=null&&pb>5))return "RICH"; if((pe!=null&&pe>0&&pe<18)&&(pb!=null&&pb<3))return "ATTRACTIVE"; return "FAIR";}
    private String summary(Double growth,Double profitability,Double cash,String valuation){return String.format("Growth %s/100, profitability %s/100, cash flow %s/100; valuation %s.",displayScore(growth),displayScore(profitability),displayScore(cash),valuation);}
    private String displayScore(Double value){return value == null ? "N/A" : String.format("%.0f", value);}
    private Double round(Double v){return v == null || !Double.isFinite(v)?null:Math.round(v*100.0)/100.0;}
    private FundamentalAnalysis unavailable(String note){return FundamentalAnalysis.builder().status("UNAVAILABLE").confidence("UNAVAILABLE").summary("Fundamental analysis is unavailable; technical analysis can continue normally.").dataNote(note).build();}
}
